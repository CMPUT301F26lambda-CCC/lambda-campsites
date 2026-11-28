/*
 * Purpose: Converts campsite reviews to and from Firestore fields.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Flow 17 review field mapping. Human review is pending.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.CampsiteReview

/** Firestore fields for `campsites/{id}/reviews/{reviewerId}` (US 01.13.02). */
object CampsiteReviewFields {
    const val RESERVATION_ID = "reservationId"
    const val TEXT = "text"
    const val POSTED_AT = "postedAt"

    /** The server fills in the posting time for a new or replaced review. */
    fun forPost(reservationId: String, text: String): Map<String, Any> = mapOf(
        RESERVATION_ID to reservationId,
        TEXT to text,
        POSTED_AT to FieldValue.serverTimestamp(),
    )

    /** Reads a valid review or returns `null` for an incomplete document. */
    fun fromMap(reviewerId: String, fields: Map<String, Any?>): CampsiteReview? {
        val reservationId = fields[RESERVATION_ID] as? String ?: return null
        val text = fields[TEXT] as? String ?: return null
        val postedAt = fields[POSTED_AT] as? Timestamp ?: return null
        if (reservationId.isBlank() || text.isBlank()) return null
        return CampsiteReview(reviewerId, reservationId, text, postedAt)
    }
}
