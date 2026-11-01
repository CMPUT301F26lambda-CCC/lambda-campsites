/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.testutil

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Runs [block] and returns the Firestore error it threw, or `null` if it
 * succeeded. Rules tests check that the error is `PERMISSION_DENIED`.
 */
fun firestoreErrorFrom(block: suspend () -> Unit): FirebaseFirestoreException? = runBlocking {
    try {
        withTimeout(10_000) { block() }
        null
    } catch (e: FirebaseFirestoreException) {
        e
    } catch (e: TimeoutCancellationException) {
        // Firestore waits forever for a server it can't reach.
        throw AssertionError("No answer from the Firestore emulator. Is it running?", e)
    }
}
