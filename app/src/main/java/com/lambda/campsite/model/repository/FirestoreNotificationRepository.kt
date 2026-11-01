/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FirestoreCampsiteRepository. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.lambda.campsite.model.Notification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Keeps notifications in Firestore at `notifications/{id}`.
 * [NotificationFields] decides how a notification is stored.
 *
 * The flow waits for sign-in first, and ends with a
 * `FirebaseFirestoreException` if Firestore refuses the read.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreNotificationRepository(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore,
) : NotificationRepository {

    private val notifications = firestore.collection(COLLECTION)

    override fun myNotifications(): Flow<List<Notification>> =
        auth.signedInUserId().flatMapLatest { uid ->
            notifications
                .whereEqualTo(NotificationFields.RECIPIENT_ID, uid)
                .orderBy(NotificationFields.SENT_AT, Query.Direction.DESCENDING)
                .liveSnapshots()
                .map { it.toNotifications() }
        }

    /**
     * Returns once the server accepts the change. Offline, the phone keeps the
     * change and this waits until the connection comes back.
     *
     * @throws com.google.firebase.firestore.FirebaseFirestoreException if the
     *   server refuses the write, for example when this notification belongs
     *   to someone else.
     */
    override suspend fun markAsRead(id: String) {
        // A write made before sign-in finishes would never be sent.
        auth.signedInUserId().first()
        notifications.document(id).update(NotificationFields.readField(true)).await()
    }

    private fun QuerySnapshot.toNotifications(): List<Notification> =
        documents.map { NotificationFields.fromMap(it.id, it.data.orEmpty()) }

    private companion object {
        const val COLLECTION = "notifications"
    }
}
