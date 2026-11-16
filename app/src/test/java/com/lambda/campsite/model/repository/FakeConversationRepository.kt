/*
 * Purpose: a ConversationRepository for unit tests. It keeps conversations
 * and their messages in memory, so controller tests run without Firebase,
 * and can be told to fail like an offline phone would.
 *
 * Outstanding issues:
 * - messages() sends every stored message, not just a page. Nothing here
 *   needs pagination yet, so loadOlderMessages always comes back empty.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FakeReservationRepository. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.Message
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [ConversationRepository] for unit tests. It keeps conversations and
 * their messages in memory, and can be told to fail like an offline phone
 * would.
 *
 * @param myUserId the user ID it treats as this device's.
 * @param participants the owner and guest ID for each reservation this
 *   device can open a conversation for. [openConversation] reads it instead
 *   of a real reservation.
 * @param initial the conversations to start with, anyone's.
 */
class FakeConversationRepository(
    private val myUserId: String = "user-1",
    private val participants: Map<String, Pair<String, String>> = emptyMap(),
    initial: List<Conversation> = emptyList(),
) : ConversationRepository {

    private val stored = MutableStateFlow(initial)
    private val messages = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private var nextId = 1

    /** When `true`, every change throws as if the phone were offline. */
    var failSaves = false

    override fun myConversations(): Flow<List<Conversation>> =
        stored.map { all ->
            all.filter { it.ownerId == myUserId || it.guestId == myUserId }
                .sortedByDescending { it.lastMessageAt ?: Timestamp(0, 0) }
        }

    override fun messages(reservationId: String): Flow<List<Message>> =
        messagesFor(reservationId).map { it.sortedByDescending(Message::sentAt) }

    override suspend fun loadOlderMessages(reservationId: String, before: Message): List<Message> =
        messagesFor(reservationId).value.filter { it.sentAt < before.sentAt }.sortedByDescending(Message::sentAt)

    override suspend fun openConversation(reservationId: String) {
        checkCanChange()
        if (stored.value.any { it.id == reservationId }) return
        val (ownerId, guestId) = participants[reservationId] ?: return
        stored.update { it + Conversation(id = reservationId, ownerId = ownerId, guestId = guestId) }
    }

    override suspend fun sendMessage(reservationId: String, text: String) {
        checkCanChange()
        val conversation = stored.value.firstOrNull { it.id == reservationId }
            ?: error("Can't send a message to $reservationId before its conversation is open.")
        val message = Message(id = "message-${nextId++}", senderId = myUserId, text = text, sentAt = Timestamp.now())
        messagesFor(reservationId).update { it + message }
        val sentByOwner = myUserId == conversation.ownerId
        stored.update { all ->
            all.filterNot { it.id == reservationId } + conversation.copy(
                lastMessage = text,
                lastMessageAt = message.sentAt,
                lastMessageId = message.id,
                ownerUnreadCount = if (sentByOwner) conversation.ownerUnreadCount else conversation.ownerUnreadCount + 1,
                guestUnreadCount = if (sentByOwner) conversation.guestUnreadCount + 1 else conversation.guestUnreadCount,
            )
        }
    }

    override suspend fun markRead(reservationId: String) {
        checkCanChange()
        val conversation = stored.value.firstOrNull { it.id == reservationId } ?: return
        val read = if (myUserId == conversation.ownerId) {
            conversation.copy(ownerUnreadCount = 0)
        } else {
            conversation.copy(guestUnreadCount = 0)
        }
        stored.update { all -> all.filterNot { it.id == reservationId } + read }
    }

    /** Adds a message to a conversation without a call, like the other person sending it. */
    fun receiveElsewhere(reservationId: String, message: Message) {
        messagesFor(reservationId).update { it + message }
    }

    private fun messagesFor(reservationId: String): MutableStateFlow<List<Message>> =
        messages.getOrPut(reservationId) { MutableStateFlow(emptyList()) }

    private fun checkCanChange() {
        if (failSaves) throw IOException("Offline")
    }
}
