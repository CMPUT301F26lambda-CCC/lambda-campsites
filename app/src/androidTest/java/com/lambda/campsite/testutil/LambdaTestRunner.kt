/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.testutil

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

/**
 * The test runner for everything in `androidTest`. It points Firebase Auth,
 * Firestore and Storage at the Firebase emulators before the app starts, so
 * no test ever touches the real project.
 *
 * The Android emulator reaches the computer it runs on at `10.0.2.2`. The
 * ports match the `emulators` section of `firebase.json`. Set as
 * `testInstrumentationRunner` in `app/build.gradle.kts`.
 */
class LambdaTestRunner : AndroidJUnitRunner() {

    override fun callApplicationOnCreate(app: Application) {
        allowLocalNetwork(app)
        // Firebase only accepts useEmulator before its first call, and
        // LambdaApplication.onCreate signs in, so this has to run first.
        Firebase.auth.useEmulator(HOST, AUTH_PORT)
        Firebase.firestore.useEmulator(HOST, FIRESTORE_PORT)
        Firebase.storage.useEmulator(HOST, STORAGE_PORT)
        super.callApplicationOnCreate(app)
    }

    /**
     * Android 17 and newer block connections to local addresses like
     * `10.0.2.2` unless the app holds `ACCESS_LOCAL_NETWORK`, which debug
     * builds declare in `src/debug/AndroidManifest.xml`. Older versions don't
     * have the permission and don't block.
     */
    private fun allowLocalNetwork(app: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
        const val STORAGE_PORT = 9199
    }
}
