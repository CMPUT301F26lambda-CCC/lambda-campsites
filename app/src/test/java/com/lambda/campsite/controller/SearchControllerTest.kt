/* Purpose: Flow 6 search tests. AI use: OpenAI GPT-6 through Codex assisted; human review pending. */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteSearch
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests text matching, published-only results, live changes and retry (US 01.01.01/.04). */
class SearchControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val aspen = Campsite("aspen", "owner", "Aspen Hollow", "Alberta", description = "Quiet forest", amenities = listOf("Fire pit"), maxOccupancy = 6, ratingCount = 2, ratingSum = 9)
    private val cedar = Campsite("cedar", "owner", "Cedar Point", "British Columbia", tags = listOf("lakeside"))

    @Test fun `empty search returns published sites ordered by name`() {
        val control = SearchController(FakeCampsiteRepository(initial = listOf(cedar, aspen, aspen.copy(id = "private", published = false))))
        assertEquals(listOf(aspen, cedar), control.state.value.results)
        assertFalse(control.state.value.isLoading)
        assertEquals(6, control.state.value.results.first().maxOccupancy)
        assertEquals(4.5, control.state.value.results.first().averageRating)
    }

    @Test fun `search is case insensitive trimmed and matches multiple fields`() {
        val control = SearchController(FakeCampsiteRepository(initial = listOf(aspen, cedar)))
        control.onSearch("  ASPEN   alberta ")
        assertEquals(listOf(aspen), control.state.value.results)
        control.onSearch("lakeside")
        assertEquals(listOf(cedar), control.state.value.results)
        assertTrue(CampsiteSearch.matches(aspen, "forest fire"))
        assertFalse(CampsiteSearch.matches(aspen, "forest beach"))
    }

    @Test fun `no matches and clearing query restore full catalogue`() {
        val control = SearchController(FakeCampsiteRepository(initial = listOf(aspen)))
        control.onSearch("unknown")
        assertTrue(control.state.value.results.isEmpty())
        control.onSearch("")
        assertEquals(listOf(aspen), control.state.value.results)
    }

    @Test fun `live edits respect current query and unpublished records disappear`() {
        val repository = FakeCampsiteRepository(initial = listOf(aspen, cedar))
        val control = SearchController(repository)
        control.onSearch("aspen")
        repository.changeElsewhere(cedar.copy(name = "Aspen Lake"))
        assertEquals(2, control.state.value.results.size)
        repository.changeElsewhere(aspen.copy(published = false))
        assertEquals(listOf("cedar"), control.state.value.results.map { it.id })
    }

    @Test fun `load errors are retryable and preserve query`() {
        val base = FakeCampsiteRepository(initial = listOf(aspen, cedar))
        var fail = true
        val repository = object : CampsiteRepository by base {
            override fun publishedCampsites(): Flow<List<Campsite>> = flow {
                if (fail) throw IOException("Offline")
                base.publishedCampsites().collect { emit(it) }
            }
        }
        val control = SearchController(repository)
        assertTrue(control.state.value.loadFailed)
        assertFalse(control.state.value.isLoading)
        control.onSearch("cedar")
        fail = false
        control.onRetry()
        assertEquals("cedar", control.state.value.query)
        assertEquals(listOf(cedar), control.state.value.results)
        assertFalse(control.state.value.loadFailed)
    }
}
