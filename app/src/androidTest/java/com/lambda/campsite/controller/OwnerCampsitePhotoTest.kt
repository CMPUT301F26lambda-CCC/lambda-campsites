/*
 * Purpose: Checks photo selection, upload, replacement, and removal in the owner campsite flow.
 * Outstanding issues: None known.
 * AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with photo lifecycle tests.
 * Human review is pending.
 */
package com.lambda.campsite.controller

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.ImageRepository
import com.lambda.campsite.testutil.ScreenFakeProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises selected, replaced and removed photos through the real owner controllers. */
class OwnerCampsitePhotoTest {
    private class RecordingImages : ImageRepository {
        var uploads = 0
        override suspend fun upload(photo: Bitmap): String = "photo-${++uploads}"
    }

    @Test fun registerUploadsSelectedPhotoAndLinksItsId() {
        val campsites = FakeCampsiteRepository()
        val images = RecordingImages()
        val saved = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val controller = OwnerCampsiteAddController(campsites, ScreenFakeProfile(), images)
            controller.onNameChange("Pine")
            controller.onLocationChange("Jasper")
            controller.onPhotoSelected(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
            controller.onSave { saved.countDown() }
        }
        assertTrue(saved.await(5, TimeUnit.SECONDS))
        assertEquals("photo-1", campsites.saved.single().imageId)
        assertEquals(1, images.uploads)
    }

    @Test fun editReplacesThenUnlinksPhotoWithoutChangingCampsiteId() {
        val original = Campsite("site-1", "owner-1", "Pine", "Jasper", imageId = "old-photo")
        val campsites = FakeCampsiteRepository(initial = listOf(original))
        val images = RecordingImages()
        val replaced = CountDownLatch(1)
        val removed = CountDownLatch(1)
        lateinit var controller: OwnerCampsiteEditController
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller = OwnerCampsiteEditController(original.id, campsites, images)
            controller.onPhotoSelected(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
            controller.onSave { replaced.countDown() }
        }
        assertTrue(replaced.await(5, TimeUnit.SECONDS))
        assertEquals("site-1", campsites.saved.last().id)
        assertEquals("photo-1", campsites.saved.last().imageId)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            controller.onPhotoRemoved()
            controller.onSave { removed.countDown() }
        }
        assertTrue(removed.await(5, TimeUnit.SECONDS))
        assertNull(campsites.saved.last().imageId)
    }
}
