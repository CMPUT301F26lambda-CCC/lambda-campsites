/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add the anonymous sign-in when the app was connected to
 * Firebase. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite

import android.app.Application
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

    private companion object {
        const val TAG = "LambdaApplication"
    }
}
