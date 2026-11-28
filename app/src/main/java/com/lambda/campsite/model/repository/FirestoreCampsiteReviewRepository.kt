/*
 * Purpose: Reads and posts written reviews in Firestore for Flow 17.
 * Outstanding issues: Admin campsite cleanup (#113) must also remove the
 * reviews subcollection when a campsite is deleted.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Firestore listener and review write. Human review is pending.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lambda.campsite.model.CampsiteReview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Firestore version of [CampsiteReviewRepository], one document per entrant and campsite. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCampsiteReviewRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CampsiteReviewRepository {
    private fun reviewCollection(campsiteId: String) =
        firestore.collection("campsites").document(campsiteId).collection("reviews")

    override fun reviews(campsiteId: String): Flow<List<CampsiteReview>> =
        auth.signedInUserId().flatMapLatest {
            reviewCollection(campsiteId).liveSnapshots().map { snapshot ->
                snapshot.documents.mapNotNull { document ->
                    CampsiteReviewFields.fromMap(document.id, document.data.orEmpty())
                }.sortedByDescending(CampsiteReview::postedAt)
            }
        }

    override suspend fun post(campsiteId: String, reservationId: String, text: String) {
        val cleanText = text.trim()
        require(cleanText.length in 1..MAX_REVIEW_LENGTH) { "Review must be 1 to 1000 characters." }
        val uid = auth.signedInUserId().first()
        reviewCollection(campsiteId).document(uid)
            .set(CampsiteReviewFields.forPost(reservationId, cleanText)).await()
    }

    private companion object {
        const val MAX_REVIEW_LENGTH = 1000
    }
}
