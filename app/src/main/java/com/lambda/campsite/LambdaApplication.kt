/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite

import android.app.Application

/**
 * The app's [Application] class. Android creates it once, before any screen,
 * and keeps it for as long as the app runs.
 *
 * It owns the [AppContainer], so the repositories live as long as the app
 * does. Registered in `AndroidManifest.xml` with `android:name`.
 */
class LambdaApplication : Application() {

    /** The shared repositories. Controllers get theirs from here through their `Factory`. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
