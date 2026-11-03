/*
 * Purpose: how a Reservation is stored in its Firestore document,
 * `reservations/{id}`: the field names, and turning a Reservation into a
 * map and back. It's part of the model layer, kept apart from the
 * repository so unit tests can check it without Firebase.
 *
 * Outstanding issues:
 * - A reservation whose dates or status can't be read comes back as null,
 *   so lists leave it out instead of showing it with made-up dates.
 * - The date parser is the same as the ones in LotteryFields and
 *   AvailabilityFields. A shared helper would remove the three copies.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * pattern in LotteryFields. It helped with the Kotlin code, syntax and
 * KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * How a [Reservation] is stored in its Firestore document,
 * `reservations/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. The check-in
 * and check-out dates are stored as text like "2027-07-02", the same as the
 * availability calendar, and the status as its name.
 *
 * The field names must match `firestore.rules`.
 */
object ReservationFields {

    const val GUEST_ID = "guestId"
    const val GUEST_NAME = "guestName"
    const val OWNER_ID = "ownerId"
    const val CAMPSITE_ID = "campsiteId"
    const val CAMPSITE_NAME = "campsiteName"
    const val LOTTERY_ID = "lotteryId"
    const val CHECK_IN = "checkIn"
    const val CHECK_OUT = "checkOut"
    const val GUESTS = "guests"
    const val RULES_AGREED_AT = "rulesAgreedAt"
    const val CREATED_AT = "createdAt"
    const val STATUS = "status"
    const val CANCELLED_AT = "cancelledAt"

    /**
     * The document fields for [reservation]. Booking writes
     * [newReservationFields] instead, which leaves the times to the server.
     */
    fun toMap(reservation: Reservation): Map<String, Any?> = mapOf(
        GUEST_ID to reservation.guestId,
        GUEST_NAME to reservation.guestName,
        OWNER_ID to reservation.ownerId,
        CAMPSITE_ID to reservation.campsiteId,
        CAMPSITE_NAME to reservation.campsiteName,
        LOTTERY_ID to reservation.lotteryId,
        CHECK_IN to reservation.checkIn.toString(),
        CHECK_OUT to reservation.checkOut.toString(),
        GUESTS to reservation.guests,
        RULES_AGREED_AT to reservation.rulesAgreedAt,
        CREATED_AT to reservation.createdAt,
        STATUS to reservation.status.name,
        CANCELLED_AT to reservation.cancelledAt,
    )

    /**
     * The fields the booking writes for a new [reservation]. The server fills
     * in [CREATED_AT] and [RULES_AGREED_AT], so a phone with the wrong clock
     * can't change when the guest booked or agreed to the rules.
     */
    fun newReservationFields(reservation: Reservation): Map<String, Any?> =
        toMap(reservation) + mapOf(
            CREATED_AT to FieldValue.serverTimestamp(),
            RULES_AGREED_AT to FieldValue.serverTimestamp(),
        )

    /**
     * The reservation stored in the document with ID [id] and these
     * [fields], or `null` if its dates or its [STATUS] are missing or can't
     * be read. Without them the app can't tell whether the stay is active.
     * Other missing fields read as empty, one guest, or time zero for the
     * timestamps, which is also how [CREATED_AT] reads until the server has
     * set it.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Reservation? {
        val checkIn = date(fields[CHECK_IN]) ?: return null
        val checkOut = date(fields[CHECK_OUT]) ?: return null
        val status = (fields[STATUS] as? String)?.let(::statusOrNull) ?: return null
        return Reservation(
            id = id,
            guestId = fields[GUEST_ID] as? String ?: "",
            guestName = fields[GUEST_NAME] as? String ?: "",
            ownerId = fields[OWNER_ID] as? String ?: "",
            campsiteId = fields[CAMPSITE_ID] as? String ?: "",
            campsiteName = fields[CAMPSITE_NAME] as? String ?: "",
            lotteryId = fields[LOTTERY_ID] as? String ?: "",
            checkIn = checkIn,
            checkOut = checkOut,
            // Firestore stores every whole number as a Long.
            guests = (fields[GUESTS] as? Number)?.toInt() ?: 1,
            rulesAgreedAt = fields[RULES_AGREED_AT] as? Timestamp ?: Timestamp(0, 0),
            createdAt = fields[CREATED_AT] as? Timestamp ?: Timestamp(0, 0),
            status = status,
            cancelledAt = fields[CANCELLED_AT] as? Timestamp,
        )
    }

    private fun date(value: Any?): LocalDate? =
        try {
            (value as? String)?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        }

    private fun statusOrNull(name: String): ReservationStatus? =
        ReservationStatus.entries.firstOrNull { it.name == name }
}
