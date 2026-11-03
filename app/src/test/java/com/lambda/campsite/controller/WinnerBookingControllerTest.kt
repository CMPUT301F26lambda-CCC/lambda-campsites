/*
 * Purpose: winner authorization, live date collisions and persisted confirmation (US 01.04.01–03, 01.05.01).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: device/TalkBack verification is separate (#126).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with regression cases; human review pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verifies every Flow 9 story boundary with fakes, including a server-side booking race. */
class WinnerBookingControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val start = LocalDate.of(2027, 7, 1)
    private val camp = Campsite("camp", "owner", "Aspen Hollow", "Alberta", "Forest", maxOccupancy = 4)
    private val other = camp.copy(id = "other", name = "Cedar Point")
    private val lottery = Lottery("lot", "owner", "Demo lottery", listOf(camp.id), Timestamp(1, 0), Timestamp(2, 0),
        start, start.plusDays(10), status = LotteryStatus.DRAWN)
    private val entry = LotteryEntry(lottery.id, "user-1", Timestamp(1, 0), EntryStatus.WON)
    private val calendar = Availability(start, start.plusDays(12))
    private fun camps() = FakeCampsiteRepository(initial = listOf(camp, other),
        initialCalendars = mapOf(camp.id to calendar, other.id to calendar))
    private fun lotteries(multiple: Boolean = false, status: EntryStatus = EntryStatus.WON) =
        FakeLotteryRepository(initial = listOf(if (multiple) lottery.copy(campsiteIds = listOf(camp.id, other.id)) else lottery),
            initialEntries = listOf(entry.copy(status = status)))
    private fun reservations() = FakeReservationRepository(campsites = listOf(camp, other), wins = listOf(lottery.id))
    private fun range(control: WinnerBookingController) { control.onDate(start); control.onDate(start.plusDays(2)); control.onContinue() }

    @Test fun `single campsite skips selection but foreign campsite cannot be chosen`() {
        val control = WinnerBookingController(lottery.id, lotteries(), camps(), reservations()) { start }
        assertEquals(WinnerBookingStep.DATES, control.state.value.step)
        control.onSelectCampsite(other.id)
        assertEquals(camp.id, control.state.value.selectedCampsiteId)
    }
    @Test fun `multiple campsite winner sees all choices and changing one clears dates`() {
        val control = WinnerBookingController(lottery.id, lotteries(true), camps(), reservations()) { start }
        assertEquals(WinnerBookingStep.CAMPSITE, control.state.value.step)
        assertEquals(2, control.state.value.sites.size)
        control.onSelectCampsite(camp.id); control.onContinue(); range(control)
        control.onSelectCampsite(other.id)
        assertNull(control.state.value.checkIn)
        assertFalse(control.state.value.rulesAgreed)
    }
    @Test fun `non winner cannot book select dates or decline`() {
        val repo = reservations()
        val control = WinnerBookingController(lottery.id, lotteries(status = EntryStatus.NOT_SELECTED), camps(), repo) { start }
        assertFalse(control.state.value.canBook)
        control.onDate(start); control.onRequestDecline(); control.onConfirmDecline(); control.onBook()
        assertNull(control.state.value.checkIn)
        assertFalse(control.state.value.confirmDecline)
        assertTrue(repo.declined.isEmpty())
        assertNull(control.state.value.savedId)
    }
    @Test fun `checkout may land on booked night but selected range cannot cross it`() {
        val sites = camps().apply { changeCalendarElsewhere(camp.id, calendar.copy(bookedNights = setOf(start.plusDays(2)))) }
        val control = WinnerBookingController(lottery.id, lotteries(), sites, reservations()) { start }
        control.onDate(start)
        assertTrue(control.state.value.checkoutAvailable(start.plusDays(2)))
        assertFalse(control.state.value.checkoutAvailable(start.plusDays(3)))
        assertFalse(control.state.value.nightAvailable(start.plusDays(2)))
        assertFalse(control.state.value.nightAvailable(start.minusDays(1)))
        assertFalse(control.state.value.nightAvailable(lottery.stayEnd))
    }
    @Test fun `empty calendar has no free dates when whole season is booked`() {
        val sites = camps().apply { changeCalendarElsewhere(camp.id, calendar.copy(
            bookedNights = (0L..11L).map { start.plusDays(it) }.toSet())) }
        val control = WinnerBookingController(lottery.id, lotteries(), sites, reservations()) { start }
        assertFalse(control.state.value.hasDates)
    }
    @Test fun `live booking elsewhere invalidates range before save`() {
        val sites = camps()
        val control = WinnerBookingController(lottery.id, lotteries(), sites, reservations()) { start }
        range(control); control.onRulesAgreed(true)
        sites.changeCalendarElsewhere(camp.id, calendar.copy(bookedNights = setOf(start.plusDays(1))))
        assertFalse(control.state.value.validRange)
        control.onBook()
        assertEquals(WinnerBookingError.INVALID_DATES, control.state.value.error)
        assertNull(control.state.value.savedId)
    }
    @Test fun `rules required and receipt matches repository after booking`() {
        val control = WinnerBookingController(lottery.id, lotteries(), camps(), reservations()) { start }
        range(control); control.onBook()
        assertEquals(WinnerBookingError.RULES_REQUIRED, control.state.value.error)
        control.onRulesAgreed(true); control.onGuests(3); control.onBook()
        val saved = control.state.value.receipt!!
        assertEquals(control.state.value.savedId, saved.id)
        assertEquals(camp.id, saved.campsiteId)
        assertEquals(start, saved.checkIn)
        assertEquals(start.plusDays(2), saved.checkOut)
        assertEquals(3, saved.guests)
        control.onBook()
        assertEquals(saved.id, control.state.value.savedId)
    }
    @Test fun `repository collision is shown without false confirmation and retry succeeds`() {
        val repo = reservations().apply { refuseNext = ReservationException.Reason.NIGHTS_NOT_FREE }
        val control = WinnerBookingController(lottery.id, lotteries(), camps(), repo) { start }
        range(control); control.onRulesAgreed(true); control.onBook()
        assertEquals(WinnerBookingError.DATES_TAKEN, control.state.value.error)
        assertNull(control.state.value.savedId)
        control.onBook()
        assertNotNull(control.state.value.receipt)
    }
    @Test fun `decline needs confirmation and failures leave the win retryable`() {
        val repo = reservations().apply { failSaves = true }
        val control = WinnerBookingController(lottery.id, lotteries(), camps(), repo) { start }
        control.onConfirmDecline()
        assertTrue(repo.declined.isEmpty())
        control.onRequestDecline(); control.onConfirmDecline()
        assertEquals(WinnerBookingError.SAVE_FAILED, control.state.value.error)
        assertNotEquals(WinnerBookingStep.DECLINED, control.state.value.step)
        repo.failSaves = false; control.onConfirmDecline()
        assertEquals(listOf(lottery.id), repo.declined)
        assertEquals(WinnerBookingStep.DECLINED, control.state.value.step)
        assertNull(control.state.value.receipt)
    }
    @Test fun `listener errors are not reported as an empty calendar`() {
        val source = camps()
        val failed = object : CampsiteRepository by source {
            override fun availability(campsiteId: String) = flow<Availability?> { throw java.io.IOException("Offline") }
        }
        val control = WinnerBookingController(lottery.id, lotteries(), failed, reservations()) { start }
        assertTrue(control.state.value.loadFailed)
        assertFalse(control.state.value.isLoading)
    }
    @Test fun `in flight booking cannot be submitted twice or navigated away`() {
        val source = reservations()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val delayed = object : ReservationRepository by source {
            override suspend fun book(lotteryId: String, campsiteId: String, checkIn: LocalDate, checkOut: LocalDate, guests: Int): String {
                calls++; gate.await(); return source.book(lotteryId, campsiteId, checkIn, checkOut, guests)
            }
        }
        val control = WinnerBookingController(lottery.id, lotteries(), camps(), delayed) { start }
        range(control); control.onRulesAgreed(true); control.onBook(); control.onBook()
        assertEquals(1, calls)
        assertTrue(control.onBack())
        gate.complete(Unit)
        assertNotNull(control.state.value.receipt)
    }
}
