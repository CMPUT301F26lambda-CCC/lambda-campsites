/*
 * Purpose: Checks Flow 10 reservation tabs, details, cancellation confirmation, and terminal states in Compose.
 * Outstanding issues: None known.
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with these UI tests.
 * Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.EntrantReservationTab
import com.lambda.campsite.controller.EntrantReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies Flow 10's visible tabs, card statuses, details and confirmation. */
class EntrantReservationsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2027, 7, 5)
    private val stay = Reservation("reservation-1", "guest-1", "Jordan Lee", "owner-1", "site-1",
        "Aspen Hollow", "lottery-1", today.plusDays(1), today.plusDays(3), 2,
        Timestamp.now(), Timestamp.now())
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun activeAndPastTabsOpenTheCorrectReservationAndStatus() {
        val past = stay.copy(id = "past", checkIn = today.minusDays(4), checkOut = today)
        val cancelled = stay.copy(id = "cancelled", status = ReservationStatus.CANCELLED)
        val state = mutableStateOf(EntrantReservationsUiState(
            reservations = listOf(stay, past, cancelled), today = today, isLoading = false))
        var opened: String? = null
        compose.setContent { LambdaCampsiteTheme {
            EntrantReservationsContent(state.value,
                onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                onReservationClick = { opened = it }, onBrowseCampsites = {}, onRetry = {})
        } }

        compose.onNodeWithText(text(R.string.flow10_status_confirmed)).assertIsDisplayed()
        compose.onNodeWithText(stay.campsiteName).performClick()
        compose.runOnIdle { assertEquals(stay.id, opened) }
        compose.onNodeWithText(text(R.string.flow10_past_tab)).performClick()
        compose.onNodeWithText(text(R.string.flow10_status_completed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_status_cancelled)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_status_confirmed)).assertDoesNotExist()
    }

    @Test fun emptyActiveTabOffersBrowseWithoutShowingAnEarlyEmptyFeed() {
        val state = mutableStateOf(EntrantReservationsUiState(today = today))
        var browse = false
        compose.setContent { LambdaCampsiteTheme {
            EntrantReservationsContent(state.value, {}, {}, { browse = true }, {})
        } }
        compose.onNodeWithText(text(R.string.flow10_no_active)).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isLoading = false) }
        compose.onNodeWithText(text(R.string.flow10_no_active)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_browse_campsites)).performClick()
        compose.runOnIdle { assertEquals(true, browse) }
    }

    @Test fun detailsShowConfirmationAndCompletionConsistently() {
        val state = mutableStateOf(EntrantReservationsUiState(
            reservations = listOf(stay), today = today, isLoading = false))
        compose.setContent { LambdaCampsiteTheme {
            EntrantReservationDetailsContent(stay.id, state.value, "Elk Island National Park, AB",
                onCancelRequested = { state.value = state.value.copy(confirmingId = it) },
                onCancelDismissed = { state.value = state.value.copy(confirmingId = null) },
                onCancelConfirmed = {}, onBack = {})
        } }

        compose.onNodeWithText(text(R.string.flow10_status_confirmed)).assertIsDisplayed()
        compose.onNodeWithText("Elk Island National Park, AB").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_cancel_reservation)).performClick()
        compose.onNodeWithText(text(R.string.flow10_cancel_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_keep_reservation)).performClick()
        compose.onNodeWithText(text(R.string.flow10_cancel_title)).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(reservations = listOf(stay.copy(
            checkIn = today.minusDays(3), checkOut = today))) }
        compose.onNodeWithText(text(R.string.flow10_status_completed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow10_cancel_reservation)).assertDoesNotExist()
    }
}
