/*
 * Purpose: In-memory campsite reviews for controller tests.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with this
 * Flow 17 test fake. Human review is pending.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.CampsiteReview
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Sends live review lists and can simulate an offline post (US 01.13.02). */
class FakeCampsiteReviewRepository(private val myUserId: String = "entrant-1") : CampsiteReviewRepository {
    private val stored = MutableStateFlow<Map<String, List<CampsiteReview>>>(emptyMap())
    /** Simulates a temporary save failure. */
    var failSaves = false

    override fun reviews(campsiteId: String): Flow<List<CampsiteReview>> =
        stored.map { it[campsiteId].orEmpty().sortedByDescending(CampsiteReview::postedAt) }

    override suspend fun post(campsiteId: String, reservationId: String, text: String) {
        if (failSaves) throw IOException("Offline")
        val review = CampsiteReview(myUserId, reservationId, text.trim(), Timestamp.now())
        stored.update { all ->
            val forSite = all[campsiteId].orEmpty().filterNot { it.reviewerId == myUserId } + review
            all + (campsiteId to forSite)
        }
    }

    /** Adds or changes a review as though another device posted it. */
    fun changeElsewhere(campsiteId: String, review: CampsiteReview) {
        stored.update { all ->
            all + (campsiteId to (all[campsiteId].orEmpty().filterNot {
                it.reviewerId == review.reviewerId
            } + review))
        }
    }
}
