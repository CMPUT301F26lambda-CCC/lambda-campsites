/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [LotteryFields], how a lottery is stored in Firestore. */
class LotteryFieldsTest {

    private val lottery = Lottery(
        id = "lottery-1",
        ownerId = "owner-1",
        name = "Moose Lake, July 2027",
        campsiteIds = listOf("campsite-1", "campsite-2"),
        registrationOpens = Timestamp(1_808_000_000, 0),
        registrationCloses = Timestamp(1_810_000_000, 0),
        stayStart = LocalDate.parse("2027-07-01"),
        stayEnd = LocalDate.parse("2027-07-31"),
        numberOfWinners = 10,
        capacity = 50,
    )

    @Test
    fun `a lottery reads back the same after it's stored`() {
        val fields = LotteryFields.toMap(lottery)

        assertEquals(lottery, LotteryFields.fromMap("lottery-1", fields))
    }

    @Test
    fun `a lottery with no waiting-list limit reads back with no limit`() {
        val unlimited = lottery.copy(capacity = null)

        assertEquals(unlimited, LotteryFields.fromMap("lottery-1", LotteryFields.toMap(unlimited)))
    }

    @Test
    fun `stay dates are stored as text`() {
        val fields = LotteryFields.toMap(lottery)

        assertEquals("2027-07-01", fields[LotteryFields.STAY_START])
        assertEquals("2027-07-31", fields[LotteryFields.STAY_END])
    }

    @Test
    fun `the entry count isn't stored, since only joining and leaving change it`() {
        val fields = LotteryFields.toMap(lottery.copy(entryCount = 12))

        assertFalse(fields.containsKey(LotteryFields.ENTRY_COUNT))
    }

    @Test
    fun `the entry count is read`() {
        val fields = LotteryFields.toMap(lottery) + mapOf(LotteryFields.ENTRY_COUNT to 12L)

        assertEquals(12, LotteryFields.fromMap("lottery-1", fields)?.entryCount)
    }

    @Test
    fun `a lottery with no registration time reads as null`() {
        val fields = LotteryFields.toMap(lottery) - LotteryFields.REGISTRATION_CLOSES

        assertNull(LotteryFields.fromMap("lottery-1", fields))
    }

    @Test
    fun `a lottery with a stay date that isn't a real date reads as null`() {
        val fields = LotteryFields.toMap(lottery) + mapOf(LotteryFields.STAY_END to "2027-02-30")

        assertNull(LotteryFields.fromMap("lottery-1", fields))
    }

    @Test
    fun `a lottery with a status the app doesn't know reads as null`() {
        val fields = LotteryFields.toMap(lottery) + mapOf(LotteryFields.STATUS to "SOMETHING_NEW")

        assertNull(LotteryFields.fromMap("lottery-1", fields))
    }

    @Test
    fun `missing fields other than the dates and status read as the defaults`() {
        val fields = mapOf(
            LotteryFields.REGISTRATION_OPENS to lottery.registrationOpens,
            LotteryFields.REGISTRATION_CLOSES to lottery.registrationCloses,
            LotteryFields.STAY_START to "2027-07-01",
            LotteryFields.STAY_END to "2027-07-31",
            LotteryFields.STATUS to "DRAWN",
        )

        val defaults = Lottery(
            id = "lottery-1",
            ownerId = "",
            name = "",
            campsiteIds = emptyList(),
            registrationOpens = lottery.registrationOpens,
            registrationCloses = lottery.registrationCloses,
            stayStart = lottery.stayStart,
            stayEnd = lottery.stayEnd,
            status = LotteryStatus.DRAWN,
        )
        assertEquals(defaults, LotteryFields.fromMap("lottery-1", fields))
    }
}
