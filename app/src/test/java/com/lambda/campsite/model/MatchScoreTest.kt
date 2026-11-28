/*
 * Purpose: unit tests for MatchScore (WOW factor #71). They check
 * that each filtered campsite gets the right match score and that the results
 * are sorted from the highest score to the lowest:
 * 1. Guests only: an exact fit scores 100%, and each extra space takes 10% off.
 * 2. Several filters: each applied filter has the same weight (50/50 with two,
 *    about 33.3% each with three), and the guests score never goes below 50%.
 * 3. No filters: there is no score, and the order stays the same.
 * 4. Same score: campsites keep the order the filters gave them, like the
 *    shortest stay first when only a check-in date is applied.
 * Outstanding issues: none.
 */
/*
* AI use: Claude Opus 5.5 (Anthropic) in Novemebr 2026, helped Harmanjoth Kaur with the syntax of the
* test cases and Kdoc. All the test cases are chosen by Harmanjoth Kaur
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScoreTest {
    private val small = Campsite("small", "owner", "Aspen Hollow", "Hinton, AB", maxOccupancy = 3)
    private val medium = Campsite("medium", "owner", "Birch Bay", "Hinton, AB", maxOccupancy = 6)
    private val large = Campsite("large", "owner", "Cedar Ridge", "Jasper, AB", maxOccupancy = 7)

    private fun ScoredResults.ids() = results.map { it.id }

    @Test fun `guests only scores an exact fit highest and sorts by score`() {
        val scored = MatchScore.scoreResults(listOf(large, medium, small),
            hasLocation = false, hasDates = false, guests = 3)
        assertEquals(mapOf("small" to 100, "medium" to 70, "large" to 60), scored.scores)
        assertEquals(listOf("small", "medium", "large"), scored.ids())
    }

    @Test fun `each applied filter has the same weight and guests never go below 50`() {
        val twoFilters = MatchScore.scoreResults(listOf(medium), hasLocation = true, hasDates = false, guests = 4)
        assertEquals(90, twoFilters.scores["medium"])
        val threeFilters = MatchScore.scoreResults(listOf(medium), hasLocation = true, hasDates = true, guests = 4)
        assertEquals(93, threeFilters.scores["medium"])
        val big = Campsite("big", "owner", "Big Meadow", "Hinton, AB", maxOccupancy = 20)
        val manySpaces = MatchScore.scoreResults(listOf(big), hasLocation = false, hasDates = false, guests = 2)
        assertEquals(50, manySpaces.scores["big"])
    }

    @Test fun `no filters means no score and the order stays the same`() {
        val scored = MatchScore.scoreResults(listOf(large, small, medium),
            hasLocation = false, hasDates = false, guests = null)
        assertTrue(scored.scores.isEmpty())
        assertEquals(listOf("large", "small", "medium"), scored.ids())
    }

    @Test fun `campsites with the same score keep the filter's order`() {
        // Only check-in is applied, so every campsite scores 100%. The filter
        // already put them shortest stay first, and that order must stay.
        val scored = MatchScore.scoreResults(listOf(small, large, medium),
            hasLocation = false, hasDates = true, guests = null)
        assertEquals(mapOf("small" to 100, "large" to 100, "medium" to 100), scored.scores)
        assertEquals(listOf("small", "large", "medium"), scored.ids())
    }
}
