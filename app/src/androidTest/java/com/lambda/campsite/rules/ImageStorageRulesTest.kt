/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * Firestore rules tests in NotificationRulesTest. Aarush Bhat reviewed
 * and committed it.
 */
package com.lambda.campsite.rules

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.storageErrorFrom
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the Storage security rules for `images/{imageId}` (US 02.01.02).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ImageStorageRulesTest {

    private val images = Firebase.storage.reference.child("images")

    @Before
    fun setUp() {
        runBlocking { awaitSignIn() }
    }

    @Test
    fun signedInUserCanUploadAJpegUnderTheSizeLimit() {
        val error = storageErrorFrom { images.child("underLimit.jpg").putBytes(jpeg(), jpegMetadata()).await() }

        assertNull(error)
    }

    @Test
    fun signedInUserCanReadAnUploadedImage() {
        runBlocking { images.child("toRead.jpg").putBytes(jpeg(), jpegMetadata()).await() }

        val error = storageErrorFrom { images.child("toRead.jpg").getBytes(MAX_DOWNLOAD_BYTES).await() }

        assertNull(error)
    }

    @Test
    fun userCantUploadAFileThatIsntAJpeg() {
        val notAJpeg = StorageMetadata.Builder().setContentType("image/png").build()

        val error = storageErrorFrom { images.child("wrongType.jpg").putBytes(jpeg(), notAJpeg).await() }

        assertNotAuthorized(error)
    }

    @Test
    fun userCantUploadAFileOverTheSizeLimit() {
        val tooBig = ByteArray(3 * 1024 * 1024)

        val error = storageErrorFrom { images.child("tooBig.jpg").putBytes(tooBig, jpegMetadata()).await() }

        assertNotAuthorized(error)
    }

    private fun assertNotAuthorized(error: StorageException?) {
        assertEquals(StorageException.ERROR_NOT_AUTHORIZED, error?.errorCode)
    }

    private fun jpegMetadata(): StorageMetadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

    /** A tiny, real JPEG, small enough to stay well under the size limit. */
    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            stream.toByteArray()
        }
    }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 1024L * 1024L
    }
}
