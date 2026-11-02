/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in NotificationFieldsTest. Aarush Bhat reviewed, ran and
 * committed them.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Image
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [ImageFields], how an image is stored in Firestore. */
class ImageFieldsTest {

    private val image = Image(id = "image-1", ownerId = "owner-1")

    @Test
    fun `an image reads back the same after it's stored`() {
        val fields = ImageFields.toMap(image)

        assertEquals(image, ImageFields.fromMap("image-1", fields))
    }

    @Test
    fun `a missing owner reads as the default`() {
        val read = ImageFields.fromMap("image-1", emptyMap())

        assertEquals(Image(id = "image-1", ownerId = ""), read)
    }
}
