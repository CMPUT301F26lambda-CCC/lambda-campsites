/*
 * Purpose: A written review posted by an entrant after a completed stay.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Flow 17 review model. Human review is pending.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp

/**
 * One entrant's written review for a campsite (US 01.13.02).
 * The reviewer ID is the review document ID, so reposting replaces the old text.
 *
 * @property reviewerId the entrant's Firebase Auth user ID.
 * @property reservationId a completed stay at the campsite that permits this review.
 * @property text the entrant's review, between 1 and 1000 characters.
 * @property postedAt the server time of the latest post.
 */
data class CampsiteReview(
    val reviewerId: String,
    val reservationId: String,
    val text: String,
    val postedAt: Timestamp,
)
