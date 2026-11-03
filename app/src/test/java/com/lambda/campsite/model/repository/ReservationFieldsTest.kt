/*
 * Purpose: unit tests for ReservationFields, how a reservation is stored in
 * Firestore (US 01.05.01, 01.06.01, 02.07.01 to 02.07.03).
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 2, 2026. It helped with the
 * test cases and the Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [ReservationFields], how a reservation is stored in Firestore. */
class ReservationFieldsTest {

    private val reservation = Reservation(
        id = "reservation-1",
        guestId = "guest-1",
        guestName = "Jordan Lee",
        ownerId = "owner-1",
        campsiteId = "campsite-1",
        campsiteName = "Moose Lake Campsite",
        lotteryId = "lottery-1",
        checkIn = LocalDate.parse("2027-07-02"),
        checkOut = LocalDate.parse("2027-07-04"),
        guests = 3,
        rulesAgreedAt = Timestamp(1_810_000_000, 0),
        createdAt = Timestamp(1_810_000_060, 0),
    )

    @Test
    fun `a reservation reads back the same after it's stored`() {
        val fields = ReservationFields.toMap(reservation)

        assertEquals(reservation, ReservationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a cancelled reservation reads back with when it was cancelled`() {
        val cancelled = reservation.copy(
            status = ReservationStatus.CANCELLED,
            cancelledAt = Timestamp(1_811_000_000, 0),
        )

        assertEquals(cancelled, ReservationFields.fromMap("reservation-1", ReservationFields.toMap(cancelled)))
    }

    @Test
    fun `dates and status are stored as text`() {
        val fields = ReservationFields.toMap(reservation)

        assertEquals("2027-07-02", fields[ReservationFields.CHECK_IN])
        assertEquals("2027-07-04", fields[ReservationFields.CHECK_OUT])
        assertEquals("CONFIRMED", fields[ReservationFields.STATUS])
    }

    @Test
    fun `a new reservation leaves both times to the server`() {
        val fields = ReservationFields.newReservationFields(reservation)

        assertEquals(FieldValue.serverTimestamp(), fields[ReservationFields.CREATED_AT])
        assertEquals(FieldValue.serverTimestamp(), fields[ReservationFields.RULES_AGREED_AT])
        assertEquals("2027-07-02", fields[ReservationFields.CHECK_IN])
    }

    @Test
    fun `guests stored as a Long read back as an Int`() {
        // Firestore hands back every whole number as a Long.
        val fields = ReservationFields.toMap(reservation) + (ReservationFields.GUESTS to 3L)

        assertEquals(3, ReservationFields.fromMap("reservation-1", fields)?.guests)
    }

    @Test
    fun `a missing status reads as no reservation`() {
        val fields = ReservationFields.toMap(reservation) - ReservationFields.STATUS

        assertNull(ReservationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a status this app doesn't know reads as no reservation`() {
        val fields = ReservationFields.toMap(reservation) + (ReservationFields.STATUS to "ON_HOLD")

        assertNull(ReservationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a check-in that isn't a date reads as no reservation`() {
        val fields = ReservationFields.toMap(reservation) + (ReservationFields.CHECK_IN to "July 2")

        assertNull(ReservationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a missing check-out reads as no reservation`() {
        val fields = ReservationFields.toMap(reservation) - ReservationFields.CHECK_OUT

        assertNull(ReservationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a createdAt the server hasn't set yet reads as time zero`() {
        val fields = ReservationFields.toMap(reservation) + (ReservationFields.CREATED_AT to null)

        assertEquals(Timestamp(0, 0), ReservationFields.fromMap("reservation-1", fields)?.createdAt)
    }
}
