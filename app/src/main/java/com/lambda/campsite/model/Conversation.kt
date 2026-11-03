/*
 * Purpose: the Conversation model class, in the model layer. One
 * conversation exists per reservation, stored at `conversations/{id}`,
 * holding the owner's and guest's unread counts and a preview of the last
 * message for the inbox.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following
 * docs/architecture.md section 4 and the Conversation class in the team's
 * class diagram. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp

/**
 * One owner-and-guest chat about a reservation (US 02.12.01, 02.12.02).
 *
 * The chat screen opens a conversation before any message exists, so
 * [lastMessage] and [lastMessageAt] are nullable.
 *
 * @property id the conversation's document ID, which is also the
 *   reservation's ID: only one conversation exists per reservation.
 * @property ownerId the Firebase Auth user ID of the campsite's owner.
 * @property guestId the Firebase Auth user ID of the reservation's guest.
 * @property lastMessage the text of the most recent message, for the inbox,
 *   or `null` while the conversation has none.
 * @property lastMessageAt when the most recent message was sent, or `null`
 *   while the conversation has none.
 * @property ownerUnreadCount how many messages the owner hasn't read yet.
 * @property guestUnreadCount how many messages the guest hasn't read yet.
 */
data class Conversation(
    val id: String,
    val ownerId: String,
    val guestId: String,
    val lastMessage: String? = null,
    val lastMessageAt: Timestamp? = null,
    val ownerUnreadCount: Int = 0,
    val guestUnreadCount: Int = 0,
) {

    /**
     * How many unread messages belong to the user with this [uid]: the
     * owner's count, the guest's count, or zero for anyone else.
     */
    fun unreadCountFor(uid: String): Int = when (uid) {
        ownerId -> ownerUnreadCount
        guestId -> guestUnreadCount
        else -> 0
    }
}
