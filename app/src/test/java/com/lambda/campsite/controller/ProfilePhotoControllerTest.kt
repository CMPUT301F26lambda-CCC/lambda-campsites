/*
 * Purpose: checks ProfilePhotoController (US 01.08.02, Flow 18).
 * 1. Picking a photo uploads it and saves it as the profile photo.
 * 2. A failed upload shows an error and keeps the old photo.
 * 3. Removing the photo saves the profile with no photo, so the initials
 *    show again.
 *
 * Outstanding issues: none
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code in November
 * 2026, helped Harmanjoth Kaur with the syntax of the test cases and the KDoc.
 * Harmanjoth Kaur chose the test cases, reviewed and committed them.
 */
package com.lambda.campsite.controller

import android.graphics.Bitmap
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.repository.FakeImageRepository
import com.lambda.campsite.model.repository.FakeProfileRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests adding and replacing the profile photo (US 01.08.02). */
class ProfilePhotoControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val entrant = Profile(deviceId = "device-1", name = "Mike Ross", email = "mike@example.com")

    // Android's Bitmap can't be created in a plain JVM test (its methods throw
    // "Stub!"), so this makes an empty one through Java's Unsafe, found by name
    // because Android's library hides it. The fake upload never reads it.
    private fun photo(): Bitmap {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, Bitmap::class.java) as Bitmap
    }

    @Test fun `picking a photo uploads it and saves it as the profile photo`() {
        val profiles = FakeProfileRepository(entrant)
        val images = FakeImageRepository()
        val control = ProfilePhotoController(profiles, images)

        control.onPhotoPicked(photo())

        assertEquals(1, images.uploaded.size)
        assertEquals("image-1", profiles.saved.last().imageId)
        assertTrue(control.state.value.hasPhoto)
        assertFalse(control.state.value.saving)
        assertFalse(control.state.value.failed)
    }

    @Test fun `a failed upload shows an error and keeps the old photo`() {
        val profiles = FakeProfileRepository(entrant.copy(imageId = "image-old"))
        val images = FakeImageRepository().apply { failUploads = true }
        val control = ProfilePhotoController(profiles, images)

        control.onPhotoPicked(photo())

        assertTrue(control.state.value.failed)
        assertFalse(control.state.value.saving)
        // Nothing was saved, so the profile still has its old photo.
        assertTrue(profiles.saved.isEmpty())
        assertTrue(control.state.value.hasPhoto)
    }
    @Test fun `removing the photo saves the profile with no photo`() {
        val profiles = FakeProfileRepository(entrant.copy(imageId = "image-old"))
        val images = FakeImageRepository()
        val control = ProfilePhotoController(profiles, images)

        control.onRemovePhoto()

        assertEquals(null, profiles.saved.last().imageId)
        assertFalse(control.state.value.hasPhoto)
        assertEquals(null, control.state.value.photoUrl)
        assertFalse(control.state.value.saving)
        assertFalse(control.state.value.failed)
        // Removing doesn't upload anything.
        assertTrue(images.uploaded.isEmpty())
    }
}