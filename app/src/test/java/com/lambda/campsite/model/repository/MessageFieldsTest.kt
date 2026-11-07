/*
 * Purpose: unit tests for MessageFields, how a message is stored in
 * Firestore (US 02.12.01, 02.12.02).
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationFieldsTest. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [MessageFields], how a message is stored in Firestore. */
class MessageFieldsTest {

    private val message = Message(
        id = "message-1",
        senderId = "guest-1",
        text = "What time should we arrive?",
        sentAt = Timestamp(1_810_000_000, 0),
    )

    @Test
    fun `a message reads back the same after it's stored`() {
        val fields = MessageFields.toMap(message)

        assertEquals(message, MessageFields.fromMap("message-1", fields))
    }

    @Test
    fun `a new message leaves sentAt to the server`() {
        val fields = MessageFields.newMessageFields(message)

        assertEquals(FieldValue.serverTimestamp(), fields[MessageFields.SENT_AT])
        assertEquals("What time should we arrive?", fields[MessageFields.TEXT])
    }

    @Test
    fun `a sentAt the server hasn't set yet reads as time zero`() {
        val fields = MessageFields.toMap(message) + (MessageFields.SENT_AT to null)

        assertEquals(Timestamp(0, 0), MessageFields.fromMap("message-1", fields).sentAt)
    }

    @Test
    fun `a missing text reads back as empty`() {
        val fields = MessageFields.toMap(message) - MessageFields.TEXT

        assertEquals("", MessageFields.fromMap("message-1", fields).text)
    }
}
