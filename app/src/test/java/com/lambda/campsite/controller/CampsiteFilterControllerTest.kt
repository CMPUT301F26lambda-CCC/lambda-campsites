/*
 * Purpose: This is designed to check the FilterController
 * Each test checks one filter on its own:
 * 1. Location "loc": only the campsite whose location contains "loc" is shown.
 * 2. 7 guests: only the campsite whose max occupancy is 7 or more is shown.
 * 3. Check-in and check-out: campsites free for the exact stay come first,
 *    then the campsite that is only free if the user leaves one day earlier.
 * 4. Only check-out: campsites free on the night before check-out are shown,
 *    and a campsite booked on that night is hidden.
 * 5. Only check-in: campsites free on the check-in night are shown, with the
 *    shortest free stay first, up to 7 nights.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped Harmanjoth
 * Kaur with the syntax of the test cases and Kdoc. All the test cases are chosen by
 * Harmanjoth Kaur
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tests each filter on its own: location, guests, both dates, only check-out and only check-in. */
class CampsiteFilterControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    private val july1: LocalDate = LocalDate.of(2027, 7, 1)
    private fun july(day: Int): LocalDate = july1.withDayOfMonth(day)

    private val small = Campsite("pr144", "owner", "PR144 Test Site", "Hinton, AB", maxOccupancy = 3)
    private val edited = Campsite("pr144-edited", "owner", "PR144 Test Site Edited", "Hinton, AB", maxOccupancy = 6)
    private val test = Campsite("test", "owner", "test", "loc", maxOccupancy = 7)
    private val all = listOf(small, edited, test)

    /**
     * Every campsite is open in July 2027:
     * - PR144 Test Site: all of July, but the night of July 3 is booked.
     * - PR144 Test Site Edited: all of July, nothing booked.
     * - test: July 1 to 5, so the last night guests can stay is July 4.
     */
    private fun repository() = FakeCampsiteRepository(
        initial = all,
        initialCalendars = mapOf(
            "pr144" to Availability(july(1), july(31), bookedNights = setOf(july(3))),
            "pr144-edited" to Availability(july(1), july(31)),
            "test" to Availability(july(1), july(5)),
        ),
    )

    /** A controller on July 1 that has already received every campsite as the search results. */
    private fun controller() =
        CampsiteFilterController(repository()) { july1 }.also { it.onSearchResultsChanged(all) }

    private fun CampsiteFilterController.ids() = state.value.results.map { it.id }

    @Test fun `location loc shows only the campsite in loc`() {
        val control = controller()
        control.onApplyFilters("loc", null, null, null)
        // "loc" isn't part of "Hinton, AB", so both PR144 sites are hidden.
        assertEquals(listOf("test"), control.ids())
    }

    @Test fun `seven guests shows only the campsite for seven`() {
        val control = controller()
        control.onApplyFilters("", null, null, 7)
        // PR144 Test Site holds 3 and PR144 Test Site Edited holds 6, so only test fits 7.
        assertEquals(listOf("test"), control.ids())
    }

    @Test fun `check-in and check-out shows exact matches first then leaving a day early`() {
        val control = controller()
        // July 2 to 4 needs the nights of July 2 and 3.
        control.onApplyFilters("", july(2), july(4), null)
        // PR144 Test Site Edited and test are free both nights, so they come first.
        // PR144 Test Site has July 3 booked, but July 2 to 3 is free, so it comes last.
        assertEquals(listOf("pr144-edited", "test", "pr144"), control.ids())
    }

    @Test fun `only check-out shows campsites free the night before`() {
        val control = controller()
        // Checking out July 4 needs at least the night of July 3.
        control.onApplyFilters("", null, july(4), null)
        // PR144 Test Site has July 3 booked, so it's hidden. The other two are free
        // July 1 to 3, the same 3 nights, so they keep name order.
        assertEquals(listOf("pr144-edited", "test"), control.ids())
    }

    @Test fun `only check-in shows the shortest free stays first`() {
        val control = controller()
        // From July 1: PR144 Test Site is free 2 nights (July 3 is booked),
        // test is free 4 nights (its season ends July 5),
        // and PR144 Test Site Edited is free the full 7 nights.
        control.onApplyFilters("", july(1), null, null)
        assertEquals(listOf("pr144", "test", "pr144-edited"), control.ids())
    }
}