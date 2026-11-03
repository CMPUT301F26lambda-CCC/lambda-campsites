/*
 * Purpose: large-text Figma layout checks for entrant search and campsite reading (#154).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack and physical-device review remain pending.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with test fixtures and assertions;
 * human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Verifies wrapping and reachable actions with long real-shaped values at 200% font size. */
class EntrantLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val site = Campsite("layout-demo", "demo-owner", "Whispering Pines Demonstration Campground",
        "A long fictional location in the Alberta foothills", maxOccupancy = 6, description = "Fictional demo data for layout review.",
        tags = listOf("Forest", "Creekside", "Family friendly"), pricePerNight = 35.0,
        rules = "Respect quiet hours.\nLeave no trace.", ratingCount = 1, ratingSum = 4)
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "flow67-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun longResultCardKeepsDetailsReachableAtLargeText() {
        var selected: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LambdaCampsiteTheme { SearchContent(SearchUiState(results = listOf(site), isLoading = false), {}, {},
                    { selected = it }, profileName = "Demo user") }
            }
        }
        compose.onNodeWithText(site.name, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_view_details)).performScrollTo().assertIsDisplayed()
        screenshot("search-large-text")
        compose.onNodeWithText(text(R.string.entrant_campsite_view_details)).performClick()
        compose.runOnIdle { assertEquals(site.id, selected) }
    }

    @Test fun campsiteSectionsAndMapRemainScrollableAtLargeText() {
        var backs = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                LambdaCampsiteTheme { CampsiteContent(CampsiteUiState(site,
                    Availability(LocalDate.of(2027,5,1), LocalDate.of(2027,9,30)), isLoading = false), {}, { backs++ }) {} }
            }
        }
        compose.onNodeWithText(site.description).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.campsite_price_rules_heading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.campsite_map_placeholder_description)).performScrollTo().assertIsDisplayed()
        screenshot("campsite-large-text")
        compose.onNodeWithText(text(R.string.campsite_ratings_heading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}
