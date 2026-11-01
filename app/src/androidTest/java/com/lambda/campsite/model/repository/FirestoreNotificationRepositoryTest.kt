/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the pattern in
 * FirestoreCampsiteRepositoryTest. It helped with the test cases, the
 * coroutines code and the Kotlin syntax. Aarush Bhat reviewed them, ran them
 * and committed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.firestoreErrorFrom
import com.lambda.campsite.testutil.signInAsNewUser
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreNotificationRepository] against the Firestore emulator
 * (US 01.03.01, 01.03.02). Each test signs in as a new user.
 *
 * Only a Cloud Function creates a notification, so these tests write them
 * straight to the emulator with [FirestoreEmulator.setIgnoringRules] instead
 * of through the repository.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreNotificationRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreNotificationRepository(Firebase.auth, firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
    }

    @Test
    fun myNotificationsHasOnlyMineNewestFirst() {
        val older = notification(id = "older", recipientId = uid, sentAt = Timestamp(1_000, 0))
        val newer = notification(id = "newer", recipientId = uid, sentAt = Timestamp(2_000, 0))
        val theirs = notification(id = "theirs", recipientId = "someone-else", sentAt = Timestamp(3_000, 0))
        setUpOnServer(older, newer, theirs)

        assertEventuallySends(listOf(newer, older), repository.myNotifications())
    }

    @Test
    fun markingAsReadReadsBackFromTheServer() {
        val mine = notification(id = "mine", recipientId = uid)
        setUpOnServer(mine)

        runBlocking { withTimeout(TIMEOUT_MS) { repository.markAsRead("mine") } }

        assertEquals(mine.copy(read = true), notificationOnServer("mine"))
    }

    @Test
    fun markingSomeoneElsesNotificationAsReadThrows() {
        val theirs = notification(id = "theirs", recipientId = "someone-else")
        setUpOnServer(theirs)

        val error = firestoreErrorFrom { repository.markAsRead("theirs") }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun notification(
        id: String,
        recipientId: String,
        sentAt: Timestamp = Timestamp(1_000, 0),
    ) = Notification(
        id = id,
        recipientId = recipientId,
        type = NotificationType.WIN,
        title = "You won the lottery",
        body = "You won the lottery for Moose Lake Campsite.",
        sentAt = sentAt,
        read = false,
    )

    /** Writes [notifications] straight to the emulator, like a Cloud Function would. */
    private fun setUpOnServer(vararg notifications: Notification) {
        for (notification in notifications) {
            FirestoreEmulator.setIgnoringRules("notifications/${notification.id}", NotificationFields.toMap(notification))
        }
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

    /** The notification as the server has it, skipping the phone's cache. */
    private fun notificationOnServer(id: String): Notification = runBlocking {
        val snapshot = firestore.document("notifications/$id").get(Source.SERVER).await()
        NotificationFields.fromMap(id, snapshot.data.orEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
