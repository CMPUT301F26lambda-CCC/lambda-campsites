/*
 * Purpose: tests the Firestore security rules for conversations against the
 * emulator (US 02.12.01, 02.12.02): who can read and open one, and marking
 * your own messages read.
 *
 * Outstanding issues:
 * - Sending a message, which also updates the conversation's preview and
 *   unread counts, is tested in MessageRulesTest instead, since it's one
 *   batch across both collections.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following
 * ReservationRulesTest. Aarush Bhat reviewed and committed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.repository.ConversationFields
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
 * Tests the security rules for `conversations/{reservationId}` (US
 * 02.12.01, 02.12.02): reading, opening a new empty conversation, and
 * marking your own unread count read.
 *
 * The first tests check that normal use is allowed, so the rules aren't
 * just denying everything. The rest each try one thing a modified app might
 * do and expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ConversationRulesTest {

    private val firestore = Firebase.firestore
    private val conversations = firestore.collection("conversations")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun ownerCanReadTheirConversation() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        val error = firestoreErrorFrom { conversations.document("mine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun guestCanReadTheirConversation() {
        setUpConversation("mine", ownerId = "owner-1", guestId = uid)

        val error = firestoreErrorFrom { conversations.document("mine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanLookUpAConversationThatDoesntExist() {
        val error = firestoreErrorFrom { conversations.document("no-such-conversation").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCantReadSomeoneElsesConversation() {
        setUpConversation("theirs", ownerId = "owner-1", guestId = "guest-1")

        val error = firestoreErrorFrom { conversations.document("theirs").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCanOpenTheConversationForTheirReservation() {
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")

        assertNull(open("mine", ownerId = uid, guestId = "guest-1"))
    }

    @Test
    fun guestCanOpenTheConversationForTheirReservation() {
        setUpReservation("mine", ownerId = "owner-1", guestId = uid)

        assertNull(open("mine", ownerId = "owner-1", guestId = uid))
    }

    @Test
    fun aThirdUserCantOpenTheConversation() {
        setUpReservation("theirs", ownerId = "owner-1", guestId = "guest-1")

        assertPermissionDenied(open("theirs", ownerId = "owner-1", guestId = "guest-1"))
    }

    @Test
    fun openingWithAnOwnerIdThatDoesntMatchTheReservationIsRefused() {
        setUpReservation("mine", ownerId = "owner-1", guestId = uid)

        assertPermissionDenied(open("mine", ownerId = uid, guestId = uid))
    }

    @Test
    fun openingWithAGuestIdThatDoesntMatchTheReservationIsRefused() {
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(open("mine", ownerId = uid, guestId = uid))
    }

    @Test
    fun openingWithAMessageAlreadyInItIsRefused() {
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(open("mine", ownerId = uid, guestId = "guest-1", lastMessage = "Hi"))
    }

    @Test
    fun openingWithANonZeroUnreadCountIsRefused() {
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")

        assertPermissionDenied(open("mine", ownerId = uid, guestId = "guest-1", ownerUnreadCount = 1))
    }

    @Test
    fun openingWithExtraFieldsIsRefused() {
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")

        val error = firestoreErrorFrom {
            conversations.document("mine").set(
                ConversationFields.newConversationFields(uid, "guest-1") + ("note" to "hi"),
            ).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCanMarkTheirOwnUnreadCountRead() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1", ownerUnreadCount = 3)

        val error = firestoreErrorFrom {
            conversations.document("mine").update(mapOf(ConversationFields.OWNER_UNREAD_COUNT to 0)).await()
        }

        assertNull(error)
    }

    @Test
    fun guestCanMarkTheirOwnUnreadCountRead() {
        setUpConversation("mine", ownerId = "owner-1", guestId = uid, guestUnreadCount = 2)

        val error = firestoreErrorFrom {
            conversations.document("mine").update(mapOf(ConversationFields.GUEST_UNREAD_COUNT to 0)).await()
        }

        assertNull(error)
    }

    @Test
    fun ownerCantMarkTheGuestsUnreadCountRead() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1", guestUnreadCount = 2)

        val error = firestoreErrorFrom {
            conversations.document("mine").update(mapOf(ConversationFields.GUEST_UNREAD_COUNT to 0)).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun markingReadToSomethingOtherThanZeroIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1", ownerUnreadCount = 3)

        val error = firestoreErrorFrom {
            conversations.document("mine").update(mapOf(ConversationFields.OWNER_UNREAD_COUNT to 1)).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun changingThePreviewWithoutSendingAMessageIsRefused() {
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")

        val error = firestoreErrorFrom {
            conversations.document("mine").update(
                mapOf(
                    ConversationFields.LAST_MESSAGE to "Made this up",
                    ConversationFields.LAST_MESSAGE_AT to FieldValue.serverTimestamp(),
                    ConversationFields.LAST_MESSAGE_ID to "made-up",
                    ConversationFields.GUEST_UNREAD_COUNT to 1,
                ),
            ).await()
        }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** Opens the conversation at `conversations/[id]`, the way the app does when the chat screen opens. */
    private fun open(
        id: String,
        ownerId: String,
        guestId: String,
        lastMessage: String? = null,
        ownerUnreadCount: Int = 0,
    ): FirebaseFirestoreException? = firestoreErrorFrom {
        conversations.document(id).set(
            ConversationFields.newConversationFields(ownerId, guestId) +
                mapOf(ConversationFields.LAST_MESSAGE to lastMessage, ConversationFields.OWNER_UNREAD_COUNT to ownerUnreadCount),
        ).await()
    }

    /** Puts a conversation at `conversations/[id]`, skipping the rules. */
    private fun setUpConversation(
        id: String,
        ownerId: String,
        guestId: String,
        ownerUnreadCount: Int = 0,
        guestUnreadCount: Int = 0,
    ) {
        val conversation = Conversation(
            id = id,
            ownerId = ownerId,
            guestId = guestId,
            ownerUnreadCount = ownerUnreadCount,
            guestUnreadCount = guestUnreadCount,
        )
        FirestoreEmulator.setIgnoringRules("conversations/$id", ConversationFields.toMap(conversation))
    }

    /** Puts a reservation at `reservations/[id]`, skipping the rules, so opening a conversation can check it. */
    private fun setUpReservation(id: String, ownerId: String, guestId: String) {
        FirestoreEmulator.setIgnoringRules(
            "reservations/$id",
            mapOf(
                "guestId" to guestId,
                "guestName" to "Jordan Lee",
                "ownerId" to ownerId,
                "campsiteId" to "campsite-1",
                "campsiteName" to "Moose Lake Campsite",
                "lotteryId" to "lottery-1",
                "checkIn" to "2027-07-02",
                "checkOut" to "2027-07-04",
                "guests" to 2,
                "status" to "CONFIRMED",
            ),
        )
    }
}
