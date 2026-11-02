/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026 to show LambdaCampsiteApp instead of the template's
 * greeting. Sashreek Addanki reviewed and committed it.
 *
 * The notification permission request was added by Aarush Bhat with the
 * assistance of Claude Sonnet 5 (Anthropic), through Claude Code, in
 * November 2026. Aarush Bhat reviewed and committed it.
 */
/*
 * AI use: ChatGPT (OpenAI), used through the ChatGPT website, helped with
 * resolve errors while connecting ProfileController with MainActivity and revising
 *  the KDoc documentation for this file in November 2026.
 * Harmanjoth Kaur reviewed and tested the changes.
 */
package com.lambda.campsite

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import com.lambda.campsite.view.screens.FirstLaunchScreen
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lambda.campsite.controller.ProfileController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.controller.ProfileError

/**
 * The app's only activity and entry point.
 *
 * Sets up the app's theme and observes the user's profile through
 * ProfileController to determine which screen to display.
 *
 * While the profile is loading, the activity waits before displaying
 * the app content. If the user has not yet agreed to the app policy,
 * it displays [FirstLaunchScreen], allowing the user to enter their
 * name and review and accept the policy.
 *
 * Once the profile is saved and the policy agreement is recorded,
 * the activity displays [LambdaCampsiteApp], which handles the
 * app's navigation and role-based navigation bar.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askForNotificationPermission()
        setContent {

                LambdaCampsiteTheme(darkTheme = false) {

                    val profileController: ProfileController =
                        viewModel(factory = ProfileController.Factory)

                    val state by profileController.state.collectAsStateWithLifecycle()
                    val profile = state.profile

                    when {
                        profile == null -> {
                            // Wait until the profile has loaded.
                        }

                        profile.policyVersionAgreed == null -> {
                            FirstLaunchScreen(
                                onGetStarted = { name ->
                                    profileController.onFinishFirstLaunch(name)
                                },saveFailed = state.error == ProfileError.SAVE_FAILED
                            )
                        }

                        else -> {
                            LambdaCampsiteApp()
                        }
                    }
                }
        }
    }

    /**
     * Android 13 and later hide every notification, including a push, until
     * the user grants this (US 01.10.01). Earlier versions don't have the
     * permission and show notifications without asking.
     */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
