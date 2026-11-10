/*
 * Purpose: unit tests for MatchScoreController (WOW factor #71). They check
 * that each filtered campsite gets the right match score and that the results
 * are sorted from the highest score to the lowest:
 * 1. Guests only: an exact fit scores 100%, and each extra space takes 10% off.
 * 2. Several filters: each applied filter has the same weight (50/50 with two,
 *    about 33.3% each with three), and the guests score never goes below 50%.
 * 3. No filters: there is no score, and the order stays the same.
 * Outstanding issues: none.
 */
/*
* AI use: Claude Opus 5.5 (Anthropic) helped Harmanjoth Kaur with the syntax of the test cases
* and Kdoc. All the test cases are chosen by Harmanjoth Kaur
 */
package com.lambda.campsite.controller
import com.lambda.campsite.model.Campsite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class MatchScoreControllerTest {
    private val small = Campsite("pr144", "owner", "PR144 Test Site", "Hinton, AB", maxOccupancy = 3)
    private val edited = Campsite("pr144-edited", "owner", "PR144 Test Site Edited", "Hinton, AB", maxOccupancy = 6)
    private val test = Campsite("test", "owner", "test", "loc", maxOccupancy = 7)

    private fun MatchScoreController.ids() = state.value.results.map { it.id }

    @Test fun `guests only scores an exact fit highest and sorts by score`() {
        val control = MatchScoreController()
        control.onFilteredResultsChanged(listOf(test, edited, small),
            hasLocation = false, hasDates = false, guests = 3)
        assertEquals(mapOf("pr144" to 100, "pr144-edited" to 70, "test" to 60), control.state.value.scores)
        assertEquals(listOf("pr144", "pr144-edited", "test"), control.ids())
    }

    @Test fun `each applied filter has the same weight and guests never go below 50`() {
        val control = MatchScoreController()
        control.onFilteredResultsChanged(listOf(edited), hasLocation = true, hasDates = false, guests = 4)
        assertEquals(90, control.state.value.scores["pr144-edited"])
        control.onFilteredResultsChanged(listOf(edited), hasLocation = true, hasDates = true, guests = 4)
        assertEquals(93, control.state.value.scores["pr144-edited"])
        val big = Campsite("big", "owner", "Big Meadow", "Hinton, AB", maxOccupancy = 20)
        control.onFilteredResultsChanged(listOf(big), hasLocation = false, hasDates = false, guests = 2)
        assertEquals(50, control.state.value.scores["big"])
    }

    @Test fun `no filters means no score and the order stays the same`() {
        val control = MatchScoreController()
        control.onFilteredResultsChanged(listOf(test, small, edited),
            hasLocation = false, hasDates = false, guests = null)
        assertTrue(control.state.value.scores.isEmpty())
        assertEquals(listOf("test", "pr144", "pr144-edited"), control.ids())
    }
}