/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add signInAsNewUser for the repository tests. Sashreek Addanki
 * reviewed it and committed it.
 */
package com.lambda.campsite.testutil

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Waits for the anonymous sign-in that `LambdaApplication` starts when the app
 * launches, and returns the signed-in user's ID.
 *
 * Call it before a test touches Firestore. Firestore keeps a separate queue of
 * writes for each user, so a write made before sign-in finishes stays queued
 * for the signed-out user and never reaches the emulator.
 */
suspend fun awaitSignIn(): String = try {
    withTimeout(10_000) {
        suspendCancellableCoroutine { continuation ->
            val auth = Firebase.auth
            val listener = object : FirebaseAuth.AuthStateListener {
                // Called straight away with the current user, then on every change.
                override fun onAuthStateChanged(auth: FirebaseAuth) {
                    val user = auth.currentUser ?: return
                    auth.removeAuthStateListener(this)
                    if (continuation.isActive) continuation.resume(user.uid)
                }
            }
            auth.addAuthStateListener(listener)
            continuation.invokeOnCancellation { auth.removeAuthStateListener(listener) }
        }
    }
} catch (e: TimeoutCancellationException) {
    throw AssertionError("Sign-in to the Auth emulator didn't finish. Is it running?", e)
}

/**
 * Signs in as a brand-new anonymous user and returns their ID. Use it when a
 * test needs a user with nothing saved yet, on the server or in the phone's
 * Firestore cache.
 */
suspend fun signInAsNewUser(): String {
    // Let the app's own sign-in finish first, so it can't replace this one.
    awaitSignIn()
    Firebase.auth.signOut()
    val user = Firebase.auth.signInAnonymously().await().user
    return requireNotNull(user) { "Anonymous sign-in returned no user." }.uid
}
