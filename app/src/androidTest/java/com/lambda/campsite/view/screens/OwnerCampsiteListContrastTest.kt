/* Issue #150 review: GPT-6 (OpenAI), through Codex, assisted me with list contrast coverage. */
package com.lambda.campsite.view.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Ensures the light owner list keeps readable text even with system dark mode enabled. */
class OwnerCampsiteListContrastTest {
    @get:Rule val compose = createComposeRule()

    private fun renderedTextColor(text: String, substring: Boolean = false): Color {
        val node = compose.onNodeWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult]
        assertTrue(action.action?.invoke(layouts) == true)
        return layouts.single().layoutInput.style.color
    }

    @Test fun countNameAndLocationUseDarkColorsOnMintInDarkMode() {
        val camp = Campsite("site-1", "owner-1", "Pine Ridge", "Jasper, AB")
        compose.setContent {
            LambdaCampsiteTheme(darkTheme = true) {
                OwnerCampsiteListScreen(listOf(camp), onCampsiteClick = {}, onRegisterClick = {})
            }
        }
        assertEquals(CampSecondaryText, renderedTextColor("1 campsite"))
        assertEquals(CampOnSurface, renderedTextColor("Pine Ridge"))
        assertEquals(CampSecondaryText, renderedTextColor("Jasper, AB", substring = true))
    }
}
