/*
 * AI use: GPT-6 (OpenAI), through Codex, helped write Flow 11 controller
 * tests for reservation groups, dates, selection and live updates.
 * Human review is pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.FakeReservationRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Tests owner reservation lists for US 02.07.01 to 02.07.03. */
class OwnerReservationsControllerTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2027, 7, 5)

    private fun reservation(id: String, checkIn: LocalDate, checkOut: LocalDate, status: ReservationStatus) =
        Reservation(
            id = id,
            guestId = "guest-1",
            guestName = "Jordan Lee",
            ownerId = "owner-1",
            campsiteId = "site-1",
            campsiteName = "Aspen Hollow",
            lotteryId = "lottery-1",
            checkIn = checkIn,
            checkOut = checkOut,
            guests = 2,
            rulesAgreedAt = Timestamp.now(),
            createdAt = Timestamp.now(),
            status = status,
        )

    @Test fun `groups only active past and guest cancelled reservations`() {
        val active = reservation("active", today, today.plusDays(2), ReservationStatus.CONFIRMED)
        val past = reservation("past", today.minusDays(3), today, ReservationStatus.CONFIRMED)
        val cancelled = reservation("cancelled", today.plusDays(3), today.plusDays(5), ReservationStatus.CANCELLED)
        val rejected = reservation("rejected", today.plusDays(3), today.plusDays(5), ReservationStatus.REJECTED)
        val otherOwner = active.copy(id = "other-owner", ownerId = "owner-2")
        val repository = FakeReservationRepository(myUserId = "owner-1", initial = listOf(active, past, cancelled, rejected, otherOwner))
        val controller = OwnerReservationsController(repository) { today }

        assertEquals(listOf("active"), controller.state.value.active.map { it.id })
        assertEquals(listOf("past"), controller.state.value.past.map { it.id })
        assertEquals(listOf("cancelled"), controller.state.value.cancelled.map { it.id })
        assertEquals(false, controller.state.value.isLoading)
    }

    @Test fun `selected details follow a live cancellation and stay in cancelled list`() {
        val active = reservation("one", today.plusDays(1), today.plusDays(3), ReservationStatus.CONFIRMED)
        val repository = FakeReservationRepository(myUserId = "owner-1", initial = listOf(active))
        val controller = OwnerReservationsController(repository) { today }

        controller.onReservationSelected("one")
        assertEquals("one", controller.state.value.selectedReservation?.id)
        repository.changeElsewhere(active.copy(status = ReservationStatus.CANCELLED, cancelledAt = Timestamp.now()))
        assertEquals(emptyList<Reservation>(), controller.state.value.active)
        controller.onTabSelected(OwnerReservationTab.CANCELLED)
        assertEquals("one", controller.state.value.visibleReservations.single().id)
        assertNull(controller.state.value.selectedReservation)
    }

    @Test fun `resuming after checkout moves confirmed stay to past`() {
        var current = today
        val stay = reservation("one", today, today.plusDays(1), ReservationStatus.CONFIRMED)
        val controller = OwnerReservationsController(
            FakeReservationRepository(myUserId = "owner-1", initial = listOf(stay)),
        ) { current }
        assertEquals(1, controller.state.value.active.size)

        current = today.plusDays(1)
        controller.onResume()
        assertEquals(emptyList<Reservation>(), controller.state.value.active)
        assertEquals("one", controller.state.value.past.single().id)
    }
}
