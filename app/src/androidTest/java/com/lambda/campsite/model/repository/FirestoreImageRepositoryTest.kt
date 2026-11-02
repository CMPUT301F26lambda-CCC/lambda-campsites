/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FirestoreNotificationRepositoryTest. Aarush Bhat reviewed,
 * ran and committed them.
 */
package com.lambda.campsite.model.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.signInAsNewUser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreImageRepository] against the Firestore and Storage
 * emulators (US 02.01.02). Each test signs in as a new user.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreImageRepositoryTest {

    private val firestore = Firebase.firestore
    private val storage = Firebase.storage
    private val repository = FirestoreImageRepository(Firebase.auth, firestore, storage)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
    }

    @Test
    fun aWidePhotoShrinksToTheMaxWidth() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(1600, 800)) } }

        val uploaded = decodePhotoOnServer(id)
        assertEquals(800, uploaded.width)
        // The aspect ratio is kept: half the width means half the height too.
        assertEquals(400, uploaded.height)
    }

    @Test
    fun aPhotoNarrowerThanTheMaxWidthIsntUpscaled() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(400, 300)) } }

        val uploaded = decodePhotoOnServer(id)
        assertEquals(400, uploaded.width)
        assertEquals(300, uploaded.height)
    }

    @Test
    fun uploadRecordsTheOwnerInFirestore() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(100, 100)) } }

        val image = runBlocking {
            val snapshot = firestore.document("images/$id").get(Source.SERVER).await()
            ImageFields.fromMap(id, snapshot.data.orEmpty())
        }
        assertEquals(uid, image.ownerId)
    }

    @Test
    fun eachUploadGetsItsOwnId() {
        val first = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(100, 100)) } }
        val second = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(100, 100)) } }

        assertNotEquals(first, second)
    }

    @Test
    fun theUploadedFileIsUnderTheSizeLimit() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { repository.upload(photo(1600, 800)) } }

        val bytes = runBlocking { storage.reference.child("images/$id.jpg").getBytes(MAX_DOWNLOAD_BYTES).await() }
        assertTrue(bytes.size < STORAGE_SIZE_LIMIT_BYTES)
    }

    private fun photo(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** The uploaded photo, decoded back from Storage, skipping the phone's cache. */
    private fun decodePhotoOnServer(id: String): Bitmap = runBlocking {
        val bytes = storage.reference.child("images/$id.jpg").getBytes(MAX_DOWNLOAD_BYTES).await()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        const val STORAGE_SIZE_LIMIT_BYTES = 2 * 1024 * 1024
    }
}
