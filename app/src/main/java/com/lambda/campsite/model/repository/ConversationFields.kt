/*
 * Purpose: how a Conversation is stored in its Firestore document,
 * `conversations/{id}`: the field names, and turning a Conversation into a
 * map and back. It's part of the model layer, kept apart from the
 * repository so unit tests can check it without Firebase.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationFields. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Conversation

/**
 * How a [Conversation] is stored in its Firestore document,
 * `conversations/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. The field
 * names must match `firestore.rules`.
 */
object ConversationFields {

    const val OWNER_ID = "ownerId"
    const val GUEST_ID = "guestId"
    const val LAST_MESSAGE = "lastMessage"
    const val LAST_MESSAGE_AT = "lastMessageAt"
    const val LAST_MESSAGE_ID = "lastMessageId"
    const val OWNER_UNREAD_COUNT = "ownerUnreadCount"
    const val GUEST_UNREAD_COUNT = "guestUnreadCount"

    /** The document fields for [conversation]. */
    fun toMap(conversation: Conversation): Map<String, Any?> = mapOf(
        OWNER_ID to conversation.ownerId,
        GUEST_ID to conversation.guestId,
        LAST_MESSAGE to conversation.lastMessage,
        LAST_MESSAGE_AT to conversation.lastMessageAt,
        LAST_MESSAGE_ID to conversation.lastMessageId,
        OWNER_UNREAD_COUNT to conversation.ownerUnreadCount,
        GUEST_UNREAD_COUNT to conversation.guestUnreadCount,
    )

    /**
     * The fields for opening a new, empty conversation: [ownerId] and
     * [guestId] copied from the reservation, no last message yet, and
     * both unread counts at zero.
     */
    fun newConversationFields(ownerId: String, guestId: String): Map<String, Any?> = mapOf(
        OWNER_ID to ownerId,
        GUEST_ID to guestId,
        LAST_MESSAGE to null,
        LAST_MESSAGE_AT to null,
        LAST_MESSAGE_ID to null,
        OWNER_UNREAD_COUNT to 0,
        GUEST_UNREAD_COUNT to 0,
    )

    /** The fields for opening a new, empty conversation matching [conversation]. */
    fun newConversationFields(conversation: Conversation): Map<String, Any?> =
        newConversationFields(conversation.ownerId, conversation.guestId)

    /**
     * The conversation stored in the document with ID [id] and these
     * [fields]. Every field defaults if missing: empty IDs, no last message,
     * and zero unread counts.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Conversation = Conversation(
        id = id,
        ownerId = fields[OWNER_ID] as? String ?: "",
        guestId = fields[GUEST_ID] as? String ?: "",
        lastMessage = fields[LAST_MESSAGE] as? String,
        lastMessageAt = fields[LAST_MESSAGE_AT] as? Timestamp,
        lastMessageId = fields[LAST_MESSAGE_ID] as? String,
        // Firestore hands back every whole number as a Long.
        ownerUnreadCount = (fields[OWNER_UNREAD_COUNT] as? Number)?.toInt() ?: 0,
        guestUnreadCount = (fields[GUEST_UNREAD_COUNT] as? Number)?.toInt() ?: 0,
    )
}
