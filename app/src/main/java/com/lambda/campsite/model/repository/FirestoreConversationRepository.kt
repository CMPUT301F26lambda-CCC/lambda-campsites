/*
 * Purpose: the Firestore version of ConversationRepository. It keeps
 * conversations at `conversations/{reservationId}` and their messages in
 * that document's `messages` subcollection.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FirestoreReservationRepository. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Keeps conversations in Firestore at `conversations/{reservationId}`, and
 * their messages in that document's `messages` subcollection.
 * [ConversationFields] and [MessageFields] decide how they're stored.
 *
 * Every flow waits for sign-in first, and ends with a
 * `FirebaseFirestoreException` if Firestore refuses the read.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in
 *   when the app starts.
 * @param firestore the database.
 * @param pageSize how many messages a page holds. Tests pass a small
 *   number so a page boundary fits in a short list; the app uses the
 *   default.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreConversationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : ConversationRepository {

    private val conversations = firestore.collection(COLLECTION)

    override fun myConversations(): Flow<List<Conversation>> =
        auth.signedInUserId().flatMapLatest { uid ->
            conversations
                .where(
                    Filter.or(
                        Filter.equalTo(ConversationFields.OWNER_ID, uid),
                        Filter.equalTo(ConversationFields.GUEST_ID, uid),
                    ),
                )
                .liveSnapshots()
                .map { it.toConversations() }
        }

    override fun messages(reservationId: String): Flow<List<Message>> =
        auth.signedInUserId().flatMapLatest {
            messagesOf(reservationId)
                .orderBy(MessageFields.SENT_AT, Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
                .liveSnapshots()
                .map { it.toMessages() }
        }

    override suspend fun loadOlderMessages(reservationId: String, before: Message): List<Message> {
        auth.signedInUserId().first()
        return messagesOf(reservationId)
            .orderBy(MessageFields.SENT_AT, Query.Direction.DESCENDING)
            .startAfter(before.sentAt)
            .limit(pageSize.toLong())
            .get()
            .await()
            .toMessages()
    }

    override suspend fun openConversation(reservationId: String) {
        TODO("opens the conversation with the real reservation's ownerId and guestId")
    }

    override suspend fun sendMessage(reservationId: String, text: String) {
        TODO("sends a message and updates the conversation's preview and the recipient's unread count")
    }

    override suspend fun markRead(reservationId: String) {
        TODO("zeroes this device's own unread count")
    }

    /** A conversation's messages, `conversations/{reservationId}/messages`. */
    private fun messagesOf(reservationId: String) = conversations.document(reservationId).collection(MESSAGES)

    /** The conversations in this snapshot, newest message first. */
    private fun QuerySnapshot.toConversations(): List<Conversation> =
        documents
            .map { ConversationFields.fromMap(it.id, it.data.orEmpty()) }
            .sortedByDescending { it.lastMessageAt ?: Timestamp(0, 0) }

    /** The messages in this snapshot, in the order Firestore sent them. */
    private fun QuerySnapshot.toMessages(): List<Message> =
        documents.map { MessageFields.fromMap(it.id, it.data.orEmpty()) }

    private companion object {
        const val COLLECTION = "conversations"
        const val MESSAGES = "messages"
        const val DEFAULT_PAGE_SIZE = 20
    }
}
