/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the LotteryEntry class and the
 * EntryStatus enumeration in the team's class diagram. It helped with the
 * Kotlin code, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp

/** Where a [LotteryEntry] stands after the draw (US 02.05.01, 01.04.03). */
enum class EntryStatus {

    /** On the waiting list, waiting for the draw. Every entry starts here. */
    WAITING,

    /** Picked by the draw. The winner can book nights or decline. */
    WON,

    /** Not picked by the draw. */
    NOT_SELECTED,

    /** The winner turned the win down (US 01.04.03). */
    DECLINED,

    /** The winner booked their stay, and [LotteryEntry.reservationId] says which reservation it is. */
    BOOKED,
}

/**
 * One entrant's place on a lottery's waiting list (US 01.02.01).
 *
 * The app creates an entry when the entrant joins and deletes it when they
 * leave. The draw sets the result. Declining and booking come with
 * reservations, and so do the class diagram's `accept()` and `decline()`.
 *
 * The class diagram's `entrant: Profile` and `reservation: Reservation?` are
 * [entrantId] and [reservationId] here, the way Firestore stores them.
 *
 * @property lotteryId the lottery whose waiting list it's on. It comes from the
 *   entry's path, `lotteries/{lotteryId}/entries/{entrantId}`.
 * @property entrantId the Firebase Auth user ID of the entrant. It's also the
 *   entry's document ID, so nobody can join the same lottery twice.
 * @property joinedAt when the server added the entry.
 * @property status where the entry stands. Only the draw sets
 *   [EntryStatus.WON] and [EntryStatus.NOT_SELECTED].
 * @property reservationId the reservation the winner booked, or `null` until
 *   they book.
 */
data class LotteryEntry(
    val lotteryId: String,
    val entrantId: String,
    val joinedAt: Timestamp,
    val status: EntryStatus = EntryStatus.WAITING,
    val reservationId: String? = null,
)
