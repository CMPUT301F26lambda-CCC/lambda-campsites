/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [Lottery]: the registration window, the waiting-list limit and
 * the dates (US 01.02.01, 02.04.03, 02.04.04, 02.04.07).
 */
class LotteryTest {

    // Registration runs from May 1 at 9:00 to June 1 at 9:00, Edmonton time,
    // and winners stay between July 1 and 31.
    private val lottery = Lottery(
        id = "lottery-1",
        ownerId = "owner-1",
        name = "Moose Lake, July 2027",
        campsiteIds = listOf("campsite-1"),
        registrationOpens = time("2027-05-01T09:00"),
        registrationCloses = time("2027-06-01T09:00"),
        stayStart = date("2027-07-01"),
        stayEnd = date("2027-07-31"),
        numberOfWinners = 10,
    )

    @Test
    fun `registration is open between its opening and closing times`() {
        assertTrue(lottery.isRegistrationOpen(time("2027-05-15T12:00")))
    }

    @Test
    fun `registration is open from the moment it opens`() {
        assertTrue(lottery.isRegistrationOpen(time("2027-05-01T09:00")))
    }

    @Test
    fun `registration isn't open before it opens`() {
        assertFalse(lottery.isRegistrationOpen(time("2027-04-30T12:00")))
    }

    @Test
    fun `registration is closed from the moment it closes`() {
        assertFalse(lottery.isRegistrationOpen(time("2027-06-01T09:00")))
    }

    @Test
    fun `registration isn't open once the lottery is cancelled`() {
        val cancelled = lottery.copy(status = LotteryStatus.ENDED)

        assertFalse(cancelled.isRegistrationOpen(time("2027-05-15T12:00")))
    }

    @Test
    fun `a waiting list with no limit always has space`() {
        assertTrue(lottery.copy(entryCount = 500).hasSpace())
    }

    @Test
    fun `a waiting list under its limit has space`() {
        assertTrue(lottery.copy(capacity = 20, entryCount = 19).hasSpace())
    }

    @Test
    fun `a full waiting list has no space`() {
        assertFalse(lottery.copy(capacity = 20, entryCount = 20).hasSpace())
    }

    @Test
    fun `dates in order are valid`() {
        assertTrue(lottery.validateDates(EDMONTON))
    }

    @Test
    fun `registration that closes before it opens is invalid`() {
        val backwards = lottery.copy(registrationCloses = time("2027-04-01T09:00"))

        assertFalse(backwards.validateDates(EDMONTON))
    }

    @Test
    fun `a stay that ends the day it starts is invalid`() {
        assertFalse(lottery.copy(stayEnd = date("2027-07-01")).validateDates(EDMONTON))
    }

    @Test
    fun `registration that closes after the stay starts is invalid`() {
        val late = lottery.copy(registrationCloses = time("2027-07-01T09:00"))

        assertFalse(late.validateDates(EDMONTON))
    }

    @Test
    fun `a lottery shows as upcoming before registration opens`() {
        assertEquals(LotteryStatus.UPCOMING, lottery.statusAt(time("2027-04-30T12:00")))
    }

    @Test
    fun `a lottery shows as open once registration opens`() {
        assertEquals(LotteryStatus.OPEN, lottery.statusAt(time("2027-05-15T12:00")))
    }

    @Test
    fun `a drawn lottery shows as drawn`() {
        val drawn = lottery.copy(status = LotteryStatus.DRAWN)

        assertEquals(LotteryStatus.DRAWN, drawn.statusAt(time("2027-06-01T09:05")))
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    /** A time like "2027-05-01T09:00" in Edmonton. */
    private fun time(text: String): Timestamp =
        Timestamp(LocalDateTime.parse(text).atZone(EDMONTON).toInstant())

    private companion object {
        val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")
    }
}
