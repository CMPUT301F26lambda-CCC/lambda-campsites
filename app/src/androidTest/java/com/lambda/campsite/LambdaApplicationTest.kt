/*
 * Purpose: tests that LambdaApplication sets up push notifications when the
 * app starts (US 01.10.01): the channel exists, and the manifest points FCM
 * at it.
 *
 * Outstanding issues:
 * - They can't check how a real push looks, because the emulators don't
 *   cover FCM.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 1, 2026. It helped with
 * the test cases and the Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite

import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Tests the notification setup [LambdaApplication] does when the app starts (US 01.10.01). */
@RunWith(AndroidJUnit4::class)
class LambdaApplicationTest {

    private val app = ApplicationProvider.getApplicationContext<LambdaApplication>()

    @Test
    fun pushChannelExistsOnceTheAppHasStarted() {
        val manager = app.getSystemService(NotificationManager::class.java)

        assertNotNull(manager.getNotificationChannel(app.getString(R.string.notification_channel_id)))
    }

    @Test
    fun manifestPointsFcmAtThePushChannel() {
        // The newer overload needs API 33, and our minSdk is 26.
        @Suppress("DEPRECATION")
        val metaData = app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData

        assertEquals(
            app.getString(R.string.notification_channel_id),
            metaData.getString("com.google.firebase.messaging.default_notification_channel_id"),
        )
    }
}
