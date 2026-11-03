/*
 * Purpose: unit tests for the Conversation model (US 02.12.01): which
 * unread count belongs to which side of the chat.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationTest. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [Conversation]: which unread count belongs to which side of the chat. */
class ConversationTest {

    private val conversation = Conversation(
        id = "reservation-1",
        ownerId = "owner-1",
        guestId = "guest-1",
        ownerUnreadCount = 2,
        guestUnreadCount = 5,
    )

    @Test
    fun `the owner's unread count belongs to the owner`() {
        assertEquals(2, conversation.unreadCountFor("owner-1"))
    }

    @Test
    fun `the guest's unread count belongs to the guest`() {
        assertEquals(5, conversation.unreadCountFor("guest-1"))
    }

    @Test
    fun `a third user has no unread count`() {
        assertEquals(0, conversation.unreadCountFor("someone-else"))
    }
}
