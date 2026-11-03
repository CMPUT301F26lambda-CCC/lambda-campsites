/*
 * Purpose: Flow 6 search summary, details and error presentation tests.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose assertions; human review pending.
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
        compose.onNodeWithText(text(R.string.flow6_max_guests, 6), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow6_rating, 4.5, 2), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Alberta", substring = true).assertIsDisplayed()
        compose.onNodeWithText(site.name, substring = true).performClick()
        compose.runOnIdle { assertEquals(site.id, selected) }
    }

    @Test fun emptySearchAndLoadFailureAreDifferentAndRetryIsActionable() {
        val state = mutableStateOf(SearchUiState(isLoading = false))
        var retries = 0
        compose.setContent { LambdaCampsiteTheme { SearchContent(state.value, {}, { retries++ }, {}) } }
        compose.onNodeWithText(text(R.string.flow6_empty)).assertIsDisplayed()
        compose.runOnIdle { state.value = SearchUiState(query = "unknown", isLoading = false) }
        compose.onNodeWithText(text(R.string.flow6_no_matches)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loadFailed = true) }
        compose.onNodeWithText(text(R.string.flow6_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun detailsDisplaySelectedCampsiteAndBackIsActionable() {
        var backs = 0
        compose.setContent { LambdaCampsiteTheme {
            CampsiteContent(CampsiteUiState(campsite = site, isLoading = false), {}, { backs++ }) {}
        } }
        compose.onNodeWithText(site.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(site.description).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.flow6_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}
