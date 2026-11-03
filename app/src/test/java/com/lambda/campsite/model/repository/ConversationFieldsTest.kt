/*
 * Purpose: unit tests for ConversationFields, how a conversation is stored
 * in Firestore (US 02.12.01).
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationFieldsTest. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [ConversationFields], how a conversation is stored in Firestore. */
class ConversationFieldsTest {

    private val conversation = Conversation(
        id = "reservation-1",
        ownerId = "owner-1",
        guestId = "guest-1",
    )

    @Test
    fun `a freshly opened conversation reads back the same after it's stored`() {
        val fields = ConversationFields.toMap(conversation)

        assertEquals(conversation, ConversationFields.fromMap("reservation-1", fields))
    }

    @Test
    fun `a conversation with messages reads back the same after it's stored`() {
        val started = conversation.copy(
            lastMessage = "See you then!",
            lastMessageAt = Timestamp(1_810_000_000, 0),
            ownerUnreadCount = 0,
            guestUnreadCount = 1,
        )

        assertEquals(started, ConversationFields.fromMap("reservation-1", ConversationFields.toMap(started)))
    }

    @Test
    fun `unread counts stored as Long read back as Int`() {
        // Firestore hands back every whole number as a Long.
        val fields = ConversationFields.toMap(conversation) +
            (ConversationFields.OWNER_UNREAD_COUNT to 3L) +
            (ConversationFields.GUEST_UNREAD_COUNT to 4L)

        val read = ConversationFields.fromMap("reservation-1", fields)

        assertEquals(3, read.ownerUnreadCount)
        assertEquals(4, read.guestUnreadCount)
    }

    @Test
    fun `missing unread counts read back as zero`() {
        val fields = ConversationFields.toMap(conversation) -
            ConversationFields.OWNER_UNREAD_COUNT -
            ConversationFields.GUEST_UNREAD_COUNT

        val read = ConversationFields.fromMap("reservation-1", fields)

        assertEquals(0, read.ownerUnreadCount)
        assertEquals(0, read.guestUnreadCount)
    }

    @Test
    fun `a missing lastMessage reads back as null`() {
        val fields = ConversationFields.toMap(conversation) - ConversationFields.LAST_MESSAGE

        assertEquals(null, ConversationFields.fromMap("reservation-1", fields).lastMessage)
    }

    @Test
    fun `the empty fields for a new conversation`() {
        val fields = ConversationFields.newConversationFields(conversation)

        assertEquals("owner-1", fields[ConversationFields.OWNER_ID])
        assertEquals("guest-1", fields[ConversationFields.GUEST_ID])
        assertEquals(null, fields[ConversationFields.LAST_MESSAGE])
        assertEquals(null, fields[ConversationFields.LAST_MESSAGE_AT])
        assertEquals(0, fields[ConversationFields.OWNER_UNREAD_COUNT])
        assertEquals(0, fields[ConversationFields.GUEST_UNREAD_COUNT])
    }
}
