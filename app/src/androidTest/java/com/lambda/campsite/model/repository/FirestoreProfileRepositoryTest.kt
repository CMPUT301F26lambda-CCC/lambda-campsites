/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases, the coroutines
 * code and the Kotlin syntax. Sashreek Addanki reviewed them, ran them and
 * committed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.firestoreErrorFrom
import com.lambda.campsite.testutil.signInAsNewUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreProfileRepository] against the Firestore emulator
 * (US 01.09.01). Each test signs in as a new user, so nothing from another
 * test is left in the phone's cache.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreProfileRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreProfileRepository(Firebase.auth, firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
    }

    @Test
    fun firstLaunchCreatesAnEntrantProfile() {
        val profile = firstProfile { true }

        assertEquals(Profile(deviceId = uid, name = "", email = ""), profile)
        assertEquals(profile, profileOnServer())
    }

    @Test
    fun savedProfileReadsBackFromTheServer() {
        val created = firstProfile { true }
        val changed = created.copy(name = "Alex Kim", email = "alex@example.com").becomeOwner()

        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(changed) } }

        assertEquals(changed, profileOnServer())
        assertEquals(changed, firstProfile { it == changed })
    }

    @Test
    fun changeMadeElsewhereArrivesThroughTheFlow() {
        val created = firstProfile { true }
        val banned = created.copy(roles = setOf(Role.ENTRANT), ownerBanned = true)

        // Like an admin banning the owner role from another phone.
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(banned))

        assertEquals(banned, firstProfile { it.ownerBanned })
    }

    @Test
    fun savingSomeoneElsesProfileThrows() {
        val someoneElse = Profile(deviceId = "someone-else", name = "Sam", email = "sam@example.com")

        val error = firestoreErrorFrom { repository.save(someoneElse) }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** The first profile from [FirestoreProfileRepository.currentProfile] that matches [predicate]. */
    private fun firstProfile(predicate: (Profile) -> Boolean): Profile = runBlocking {
        withTimeout(TIMEOUT_MS) { repository.currentProfile().first(predicate) }
    }

    /** The profile as the server has it, skipping the phone's cache. */
    private fun profileOnServer(): Profile = runBlocking {
        val snapshot = firestore.document("profiles/$uid").get(Source.SERVER).await()
        ProfileFields.fromMap(uid, snapshot.data.orEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
