/*
 * Purpose: the Reservation model class and its ReservationStatus enum, in the
 * model layer. A reservation is a lottery winner's booked stay at one
 * campsite, stored at `reservations/{id}`. It works out from its dates
 * whether a stay is active or completed, so nothing has to update its
 * status at midnight.
 *
 * Outstanding issues:
 * - number is the first 8 characters of the document ID in capitals. It's
 *   short enough to read out to an owner, but unlike id it isn't
 *   guaranteed to be unique.
 * - The class diagram's confirm(), cancel() and reject() are on
 *   ReservationRepository instead of here, because each one also changes
 *   the campsite's calendar in the same transaction.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * Reservation class and the ReservationStatus enumeration in the team's
 * class diagram. It helped with the Kotlin code, syntax and KDoc. Sashreek
 * Addanki reviewed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp
import java.time.LocalDate

/** Where a [Reservation] stands (US 01.07.01, 02.07.03, 02.08.01). */
enum class ReservationStatus {

    /**
     * Booked. Every reservation starts here. Whether the stay is still ahead
     * or already over comes from the dates, not from the status.
     */
    CONFIRMED,

    /** The guest cancelled it (US 01.07.01). Its nights went back to the calendar. */
    CANCELLED,

    /** The owner rejected it (US 02.08.01). Its nights went back to the calendar. */
    REJECTED,
}

/**
 * A lottery winner's booked stay at one campsite (US 01.04.02, 01.05.01).
 *
 * The booking transaction creates it as [ReservationStatus.CONFIRMED], adds
 * its nights to the campsite's calendar and sets the winning entry to
 * BOOKED. A stay covers the nights from [checkIn] up to, but not including,
 * [checkOut], so one guest can check in on the day another checks out.
 *
 * Like [Lottery], a reservation never changes in place. The repository
 * saves a changed copy.
 *
 * The class diagram's `guest: Profile`, `campsite: Campsite` and
 * `lottery: Lottery` are [guestId], [campsiteId] and [lotteryId] here, the
 * way Firestore stores them. [guestName], [campsiteName] and [ownerId] are
 * copies, so the owner's lists load with one query and without reading the
 * guest's private profile (US 02.07.01 to 02.07.03).
 *
 * @property id the reservation's document ID.
 * @property guestId the Firebase Auth user ID of the winner who booked it.
 * @property guestName the guest's name when they booked, for the owner's lists.
 * @property ownerId the Firebase Auth user ID of the campsite's owner.
 * @property campsiteId the campsite that was booked.
 * @property campsiteName the campsite's name when it was booked, for lists.
 * @property lotteryId the lottery the guest won.
 * @property checkIn the first night of the stay.
 * @property checkOut the day the guest leaves. That night isn't part of the stay.
 * @property guests how many people are staying. The booking checks it
 *   against the campsite's `maxOccupancy`.
 * @property rulesAgreedAt when the guest agreed to the campsite's rules
 *   before booking (US 02.11.01).
 * @property createdAt when the server created it.
 * @property status whether it's confirmed, cancelled or rejected.
 * @property cancelledAt when it was cancelled or rejected, or `null` while
 *   it's confirmed.
 */
data class Reservation(
    val id: String,
    val guestId: String,
    val guestName: String,
    val ownerId: String,
    val campsiteId: String,
    val campsiteName: String,
    val lotteryId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int,
    val rulesAgreedAt: Timestamp,
    val createdAt: Timestamp,
    val status: ReservationStatus = ReservationStatus.CONFIRMED,
    val cancelledAt: Timestamp? = null,
) {

    /**
     * A short reservation number for the confirmation (US 01.05.01): the
     * first 8 characters of [id], in capitals.
     */
    val number: String
        get() = id.take(NUMBER_LENGTH).uppercase()

    /**
     * Returns `true` if the stay is still ahead or under way on [today]: it's
     * confirmed and [today] is before [checkOut] (US 01.06.01, 02.07.01).
     */
    fun isActive(today: LocalDate): Boolean =
        status == ReservationStatus.CONFIRMED && today.isBefore(checkOut)

    /**
     * Returns `true` if the stay is over on [today]: it's confirmed and
     * [today] is [checkOut] or later (US 01.06.02, 02.07.02). Only a
     * completed stay lets the guest rate the campsite (US 01.13.01).
     */
    fun isCompleted(today: LocalDate): Boolean =
        status == ReservationStatus.CONFIRMED && !today.isBefore(checkOut)

    private companion object {
        const val NUMBER_LENGTH = 8
    }
}
