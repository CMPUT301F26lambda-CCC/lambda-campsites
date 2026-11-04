/*
 * Purpose: unit tests for the Reservation model (US 01.05.01, 01.06.01,
 * 01.06.02, 02.07.01, 02.07.02): when a stay counts as active or
 * completed, and the short reservation number.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 2, 2026. It helped with the
 * test cases and the Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Reservation]: when a stay is active or completed, and its
 * number (US 01.05.01, 01.06.01, 01.06.02, 02.07.01, 02.07.02).
 */
class ReservationTest {

    // Two nights, July 2 and 3, checking out on July 4.
    private val reservation = Reservation(
        id = "abc123xyz789",
        guestId = "guest-1",
        guestName = "Jordan Lee",
        ownerId = "owner-1",
        campsiteId = "campsite-1",
        campsiteName = "Moose Lake Campsite",
        lotteryId = "lottery-1",
        checkIn = date("2027-07-02"),
        checkOut = date("2027-07-04"),
        guests = 2,
        rulesAgreedAt = Timestamp(0, 0),
        createdAt = Timestamp(0, 0),
    )

    @Test
    fun `a stay that hasn't started is active`() {
        assertTrue(reservation.isActive(date("2027-06-15")))
    }

    @Test
    fun `a stay under way is active`() {
        assertTrue(reservation.isActive(date("2027-07-03")))
    }

    @Test
    fun `a stay isn't active on its check-out day`() {
        assertFalse(reservation.isActive(date("2027-07-04")))
    }

    @Test
    fun `a cancelled stay isn't active`() {
        val cancelled = reservation.copy(status = ReservationStatus.CANCELLED)

        assertFalse(cancelled.isActive(date("2027-06-15")))
    }

    @Test
    fun `a rejected stay isn't active`() {
        val rejected = reservation.copy(status = ReservationStatus.REJECTED)

        assertFalse(rejected.isActive(date("2027-06-15")))
    }

    @Test
    fun `a stay is completed from its check-out day`() {
        assertTrue(reservation.isCompleted(date("2027-07-04")))
    }

    @Test
    fun `a stay is still completed long after check-out`() {
        assertTrue(reservation.isCompleted(date("2028-01-01")))
    }

    @Test
    fun `a stay under way isn't completed`() {
        assertFalse(reservation.isCompleted(date("2027-07-03")))
    }

    @Test
    fun `a cancelled stay is never completed`() {
        val cancelled = reservation.copy(status = ReservationStatus.CANCELLED)

        assertFalse(cancelled.isCompleted(date("2028-01-01")))
    }

    @Test
    fun `a rejected stay is never completed`() {
        val rejected = reservation.copy(status = ReservationStatus.REJECTED)

        assertFalse(rejected.isCompleted(date("2028-01-01")))
    }

    @Test
    fun `the number is the first 8 characters of the ID in capitals`() {
        assertEquals("ABC123XY", reservation.number)
    }

    @Test
    fun `a short ID gives a short number`() {
        assertEquals("AB1", reservation.copy(id = "ab1").number)
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)
}
