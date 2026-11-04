/*
 * Purpose: tests FirestoreProfileRepository against the Firestore emulator
 * (US 01.09.01, 01.10.01): creating, saving and reading back a profile,
 * and saving the push token on its own.
 *
 * Outstanding issues:
 * - Nothing tests the offline behaviour, because the tests can't cut the
 *   emulator off partway through.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases, the coroutines
 * code and the Kotlin syntax. Sashreek Addanki reviewed them, ran them and
 * committed them.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add the push token tests, on November 1, 2026, and change
 * the one for a token saved before the profile exists, on November 2, 2026.
 * Sashreek Addanki reviewed them.
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
import org.junit.Assert.assertNotNull
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

    @Test
    fun savingThePushTokenKeepsTheRestOfTheProfile() {
        val named = firstProfile { true }.copy(name = "Alex Kim", email = "alex@example.com")
        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(named) } }

        runBlocking { withTimeout(TIMEOUT_MS) { repository.savePushToken("token-1") } }

        assertEquals(named.copy(pushToken = "token-1"), profileOnServer())
    }

    @Test
    fun savingThePushTokenBeforeTheProfileExistsDoesntCreateIt() {
        // Firebase can hand out the token before the first launch creates the
        // profile. A profile created by the token save would hold only the
        // token, and the profile rules would refuse every save after it.
        val error = firestoreErrorFrom { repository.savePushToken("token-1") }

        // The rules refuse an update to a profile that doesn't exist, before
        // Firestore gets as far as NOT_FOUND. Either way nothing is created.
        assertNotNull(error)
        assertEquals(Profile(deviceId = uid, name = "", email = ""), firstProfile { true })
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
