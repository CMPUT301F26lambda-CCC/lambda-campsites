/*
 * Feature owner: Sreeman Reddy. Purpose: Flow 11 app navigation and live
 * owner reservation/calendar integration against local Firebase emulators.
 * AI use: GPT-6 (OpenAI), through Codex, helped write the emulator fixtures,
 * navigation assertions and live cancellation check. Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.Role
import com.lambda.campsite.model.repository.CampsiteFields
import com.lambda.campsite.model.repository.FirestoreProfileRepository
import com.lambda.campsite.model.repository.ProfileFields
import com.lambda.campsite.model.repository.ReservationFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.signInAsNewUser
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Verifies Flow 11's actual owner navigation, scoped lists and live calendar (US 02.06.01–02.07.03). */
class OwnerReservationsFirebaseTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var ownerId: String
    private lateinit var campsite: Campsite
    private lateinit var booked: Reservation
    private lateinit var bookedNight: LocalDate

    @Before fun seed() {
        check(Firebase.firestore.firestoreSettings.host == "10.0.2.2:8080") { "Local emulator required" }
        FirestoreEmulator.clear()
        ownerId = runBlocking { withTimeout(15_000) { signInAsNewUser() } }
        val suffix = UUID.randomUUID().toString()
        val today = LocalDate.now()
        bookedNight = today.plusDays(2)
        FirestoreEmulator.setIgnoringRules(
            "profiles/$ownerId",
            ProfileFields.toMap(Profile(ownerId, "Demo Owner", "owner@example.com",
                roles = setOf(Role.ENTRANT, Role.OWNER))),
        )
        campsite = Campsite("site-$suffix", ownerId, "Aspen Demo", "Alberta", maxOccupancy = 4,
            description = "Fictional test campsite")
        FirestoreEmulator.setIgnoringRules("campsites/${campsite.id}", CampsiteFields.toMap(campsite))
        setCalendar(listOf(bookedNight.toString()))
        booked = reservation("active-$suffix", "Jordan Demo", bookedNight, bookedNight.plusDays(2))
        val past = reservation("past-$suffix", "Alex Demo", today.minusDays(3), today.minusDays(1))
        val cancelled = reservation("cancelled-$suffix", "Casey Demo", today.plusDays(5), today.plusDays(7))
            .copy(status = ReservationStatus.CANCELLED, cancelledAt = Timestamp.now())
        val foreign = booked.copy(id = "foreign-$suffix", ownerId = "another-owner", guestName = "Private Guest")
        listOf(booked, past, cancelled, foreign).forEach {
            FirestoreEmulator.setIgnoringRules("reservations/${it.id}", ReservationFields.toMap(it))
        }
    }

    private fun reservation(id: String, guestName: String, checkIn: LocalDate, checkOut: LocalDate) =
        Reservation(
            id = id, guestId = "guest-$id", guestName = guestName, ownerId = ownerId,
            campsiteId = campsite.id, campsiteName = campsite.name, lotteryId = "lottery-demo",
            checkIn = checkIn, checkOut = checkOut, guests = 2,
            rulesAgreedAt = Timestamp.now(), createdAt = Timestamp.now(),
        )

    private fun setCalendar(nights: List<String>) {
        FirestoreEmulator.setIgnoringRules(
            "campsites/${campsite.id}/availability/calendar",
            mapOf(
                "seasonStart" to bookedNight.minusMonths(1).toString(),
                "seasonEnd" to bookedNight.plusMonths(2).toString(),
                "bookedNights" to nights,
            ),
        )
    }

    @Test fun ownerSeesScopedListsAndCalendarUpdatesAfterCancellation() {
        lateinit var profile: ProfileController
        compose.runOnUiThread { profile = ProfileController(FirestoreProfileRepository(Firebase.auth, Firebase.firestore)) }
        compose.waitUntil(15_000) { profile.state.value.profile?.deviceId == ownerId }
        compose.runOnUiThread { profile.onSwitchRole(Role.OWNER) }
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp(profile) } }

        compose.waitUntil(15_000) { compose.onAllNodesWithText("Jordan Demo", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Private Guest", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Aspen Demo").performClick()
        compose.onNodeWithText("Jordan Demo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close reservation details").performClick()

        compose.onNodeWithText("Past").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Alex Demo", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Jordan Demo", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Cancelled").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Casey Demo", substring = true).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Campsites").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(campsite.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(campsite.name).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Booked dates").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Booked dates").performClick()
        if (YearMonth.from(bookedNight) != YearMonth.now()) {
            compose.onNodeWithContentDescription("Next month").performClick()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("booked. Double tap", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("booked. Double tap", substring = true).performClick()
        compose.onNodeWithText("Jordan Demo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close reservation details").performClick()

        FirestoreEmulator.setIgnoringRules("reservations/${booked.id}",
            ReservationFields.toMap(booked.copy(status = ReservationStatus.CANCELLED, cancelledAt = Timestamp.now())))
        setCalendar(emptyList())
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("No bookings for this month.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No bookings for this month.").assertIsDisplayed()
    }
}
