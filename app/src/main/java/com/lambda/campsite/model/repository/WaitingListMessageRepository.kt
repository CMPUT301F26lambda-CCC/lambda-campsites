/*
 * Purpose: Sends waiting-list messages and reads the signed-in owner's sent history.
 * Outstanding issues: Shared Firebase deployment and real push verification remain.
 * Lalith Valluru, November 2026. GPT-6 through Codex assisted with #37 and #38.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One message and the recipient IDs recorded at send time. */
data class SentWaitingListMessage(
    val id: String,
    val lotteryName: String,
    val message: String,
    val sentAt: Timestamp,
    val recipientIds: List<String>,
)

/** Firebase operations for waiting-list messages. */
@OptIn(ExperimentalCoroutinesApi::class)
class WaitingListMessageRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    /** Watches the current owner's messages in descending send order. */
    fun history() = auth.signedInUserId().flatMapLatest { uid ->
        firestore.collection("profiles").document(uid).collection("sentNotifications")
            .orderBy("sentAt", Query.Direction.DESCENDING).liveSnapshots().map { snapshot ->
                snapshot.documents.filter { it.getBoolean("complete") != false }.map { doc ->
                    SentWaitingListMessage(
                        doc.id,
                        requireNotNull(doc.getString("lotteryName")),
                        requireNotNull(doc.getString("message")),
                        requireNotNull(doc.getTimestamp("sentAt")),
                        (doc.get("recipientIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    )
                }
            }
    }

    /** Reads the participant names captured during delivery. */
    suspend fun recipientNames(message: SentWaitingListMessage): List<String> {
        if (message.recipientIds.isNotEmpty()) return message.recipientIds
        val uid = requireNotNull(auth.currentUser?.uid)
        return firestore.collection("profiles").document(uid).collection("sentNotifications")
            .document(message.id).collection("recipients").get().await().documents.map {
                it.getString("name")?.takeIf(String::isNotBlank) ?: it.id
            }
    }

    /** Sends once for a request ID, including when an uncertain result is retried. */
    suspend fun send(lotteryId: String, message: String, requestId: String) {
        functions.getHttpsCallable("send_waiting_list_notification")
            .call(mapOf("lotteryId" to lotteryId, "message" to message, "requestId" to requestId)).await()
    }
}
