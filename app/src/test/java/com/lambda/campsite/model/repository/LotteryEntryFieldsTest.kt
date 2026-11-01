/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.LotteryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [LotteryEntryFields], how a waiting-list entry is stored in Firestore. */
class LotteryEntryFieldsTest {

    @Test
    fun `a new entry is waiting, with the entrant's ID and the server's time`() {
        val fields = LotteryEntryFields.newEntryFields("entrant-1")

        val expected = mapOf(
            LotteryEntryFields.ENTRANT_ID to "entrant-1",
            LotteryEntryFields.JOINED_AT to FieldValue.serverTimestamp(),
            LotteryEntryFields.STATUS to "WAITING",
        )
        assertEquals(expected, fields)
    }

    @Test
    fun `an entry reads back the same after it's stored`() {
        val entry = LotteryEntry(
            lotteryId = "lottery-1",
            entrantId = "entrant-1",
            joinedAt = Timestamp(1_808_100_000, 0),
            status = EntryStatus.BOOKED,
            reservationId = "reservation-1",
        )

        val fields = LotteryEntryFields.toMap(entry)

        assertEquals(entry, LotteryEntryFields.fromMap("lottery-1", "entrant-1", fields))
    }

    @Test
    fun `an entry with a status the app doesn't know reads as null`() {
        val fields = mapOf(LotteryEntryFields.STATUS to "SOMETHING_NEW")

        assertNull(LotteryEntryFields.fromMap("lottery-1", "entrant-1", fields))
    }

    @Test
    fun `an entry with no status reads as null`() {
        assertNull(LotteryEntryFields.fromMap("lottery-1", "entrant-1", emptyMap()))
    }

    @Test
    fun `missing fields other than the status read as the defaults`() {
        val read = LotteryEntryFields.fromMap("lottery-1", "entrant-1", mapOf(LotteryEntryFields.STATUS to "WAITING"))

        assertEquals(LotteryEntry("lottery-1", "entrant-1", joinedAt = Timestamp(0, 0)), read)
    }
}
