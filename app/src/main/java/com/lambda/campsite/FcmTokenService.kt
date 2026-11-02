/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in LambdaApplication. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Saves this device's FCM token on its profile, so `sendPush` knows where to
 * send a push (US 01.10.01).
 *
 * Firebase calls [onNewToken] once a token exists, including the first one,
 * and again whenever it rotates. Registered in `AndroidManifest.xml`.
 */
class FcmTokenService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // onNewToken is deprecated in favour of onRegistered, but onRegistered
    // hands back the Firebase Installation ID, not an FCM token sendPush can
    // send a push to. Firebase hasn't shipped a token-based replacement yet.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        val repository = (application as LambdaApplication).container.profileRepository
        scope.launch {
            try {
                val profile = repository.currentProfile().first()
                repository.save(profile.copy(pushToken = token))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Firebase doesn't repeat a token once it's delivered it, so a
                // failed save here means this device won't get pushes until
                // the token happens to rotate again.
                Log.w(TAG, "Couldn't save the FCM token", e)
            }
        }
    }

    private companion object {
        const val TAG = "FcmTokenService"
    }
}
