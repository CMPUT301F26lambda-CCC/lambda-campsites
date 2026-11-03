/*
 * Purpose: the model layer's interface for owner-and-entrant chat: watching
 * a device's conversations and one conversation's messages, opening a
 * conversation, sending a message, and marking one read. Controllers depend
 * on it instead of on Firestore (the repository pattern), so unit tests can
 * swap in a fake.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationRepository. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Watches conversations and messages, and opens a conversation, sends a
 * message and marks one read.
 *
 * Controllers depend on this interface, never on a particular version of
 * it. The app uses `FirestoreConversationRepository`, and unit tests use
 * `FakeConversationRepository`.
 */
interface ConversationRepository {

    /**
     * This device's conversations, as owner or guest, with the newest
     * message first, sent again whenever one changes (US 02.12.01). A
     * conversation nobody has opened yet isn't in this list.
     */
    fun myConversations(): Flow<List<Conversation>>

    /**
     * The newest page of messages in the conversation with this
     * [reservationId], newest first, sent again whenever one arrives
     * (US 02.12.01, 02.12.02).
     */
    fun messages(reservationId: String): Flow<List<Message>>

    /**
     * The next page of messages in the conversation with this
     * [reservationId] that are older than [before] (US 02.12.01).
     */
    suspend fun loadOlderMessages(reservationId: String, before: Message): List<Message>

    /**
     * Opens the conversation for the reservation with this [reservationId],
     * if nobody has opened it yet (US 02.12.01). Safe to call every time the
     * chat screen opens.
     *
     * @throws Exception if the server can't be reached, or there's no such
     *   reservation this device can read.
     */
    suspend fun openConversation(reservationId: String)

    /**
     * Sends [text] to the conversation with this [reservationId], as the
     * owner or the guest, and raises the other side's unread count by one
     * (US 02.12.01, 02.12.02).
     *
     * @throws Exception if the server can't be reached, or the conversation
     *   hasn't been opened yet.
     */
    suspend fun sendMessage(reservationId: String, text: String)

    /**
     * Marks every message in the conversation with this [reservationId]
     * read, for this device's side only (US 02.12.02).
     *
     * @throws Exception if the server can't be reached.
     */
    suspend fun markRead(reservationId: String)
}
