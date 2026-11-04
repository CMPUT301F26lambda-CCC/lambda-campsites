/*
 * Purpose: entrant details and access-loss tests (US 01.01.05).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: none known in these controller scenarios.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with test cases; human review pending.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests published details, season updates, inaccessible records and retry (US 01.01.05). */
class CampsiteControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val site = Campsite("site", "owner", "Aspen Hollow", "Alberta")
    private val season = Availability(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 9, 1))

    @Test fun `published details and calendar update live`() {
        val repository = FakeCampsiteRepository(initial = listOf(site), initialCalendars = mapOf(site.id to season))
        val control = CampsiteController(site.id, repository)
        assertEquals(site, control.state.value.campsite)
        assertEquals(season, control.state.value.availability)
        repository.changeElsewhere(site.copy(name = "Updated Aspen"))
        assertEquals("Updated Aspen", control.state.value.campsite?.name)
        repository.changeCalendarElsewhere(site.id, season.copy(bookedNights = setOf(season.seasonStart)))
        assertEquals(setOf(season.seasonStart), control.state.value.availability?.bookedNights)
    }

    @Test fun `missing or unpublished campsite is unavailable`() {
        listOf(emptyList(), listOf(site.copy(published = false))).forEach { sites ->
            val control = CampsiteController(site.id, FakeCampsiteRepository(initial = sites))
            assertEquals(CampsiteDetailsError.NOT_AVAILABLE, control.state.value.error)
            assertFalse(control.state.value.isLoading)
            assertNull(control.state.value.campsite)
        }
    }

    @Test fun `unpublishing an open campsite removes its details`() {
        val repository = FakeCampsiteRepository(initial = listOf(site))
        val control = CampsiteController(site.id, repository)
        repository.changeElsewhere(site.copy(published = false))
        assertNull(control.state.value.campsite)
        assertEquals(CampsiteDetailsError.NOT_AVAILABLE, control.state.value.error)
    }

    @Test fun `details without a season still load`() {
        val control = CampsiteController(site.id, FakeCampsiteRepository(initial = listOf(site)))
        assertEquals(site, control.state.value.campsite)
        assertNull(control.state.value.availability)
        assertNull(control.state.value.error)
    }

    @Test fun `calendar failure shows a retryable error and retry recovers`() {
        val base = FakeCampsiteRepository(initial = listOf(site))
        var fail = true
        val repository = object : CampsiteRepository by base {
            override fun availability(campsiteId: String): Flow<Availability?> = flow {
                if (fail) throw IOException("Offline")
                emit(season)
            }
        }
        val control = CampsiteController(site.id, repository)
        assertEquals(CampsiteDetailsError.LOAD_FAILED, control.state.value.error)
        fail = false
        control.onRetry()
        assertEquals(site, control.state.value.campsite)
        assertEquals(season, control.state.value.availability)
        assertNull(control.state.value.error)
    }

}
