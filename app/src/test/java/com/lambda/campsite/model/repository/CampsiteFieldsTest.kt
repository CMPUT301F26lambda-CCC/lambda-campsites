/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 *
 * The imageId test was added by Aarush Bhat with the assistance of Claude
 * Sonnet 5 (Anthropic), through Claude Code, in November 2026. Aarush Bhat
 * reviewed, ran and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Campsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [CampsiteFields], how a campsite is stored in Firestore. */
class CampsiteFieldsTest {

    private val campsite = Campsite(
        id = "campsite-1",
        ownerId = "owner-1",
        name = "Moose Lake Campsite",
        location = "Elk Island National Park, AB",
        description = "A quiet site by the lake.",
        amenities = listOf("Fire pit", "Picnic table"),
        maxOccupancy = 6,
        latitude = 53.6,
        longitude = -112.85,
        tags = listOf("lakeside", "quiet"),
        pricePerNight = 32.5,
        rules = "No pets. Cancel up to 3 days before check-in.",
        published = true,
        imageId = "image-1",
    )

    @Test
    fun `a campsite reads back the same after it's stored`() {
        val fields = CampsiteFields.toMap(campsite)

        assertEquals(campsite, CampsiteFields.fromMap("campsite-1", fields))
    }

    @Test
    fun `the rating totals aren't stored, since only a Cloud Function writes them`() {
        val fields = CampsiteFields.toMap(campsite.copy(ratingCount = 2, ratingSum = 9))

        assertFalse(fields.containsKey(CampsiteFields.RATING_COUNT))
        assertFalse(fields.containsKey(CampsiteFields.RATING_SUM))
    }

    @Test
    fun `the rating totals are read`() {
        val read = CampsiteFields.fromMap(
            "campsite-1",
            mapOf(CampsiteFields.RATING_COUNT to 2L, CampsiteFields.RATING_SUM to 9L),
        )

        assertEquals(2, read.ratingCount)
        assertEquals(9, read.ratingSum)
    }

    @Test
    fun `missing fields read as the defaults, and the campsite as unpublished`() {
        val read = CampsiteFields.fromMap("campsite-1", emptyMap())

        assertEquals(Campsite(id = "campsite-1", ownerId = "", name = "", location = "", published = false), read)
    }

    @Test
    fun `whole numbers stored for decimal fields read back as decimals`() {
        val read = CampsiteFields.fromMap(
            "campsite-1",
            mapOf(CampsiteFields.LATITUDE to 53L, CampsiteFields.PRICE_PER_NIGHT to 30L),
        )

        assertEquals(53.0, read.latitude!!, 0.0)
        assertEquals(30.0, read.pricePerNight!!, 0.0)
    }

    @Test
    fun `a campsite with no image reads as having none`() {
        val fields = CampsiteFields.toMap(campsite.copy(imageId = null))

        assertNull(CampsiteFields.fromMap("campsite-1", fields).imageId)
    }

    @Test
    fun `list items that aren't strings are skipped`() {
        val read = CampsiteFields.fromMap("campsite-1", mapOf(CampsiteFields.TAGS to listOf("lakeside", 7L, null)))

        assertEquals(listOf("lakeside"), read.tags)
    }
}