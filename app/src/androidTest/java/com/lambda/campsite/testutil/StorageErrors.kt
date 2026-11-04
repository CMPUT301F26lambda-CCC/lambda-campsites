/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FirestoreErrors. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.testutil

import com.google.firebase.storage.StorageException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Runs [block] and returns the Storage error it threw, or `null` if it
 * succeeded. Rules tests check that its [StorageException.getErrorCode] is
 * [StorageException.ERROR_NOT_AUTHORIZED].
 */
fun storageErrorFrom(block: suspend () -> Unit): StorageException? = runBlocking {
    try {
        withTimeout(10_000) { block() }
        null
    } catch (e: StorageException) {
        e
    } catch (e: TimeoutCancellationException) {
        // Storage waits forever for a server it can't reach.
        throw AssertionError("No answer from the Storage emulator. Is it running?", e)
    }
}
