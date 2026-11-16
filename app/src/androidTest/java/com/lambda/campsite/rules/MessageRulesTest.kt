/*
 * Purpose: tests the Firestore security rules for messages against the
 * emulator (US 02.12.01, 02.12.02): who can read them, and that sending one
 * only goes through together with the matching update to its conversation's
 * preview and unread count.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following
 * ReservationRulesTest's batched-write tests. Aarush Bhat reviewed and
 * committed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.repository.ConversationFields
import com.lambda.campsite.model.repository.MessageFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the security rules for `conversations/{reservationId}/messages/{id}`
 * (US 02.12.01, 02.12.02): reading, and sending as the batch that creates the
 * message and updates its conversation's preview and unread count together.
 *
 * The first tests check that normal use is allowed, so the rules aren't
 * just denying everything. The rest each try one thing a modified app might
 * do and expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class MessageRulesTest {

    private val firestore = Firebase.firestore
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun ownerCanSendAMessage() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertNull(send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT))
    }

    @Test
    fun guestCanSendAMessage() {
        setUpConversation("mine", ownerId = "owner-1", guestId = uid)

        assertNull(send("mine", senderId = uid, recipientField = ConversationFields.OWNER_UNREAD_COUNT))
    }

    @Test
    fun aThirdUserCantSendAMessage() {
        setUpConversation("theirs", ownerId = "owner-1", guestId = "guest-1")

        assertPermissionDenied(send("theirs", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT))
    }

    @Test
    fun sendingWithSomeoneElsesSenderIdIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = "guest-1", recipientField = ConversationFields.GUEST_UNREAD_COUNT),
        )
    }

    @Test
    fun sendingWithoutUpdatingTheConversationsPreviewIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, updateConversation = false),
        )
    }

    @Test
    fun sendingWithoutIncrementingTheRecipientsUnreadCountIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, bumpRecipientCount = false),
        )
    }

    @Test
    fun sendingThatAlsoChangesTheSendersOwnUnreadCountIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, alsoBumpSenderCount = true),
        )
    }

    @Test
    fun aPreviewThatDoesntMatchTheMessagesTextIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, previewText = "Something else"),
        )
    }

    @Test
    fun aBackdatedSentAtIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, serverTimestamp = false),
        )
    }

    @Test
    fun emptyTextIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, text = ""),
        )
    }

    @Test
    fun extraFieldsOnTheMessageAreRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(
            send("mine", senderId = uid, recipientField = ConversationFields.GUEST_UNREAD_COUNT, extraField = true),
        )
    }

    @Test
    fun ownerCanReadAMessage() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")
        val id = setUpMessage("mine", senderId = "guest-1", text = "Hi")

        val error = firestoreErrorFrom { messages("mine").document(id).get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun aThirdUserCantReadAMessage() {
        setUpConversation("theirs", ownerId = "owner-1", guestId = "guest-1")
        val id = setUpMessage("theirs", senderId = "guest-1", text = "Hi")

        val error = firestoreErrorFrom { messages("theirs").document(id).get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun messages(conversationId: String) =
        firestore.collection("conversations").document(conversationId).collection("messages")

    /**
     * Sends a message to the conversation at [conversationId] as [senderId], as one
     * batch: creating the message, and updating the conversation's preview and
     * [recipientField]'s unread count. Each flag breaks one part of the pairing, so a
     * test can check it's required.
     */
    private fun send(
        conversationId: String,
        senderId: String,
        recipientField: String,
        text: String = "What time should we arrive?",
        previewText: String = text,
        serverTimestamp: Boolean = true,
        updateConversation: Boolean = true,
        bumpRecipientCount: Boolean = true,
        alsoBumpSenderCount: Boolean = false,
        extraField: Boolean = false,
    ): FirebaseFirestoreException? {
        val conversationRef = firestore.collection("conversations").document(conversationId)
        val messageRef = messages(conversationId).document()
        val senderField =
            if (recipientField == ConversationFields.GUEST_UNREAD_COUNT) {
                ConversationFields.OWNER_UNREAD_COUNT
            } else {
                ConversationFields.GUEST_UNREAD_COUNT
            }
        return firestoreErrorFrom {
            val batch = firestore.batch()
            val messageFields = mutableMapOf<String, Any?>(
                MessageFields.SENDER_ID to senderId,
                MessageFields.TEXT to text,
                MessageFields.SENT_AT to if (serverTimestamp) FieldValue.serverTimestamp() else Timestamp(1_810_000_000, 0),
            )
            if (extraField) messageFields["note"] = "hi"
            batch.set(messageRef, messageFields)
            if (updateConversation) {
                val conversationUpdate = mutableMapOf<String, Any?>(
                    ConversationFields.LAST_MESSAGE to previewText,
                    ConversationFields.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
                    ConversationFields.LAST_MESSAGE_ID to messageRef.id,
                    recipientField to (if (bumpRecipientCount) 1 else 0),
                )
                if (alsoBumpSenderCount) conversationUpdate[senderField] = 1
                batch.update(conversationRef, conversationUpdate)
            }
            batch.commit().await()
        }
    }

    /** Puts a message at `conversations/[conversationId]/messages/[id]`, skipping the rules. */
    private fun setUpMessage(conversationId: String, senderId: String, text: String): String {
        val id = messages(conversationId).document().id
        FirestoreEmulator.setIgnoringRules(
            "conversations/$conversationId/messages/$id",
            mapOf(
                MessageFields.SENDER_ID to senderId,
                MessageFields.TEXT to text,
                MessageFields.SENT_AT to Timestamp(1_810_000_000, 0),
            ),
        )
        return id
    }

    /** Puts a conversation at `conversations/[id]`, skipping the rules. */
    private fun setUpConversation(id: String, ownerId: String, guestId: String) {
        FirestoreEmulator.setIgnoringRules(
            "conversations/$id",
            ConversationFields.toMap(Conversation(id = id, ownerId = ownerId, guestId = guestId)),
        )
    }
}
