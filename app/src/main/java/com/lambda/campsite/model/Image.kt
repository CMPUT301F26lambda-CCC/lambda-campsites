/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in Notification. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model

/**
 * A photo uploaded to the app (US 02.01.02).
 *
 * The photo file itself lives in Firebase Storage at `images/{id}.jpg`. This
 * document only records who uploaded it, so an admin removing it (03.03.01)
 * knows whose [ImageOwner] to clear it from.
 *
 * @property id the image's document ID, and the name of its file in Storage
 *   without the `.jpg` extension.
 * @property ownerId the Firebase Auth user ID of whoever uploaded it.
 */
data class Image(
    val id: String,
    val ownerId: String,
)

/**
 * A domain class that can have one [Image], like [Campsite].
 *
 * One Image class serves every kind of owner, so when an admin removes a
 * photo, the clean-up function can clear [imageId] without caring which kind
 * of owner it is.
 */
interface ImageOwner {
    val imageId: String?
}
