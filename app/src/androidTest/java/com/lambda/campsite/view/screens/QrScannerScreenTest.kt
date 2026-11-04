/*
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with scanner UI tests.
 * Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lambda.campsite.R
import com.lambda.campsite.controller.QrScanUiState
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Checks the Figma scanner and permission paths without opening a real camera. */
class QrScannerScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun deniedCameraShowsSettingsAndExitActions() {
        var openedSettings = false
        var wentBack = false
        compose.setContent { LambdaCampsiteTheme {
            QrScannerContent(QrScanUiState(), permissionGranted = false,
                cameraUnavailable = false, torchAvailable = false, torchEnabled = false,
                onBack = { wentBack = true }, onOpenSettings = { openedSettings = true },
                onTorchToggle = {})
        } }
        compose.onNodeWithText(text(R.string.flow16_camera_needed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow16_camera_explanation)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow16_open_settings)).performClick()
        compose.runOnIdle { assertEquals(true, openedSettings) }
        compose.onNodeWithText(text(R.string.flow16_not_now)).performClick()
        compose.runOnIdle { assertEquals(true, wentBack) }
    }

    @Test fun scannerShowsViewfinderInstructionsAndTorchAction() {
        var toggled = false
        compose.setContent { LambdaCampsiteTheme {
            QrScannerContent(QrScanUiState(), permissionGranted = true,
                cameraUnavailable = false, torchAvailable = true, torchEnabled = false,
                onBack = {}, onOpenSettings = {}, onTorchToggle = { toggled = true })
        } }
        compose.onNodeWithText(text(R.string.flow16_scan_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow16_scan_hint)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.flow16_torch_on)).performClick()
        compose.runOnIdle { assertEquals(true, toggled) }
    }

    @Test fun unavailableCameraHasSafeExit() {
        var wentBack = false
        compose.setContent { LambdaCampsiteTheme {
            QrScannerContent(QrScanUiState(), permissionGranted = true,
                cameraUnavailable = true, torchAvailable = false, torchEnabled = false,
                onBack = { wentBack = true }, onOpenSettings = {}, onTorchToggle = {})
        } }
        compose.onNodeWithText(text(R.string.flow16_camera_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow16_not_now)).performClick()
        compose.runOnIdle { assertEquals(true, wentBack) }
    }
}
