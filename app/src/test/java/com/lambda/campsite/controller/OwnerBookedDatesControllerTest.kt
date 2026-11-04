/*
 * AI use: GPT-6 (OpenAI), through Codex, helped write Flow 11 calendar tests
 * for month navigation, booked dates and live cancellation updates.
 * Human review is pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.FakeReservationRepository
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.ReservationRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests the owner calendar for US 02.06.01. */
class OwnerBookedDatesControllerTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val july = YearMonth.of(2027, 7)
    private val firstNight = LocalDate.of(2027, 7, 8)
    private val camp = Campsite(
        id = "site-1", ownerId = "owner-1", name = "Aspen Hollow",
        location = "Alberta", description = "Forest", maxOccupancy = 4,
    )
    private val booking = Reservation(
        id = "booking-1", guestId = "guest-1", guestName = "Jordan Lee",
        ownerId = "owner-1", campsiteId = "site-1", campsiteName = camp.name,
        lotteryId = "lottery-1", checkIn = firstNight, checkOut = firstNight.plusDays(2),
        guests = 2, rulesAgreedAt = Timestamp.now(), createdAt = Timestamp.now(),
    )
    private val calendar = Availability(
        seasonStart = LocalDate.of(2027, 5, 1),
        seasonEnd = LocalDate.of(2027, 10, 1),
        bookedNights = setOf(firstNight, firstNight.plusDays(1)),
    )

    @Test fun `calendar shows booked nights and opens their reservation`() {
        val campsites = FakeCampsiteRepository(initial = listOf(camp), initialCalendars = mapOf(camp.id to calendar))
        val reservations = FakeReservationRepository(initial = listOf(booking))
        val controller = OwnerBookedDatesController(camp.id, campsites, reservations, july)

        assertEquals(false, controller.state.value.isLoading)
        assertEquals(calendar.bookedNights, controller.state.value.bookedThisMonth)
        controller.onBookedDateSelected(firstNight.plusDays(1))
        assertEquals("booking-1", controller.state.value.selectedReservation?.id)
        assertEquals("Jordan Lee", controller.state.value.selectedReservation?.guestName)
        controller.onNextMonth()
        assertEquals(YearMonth.of(2027, 8), controller.state.value.month)
        assertEquals(emptySet<LocalDate>(), controller.state.value.bookedThisMonth)
        assertNull(controller.state.value.selectedReservation)
        controller.onPreviousMonth()
        assertEquals(july, controller.state.value.month)
    }

    @Test fun `cancelling elsewhere clears the booked dates and their details`() {
        val campsites = FakeCampsiteRepository(initial = listOf(camp), initialCalendars = mapOf(camp.id to calendar))
        val reservations = FakeReservationRepository(initial = listOf(booking))
        val controller = OwnerBookedDatesController(camp.id, campsites, reservations, july)
        controller.onBookedDateSelected(firstNight)

        reservations.changeElsewhere(booking.copy(status = ReservationStatus.CANCELLED, cancelledAt = Timestamp.now()))
        campsites.changeCalendarElsewhere(camp.id, calendar.copy(bookedNights = emptySet()))

        assertEquals(emptySet<LocalDate>(), controller.state.value.bookedThisMonth)
        assertNull(controller.state.value.selectedReservation)
    }

    @Test fun `selector changes calendars only among owned campsites`() {
        val second = camp.copy(id = "site-2", name = "Cedar Point")
        val foreign = camp.copy(id = "foreign", ownerId = "other-owner")
        val secondNight = firstNight.plusDays(5)
        val secondCalendar = calendar.copy(bookedNights = setOf(secondNight))
        val secondBooking = booking.copy(id = "second-booking", campsiteId = second.id,
            campsiteName = second.name, checkIn = secondNight, checkOut = secondNight.plusDays(1))
        val campsites = FakeCampsiteRepository(initial = listOf(camp, second, foreign),
            initialCalendars = mapOf(camp.id to calendar, second.id to secondCalendar))
        val reservations = FakeReservationRepository(initial = listOf(booking, secondBooking))
        val controller = OwnerBookedDatesController(camp.id, campsites, reservations, july)

        assertEquals(listOf(camp.id, second.id), controller.state.value.campsites.map { it.id })
        controller.onCampsiteSelected(foreign.id)
        assertEquals(camp.id, controller.state.value.selectedCampsiteId)
        controller.onCampsiteSelected(second.id)
        assertEquals(second.id, controller.state.value.selectedCampsiteId)
        assertEquals(setOf(secondNight), controller.state.value.bookedThisMonth)
        controller.onBookedDateSelected(secondNight)
        assertEquals(secondBooking.id, controller.state.value.selectedReservation?.id)
    }

    @Test fun `selected campsite stays loading until both calendar streams arrive`() {
        val second = camp.copy(id = "site-2", name = "Cedar Point")
        val secondNight = firstNight.plusDays(5)
        val secondCalendar = calendar.copy(bookedNights = setOf(secondNight))
        val secondBooking = booking.copy(id = "booking-2", campsiteId = second.id,
            campsiteName = second.name, checkIn = secondNight, checkOut = secondNight.plusDays(1))
        val campsiteGate = CompletableDeferred<Unit>()
        val reservationGate = CompletableDeferred<Unit>()
        val baseCampsites = FakeCampsiteRepository(initial = listOf(camp, second),
            initialCalendars = mapOf(camp.id to calendar))
        val baseReservations = FakeReservationRepository(initial = listOf(booking))
        val campsites = object : CampsiteRepository by baseCampsites {
            override fun availability(campsiteId: String): Flow<Availability?> =
                if (campsiteId == second.id) flow {
                    campsiteGate.await()
                    emit(secondCalendar)
                } else baseCampsites.availability(campsiteId)
        }
        val reservations = object : ReservationRepository by baseReservations {
            override fun campsiteReservations(campsiteId: String): Flow<List<Reservation>> =
                if (campsiteId == second.id) flow {
                    reservationGate.await()
                    emit(listOf(secondBooking))
                } else baseReservations.campsiteReservations(campsiteId)
        }
        val controller = OwnerBookedDatesController(camp.id, campsites, reservations, july)

        assertFalse(controller.state.value.isLoading)
        controller.onCampsiteSelected(second.id)
        assertTrue(controller.state.value.isLoading)
        assertNull(controller.state.value.availability)
        campsiteGate.complete(Unit)
        assertTrue(controller.state.value.isLoading)
        reservationGate.complete(Unit)
        assertFalse(controller.state.value.isLoading)
        assertEquals(setOf(secondNight), controller.state.value.bookedThisMonth)
        assertEquals(secondBooking, controller.state.value.reservations.single())
    }

    @Test fun `available nights require a configured season and exclude checkout and booked dates`() {
        val state = OwnerBookedDatesUiState(availability = calendar)
        assertFalse(state.isAvailableNight(calendar.seasonStart.minusDays(1)))
        assertTrue(state.isAvailableNight(calendar.seasonStart))
        assertFalse(state.isAvailableNight(firstNight))
        assertTrue(state.isAvailableNight(calendar.seasonEnd.minusDays(1)))
        assertFalse(state.isAvailableNight(calendar.seasonEnd))
        assertFalse(state.copy(availability = null).isAvailableNight(firstNight.plusDays(5)))
    }
}
