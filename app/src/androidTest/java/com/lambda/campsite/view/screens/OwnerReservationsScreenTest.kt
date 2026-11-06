/*
 * Purpose: Checks Flow 11 owner reservation and booked-date screens in isolated Compose tests.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, helped write isolated Compose tests
 * for Flow 11's owner reservation list and calendar (US 02.06.01 and
 * 02.07.01 to 02.07.03). Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerBookedDatesUiState
import com.lambda.campsite.controller.OwnerReservationTab
import com.lambda.campsite.controller.OwnerReservationsUiState
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.components.OwnerReservationDetailsSheet
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Checks Flow 11's visible lists, empty messages and booked-day action. */
class OwnerReservationsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val today = LocalDate.of(2027, 7, 5)
    private val stay = Reservation(
        id = "booking-1", guestId = "guest-1", guestName = "Jordan Lee",
        ownerId = "owner-1", campsiteId = "site-1", campsiteName = "Aspen Hollow",
        lotteryId = "lottery-1", checkIn = today.plusDays(1), checkOut = today.plusDays(3),
        guests = 2, rulesAgreedAt = Timestamp.now(), createdAt = Timestamp.now(),
    )

    @Test fun ownerHomeOpensFigmaReservationAndCalendarEntries() {
        val state = OwnerReservationsUiState(reservations = listOf(stay), today = today, isLoading = false)
        var selected: OwnerReservationTab? = null
        var openedCalendar = false
        compose.setContent { LambdaCampsiteTheme {
            OwnerHomeContent(state, "Demo Owner", { selected = it }, { openedCalendar = true }, {})
        } }

        compose.onNodeWithText("Welcome back, Demo").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reservation_tab_active)).performClick()
        compose.runOnIdle { assertEquals(OwnerReservationTab.ACTIVE, selected) }
        compose.onNodeWithText(text(R.string.booked_dates_title)).performClick()
        compose.runOnIdle { assertEquals(true, openedCalendar) }
    }

    @Test fun activeAndCancelledTabsShowOnlyTheirReservations() {
        val state = mutableStateOf(
            OwnerReservationsUiState(
                reservations = listOf(
                    stay,
                    stay.copy(id = "past", checkIn = today.minusDays(3), checkOut = today),
                    stay.copy(id = "cancelled", status = ReservationStatus.CANCELLED),
                ),
                today = today,
                isLoading = false,
            )
        )
        var selected: String? = null
        compose.setContent { LambdaCampsiteTheme {
            OwnerReservationsContent(
                state.value,
                onTabSelected = { state.value = state.value.copy(selectedTab = it) },
                onReservationSelected = { selected = it },
                onRetry = {},
            )
        } }

        compose.onNodeWithText("Aspen Hollow").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(stay.id, selected) }
        compose.onNodeWithText(text(R.string.reservation_tab_past)).performClick()
        compose.onNodeWithText("Aspen Hollow").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("past", selected) }
        compose.onNodeWithText(text(R.string.reservation_tab_cancelled)).performClick()
        compose.onNodeWithText("Aspen Hollow").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("cancelled", selected) }
    }

    @Test fun emptyListsShowStorySpecificMessages() {
        val state = mutableStateOf(OwnerReservationsUiState(today = today, isLoading = false))
        compose.setContent { LambdaCampsiteTheme {
            OwnerReservationsContent(state.value, { state.value = state.value.copy(selectedTab = it) }, {}, {})
        } }
        compose.onNodeWithText(text(R.string.no_active_reservations)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reservation_tab_past)).performClick()
        compose.onNodeWithText(text(R.string.no_past_reservations)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reservation_tab_cancelled)).performClick()
        compose.onNodeWithText(text(R.string.no_cancelled_reservations)).assertIsDisplayed()
    }

    @Test fun bookedDateOpensDetailsAndEmptyMonthHasMessage() {
        val date = today.plusDays(1)
        val camp = Campsite("site-1", "owner-1", "Aspen Hollow", "Alberta", maxOccupancy = 4, description = "Forest")
        val calendar = Availability(today.minusMonths(1), today.plusMonths(3), setOf(date))
        val state = mutableStateOf(
            OwnerBookedDatesUiState(
                campsite = camp,
                availability = calendar,
                reservations = listOf(stay),
                month = YearMonth.from(date),
                isLoading = false,
            )
        )
        var selectedDate: LocalDate? = null
        compose.setContent { LambdaCampsiteTheme {
            OwnerBookedDatesContent(
                state.value,
                onBack = {},
                onPreviousMonth = { state.value = state.value.copy(month = state.value.month.minusMonths(1)) },
                onNextMonth = { state.value = state.value.copy(month = state.value.month.plusMonths(1)) },
                onBookedDateSelected = { selectedDate = it },
                onRetry = {},
            )
        } }
        compose.onNodeWithContentDescription("booked. Double tap", substring = true).performClick()
        compose.runOnIdle { assertEquals(date, selectedDate) }
        compose.onNodeWithContentDescription(text(R.string.next_month)).performClick()
        compose.onNodeWithText(text(R.string.no_bookings_this_month)).assertIsDisplayed()
    }

    @Test fun switchingCampsitesShowsLoadingUntilBookingsArrive() {
        val second = Campsite("site-2", "owner-1", "Cedar Point", "Alberta", maxOccupancy = 4, description = "Lake")
        val first = Campsite("site-1", "owner-1", "Aspen Hollow", "Alberta", maxOccupancy = 4, description = "Forest")
        val night = today.plusDays(4)
        val state = mutableStateOf(OwnerBookedDatesUiState(
            campsite = first, campsites = listOf(first, second), selectedCampsiteId = first.id,
            availability = Availability(today.minusMonths(1), today.plusMonths(3)),
            month = YearMonth.from(today), isLoading = false,
        ))
        compose.setContent { LambdaCampsiteTheme {
            OwnerBookedDatesContent(state.value, {}, {}, {}, {}, {}, onCampsiteSelected = { id ->
                state.value = state.value.copy(campsite = second, selectedCampsiteId = id,
                    availability = null, isLoading = true)
            })
        } }

        compose.onNodeWithText(first.name).performClick()
        compose.onNodeWithText(second.name).performClick()
        compose.onNodeWithText(text(R.string.calendar_loading)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.no_bookings_this_month)).assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                availability = Availability(today.minusMonths(1), today.plusMonths(3), setOf(night)),
                isLoading = false,
            )
        }
        compose.onNodeWithText(text(R.string.calendar_loading)).assertDoesNotExist()
        compose.onNodeWithContentDescription("booked. Double tap", substring = true).assertExists()
    }

    @Test fun datesOutsideSeasonOrWithoutSeasonAreNeverAnnouncedAvailable() {
        val camp = Campsite("site-1", "owner-1", "Aspen Hollow", "Alberta", maxOccupancy = 4, description = "Forest")
        val open = LocalDate.of(2027, 7, 10)
        val close = LocalDate.of(2027, 7, 12)
        val state = mutableStateOf(OwnerBookedDatesUiState(campsite = camp,
            availability = Availability(open, close), month = YearMonth.from(open), isLoading = false))
        compose.setContent { LambdaCampsiteTheme {
            OwnerBookedDatesContent(state.value, {}, {}, {}, {}, {})
        } }
        fun description(id: Int, date: LocalDate): String =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(id,
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))

        compose.onNodeWithContentDescription(description(R.string.outside_season_date_description, open.minusDays(1))).assertExists()
        compose.onNodeWithContentDescription(description(R.string.available_date_description, open)).assertExists()
        compose.onNodeWithContentDescription(description(R.string.outside_season_date_description, close)).assertExists()
        compose.runOnIdle { state.value = state.value.copy(availability = null) }
        compose.onNodeWithContentDescription(description(R.string.no_season_date_description, open)).assertExists()
        compose.onNodeWithContentDescription(description(R.string.available_date_description, open)).assertDoesNotExist()
    }

    @Test fun pastReservationCardAndDetailsBothSayCompleted() {
        val past = stay.copy(checkIn = today.minusDays(3), checkOut = today)
        val state = OwnerReservationsUiState(reservations = listOf(past), today = today,
            selectedTab = OwnerReservationTab.PAST, isLoading = false)
        val selected = mutableStateOf(false)
        compose.setContent { LambdaCampsiteTheme {
            if (selected.value) OwnerReservationDetailsSheet(past, onDismiss = {},
                completed = past.isCompleted(state.today))
            else OwnerReservationsContent(state, {}, { selected.value = true }, {}, {})
        } }

        compose.onNodeWithText(text(R.string.reservation_status_completed)).assertIsDisplayed()
        compose.onNodeWithText(past.campsiteName).performClick()
        compose.onNodeWithText(text(R.string.reservation_details_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reservation_status_completed)).assertExists()
        compose.onNodeWithText(text(R.string.reservation_status_confirmed)).assertDoesNotExist()
    }

    @Test fun detailsSheetShowsEntrantDatesAndStatus() {
        var dismissed = false
        compose.setContent { LambdaCampsiteTheme { OwnerReservationDetailsSheet(stay, onDismiss = { dismissed = true }) } }
        compose.onNodeWithText(text(R.string.reservation_details_title)).assertIsDisplayed()
        compose.onNodeWithText(stay.guestName).assertIsDisplayed()
        compose.onNodeWithText(stay.campsiteName).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.reservation_status_confirmed)).assertExists()
        compose.onNodeWithText(stay.guests.toString()).assertExists()
        compose.onNodeWithContentDescription(text(R.string.close_reservation_details)).performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }
}
