/*
 * Purpose: Flow 15 follow-up #174 empty states, partial calendar recovery and picker month.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-04.
 * Outstanding issues: manual TalkBack review remains.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with filter UI regression
 * fixtures and assertions; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFilterUiState
import com.lambda.campsite.controller.SearchUiState
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.view.components.CampsiteFilterSheet
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CampsiteFilterRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val site = Campsite("demo", "owner", "Aspen Demo", "Alberta")

    @Test fun filtersWithoutSearchShowFilterMessageAndClearFilters() {
        var clears = 0
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(SearchUiState(isLoading = false), {}, {}, {},
                filters = CampsiteFilterUiState(guests = 10), onClearFilters = { clears++ })
        } }
        compose.onNodeWithText(text(R.string.flow15_no_filter_results)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow15_clear_filters)).performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test fun clearingFiltersPreservesSubmittedSearchAndRestoresCandidates() {
        val filters = mutableStateOf(CampsiteFilterUiState(guests = 10))
        val state = mutableStateOf(SearchUiState(query = "Aspen", submittedQuery = "Aspen", isLoading = false))
        var searchClears = 0
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(state.value, {}, {}, {}, onClearSearch = { searchClears++ },
                filters = filters.value, onClearFilters = {
                    filters.value = CampsiteFilterUiState()
                    state.value = state.value.copy(results = listOf(site))
                })
        } }
        compose.onNodeWithText(text(R.string.flow15_no_filter_results)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow15_clear_filters)).performClick()
        compose.onNodeWithText(site.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("Aspen", state.value.submittedQuery)
            assertEquals("Aspen", state.value.query)
            assertEquals(0, searchClears)
        }
    }

    @Test fun failedAvailabilityKeepsConfirmedCardsAndOffersItsOwnRetry() {
        var retries = 0
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(SearchUiState(results = listOf(site), isLoading = false), {}, {}, {},
                filters = CampsiteFilterUiState(checkIn = LocalDate.now(), failedCalendarCount = 1),
                onRetryCalendars = { retries++ })
        } }
        compose.onNodeWithText(text(R.string.flow15_availability_partial)).assertIsDisplayed()
        compose.onNodeWithText(site.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow15_retry_availability)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun allCalendarFailuresDoNotClaimNoMatchesOrNoPublishedCampsites() {
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(SearchUiState(isLoading = false), {}, {}, {},
                filters = CampsiteFilterUiState(checkIn = LocalDate.now(), failedCalendarCount = 2))
        } }
        compose.onNodeWithText(text(R.string.flow15_availability_partial)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow15_retry_availability)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow15_no_filter_results)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.entrant_campsite_empty)).assertDoesNotExist()
    }

    @Test fun checkoutPickerOpensOnFutureCheckInMonth() {
        val future = LocalDate.now().plusMonths(4).withDayOfMonth(10)
        compose.setContent { LambdaCampsiteTheme {
            CampsiteFilterSheet("", future, null, null, { _, _, _, _ -> }, {}, {})
        } }
        compose.onNodeWithText(text(R.string.flow15_check_out)).performScrollTo().performClick()
        val month = future.plusDays(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.US))
        compose.onNodeWithText(month).assertIsDisplayed()
    }

    @Test fun clearAllResetsEveryDraftFilterBeforeApply() {
        var clears = 0
        var result: List<Any?>? = null
        compose.setContent { LambdaCampsiteTheme {
            CampsiteFilterSheet("Alberta", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 4,
                { location, start, end, guests -> result = listOf(location, start, end, guests) },
                { clears++ }, {})
        } }
        compose.onNodeWithText(text(R.string.flow15_clear)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow15_apply)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, clears)
            assertEquals(listOf<Any?>("", null, null, null), result)
        }
    }
}
