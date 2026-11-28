/*
 * Purpose: The model-layer interface for reading and posting campsite reviews.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Flow 17 review repository contract. Human review is pending.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.CampsiteReview
import kotlinx.coroutines.flow.Flow

/** Written reviews for one campsite, independent of its star ratings (US 01.13.02). */
interface CampsiteReviewRepository {
    /** All posted reviews for [campsiteId], updated when a review changes. */
    fun reviews(campsiteId: String): Flow<List<CampsiteReview>>

    /**
     * Posts or replaces this entrant's review using a completed [reservationId].
     * Firestore rules enforce the stay, identity and text limits.
     *
     * @throws Exception if the write is denied or the device is offline.
     */
    suspend fun post(campsiteId: String, reservationId: String, text: String)
}
