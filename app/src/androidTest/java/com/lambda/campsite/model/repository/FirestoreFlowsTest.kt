/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.firestore
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [liveSnapshots] against the Firestore emulator.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreFlowsTest {

    @Before
    fun signIn() {
        runBlocking { awaitSignIn() }
    }

    @Test
    fun refusedListenerEndsWithThePermissionError() {
        val document = Firebase.firestore.document("noRule/test")

        val error = firestoreErrorFrom { document.liveSnapshots().first() }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }
}
