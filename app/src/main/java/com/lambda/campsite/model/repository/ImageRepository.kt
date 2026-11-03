/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in NotificationRepository. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import android.graphics.Bitmap
import com.lambda.campsite.model.ImageOwner

/**
 * Uploads campsite photos.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [FirestoreImageRepository], and unit tests use
 * `FakeImageRepository`.
 */
interface ImageRepository {

    /**
     * Shrinks [photo] to about 800 pixels wide, uploads it to Storage at
     * `images/{id}.jpg`, creates its Firestore document, and returns the new
     * image's ID (US 02.01.02). Save it as an [ImageOwner]'s `imageId`.
     *
     * @throws Exception if the upload fails, for example when the phone is offline.
     */
    suspend fun upload(photo: Bitmap): String
}
