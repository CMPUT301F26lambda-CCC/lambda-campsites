/*
 * Purpose: This is designed to check the FilterController
 * Each test checks one filter on its own:
 * 1. Location "loc": only the campsite whose location contains "loc" is shown.
 * 2. 7 guests: only the campsite whose max occupancy is 7 or more is shown.
 * 3. Check-in and check-out: only the complete requested stay matches.
 * 4. Only check-out: campsites free on the night before check-out are shown,
 *    and a campsite booked on that night is hidden.
 * 5. Only check-in: campsites free on the check-in night are shown, with the
 *    shortest free stay first, up to 7 nights.
 * (US 01.01.02),(US 01.01.03)
 * Outstanding issue- None
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped Harmanjoth
 * Kaur with the syntax of the test cases and Kdoc. All the test cases are chosen by
 * Harmanjoth Kaur
 */
// Follow-up #174: Lalith Valluru, 2026-11-04. OpenAI GPT-6 through Codex assisted
// with exact-night, combined-filter and listener-isolation regressions; human review pending.
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

    @Test fun `check-in and check-out excludes campsites that need an earlier checkout`() {
        val control = controller()
        // July 2 to 4 needs the nights of July 2 and 3.
        control.onApplyFilters("", july(2), july(4), null)
        // PR144 Test Site Edited and test are free both nights, so they come first.
        // PR144 Test Site has July 3 booked, so a shorter stay must not count.
        assertEquals(listOf("pr144-edited", "test"), control.ids())
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

    @Test fun `checkout night stays exclusive and a one-night blocked stay never matches`() {
        val control = controller()
        control.onApplyFilters("", july(2), july(3), null)
        assertEquals(listOf("pr144", "pr144-edited", "test"), control.ids())
        control.onApplyFilters("", july(3), july(4), null)
        assertEquals(listOf("pr144-edited", "test"), control.ids())
    }

    @Test fun `all filter conditions apply together and clearing restores searched candidates`() {
        val control = controller()
        control.onSearchResultsChanged(listOf(small, edited))
        control.onApplyFilters("Hinton", july(2), july(4), 6)
        assertEquals(listOf("pr144-edited"), control.ids())
        control.onApplyFilters("Hinton", july(2), july(4), 7)
        assertEquals(emptyList<String>(), control.ids())
        control.onClearFilters()
        assertEquals(listOf("pr144", "pr144-edited"), control.ids())
    }

    @Test fun `one failed calendar preserves matches and retry restarts only that listener`() {
        val base = repository()
        val reads = all.associate { it.id to kotlinx.coroutines.flow.MutableStateFlow(
            Result.success<com.lambda.campsite.model.Availability?>(Availability(july(1), july(31)))) }
        val calls = mutableMapOf<String, Int>()
        reads.getValue(small.id).value = Result.failure(java.io.IOException("Unavailable"))
        val repo = object : com.lambda.campsite.model.repository.CampsiteRepository by base {
            override fun availability(id: String): kotlinx.coroutines.flow.Flow<Availability?> {
                calls[id] = (calls[id] ?: 0) + 1
                return kotlinx.coroutines.flow.flow { reads.getValue(id).collect { emit(it.getOrThrow()) } }
            }
        }
        val control = CampsiteFilterController(repo) { july1 }
        control.onSearchResultsChanged(all)
        control.onApplyFilters("", july(2), july(4), null)
        assertEquals(listOf(edited.id, test.id), control.ids())
        assertEquals(1, control.state.value.failedCalendarCount)
        org.junit.Assert.assertFalse(control.state.value.loadFailed)
        reads.getValue(edited.id).value = Result.success(Availability(july(1), july(31), setOf(july(3))))
        assertEquals(listOf(test.id), control.ids())
        reads.getValue(small.id).value = Result.success(Availability(july(1), july(31)))
        control.onRetry()
        assertEquals(listOf(small.id, test.id), control.ids())
        assertEquals(0, control.state.value.failedCalendarCount)
        assertEquals(2, calls[small.id])
        assertEquals(1, calls[edited.id])
        assertEquals(1, calls[test.id])
    }

    @Test fun `a pending calendar does not hide confirmed matches and clearing cancels stale failures`() {
        val base = repository()
        val repo = object : com.lambda.campsite.model.repository.CampsiteRepository by base {
            override fun availability(id: String): kotlinx.coroutines.flow.Flow<Availability?> =
                if (id == small.id) kotlinx.coroutines.flow.flow { kotlinx.coroutines.awaitCancellation() }
                else base.availability(id)
        }
        val control = CampsiteFilterController(repo) { july1 }
        control.onSearchResultsChanged(all)
        control.onApplyFilters("", july(2), july(4), null)
        assertEquals(listOf(edited.id, test.id), control.ids())
        org.junit.Assert.assertFalse(control.state.value.isLoading)
        assertEquals(1, control.state.value.pendingCalendarCount)
        control.onClearFilters()
        assertEquals(0, control.state.value.pendingCalendarCount)
        assertEquals(0, control.state.value.failedCalendarCount)
        assertEquals(all.map { it.id }, control.ids())
    }

    @Test fun `all calendars failing is an unknown availability state and clear restores results`() {
        val base = repository()
        val repo = object : com.lambda.campsite.model.repository.CampsiteRepository by base {
            override fun availability(id: String): kotlinx.coroutines.flow.Flow<Availability?> =
                kotlinx.coroutines.flow.flow { throw java.io.IOException("Unavailable") }
        }
        val control = CampsiteFilterController(repo) { july1 }
        control.onSearchResultsChanged(all)
        control.onApplyFilters("", july(2), july(4), null)
        assertEquals(emptyList<String>(), control.ids())
        assertEquals(all.size, control.state.value.failedCalendarCount)
        org.junit.Assert.assertFalse(control.state.value.isLoading)
        control.onClearFilters()
        assertEquals(all.map { it.id }, control.ids())
        assertEquals(0, control.state.value.failedCalendarCount)
    }

    @Test fun `live bookings remove exact matches and cancellation restores them`() {
        val repo = repository()
        val control = CampsiteFilterController(repo) { july1 }
        control.onSearchResultsChanged(all)
        control.onApplyFilters("", july(2), july(4), null)
        repo.changeCalendarElsewhere(edited.id, Availability(july(1), july(31), setOf(july(3))))
        assertEquals(listOf(test.id), control.ids())
        repo.changeCalendarElsewhere(edited.id, Availability(july(1), july(31)))
        assertEquals(listOf(edited.id, test.id), control.ids())
    }

    @Test fun `invalid date ranges and unset seasons do not match or crash`() {
        val control = controller()
        control.onApplyFilters("", july(4), july(4), null)
        assertEquals(emptyList<String>(), control.ids())
        control.onApplyFilters("", july(4), july(2), null)
        assertEquals(emptyList<String>(), control.ids())
        val noSeasons = CampsiteFilterController(FakeCampsiteRepository(initial = all)) { july1 }
        noSeasons.onSearchResultsChanged(all)
        noSeasons.onApplyFilters("", july(2), july(4), null)
        assertEquals(emptyList<String>(), noSeasons.ids())
        assertEquals(0, noSeasons.state.value.failedCalendarCount)
    }
}