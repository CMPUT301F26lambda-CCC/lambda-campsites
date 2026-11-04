/*
* Purpose: UI tests for SettingScreen US 01.08.01,US 01.10.01,US 01.11.01
* . They check the Settings flow
* 1.turning notifications off and on,
* 2.opening and closing the app policy
* 3.cancelling the delete-profile dialog,
* 4.confirming delete, which has to end on the first-launch screen.
* 5. fullSettingsFlow: all of the above in one run, in the order a user would
*   do them.
*
* Outstanding issues:
* - These tests only cover the screens. They don't check that the profile is
*   really deleted as Only UI is done for Delete Profile
*
* AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat app,
* helped with the test cases, the Kotlin syntax and reviewing Kdoc on November 2026.
* Harmanjoth Kaur reviewed and ran them.
*/
package com.lambda.campsite.view.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [SettingScreen] covering notification preferences, app policy
 * access and profile deletion (US 01.08.01, US 01.10.01 and US 01.11.01).
 *
 * The tests verify that users can:
 * - Turn notifications off and on.
 * - Open and close the app policy.
 * - Cancel profile deletion and remain on the Settings screen.
 * - Confirm profile deletion and navigate to the FirstLaunch screen.
 * - Complete the entire Settings flow in one test.
 *
 * Also verifies that the notification callback receives the correct values
 * and the deletion callback is only invoked when deletion is confirmed.
 */
class SettingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun str(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    /** Settings and first-launch screens wired together, with no Firebase. */
    @Composable
    private fun SettingsFlowHost(
        notificationsOn: Boolean = true,
        onNotificationsChange: (Boolean) -> Unit = {},
        onDeleteCalled: () -> Unit = {},
    ) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "settings") {
            composable("settings") {
                SettingScreen(
                    name = "Test",
                    notificationsOn = notificationsOn,
                    onNotificationsChange = onNotificationsChange,
                    onBack = {},
                    onDeleteProfile = {
                        onDeleteCalled()
                        navController.navigate("first_launch") {
                            popUpTo("settings") { inclusive = true }
                        }
                    },
                )
            }
            composable("first_launch") {
                FirstLaunchScreen(onGetStarted = {})
            }
        }
    }
    @Test
    fun turnNotificationsOffAndOn() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            SettingsFlowHost(notificationsOn = true, onNotificationsChange = { changes.add(it) })
        }

        composeRule.onNode(isToggleable()).assertIsOn()

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNode(isToggleable()).assertIsOff()

        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNode(isToggleable()).assertIsOn()

        assertEquals(listOf(false, true), changes)
    }

    @Test
    fun openAndClosePolicyReturnsToSettings() {
        composeRule.setContent { SettingsFlowHost() }

        composeRule.onNodeWithText(str(R.string.settings_policy)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("close_policy_button").assertIsDisplayed()
        composeRule.onNodeWithTag("agree_policy_button").assertDoesNotExist()

        composeRule.onNodeWithTag("close_policy_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("close_policy_button").assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.profile_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).assertIsDisplayed()
    }

    @Test
    fun cancelOnDeleteDialogStaysOnSettings() {
        var deleteCalled = false
        composeRule.setContent { SettingsFlowHost(onDeleteCalled = { deleteCalled = true }) }

        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).performClick()
        composeRule.onNodeWithText(str(R.string.delete_dialog_title)).assertIsDisplayed()

        composeRule.onNodeWithText(str(R.string.cancel)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(str(R.string.delete_dialog_title)).assertDoesNotExist()
        composeRule.onNodeWithText(str(R.string.profile_settings)).assertIsDisplayed()
        composeRule.onNodeWithTag("get_started_button").assertDoesNotExist()
        assertFalse(deleteCalled)
    }

    @Test
    fun confirmDeleteGoesToFirstLaunchScreen() {
        var deleteCalled = false
        composeRule.setContent { SettingsFlowHost(onDeleteCalled = { deleteCalled = true }) }

        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).performClick()
        composeRule.onNodeWithText(str(R.string.delete)).performClick()
        composeRule.waitForIdle()

        assertTrue(deleteCalled)
        composeRule.onNodeWithTag("name_field").assertIsDisplayed()
        composeRule.onNodeWithTag("get_started_button").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).assertDoesNotExist()
    }

    /** The whole sequence in one run: toggle, policy, cancel delete, confirm delete. */
    @Test
    fun fullSettingsFlow() {
        var deleteCalled = false
        composeRule.setContent {
            SettingsFlowHost(notificationsOn = true, onDeleteCalled = { deleteCalled = true })
        }

        // Notifications off, then on
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).performClick()
        composeRule.onNode(isToggleable()).assertIsOn()

        // Open policy, close it, back on Settings
        composeRule.onNodeWithText(str(R.string.settings_policy)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("close_policy_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.profile_settings)).assertIsDisplayed()

        // Delete, then Cancel: still on Settings, nothing deleted
        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).performClick()
        composeRule.onNodeWithText(str(R.string.cancel)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(str(R.string.profile_settings)).assertIsDisplayed()
        assertFalse(deleteCalled)

        // Delete again, then confirm: first-launch screen
        composeRule.onNodeWithText(str(R.string.settings_delete_profile)).performClick()
        composeRule.onNodeWithText(str(R.string.delete)).performClick()
        composeRule.waitForIdle()
        assertTrue(deleteCalled)
        composeRule.onNodeWithTag("get_started_button").assertIsDisplayed()
    }
}