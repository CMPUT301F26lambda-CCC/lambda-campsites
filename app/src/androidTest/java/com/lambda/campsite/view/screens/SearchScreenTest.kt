/*
 * Purpose: Flow 6 search summary, details and error presentation tests.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose assertions; human review pending.
  * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: manual TalkBack verification remains pending.
*/
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteUiState
import com.lambda.campsite.controller.SearchUiState
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tests Flow 6's visible summary fields, selected IDs, details and retry controls. */
class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val site = Campsite("aspen-demo", "owner-demo", "Aspen demo", "Alberta", maxOccupancy = 6,
        ratingCount = 2, ratingSum = 9, description = "Demo details")
    private fun text(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun summariesShowRequiredFieldsAndOpenSelectedId() {
        var selected: String? = null
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(SearchUiState(results = listOf(site), isLoading = false), {}, {}, { selected = it })
        } }
        compose.onNodeWithText(text(R.string.entrant_campsite_max_guests, 6), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_rating, 4.5, 2), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Alberta", substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_view_details)).performClick()
        compose.runOnIdle { assertEquals(site.id, selected) }
    }

    @Test fun searchButtonSubmitsDraftAndClearResultsUsesItsOwnAction() {
        val state = mutableStateOf(SearchUiState(isLoading = false))
        var submissions = 0
        var clears = 0
        compose.setContent { LambdaCampsiteTheme {
            SearchContent(state.value, { state.value = state.value.copy(query = it) }, {}, {},
                onSubmitSearch = {
                    submissions++
                    state.value = state.value.copy(submittedQuery = state.value.query)
                }, onClearSearch = {
                    clears++
                    state.value = state.value.copy(query = "", submittedQuery = "")
                })
        } }
        compose.onNodeWithText(text(R.string.entrant_campsite_find_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_search_label)).performTextInput("unmatched")
        compose.runOnIdle { assertEquals(0, submissions); assertEquals("", state.value.submittedQuery) }
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_search_action)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_no_matches)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_clear_results)).performClick()
        compose.runOnIdle { assertEquals(1, submissions); assertEquals(1, clears); assertEquals("", state.value.query) }
        compose.onNodeWithText(text(R.string.entrant_campsite_empty)).assertIsDisplayed()
    }

    @Test fun emptySearchAndLoadFailureAreDifferentAndRetryIsActionable() {
        val state = mutableStateOf(SearchUiState(isLoading = false))
        var retries = 0
        compose.setContent { LambdaCampsiteTheme { SearchContent(state.value, {}, { retries++ }, {}) } }
        compose.onNodeWithText(text(R.string.entrant_campsite_empty)).assertIsDisplayed()
        compose.runOnIdle { state.value = SearchUiState(query = "unknown", submittedQuery = "unknown", isLoading = false) }
        compose.onNodeWithText(text(R.string.entrant_campsite_no_matches)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loadFailed = true) }
        compose.onNodeWithText(text(R.string.entrant_campsite_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun detailsDisplaySelectedCampsiteAndBackIsActionable() {
        var backs = 0
        compose.setContent { LambdaCampsiteTheme {
            CampsiteContent(CampsiteUiState(campsite = site, isLoading = false), {}, { backs++ }) {}
        } }
        compose.onNodeWithText(site.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(site.description).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}
