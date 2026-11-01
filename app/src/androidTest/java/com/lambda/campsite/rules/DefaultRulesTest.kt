/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped move the error helper into testutil for the other rules
 * tests. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the rule every other rule builds on: nobody can read or write a path
 * that no rule allows.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class DefaultRulesTest {

    private val document = Firebase.firestore.document("noRule/test")

    @Before
    fun signIn() {
        runBlocking { awaitSignIn() }
    }

    @Test
    fun writeToAPathWithNoRuleIsDenied() {
        val error = firestoreErrorFrom { document.set(mapOf("value" to 1)).await() }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    @Test
    fun readOfAPathWithNoRuleIsDenied() {
        val error = firestoreErrorFrom { document.get(Source.SERVER).await() }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }
}
