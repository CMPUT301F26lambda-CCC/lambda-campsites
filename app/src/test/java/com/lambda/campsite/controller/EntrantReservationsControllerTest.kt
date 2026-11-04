/*
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with these regression tests.
 * Human review is pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.FakeReservationRepository
import com.lambda.campsite.model.repository.ReservationException
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Flow 10 list, date and cancellation behaviour over the signed-in entrant's feed. */
class EntrantReservationsControllerTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val today = LocalDate.of(2027, 7, 5)

    private fun stay(id: String, start: Int, end: Int,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
        guestId: String = "entrant-1") = Reservation(id, guestId, "Jordan Lee", "owner-1", "site-1",
        "Aspen Hollow", "lottery-1", today.plusDays(start.toLong()), today.plusDays(end.toLong()),
        2, Timestamp.now(), Timestamp.now(), status)

    @Test fun `active soonest first and past includes every terminal outcome`() {
        val stays = listOf(stay("later", 8, 10), stay("completed", -4, 0),
            stay("cancelled", 4, 6, ReservationStatus.CANCELLED),
            stay("rejected", 3, 5, ReservationStatus.REJECTED), stay("sooner", 1, 3),
            stay("another-guest", 2, 4, guestId = "other"))
        val controller = EntrantReservationsController(
            FakeReservationRepository(myUserId = "entrant-1", initial = stays)) { today }

        assertFalse(controller.state.value.isLoading)
        assertEquals(listOf("sooner", "later"), controller.state.value.active.map { it.id })
        controller.onTabSelected(EntrantReservationTab.PAST)
        assertEquals(setOf("completed", "cancelled", "rejected"),
            controller.state.value.visibleReservations.map { it.id }.toSet())
        assertFalse(controller.state.value.reservations.any { it.id == "another-guest" })
    }

    @Test fun `cancellation needs confirmation and moves stay into past with nights freed`() {
        val reservation = stay("one", 1, 3)
        val repo = FakeReservationRepository(myUserId = "entrant-1", initial = listOf(reservation)) { today }
        val controller = EntrantReservationsController(repo) { today }
        assertEquals(setOf(today.plusDays(1), today.plusDays(2)), repo.bookedNights("site-1"))

        controller.onCancelRequested("one")
        assertEquals("one", controller.state.value.confirmingId)
        controller.onCancelDismissed()
        assertNull(controller.state.value.confirmingId)
        assertEquals(ReservationStatus.CONFIRMED, controller.state.value.reservation("one")?.status)

        controller.onCancelRequested("one")
        controller.onCancelConfirmed()
        assertEquals(ReservationStatus.CANCELLED, controller.state.value.reservation("one")?.status)
        assertTrue(controller.state.value.active.isEmpty())
        assertEquals("one", controller.state.value.past.single().id)
        assertTrue(repo.bookedNights("site-1").isEmpty())
        assertEquals("one", controller.state.value.cancelledId)
        controller.onCancelResultShown()
        assertNull(controller.state.value.cancelledId)
    }

    @Test fun `started completed and rejected stays cannot open cancellation`() {
        val stays = listOf(stay("started", -1, 2), stay("done", -4, 0),
            stay("rejected", 2, 4, ReservationStatus.REJECTED))
        val controller = EntrantReservationsController(
            FakeReservationRepository(myUserId = "entrant-1", initial = stays)) { today }
        stays.forEach {
            assertFalse(controller.state.value.canCancel(it))
            controller.onCancelRequested(it.id)
            assertNull(controller.state.value.confirmingId)
        }
    }

    @Test fun `failed cancellation preserves confirmed reservation and shows reason`() {
        val repo = FakeReservationRepository(myUserId = "entrant-1", initial = listOf(stay("one", 1, 3))) { today }
        val controller = EntrantReservationsController(repo) { today }
        repo.refuseNext = ReservationException.Reason.CANT_CHANGE
        controller.onCancelRequested("one")
        controller.onCancelConfirmed()

        assertEquals(EntrantCancellationError.NOT_ALLOWED, controller.state.value.cancelError)
        assertEquals("one", controller.state.value.confirmingId)
        assertEquals(ReservationStatus.CONFIRMED, controller.state.value.reservation("one")?.status)
    }

    @Test fun `live change and resumed date reclassify without reloading`() {
        var currentDate = today
        val reservation = stay("one", 1, 3)
        val repo = FakeReservationRepository(myUserId = "entrant-1", initial = listOf(reservation)) { currentDate }
        val controller = EntrantReservationsController(repo) { currentDate }
        repo.changeElsewhere(reservation.copy(status = ReservationStatus.REJECTED))
        assertTrue(controller.state.value.active.isEmpty())
        assertEquals("one", controller.state.value.past.single().id)
        repo.changeElsewhere(reservation)
        currentDate = today.plusDays(3)
        controller.onResume()
        assertTrue(controller.state.value.active.isEmpty())
        assertEquals("one", controller.state.value.past.single().id)
    }

    @Test fun `past reservations are newest first by completed or cancelled date`() {
        val older = stay("older", -7, -5)
        val newer = stay("newer", -3, -1)
        val cancelled = stay("cancelled", 4, 6, ReservationStatus.CANCELLED).copy(
            cancelledAt = Timestamp(java.util.Date.from(today.atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant())))
        val controller = EntrantReservationsController(FakeReservationRepository(
            myUserId = "entrant-1", initial = listOf(older, cancelled, newer))) { today }

        assertEquals(listOf("cancelled", "newer", "older"), controller.state.value.past.map { it.id })
    }
}
