/*
 * Purpose: prevent outgoing navigation screens from overriding status-bar contrast.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: physical-device review remains pending.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with the regression test;
 * human review pending.
 */
package com.lambda.campsite.view.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Reproduces overlapping old/new screen effects during a Compose navigation transition. */
class StatusBarStyleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disposingOutgoingScreenRetainsIncomingScreensIconChoice() {
        val outgoing = mutableStateOf(true)
        val incoming = mutableStateOf(false)
        lateinit var hostView: View
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            if (outgoing.value) StatusBarIconStyle(darkIcons = true)
            if (incoming.value) StatusBarIconStyle(darkIcons = false)
        }
        compose.runOnIdle { incoming.value = true }
        compose.runOnIdle {
            assertFalse(WindowCompat.getInsetsController(activity(hostView.context).window, hostView).isAppearanceLightStatusBars)
            outgoing.value = false
        }
        compose.runOnIdle {
            assertFalse(WindowCompat.getInsetsController(activity(hostView.context).window, hostView).isAppearanceLightStatusBars)
        }
    }

    private fun activity(context: Context): Activity {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        error("No activity hosts this test view")
    }
}
