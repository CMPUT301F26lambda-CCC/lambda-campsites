/*
 * Purpose: the app's only activity, the entry point Android starts. Inside
 * the brand theme it draws FirstLaunchScreen until the user has agreed to
 * the app policy, then LambdaCampsiteApp. It also asks for the notification
 * permission and hands the current push token to ProfileController.
 *
 * Outstanding issues:
 * - While the profile loads it draws nothing, and if loading fails the
 *   screen stays blank(Fixed). The follow-up to #139 adds a spinner and an error
 *   message.
 * - The permission prompt shows straight away, before the user has seen
 *   anything. Asking at a moment that explains why, like joining a first
 *   lottery, would be friendlier.
 *
 * AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat app, helped
 * Harmanjoth Kaur in resolving the blank screen issue that occurred when profile loading failed.
 * This outstanding issue is fixed in November 2026.
 * Harmanjoth Kaur reviewed and tested the changes.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped change
 * this file in October 2026 to show LambdaCampsiteApp instead of the template's
 * greeting. Sashreek Addanki reviewed and committed it.
 *
 * The notification permission request was added by Aarush Bhat with the
 * assistance of Claude Sonnet 5 (Anthropic), through Claude Code, in
 * November 2026. Aarush Bhat reviewed and committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki hand the push token to ProfileController on each start,
 * and ask for the notification permission only on a fresh start, on
 * November 1, 2026. Sashreek Addanki reviewed them.
 *
 * AI use: ChatGPT (OpenAI), used through the ChatGPT website, helped with
 * resolve errors while connecting ProfileController with MainActivity and revising
 *  the KDoc documentation for this file in November 2026.
 * Harmanjoth Kaur reviewed and tested the changes.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki repair the merge with main and explain the getToken()
 * deprecation, on November 2, 2026. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import com.lambda.campsite.view.screens.FirstLaunchScreen
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.controller.ProfileError
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's only activity and entry point.
 *
 * Sets up the app's theme and observes the user's profile through
 * ProfileController to determine which screen to display.
 *
 * While the profile is loading, the activity shows a progress spinner.
 * If the profile cannot be loaded, it shows an error message asking the
 * user to check their connection and restart the app.
 * If the user has not yet agreed to the app policy,
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

    // The same instance LambdaCampsiteApp gets: both come from this
    // activity's ViewModel store.
    private val profileController: ProfileController by viewModels { ProfileController.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start. After a rotation the user has already
        // answered the prompt, and the token hasn't changed.
        if (savedInstanceState == null) {
            askForNotificationPermission()
            savePushToken()
        }
        setContent {

                LambdaCampsiteTheme(darkTheme = false) {

                    val state by profileController.state.collectAsStateWithLifecycle()
                    val profile = state.profile
                    when {
                        profile == null && state.error == ProfileError.LOAD_FAILED -> ProfileLoadError()
                        profile == null -> ProfileLoading()
                        profile.policyVersionAgreed == null -> {
                            FirstLaunchScreen(
                                onGetStarted = { name -> profileController.onFinishFirstLaunch(name) },
                                saveFailed = state.error == ProfileError.SAVE_FAILED
                            )
                        }
                        else -> LambdaCampsiteApp()
                    }
                }
        }
    }

    /**
     * Android 13 and later hide every notification, including a push, until
     * the user grants this (US 01.10.01). Earlier versions don't have the
     * permission and show notifications without asking.
     *
     * [onCreate] only calls this on a fresh start. Asking again after a
     * rotation would count as a second "no" if the user declines, and Android
     * 11 and later stop showing the prompt after two.
     */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Hands this device's current FCM token to [profileController], which
     * saves it if the profile doesn't have it yet (US 01.10.01). If Firebase
     * can't give a token right now, for example offline, the next start
     * tries again.
     */
    @Suppress("DEPRECATION")
    private fun savePushToken() {
        // getToken() is deprecated in favour of the Firebase Installation ID,
        // but sendPush sends to the FCM token, the same one FcmTokenService
        // saves from onNewToken. Both stay on the token until Firebase ships
        // a token-based replacement.
        Firebase.messaging.token.addOnSuccessListener(profileController::onPushTokenAvailable)
    }
}
/** It shows a progress spinner while the profile is loading. */
@Composable
private fun ProfileLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
/** It shows an error message when the profile could not be loaded. */
@Composable
private fun ProfileLoadError() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.profile_load_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

