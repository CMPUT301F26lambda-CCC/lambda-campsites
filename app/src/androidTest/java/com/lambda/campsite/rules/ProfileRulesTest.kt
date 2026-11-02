/*
 * Purpose: tests the Firestore security rules for profiles against the
 * emulator (US 01.09.01, 03.02.01, 03.07.01, 03.11.01): who can read and
 * write one, and that only admins can change roles, bans and removal.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the profile rules in
 * docs/architecture.md section 4. It helped with the Kotlin code, syntax and
 * KDoc. Sashreek Addanki reviewed it and committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add the tests for a profile missing its ban fields, on
 * November 2, 2026. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
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
 * Tests the security rules for `profiles/{uid}` (US 01.09.01, 03.02.01,
 * 03.07.01, 03.11.01).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ProfileRulesTest {

    private val profiles = Firebase.firestore.collection("profiles")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun userCanCreateTheirOwnProfile() {
        val error = firestoreErrorFrom { profiles.document(uid).set(profileFields()).await() }

        assertNull(error)
    }

    @Test
    fun userCanReadTheirOwnProfile() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())

        val error = firestoreErrorFrom { profiles.document(uid).get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanBecomeAnOwnerWhenNotBanned() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())

        val error = firestoreErrorFrom {
            profiles.document(uid).set(profileFields(roles = listOf("ENTRANT", "OWNER"))).await()
        }

        assertNull(error)
    }

    @Test
    fun profileHoldingOnlyItsPushTokenCanBeFilledIn() {
        // What an older app's push token save could leave behind before the
        // profile itself was created.
        FirestoreEmulator.setIgnoringRules("profiles/$uid", mapOf("pushToken" to "token-1"))

        val error = firestoreErrorFrom { profiles.document(uid).set(profileFields()).await() }

        assertNull(error)
    }

    @Test
    fun signedOutUserCantReadAProfile() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())
        Firebase.auth.signOut()
        try {
            val error = firestoreErrorFrom { profiles.document(uid).get(Source.SERVER).await() }

            assertPermissionDenied(error)
        } finally {
            // Sign back in, so the next test starts the way the app does.
            runBlocking { Firebase.auth.signInAnonymously().await() }
        }
    }

    @Test
    fun userCantReadSomeoneElsesProfile() {
        FirestoreEmulator.setIgnoringRules("profiles/someone-else", profileFields())

        val error = firestoreErrorFrom { profiles.document("someone-else").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantWriteSomeoneElsesProfile() {
        val error = firestoreErrorFrom { profiles.document("someone-else").set(profileFields()).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantCreateAProfileAsAnAdmin() {
        val error = firestoreErrorFrom {
            profiles.document(uid).set(profileFields(roles = listOf("ENTRANT", "ADMIN"))).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantAddTheAdminRole() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())

        val error = firestoreErrorFrom {
            profiles.document(uid).set(profileFields(roles = listOf("ENTRANT", "ADMIN"))).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun bannedUserCantBecomeAnOwner() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(ownerBanned = true))

        val error = firestoreErrorFrom {
            profiles.document(uid)
                .set(profileFields(roles = listOf("ENTRANT", "OWNER"), ownerBanned = true))
                .await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantClearTheirOwnerBan() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(ownerBanned = true))

        val error = firestoreErrorFrom { profiles.document(uid).set(profileFields(ownerBanned = false)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun profileMissingItsBanFieldCantBanItselfOrRemoveItself() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", mapOf("pushToken" to "token-1"))

        val banned = firestoreErrorFrom { profiles.document(uid).set(profileFields(ownerBanned = true)).await() }
        val removed = firestoreErrorFrom { profiles.document(uid).set(profileFields(removed = true)).await() }

        assertPermissionDenied(banned)
        assertPermissionDenied(removed)
    }

    @Test
    fun userCantMarkTheirProfileRemoved() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())

        val error = firestoreErrorFrom { profiles.document(uid).set(profileFields(removed = true)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun removedProfileCantBeChanged() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(removed = true))

        val error = firestoreErrorFrom {
            profiles.document(uid).set(profileFields(name = "New name", removed = true)).await()
        }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** A whole profile document, in the shape the app stores it. */
    private fun profileFields(
        name: String = "Alex Kim",
        roles: List<String> = listOf("ENTRANT"),
        ownerBanned: Boolean = false,
        removed: Boolean = false,
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to "alex@example.com",
        "phone" to null,
        "roles" to roles,
        "notificationsOn" to true,
        "policyVersionAgreed" to null,
        "ownerBanned" to ownerBanned,
        "removed" to removed,
        "pushToken" to null,
    )
}
