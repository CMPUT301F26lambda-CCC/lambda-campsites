/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [Campsite]: the average rating (US 01.01.04, 02.01.04). */
class CampsiteTest {

    private val campsite = Campsite(
        id = "campsite-1",
        ownerId = "owner-1",
        name = "Moose Lake Campsite",
        location = "Elk Island National Park, AB",
    )

    @Test
    fun `a campsite nobody has rated has no average rating`() {
        assertNull(campsite.averageRating)
    }

    @Test
    fun `the average rating is the star total divided by the number of ratings`() {
        val rated = campsite.copy(ratingCount = 4, ratingSum = 15)

        assertEquals(3.75, rated.averageRating!!, 0.0)
    }
}