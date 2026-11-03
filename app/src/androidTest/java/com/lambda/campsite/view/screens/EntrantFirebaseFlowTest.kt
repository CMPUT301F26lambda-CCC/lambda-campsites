/*
 * Purpose: verify Flow 6/7 using production navigation/repositories against local Firebase emulators.
 * Outstanding issues: manual TalkBack and team review remain for follow-up #149.
 * AI use: OpenAI GPT-6 through Codex assisted with test setup and assertions; human review pending.
  * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Layout follow-up (#154): Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI),
 * through Codex, assisted with Figma layout/status/navigation-color integration
 * or its regression checks. Follow-up human review remains pending.
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
import java.io.File
import android.graphics.Bitmap
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
            description = "Local test data only. A wooded demo campsite beside a fictional creek.", maxOccupancy = 4, amenities = listOf("Demo fire pit"),
            tags = listOf("Forest", "Creekside"), pricePerNight = 35.0, rules = "Respect quiet hours.\nKeep the campsite clean.")
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

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "flow67-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun awaitNoticeDismissed(value: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isEmpty() }
    }

    private fun openDetails(expectEnabled: Boolean = true) {
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp() } }
        awaitText(site.name)
        awaitText(text(R.string.campsite_open_badge))
        capture("search")
        compose.onNodeWithText(text(R.string.entrant_campsite_search_label)).performTextInput(site.name)
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_search_action)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_view_details)).performClick()
        awaitText(lottery.name)
        capture("campsite-top")
        compose.onNodeWithContentDescription(text(R.string.campsite_map_placeholder_description)).performScrollTo()
        capture("campsite-map")
        compose.onNodeWithText(text(R.string.entrant_campsite_join_active)).performScrollTo().performClick()
        awaitText(text(R.string.entrant_lottery_deadline_label))
        capture("lottery-details")
        val button = compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo()
        if (expectEnabled) button.assertIsEnabled() else button.assertIsNotEnabled()
    }

    private fun entryOnServer() = runBlocking {
        withTimeout(15_000) { firestore.document("lotteries/${lottery.id}/entries/$uid").get(Source.SERVER).await() }
    }

    private fun countOnServer() = runBlocking {
        withTimeout(15_000) { firestore.document("lotteries/${lottery.id}").get(Source.SERVER).await().getLong("entryCount") }
    }

    @Test fun searchDetailsJoinLeavePersistThroughRealTransactionsAndBackRetainsSearch() {
        openDetails()
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().performClick()
        awaitText(text(R.string.entrant_lottery_joined))
        compose.onNodeWithText(text(R.string.entrant_lottery_joined)).assertIsDisplayed()
        assertEquals("WAITING", entryOnServer().getString("status"))
        assertEquals(1L, countOnServer())
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo()
        val noticeBottom = compose.onNodeWithText(text(R.string.entrant_lottery_joined)).fetchSemanticsNode().boundsInRoot.bottom
        val actionTop = compose.onNodeWithText(text(R.string.entrant_lottery_leave)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Snackbar must be above the withdrawal action", noticeBottom <= actionTop)
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo().performClick()
        awaitText(text(R.string.entrant_lottery_cancel))
        capture("withdraw-dialog")
        compose.onNodeWithText(text(R.string.entrant_lottery_cancel)).performClick()
        assertTrue(entryOnServer().exists())
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_confirm_withdraw)).performClick()
        awaitText(text(R.string.entrant_lottery_left))
        assertFalse(entryOnServer().exists())
        assertEquals(0L, countOnServer())
        compose.onNodeWithContentDescription(text(R.string.entrant_lottery_details_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_back)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_search_label)).assertTextContains(site.name)
    }

    @Test fun closedRegistrationDisablesJoinWithoutCreatingAnEntry() {
        openDetails()
        lottery = lottery.copy(registrationCloses = Timestamp(Instant.now().minusSeconds(60)))
        saveLottery(lottery)
        awaitText(text(R.string.entrant_lottery_status_closed))
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().assertIsNotEnabled()
        assertFalse(entryOnServer().exists())
        assertEquals(0L, countOnServer())
    }

    @Test fun fullWaitingListDisablesJoinWithoutChangingItsCount() {
        lottery = lottery.copy(capacity = 1)
        saveLottery(lottery, count = 1)
        FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}/entries/demo-other-entrant", mapOf(
            "entrantId" to "demo-other-entrant", "joinedAt" to Timestamp.now(), "status" to "WAITING", "reservationId" to null))
        openDetails(expectEnabled = false)
        compose.onNodeWithText(text(R.string.entrant_lottery_full)).performScrollTo().assertIsDisplayed()
        capture("full-waiting-list")
        assertFalse(entryOnServer().exists())
        assertEquals(1L, countOnServer())
    }

    @Test fun upcomingAndDrawnUpdatesDisableJoinWithReasons() {
        openDetails()
        lottery = lottery.copy(registrationOpens = Timestamp(Instant.now().plusSeconds(3600)))
        saveLottery(lottery)
        awaitText(text(R.string.entrant_lottery_status_upcoming))
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().assertIsNotEnabled()
        lottery = lottery.copy(status = LotteryStatus.DRAWN)
        saveLottery(lottery)
        awaitText(text(R.string.entrant_lottery_status_drawn))
        compose.onNodeWithText(text(R.string.entrant_lottery_drawn_reason)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().assertIsNotEnabled()
        assertFalse(entryOnServer().exists())
    }

    @Test fun reopeningJoinedLotteryShowsMembershipAndClosingDisablesWithdrawal() {
        openDetails()
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().performClick()
        awaitText(text(R.string.entrant_lottery_joined))
        compose.onNodeWithContentDescription(text(R.string.entrant_lottery_details_back)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_join_active)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo().assertIsEnabled()
        lottery = lottery.copy(registrationCloses = Timestamp(Instant.now().minusSeconds(60)))
        saveLottery(lottery, count = 1)
        awaitText(text(R.string.entrant_lottery_withdraw_closed))
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo().assertIsNotEnabled()
        assertTrue(entryOnServer().exists())
        assertEquals(1L, countOnServer())
    }

    @Test fun unpublishingOpenCampsiteHidesRegistrationAndShowsUnavailable() {
        openDetails()
        FirestoreEmulator.setIgnoringRules("campsites/${site.id}", CampsiteFields.toMap(site.copy(published = false)))
        awaitText(text(R.string.entrant_campsite_not_available))
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.entrant_campsite_load_failed)).assertDoesNotExist()
    }
}
