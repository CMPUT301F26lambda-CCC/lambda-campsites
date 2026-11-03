/*
 * Purpose: verify Flow 6/7 using production navigation/repositories against local Firebase emulators.
 * Outstanding issues: manual TalkBack and team review remain before closing #123/#124.
 * AI use: OpenAI GPT-6 through Codex assisted with test setup and assertions; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.R
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.signInAsNewUser
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Real NavHost/controller/repository integration. Every read and write is guarded to the
 * local emulator host. Demo records are set up through the project's emulator helper;
 * no shared Firebase data, Cloud Functions or deployed rules are changed.
 */
class EntrantFirebaseFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var uid: String
    private lateinit var site: Campsite
    private lateinit var lottery: Lottery
    private val firestore get() = Firebase.firestore

    /** Seeds a new fictional campsite and lottery in this task's local emulator only. */
    @Before fun seedDemo() {
        check(firestore.firestoreSettings.host == "10.0.2.2:8080") {
            "Refusing integration test: Firestore is not the local emulator."
        }
        FirestoreEmulator.clear()
        uid = runBlocking { withTimeout(15_000) { signInAsNewUser() } }
        FirestoreEmulator.setIgnoringRules("profiles/$uid",
            ProfileFields.toMap(Profile(deviceId = uid, name = "Demo entrant", email = "demo@example.com", policyVersionAgreed = 1)))
        val suffix = UUID.randomUUID().toString().take(8)
        site = Campsite("flow67-site-$suffix", "demo-owner", "Emulator Demo Camp $suffix", "Fictional demo location",
            description = "Local test data only.", maxOccupancy = 4, amenities = listOf("Demo fire pit"))
        FirestoreEmulator.setIgnoringRules("campsites/${site.id}", CampsiteFields.toMap(site))
        val start = LocalDate.now().plusMonths(1)
        FirestoreEmulator.setIgnoringRules("campsites/${site.id}/availability/calendar", mapOf(
            "seasonStart" to start.toString(), "seasonEnd" to start.plusDays(30).toString(), "bookedNights" to emptyList<String>()))
        lottery = Lottery("flow67-lottery-$suffix", "demo-owner", "Open demo lottery $suffix", listOf(site.id),
            Timestamp(Instant.now().minusSeconds(3600)), Timestamp(Instant.now().plusSeconds(7200)),
            start, start.plusDays(10), numberOfWinners = 1, capacity = 5)
        saveLottery(lottery)
    }

    private fun saveLottery(value: Lottery, count: Int = 0) {
        FirestoreEmulator.setIgnoringRules("lotteries/${value.id}", LotteryFields.toMap(value) + ("entryCount" to count))
    }

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun awaitText(value: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openDetails() {
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp() } }
        awaitText(site.name)
        compose.onNodeWithText(text(R.string.flow6_search_label)).performTextInput(site.name)
        compose.onNode(hasText(site.name, substring = true) and !hasSetTextAction()).performClick()
        awaitText(lottery.name)
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().assertIsEnabled()
    }

    private fun entryOnServer() = runBlocking {
        withTimeout(15_000) { firestore.document("lotteries/${lottery.id}/entries/$uid").get(Source.SERVER).await() }
    }

    private fun countOnServer() = runBlocking {
        withTimeout(15_000) { firestore.document("lotteries/${lottery.id}").get(Source.SERVER).await().getLong("entryCount") }
    }

    @Test fun searchDetailsJoinLeavePersistThroughRealTransactionsAndBackRetainsSearch() {
        openDetails()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().performClick()
        awaitText(text(R.string.flow7_joined))
        compose.onNodeWithText(text(R.string.flow7_joined)).performScrollTo().assertIsDisplayed()
        assertEquals("WAITING", entryOnServer().getString("status"))
        assertEquals(1L, countOnServer())
        compose.onNodeWithText(text(R.string.flow7_leave)).performScrollTo().performClick()
        awaitText(text(R.string.flow7_left))
        assertFalse(entryOnServer().exists())
        assertEquals(0L, countOnServer())
        compose.onNodeWithContentDescription(text(R.string.flow6_back)).performClick()
        compose.onNodeWithText(text(R.string.flow6_search_label)).assertTextContains(site.name)
    }

    @Test fun closedRegistrationShowsMessageWithoutCreatingAnEntry() {
        lottery = lottery.copy(registrationCloses = Timestamp(Instant.now().minusSeconds(60)))
        saveLottery(lottery)
        openDetails()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().performClick()
        awaitText(text(R.string.flow7_closed))
        compose.onNodeWithText(text(R.string.flow7_closed)).performScrollTo().assertIsDisplayed()
        assertFalse(entryOnServer().exists())
        assertEquals(0L, countOnServer())
    }

    @Test fun fullWaitingListShowsMessageWithoutChangingItsCount() {
        lottery = lottery.copy(capacity = 1)
        saveLottery(lottery, count = 1)
        FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}/entries/demo-other-entrant", mapOf(
            "entrantId" to "demo-other-entrant", "joinedAt" to Timestamp.now(), "status" to "WAITING", "reservationId" to null))
        openDetails()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().performClick()
        awaitText(text(R.string.flow7_full))
        compose.onNodeWithText(text(R.string.flow7_full)).performScrollTo().assertIsDisplayed()
        assertFalse(entryOnServer().exists())
        assertEquals(1L, countOnServer())
    }
}
