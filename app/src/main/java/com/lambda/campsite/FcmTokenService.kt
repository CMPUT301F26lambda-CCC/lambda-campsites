/*
 * Purpose: receives this device's FCM push token from Firebase and saves it
 * on the profile, so the sendPush Cloud Function knows where to send a push
 * (US 01.10.01). Registered in AndroidManifest.xml.
 *
 * Outstanding issues:
 * - If the save takes longer than SAVE_TIMEOUT_MS, for example on a first
 *   launch with no connection, this gives up. MainActivity hands the token
 *   to ProfileController on the next start, which saves it then.
 * - onNewToken is deprecated but kept, because its replacement,
 *   onRegistered, hands back a Firebase Installation ID, not a token
 *   sendPush can send to.
 * - There's no automated test. onNewToken only runs when Firebase issues a
 *   real token, which the emulators can't do.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in LambdaApplication. Aarush Bhat reviewed and committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki switch the save to savePushToken and make it finish
 * inside onNewToken, on November 1, 2026. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Saves this device's FCM token on its profile, so `sendPush` knows where to
 * send a push (US 01.10.01).
 *
 * Firebase calls [onNewToken] once a token exists, including the first one,
 * and again whenever it rotates. The save finishes before [onNewToken]
 * returns. Registered in `AndroidManifest.xml`.
 */
class FcmTokenService : FirebaseMessagingService() {

    // onNewToken is deprecated in favour of onRegistered, but onRegistered
    // hands back the Firebase Installation ID, not an FCM token sendPush can
    // send a push to. Firebase hasn't shipped a token-based replacement yet.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val repository = (application as LambdaApplication).container.profileRepository
        // Firebase calls this on a background thread and treats the service as
        // done once it returns, so the save has to finish here. A coroutine
        // left running after this returns could be stopped along with the app.
        try {
            runBlocking { withTimeout(SAVE_TIMEOUT_MS) { repository.savePushToken(token) } }
        } catch (e: Exception) {
            // Offline or too slow. MainActivity saves the token on the next start.
            Log.w(TAG, "Couldn't save the FCM token", e)
        }
    }

    private companion object {
        const val TAG = "FcmTokenService"

        /** How long to wait for sign-in and the save before leaving it to the next start. */
        const val SAVE_TIMEOUT_MS = 10_000L
    }
}
