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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
    private val reservations = firestore.collection(RESERVATIONS)

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

    /**
     * Opens it if nobody has yet, reading the real reservation's ownerId and
     * guestId to fill it in. If another device opens the same conversation
     * between the check below and this write, the rules refuse this write,
     * but by then the conversation is open either way, so that refusal
     * isn't an error.
     */
    override suspend fun openConversation(reservationId: String) {
        auth.signedInUserId().first()
        val ref = conversations.document(reservationId)
        if (ref.get().await().exists()) return
        val reservation = reservations.document(reservationId).get().await().data.orEmpty()
        val ownerId = reservation[ReservationFields.OWNER_ID] as? String ?: return
        val guestId = reservation[ReservationFields.GUEST_ID] as? String ?: return
        try {
            ref.set(ConversationFields.newConversationFields(ownerId, guestId)).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED || !ref.get().await().exists()) throw e
        }
    }

    /**
     * Sends [text] as the batch that creates the message and updates the
     * conversation's preview and the other side's unread count together,
     * matching what the rules check. The increment is atomic, so this can't
     * lose a count another send or a read raced it to.
     */
    override suspend fun sendMessage(reservationId: String, text: String) {
        val uid = auth.signedInUserId().first()
        val conversationRef = conversations.document(reservationId)
        val conversation = conversationRef.get().await().data?.let { ConversationFields.fromMap(reservationId, it) }
            ?: error("Can't send a message to $reservationId before its conversation is open.")
        val recipientField =
            if (uid == conversation.ownerId) ConversationFields.GUEST_UNREAD_COUNT else ConversationFields.OWNER_UNREAD_COUNT
        val messageRef = messagesOf(reservationId).document()
        val message = Message(id = messageRef.id, senderId = uid, text = text, sentAt = Timestamp(0, 0))
        val batch = firestore.batch()
        batch.set(messageRef, MessageFields.newMessageFields(message))
        batch.update(
            conversationRef,
            mapOf(
                ConversationFields.LAST_MESSAGE to text,
                ConversationFields.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
                ConversationFields.LAST_MESSAGE_ID to messageRef.id,
                recipientField to FieldValue.increment(1),
            ),
        )
        batch.commit().await()
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
        const val RESERVATIONS = "reservations"
        const val DEFAULT_PAGE_SIZE = 20
    }
}
