/*
 * Purpose: full Flow 9 navigation and stored receipt/decline against local Firebase emulators.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack/device review remains pending (#126).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with emulator fixtures and assertions; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.R
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.*
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Refuses production Firestore; checks live reads and transactional writes with the project's rules. */
class WinnerBookingFirebaseTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var uid: String
    private val start = LocalDate.now().plusMonths(1).withDayOfMonth(1)
    private val camp = Campsite("flow9-camp", "demo-owner", "Flow 9 Demo Campsite", "Demo location", maxOccupancy = 4, rules = "Demo campsite rules.")
    private val lottery = Lottery("flow9-lottery", "demo-owner", "Flow 9 Demo Lottery", listOf(camp.id), Timestamp(1, 0), Timestamp(2, 0), start, start.plusDays(20), status = LotteryStatus.DRAWN)
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun awaitText(value: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty() }
    @Before fun seed() {
        check(Firebase.firestore.firestoreSettings.host == "10.0.2.2:8080") { "Refusing non-emulator Firestore" }
        FirestoreEmulator.clear()
        uid = runBlocking { withTimeout(15_000) { signInAsNewUser() } }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(Profile(deviceId = uid, name = "Demo Winner", email = "winner@example.com", policyVersionAgreed = 1)))
        FirestoreEmulator.setIgnoringRules("campsites/${camp.id}", CampsiteFields.toMap(camp))
        FirestoreEmulator.setIgnoringRules("campsites/${camp.id}/availability/calendar", mapOf("seasonStart" to start.toString(), "seasonEnd" to start.plusDays(21).toString()))
        FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}", LotteryFields.toMap(lottery))
        FirestoreEmulator.setIgnoringRules("lotteries/${lottery.id}/entries/$uid", LotteryEntryFields.toMap(LotteryEntry(lottery.id, uid, Timestamp(1, 0), EntryStatus.WON)))
    }
    private fun open() {
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp() } }
        awaitText(text(R.string.tab_reservations))
        compose.onNodeWithText(text(R.string.tab_reservations)).performClick()
        awaitText(text(R.string.flow9_wins_title))
        compose.onNodeWithText(text(R.string.flow9_wins_title)).performClick()
        awaitText(lottery.name)
        compose.onNodeWithText(text(R.string.flow9_open_win)).performClick()
        awaitText(text(R.string.flow9_dates_heading))
    }
    private fun server(path: String) = runBlocking { withTimeout(15_000) {
        Firebase.firestore.document(path).get(Source.SERVER).await()
    } }
    @Test fun reservationConfirmationMatchesStoredReservationAndConsumesWin() {
        open()
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        compose.onNodeWithContentDescription(start.format(format), substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription(start.plusDays(2).format(format), substring = true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_continue)).performScrollTo().performClick()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_book)).performScrollTo().performClick()
        awaitText(text(R.string.flow9_all_set))
        val entry = server("lotteries/${lottery.id}/entries/$uid")
        assertEquals("BOOKED", entry.getString("status"))
        val id = requireNotNull(entry.getString("reservationId"))
        val receipt = server("reservations/$id")
        assertEquals(uid, receipt.getString("guestId"))
        assertEquals(start.toString(), receipt.getString("checkIn"))
        assertEquals(start.plusDays(2).toString(), receipt.getString("checkOut"))
        compose.onNodeWithText("Reservation number: ${id.take(8).uppercase()}").performScrollTo().assertIsDisplayed()
        assertEquals(listOf(start.toString(), start.plusDays(1).toString()), server("campsites/${camp.id}/availability/calendar").get("bookedNights"))
    }
    @Test fun confirmedDeclinePersistsInHistoryWithoutReservationOrHeldDates() {
        open()
        compose.onNodeWithText(text(R.string.flow9_decline)).performScrollTo().performClick()
        compose.onAllNodesWithText(text(R.string.flow9_decline)).onLast().performClick()
        awaitText(text(R.string.flow9_declined))
        assertEquals("DECLINED", server("lotteries/${lottery.id}/entries/$uid").getString("status"))
        assertNull(server("campsites/${camp.id}/availability/calendar").get("bookedNights"))
        compose.onNodeWithText(text(R.string.flow9_done)).performClick()
        awaitText(text(R.string.flow9_history_declined))
        compose.onNodeWithText(text(R.string.flow9_history_declined)).assertIsDisplayed()
        val bookings = runBlocking { withTimeout(15_000) { Firebase.firestore.collection("reservations").whereEqualTo("guestId", uid).get(Source.SERVER).await() } }
        assertTrue(bookings.isEmpty)
    }
}
