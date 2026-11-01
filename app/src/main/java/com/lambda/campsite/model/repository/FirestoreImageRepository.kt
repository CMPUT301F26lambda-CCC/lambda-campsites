/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FirestoreNotificationRepository. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model.repository

import android.graphics.Bitmap
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.lambda.campsite.model.Image
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Uploads campsite photos to Firebase Storage, and records each one at
 * `images/{id}` in Firestore. [ImageFields] decides how the record is
 * stored.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 * @param storage where photo files are kept.
 */
class FirestoreImageRepository(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore,
    storage: FirebaseStorage,
) : ImageRepository {

    private val images = firestore.collection(COLLECTION)
    private val photos = storage.reference.child(COLLECTION)

    /**
     * Returns once the server accepts both the photo and its document.
     * Offline, the upload fails right away instead of queuing, since a
     * Storage upload isn't kept for later the way a Firestore write is.
     */
    override suspend fun upload(photo: Bitmap): String {
        val uid = auth.signedInUserId().first()
        val id = images.document().id
        val bytes = photo.shrunkToWidth(MAX_WIDTH).toJpegBytes()
        // storage.rules checks the content type, so it has to be set here:
        // putBytes with no metadata leaves it unset.
        photos.child("$id.jpg").putBytes(bytes, JPEG_METADATA).await()
        images.document(id).set(ImageFields.toMap(Image(id = id, ownerId = uid))).await()
        return id
    }

    private fun Bitmap.shrunkToWidth(maxWidth: Int): Bitmap {
        if (width <= maxWidth) return this
        val scaledHeight = height * maxWidth / width
        return Bitmap.createScaledBitmap(this, maxWidth, scaledHeight, true)
    }

    private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        stream.toByteArray()
    }

    private companion object {
        const val COLLECTION = "images"
        const val MAX_WIDTH = 800
        const val JPEG_QUALITY = 80
        val JPEG_METADATA: StorageMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
    }
}
