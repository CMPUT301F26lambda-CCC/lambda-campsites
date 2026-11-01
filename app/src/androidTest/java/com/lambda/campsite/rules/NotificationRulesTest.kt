/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * notification rules in firestore.rules. Aarush Bhat reviewed and
 * committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add the query tests and the test for the read value in
 * November 2026. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
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
 * Tests the security rules for `notifications/{notificationId}` (US 01.03.01,
 * 01.03.02).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class NotificationRulesTest {

    private val notifications = Firebase.firestore.collection("notifications")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun userCanReadTheirOwnNotification() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom { notifications.document("mine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanMarkTheirOwnNotificationRead() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom {
            notifications.document("mine").set(mapOf("read" to true), SetOptions.merge()).await()
        }

        assertNull(error)
    }

    @Test
    fun userCanQueryTheirOwnNotifications() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom {
            notifications
                .whereEqualTo("recipientId", uid)
                .orderBy("sentAt", Query.Direction.DESCENDING)
                .get(Source.SERVER)
                .await()
        }

        assertNull(error)
    }

    @Test
    fun userCantReadSomeoneElsesNotification() {
        FirestoreEmulator.setIgnoringRules("notifications/theirs", notificationFields(recipientId = "someone-else"))

        val error = firestoreErrorFrom { notifications.document("theirs").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun queryWithoutTheRecipientFilterIsRefused() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom { notifications.get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantMarkSomeoneElsesNotificationRead() {
        FirestoreEmulator.setIgnoringRules("notifications/theirs", notificationFields(recipientId = "someone-else"))

        val error = firestoreErrorFrom {
            notifications.document("theirs").set(mapOf("read" to true), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantCreateANotification() {
        val error = firestoreErrorFrom {
            notifications.document("new").set(notificationFields(recipientId = uid)).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantChangeAnythingOtherThanRead() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom {
            notifications.document("mine").set(mapOf("title" to "Changed"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantSetReadToSomethingOtherThanTrueOrFalse() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom {
            notifications.document("mine").set(mapOf("read" to "yes"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantDeleteANotification() {
        FirestoreEmulator.setIgnoringRules("notifications/mine", notificationFields(recipientId = uid))

        val error = firestoreErrorFrom { notifications.document("mine").delete().await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** A whole notification document, in the shape the app stores it. */
    private fun notificationFields(recipientId: String): Map<String, Any?> = mapOf(
        "recipientId" to recipientId,
        "type" to "WIN",
        "title" to "You won the lottery",
        "body" to "You won the lottery for Moose Lake Campsite.",
        "sentAt" to Timestamp(1_000, 0),
        "read" to false,
    )
}
