/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * notification rules tests in NotificationRulesTest. Aarush Bhat reviewed
 * and committed it.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the security rules for `images/{imageId}` (US 02.01.02).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ImageRulesTest {

    private val images = Firebase.firestore.collection("images")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun userCanReadAnImage() {
        FirestoreEmulator.setIgnoringRules("images/theirs", mapOf("ownerId" to "someone-else"))

        val error = firestoreErrorFrom { images.document("theirs").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanCreateTheirOwnImage() {
        val error = firestoreErrorFrom { images.document("new").set(mapOf("ownerId" to uid)).await() }

        assertNull(error)
    }

    @Test
    fun userCantCreateAnImageAsSomeoneElse() {
        val error = firestoreErrorFrom { images.document("new").set(mapOf("ownerId" to "someone-else")).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantChangeAnImage() {
        FirestoreEmulator.setIgnoringRules("images/mine", mapOf("ownerId" to uid))

        val error = firestoreErrorFrom {
            images.document("mine").set(mapOf("ownerId" to "someone-else"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantDeleteAnImage() {
        FirestoreEmulator.setIgnoringRules("images/mine", mapOf("ownerId" to uid))

        val error = firestoreErrorFrom { images.document("mine").delete().await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }
}
