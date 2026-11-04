/*
 * Purpose: a ReservationRepository for unit tests. It keeps reservations and
 * unused wins in memory, so controller tests run without Firebase, and can
 * be told to fail like an offline phone or to refuse the next change.
 *
 * Outstanding issues:
 * - It doesn't know lotteries' stay dates or campsites' seasons, so it never
 *   refuses OUTSIDE_STAY for dates outside a lottery, or NIGHTS_NOT_FREE for
 *   nights outside a season. A test that needs those sets refuseNext.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * pattern in FakeLotteryRepository. It helped with the Kotlin code, syntax
 * and KDoc. Sashreek Addanki reviewed it.
 */
// UI-test reuse: Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI), through Codex,
// assisted with reusing this existing unit-test fake for Flow 9; human review pending.
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [ReservationRepository] for unit tests. It keeps reservations and unused
 * wins in memory, and can be told to fail like an offline phone would, or
 * to refuse the next change for a chosen reason.
 *
 * @param myUserId the user ID it treats as this device's.
 * @param myName the guest name it puts on reservations this device books.
 * @param campsites the campsites that can be booked. Booking reads their
 *   owner, name and `maxOccupancy` from here.
 * @param wins the IDs of the lotteries this device has an unused win in.
 * @param initial the reservations to start with, anyone's.
 * @param today the date it treats as today, for checking whether a stay has
 *   started.
 */
class FakeReservationRepository(
    private val myUserId: String = "user-1",
    private val myName: String = "Jordan Lee",
    campsites: List<Campsite> = emptyList(),
    wins: List<String> = emptyList(),
    initial: List<Reservation> = emptyList(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ReservationRepository {

    private val campsites = campsites.associateBy { it.id }
    private val unusedWins = wins.toMutableSet()
    private val stored = MutableStateFlow(initial)
    private var nextId = 1

    /** The lottery IDs passed to [decline], in order. */
    val declined = mutableListOf<String>()

    /** When `true`, every change throws as if the phone were offline. */
    var failSaves = false

    /**
     * When set, the next change throws a [ReservationException] with this
     * reason, and then it's cleared. It stands in for checks this fake can't
     * make, like a stay outside the lottery's dates.
     */
    var refuseNext: ReservationException.Reason? = null

    override fun myReservations(): Flow<List<Reservation>> = sorted { it.guestId == myUserId }

    override fun ownerReservations(): Flow<List<Reservation>> = sorted { it.ownerId == myUserId }

    override fun campsiteReservations(campsiteId: String): Flow<List<Reservation>> =
        sorted { it.campsiteId == campsiteId }

    override suspend fun book(
        lotteryId: String,
        campsiteId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): String {
        checkCanChange()
        if (lotteryId !in unusedWins) refuse(ReservationException.Reason.NOT_A_WINNER)
        val campsite = campsites[campsiteId] ?: refuse(ReservationException.Reason.NOT_IN_LOTTERY)
        if (!checkOut.isAfter(checkIn)) refuse(ReservationException.Reason.OUTSIDE_STAY)
        if (guests < 1 || guests > campsite.maxOccupancy) refuse(ReservationException.Reason.GUESTS_DONT_FIT)
        if (nights(checkIn, checkOut).any { it in bookedNights(campsiteId) }) {
            refuse(ReservationException.Reason.NIGHTS_NOT_FREE)
        }
        val reservation = Reservation(
            id = "reservation-${nextId++}",
            guestId = myUserId,
            guestName = myName,
            ownerId = campsite.ownerId,
            campsiteId = campsiteId,
            campsiteName = campsite.name,
            lotteryId = lotteryId,
            checkIn = checkIn,
            checkOut = checkOut,
            guests = guests,
            rulesAgreedAt = Timestamp.now(),
            createdAt = Timestamp.now(),
        )
        stored.update { it + reservation }
        unusedWins -= lotteryId
        return reservation.id
    }

    override suspend fun cancel(reservationId: String) =
        change(reservationId, ReservationStatus.CANCELLED) { it.guestId == myUserId }

    override suspend fun reject(reservationId: String) =
        change(reservationId, ReservationStatus.REJECTED) { it.ownerId == myUserId }

    override suspend fun decline(lotteryId: String) {
        checkCanChange()
        if (!unusedWins.remove(lotteryId)) refuse(ReservationException.Reason.NOT_A_WINNER)
        declined += lotteryId
    }

    /** The nights confirmed reservations hold at the campsite with this [campsiteId]. */
    fun bookedNights(campsiteId: String): Set<LocalDate> =
        stored.value
            .filter { it.campsiteId == campsiteId && it.status == ReservationStatus.CONFIRMED }
            .flatMap { nights(it.checkIn, it.checkOut) }
            .toSet()

    /** Changes or adds a reservation without a call, like the other person cancelling it. */
    fun changeElsewhere(reservation: Reservation) {
        stored.update { all -> all.filterNot { it.id == reservation.id } + reservation }
    }

    private fun sorted(keep: (Reservation) -> Boolean): Flow<List<Reservation>> =
        stored.map { all -> all.filter(keep).sortedBy { it.checkIn } }

    private fun change(reservationId: String, status: ReservationStatus, canChange: (Reservation) -> Boolean) {
        checkCanChange()
        val reservation = stored.value.firstOrNull { it.id == reservationId && canChange(it) }
            ?: refuse(ReservationException.Reason.NO_SUCH_RESERVATION)
        if (reservation.status != ReservationStatus.CONFIRMED || !today().isBefore(reservation.checkIn)) {
            refuse(ReservationException.Reason.CANT_CHANGE)
        }
        changeElsewhere(reservation.copy(status = status, cancelledAt = Timestamp.now()))
    }

    private fun checkCanChange() {
        if (failSaves) throw IOException("Offline")
        refuseNext?.let { reason ->
            refuseNext = null
            refuse(reason)
        }
    }

    private fun nights(checkIn: LocalDate, checkOut: LocalDate): List<LocalDate> =
        generateSequence(checkIn) { it.plusDays(1) }.takeWhile { it.isBefore(checkOut) }.toList()

    private fun refuse(reason: ReservationException.Reason): Nothing = throw ReservationException(reason)
}
