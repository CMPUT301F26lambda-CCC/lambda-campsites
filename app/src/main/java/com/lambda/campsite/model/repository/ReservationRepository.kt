/*
 * Purpose: the model layer's interface for reservations: watching a guest's,
 * an owner's or a campsite's reservations, and booking, cancelling,
 * rejecting and declining stays. Controllers depend on it instead of on
 * Firestore (the repository pattern), so unit tests can swap in a fake.
 *
 * Outstanding issues:
 * - The lists come back with every status, and the screens pick active,
 *   past or cancelled with Reservation.isActive and isCompleted. Whether a
 *   stay is active depends on today's date, which a stored query can't keep
 *   up with, and one person has few enough reservations to filter on the
 *   phone.
 * - A stay can only be cancelled or rejected before its check-in day.
 *   Freeing the nights of a stay that's already under way would hand out
 *   nights that have partly passed.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * pattern in LotteryRepository. It helped with the Kotlin code, syntax and
 * KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Reservation
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Loads reservations, and books, cancels, rejects and declines stays.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses `FirestoreReservationRepository`, and unit tests use
 * `FakeReservationRepository`.
 */
interface ReservationRepository {

    /**
     * This device's reservations, with every status, earliest check-in
     * first, sent again whenever one changes (US 01.06.01, 01.06.02).
     */
    fun myReservations(): Flow<List<Reservation>>

    /**
     * The reservations at every campsite this device's profile owns, with
     * every status, earliest check-in first, sent again whenever one
     * changes (US 02.07.01 to 02.07.03).
     */
    fun ownerReservations(): Flow<List<Reservation>>

    /**
     * The reservations at the campsite with this [campsiteId], with every
     * status, earliest check-in first, sent again whenever one changes
     * (US 02.06.01). Only the campsite's owner can read them.
     */
    fun campsiteReservations(campsiteId: String): Flow<List<Reservation>>

    /**
     * Books the nights from [checkIn] up to [checkOut] at the campsite with
     * this [campsiteId], using this device's win in the lottery with this
     * [lotteryId], and returns the new reservation's ID (US 01.04.02,
     * 01.05.01). Calling it means the guest has agreed to the campsite's
     * rules (US 02.11.01).
     *
     * It runs as one transaction on the server's latest copies. It checks the
     * win, the dates, the calendar and [guests], then adds the nights to the
     * calendar, creates the reservation and sets the entry to BOOKED. If two
     * winners pick the same night at the same moment, only one of them
     * gets it.
     *
     * @throws ReservationException if this device has no unused win in that
     *   lottery, the campsite isn't part of it, the dates don't fit the
     *   lottery's stay or the calendar, or [guests] doesn't fit the campsite.
     * @throws Exception if the server can't be reached. A transaction can't
     *   wait for the connection to come back.
     */
    suspend fun book(
        lotteryId: String,
        campsiteId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): String

    /**
     * Cancels this device's reservation with this [reservationId] and frees
     * its nights, in one transaction (US 01.07.01).
     *
     * @throws ReservationException if there's no such reservation of this
     *   device's, it's already cancelled or rejected, or its stay has started.
     * @throws Exception if the server can't be reached.
     */
    suspend fun cancel(reservationId: String)

    /**
     * Rejects the reservation with this [reservationId] at a campsite this
     * device's profile owns, and frees its nights, in one transaction
     * (US 02.08.01).
     *
     * @throws ReservationException if there's no such reservation at one of
     *   this device's campsites, it's already cancelled or rejected, or its
     *   stay has started.
     * @throws Exception if the server can't be reached.
     */
    suspend fun reject(reservationId: String)

    /**
     * Turns down this device's win in the lottery with this [lotteryId]
     * without booking (US 01.04.03). The entry becomes DECLINED. Nothing
     * passes to another entrant, since there's no second draw.
     *
     * @throws ReservationException if this device has no unused win in that
     *   lottery.
     * @throws Exception if the server can't be reached.
     */
    suspend fun decline(lotteryId: String)
}

/**
 * Thrown when booking, cancelling, rejecting or declining isn't allowed.
 * [reason] says why, so the screen can show the right message.
 */
class ReservationException(val reason: Reason) : Exception("Can't book or change the reservation: $reason") {

    /** Why the change was refused. */
    enum class Reason {

        /** This device has no unused win in that lottery: it didn't win, or already booked or declined. */
        NOT_A_WINNER,

        /** There's no such lottery or campsite, or the campsite isn't part of the lottery. */
        NOT_IN_LOTTERY,

        /** Check-out isn't after check-in, or the stay isn't inside the lottery's dates. */
        OUTSIDE_STAY,

        /** At least one night is already booked or outside the campsite's season (US 01.04.01). */
        NIGHTS_NOT_FREE,

        /** Fewer than one guest, or more than the campsite's `maxOccupancy`. */
        GUESTS_DONT_FIT,

        /** There's no such reservation that this device can change. */
        NO_SUCH_RESERVATION,

        /** The reservation is already cancelled or rejected, or its stay has started. */
        CANT_CHANGE,
    }
}
