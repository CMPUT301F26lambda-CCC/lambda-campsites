/*
 * Purpose: unit tests for Campsite.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 *
 * The validate tests were added in November 2026 with GLM 5.3 (Z.ai), used
 * through OpenCode, following docs/testing.md's example for US 02.01.02.
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [Campsite]: the average rating (US 01.01.04, 02.01.04) and the required-field rules (US 02.01.02). */
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

    @Test
    fun `a complete campsite misses nothing the owner has to give`() {
        assertTrue(campsite.validate().isEmpty())
    }

    @Test
    fun `validate rejects a missing name`() {
        val result = campsite.copy(name = "   ").validate()

        assertEquals(setOf(CampsiteRequiredField.NAME), result)
    }

    @Test
    fun `validate rejects a missing location`() {
        val result = campsite.copy(location = "").validate()

        assertEquals(setOf(CampsiteRequiredField.LOCATION), result)
    }

    @Test
    fun `validate rejects an occupancy of 0`() {
        val result = campsite.copy(maxOccupancy = 0).validate()

        assertEquals(setOf(CampsiteRequiredField.MAX_OCCUPANCY), result)
    }
}