/*
 * Purpose: the app's Application class, which Android creates once before
 * any screen. It owns the AppContainer of repositories, signs the install in
 * to Firebase, and creates the notification channel pushes arrive in.
 *
 * Outstanding issues:
 * - If anonymous sign-in fails, for example offline on a first launch,
 *   nothing tries again until the app is next opened.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add the anonymous sign-in when the app was connected to
 * Firebase. Sashreek Addanki reviewed it and committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add the notification channel, on November 1, 2026.
 * Sashreek Addanki reviewed it.
 */
package com.lambda.campsite

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log

/**
 * The app's [Application] class. Android creates it once, before any screen,
 * and keeps it for as long as the app runs.
 *
 * It owns the [AppContainer], so the repositories live as long as the app
 * does, and it signs the install in to Firebase. Registered in
 * `AndroidManifest.xml` with `android:name`.
 */
class LambdaApplication : Application() {

    /** The shared repositories. Controllers get theirs from here through their `Factory`. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
        createNotificationChannel()
        signInIfNeeded()
    }

    /**
     * Signs this install in anonymously on its first launch, which gives it
     * its user ID (US 01.09.01). Firebase remembers the sign-in, so later
     * launches skip this and keep the same ID until the app is uninstalled or
     * its data is cleared.
     *
     * Signing in needs a connection. If it fails, the app tries again on its
     * next launch.
     */
    private fun signInIfNeeded() {
        val auth = container.auth
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnFailureListener { Log.w(TAG, "Anonymous sign-in failed", it) }
        }
    }

    /**
     * Creates the channel pushes arrive in (US 01.10.01). The manifest names
     * it as FCM's default, so a push that arrives while the app is in the
     * background lands here instead of in a channel FCM calls
     * "Miscellaneous". Creating a channel that already exists only updates
     * its name and description.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getString(R.string.notification_channel_id),
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = getString(R.string.notification_channel_description)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "LambdaApplication"
    }
}
