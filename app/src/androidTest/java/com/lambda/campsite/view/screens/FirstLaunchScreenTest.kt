/*
 * AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat app,
 * helped write these tests in November 2026. It helped with the test cases,
 * the Compose UI testing syntax and finding why the tests would not run as
 * instrumented tests. Harmanjoth Kaur reviewed them and committed them.
 */

package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo

class FirstLaunchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var receivedName: String? = null

    private fun launchScreen() {
        receivedName = null
        composeTestRule.setContent {
            FirstLaunchScreen(onGetStarted = { receivedName = it })
        }
    }

    private fun typeName(name: String) {
        composeTestRule.onNodeWithTag("name_field").performTextInput(name)
    }

    private fun tapCheckbox() {
        composeTestRule.onNodeWithTag("policy_checkbox").performScrollTo().performClick()
    }

    private fun openPolicySheet() {
        composeTestRule.onNodeWithTag("policy_link").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("agree_policy_button")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun getStartedIsDisabledAtFirst() {
        launchScreen()

        composeTestRule.onNodeWithTag("get_started_button").assertIsNotEnabled()
    }

    @Test
    fun getStartedStaysDisabledWithoutAgreeing() {
        launchScreen()
        typeName("Harman")

        composeTestRule.onNodeWithTag("get_started_button").assertIsNotEnabled()
    }

    @Test
    fun getStartedStaysDisabledWithBlankName() {
        launchScreen()
        typeName("   ")
        tapCheckbox()

        composeTestRule.onNodeWithTag("get_started_button").assertIsNotEnabled()
    }

    @Test
    fun checkingTheBoxEnablesGetStarted() {
        launchScreen()
        typeName("Harman")
        tapCheckbox()

        composeTestRule.onNodeWithTag("get_started_button").assertIsEnabled()
    }

    @Test
    fun policyLinkOpensTheSheet() {
        launchScreen()
        typeName("Harman")

        openPolicySheet()

        composeTestRule.onNodeWithTag("agree_policy_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_policy_button").assertIsDisplayed()
    }

    @Test
    fun agreeInSheetChecksTheBox() {
        launchScreen()
        typeName("Harman")
        openPolicySheet()

        composeTestRule.onNodeWithTag("agree_policy_button").performClick()

        composeTestRule.onNodeWithTag("policy_checkbox").assertIsOn()
    }

    @Test
    fun closeInSheetDoesNotAgree() {
        launchScreen()
        typeName("Harman")
        openPolicySheet()

        composeTestRule.onNodeWithTag("close_policy_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("agree_policy_button")
                .fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithTag("policy_checkbox").assertIsOff()
    }

    @Test
    fun getStartedPassesTheTrimmedName() {
        launchScreen()
        typeName("  Harman  ")
        tapCheckbox()
        assertNull(receivedName)

        composeTestRule.onNodeWithTag("get_started_button").performScrollTo().performClick()

        assertEquals("Harman", receivedName)
    }
}