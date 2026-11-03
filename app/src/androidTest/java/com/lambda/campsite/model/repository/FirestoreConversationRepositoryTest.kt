/*
 * Purpose: tests FirestoreConversationRepository against the Firestore
 * emulator (US 02.12.01, 02.12.02): the conversation list, paging through
 * messages, opening a conversation, sending one and marking one read,
 * including a message sent on one device showing up on another's.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following
 * FirestoreReservationRepositoryTest. Aarush Bhat reviewed and committed
 * them.
 */
package com.lambda.campsite.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.initialize
import com.lambda.campsite.model.Conversation
import com.lambda.campsite.model.Message
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.LambdaTestRunner
import com.lambda.campsite.testutil.signInAsNewUser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreConversationRepository] against the Firestore emulator
 * (US 02.12.01, 02.12.02). Each test signs in as a new user, so nothing
 * from another test is left in the phone's cache.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreConversationRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreConversationRepository(Firebase.auth, firestore, pageSize = 2)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
        setUpReservation("mine", ownerId = uid, guestId = "guest-1")
        setUpConversation("mine", ownerId = uid, guestId = "guest-1")
    }

    @Test
    fun myConversationsAreTheOnesIveOpenedNewestMessageFirst() {
        // setUp already opened "mine", with no message in it yet, so it sorts last.
        val mine = Conversation(id = "mine", ownerId = uid, guestId = "guest-1")
        setUpReservation("older", ownerId = uid, guestId = "guest-2")
        setUpReservation("newer", ownerId = "owner-2", guestId = uid)
        val older = setUpConversation("older", ownerId = uid, guestId = "guest-2", lastMessageAt = Timestamp(1_800_000_000, 0))
        val newer = setUpConversation("newer", ownerId = "owner-2", guestId = uid, lastMessageAt = Timestamp(1_801_000_000, 0))
        setUpReservation("theirs", ownerId = "owner-2", guestId = "guest-2")
        setUpConversation("theirs", ownerId = "owner-2", guestId = "guest-2")

        assertEventuallySends(listOf(newer, older, mine), repository.myConversations())
    }

    @Test
    fun messagesAreTheNewestPageNewestFirst() {
        val oldest = setUpMessage("mine", "oldest", senderId = uid, text = "one", sentAt = Timestamp(1_800_000_000, 0))
        val middle = setUpMessage("mine", "middle", senderId = uid, text = "two", sentAt = Timestamp(1_800_000_010, 0))
        val latest = setUpMessage("mine", "latest", senderId = uid, text = "three", sentAt = Timestamp(1_800_000_020, 0))

        // pageSize is 2, so the oldest of the three doesn't fit on the first page.
        assertEventuallySends(listOf(latest, middle), repository.messages("mine"))
    }

    @Test
    fun loadingOlderMessagesReturnsTheNextPageBack() {
        val oldest = setUpMessage("mine", "oldest", senderId = uid, text = "one", sentAt = Timestamp(1_800_000_000, 0))
        val middle = setUpMessage("mine", "middle", senderId = uid, text = "two", sentAt = Timestamp(1_800_000_010, 0))
        setUpMessage("mine", "latest", senderId = uid, text = "three", sentAt = Timestamp(1_800_000_020, 0))

        // pageSize is 2, so "middle" is the oldest message already on the first page.
        val olderPage = runBlocking { withTimeout(TIMEOUT_MS) { repository.loadOlderMessages("mine", before = middle) } }

        assertEquals(listOf(oldest), olderPage)
    }

    @Test
    fun openingACreatesAnEmptyConversationFromTheRealReservation() {
        setUpReservation("fresh", ownerId = uid, guestId = "guest-9")

        runBlocking { withTimeout(TIMEOUT_MS) { repository.openConversation("fresh") } }

        assertEquals(Conversation(id = "fresh", ownerId = uid, guestId = "guest-9"), conversationOnServer("fresh"))
    }

    @Test
    fun openingAnAlreadyOpenConversationDoesNothing() {
        // setUp already opened "mine".
        runBlocking { withTimeout(TIMEOUT_MS) { repository.openConversation("mine") } }

        assertEquals(Conversation(id = "mine", ownerId = uid, guestId = "guest-1"), conversationOnServer("mine"))
    }

    @Test
    fun openingAReservationYouCantReadIsRefused() {
        setUpReservation("theirs", ownerId = "owner-2", guestId = "guest-2")

        assertThrows(FirebaseFirestoreException::class.java) {
            runBlocking { withTimeout(TIMEOUT_MS) { repository.openConversation("theirs") } }
        }
    }

    @Test
    fun openingTheSameConversationTwiceAtOnceSucceedsForBoth() {
        setUpReservation("raced", ownerId = uid, guestId = "guest-9")

        val results = runBlocking {
            withTimeout(TIMEOUT_MS) {
                listOf(async { repository.openConversation("raced") }, async { repository.openConversation("raced") })
                    .awaitAll()
            }
        }

        assertEquals(2, results.size)
        assertEquals(Conversation(id = "raced", ownerId = uid, guestId = "guest-9"), conversationOnServer("raced"))
    }

    @Test
    fun sendingAsTheOwnerUpdatesThePreviewAndTheGuestsUnreadCount() {
        runBlocking { withTimeout(TIMEOUT_MS) { repository.sendMessage("mine", "What time should we arrive?") } }

        val saved = requireNotNull(conversationOnServer("mine"))
        assertEquals("What time should we arrive?", saved.lastMessage)
        assertEquals(1, saved.guestUnreadCount)
        assertEquals(0, saved.ownerUnreadCount)
        assertEventuallySends(listOf("What time should we arrive?"), mapToText(repository.messages("mine")))
    }

    @Test
    fun aSecondMessageAddsToTheUnreadCountInstead() {
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                repository.sendMessage("mine", "one")
                repository.sendMessage("mine", "two")
            }
        }

        assertEquals(2, conversationOnServer("mine")?.guestUnreadCount)
    }

    @Test
    fun sendingBeforeTheConversationIsOpenIsRefused() {
        setUpReservation("fresh", ownerId = uid, guestId = "guest-9")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { withTimeout(TIMEOUT_MS) { repository.sendMessage("fresh", "hi") } }
        }
    }

    @Test
    fun aMessageSentOnOneDeviceShowsUpOnTheOthersDevice() {
        val (secondAuth, secondFirestore) = secondPhone()
        val guestUid = runBlocking {
            secondAuth.signOut()
            requireNotNull(secondAuth.signInAnonymously().await().user).uid
        }
        setUpReservation("shared", ownerId = uid, guestId = guestUid)
        setUpConversation("shared", ownerId = uid, guestId = guestUid)
        val secondRepository = FirestoreConversationRepository(secondAuth, secondFirestore, pageSize = 2)

        runBlocking { withTimeout(TIMEOUT_MS) { repository.sendMessage("shared", "See you at noon") } }

        assertEventuallySends(listOf("See you at noon"), mapToText(secondRepository.messages("shared")))
    }

    private fun mapToText(flow: Flow<List<Message>>): Flow<List<String>> =
        flow.map { messages -> messages.map { it.text } }

    /** A second Firebase app, so a test can act as a second device signed in as a different user. */
    private fun secondPhone(): Pair<FirebaseAuth, FirebaseFirestore> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == SECOND_PHONE }
            ?: Firebase.initialize(context, Firebase.app.options, SECOND_PHONE).also {
                Firebase.auth(it).useEmulator(LambdaTestRunner.HOST, LambdaTestRunner.AUTH_PORT)
                Firebase.firestore(it).useEmulator(LambdaTestRunner.HOST, LambdaTestRunner.FIRESTORE_PORT)
            }
        return Firebase.auth(app) to Firebase.firestore(app)
    }

    @Test
    fun loadingOlderMessagesPastTheOldestReturnsNothing() {
        val oldest = setUpMessage("mine", "oldest", senderId = uid, text = "one", sentAt = Timestamp(1_800_000_000, 0))

        val olderPage = runBlocking { withTimeout(TIMEOUT_MS) { repository.loadOlderMessages("mine", before = oldest) } }

        assertEquals(emptyList<Message>(), olderPage)
    }

    /** Waits for [flow] to send [expected], and fails with the last value it sent if it never does. */
    private fun <T> assertEventuallySends(expected: T, flow: Flow<T>) {
        var last: T? = null
        try {
            runBlocking { withTimeout(TIMEOUT_MS) { flow.first { last = it; it == expected } } }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Expected $expected, but the last value sent was $last", e)
        }
    }

    /** Puts a reservation at `reservations/[id]`, skipping the rules. */
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

    /** Puts a conversation at `conversations/[id]`, skipping the rules, and returns it. */
    private fun setUpConversation(
        id: String,
        ownerId: String,
        guestId: String,
        lastMessageAt: Timestamp? = null,
    ): Conversation {
        val conversation = Conversation(id = id, ownerId = ownerId, guestId = guestId, lastMessageAt = lastMessageAt)
        FirestoreEmulator.setIgnoringRules("conversations/$id", ConversationFields.toMap(conversation))
        return conversation
    }

    /** Puts a message at `conversations/[conversationId]/messages/[id]`, skipping the rules, and returns it. */
    private fun setUpMessage(conversationId: String, id: String, senderId: String, text: String, sentAt: Timestamp): Message {
        val message = Message(id = id, senderId = senderId, text = text, sentAt = sentAt)
        FirestoreEmulator.setIgnoringRules("conversations/$conversationId/messages/$id", MessageFields.toMap(message))
        return message
    }

    /** The conversation as the server has it, skipping the phone's cache, or `null` if there isn't one. */
    private fun conversationOnServer(id: String): Conversation? = runBlocking {
        val snapshot = firestore.document("conversations/$id").get(Source.SERVER).await()
        if (!snapshot.exists()) null else ConversationFields.fromMap(id, snapshot.data.orEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SECOND_PHONE = "second-phone"
    }
}
