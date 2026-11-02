/*
 * Purpose: the app's only activity, the entry point Android starts. It
 * draws LambdaCampsiteApp inside the brand theme, asks for the
 * notification permission, and hands the current push token to
 * ProfileController.
 *
 * Outstanding issues:
 * - The permission prompt shows straight away, before the user has seen
 *   anything. Asking at a moment that explains why, like joining a first
 *   lottery, would be friendlier.
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
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme

/**
 * The app's only activity. It draws [LambdaCampsiteApp], which holds the
 * navigation and the nav bar, inside the brand theme.
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
            LambdaCampsiteTheme {
                LambdaCampsiteApp()
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
    private fun savePushToken() {
        Firebase.messaging.token.addOnSuccessListener(profileController::onPushTokenAvailable)
    }
}
