/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the availability rules in
 * firestore.rules. It helped with the test cases and the Kotlin syntax.
 * Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
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
 * Tests the security rules for `campsites/{campsiteId}/availability/{docId}`
 * (US 01.04.01, 02.01.02).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error. The signed-in user is an owner, `campsites/mine`
 * is theirs and `campsites/theirs` belongs to someone else.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class AvailabilityRulesTest {

    private val campsites = Firebase.firestore.collection("campsites")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid))
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))
    }

    @Test
    fun userCanReadACampsitesCalendar() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs/availability/calendar", calendarFields())

        val error = firestoreErrorFrom { calendar("theirs").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanLookUpACalendarThatDoesntExist() {
        val error = firestoreErrorFrom { calendar("theirs").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanSetTheSeasonOfTheirCampsite() {
        val error = firestoreErrorFrom { calendar("mine").set(season()).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanChangeTheSeasonAndKeepTheBookedNights() {
        FirestoreEmulator.setIgnoringRules("campsites/mine/availability/calendar", calendarFields())

        val error = firestoreErrorFrom {
            calendar("mine").set(season(end = "2027-10-15"), SetOptions.merge()).await()
        }

        assertNull(error)
    }

    @Test
    fun ownerCantSetTheSeasonOfSomeoneElsesCampsite() {
        val error = firestoreErrorFrom { calendar("theirs").set(season()).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun bannedOwnerCantSetTheSeason() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(ownerBanned = true))

        val error = firestoreErrorFrom { calendar("mine").set(season()).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantSetASeasonThatClosesBeforeItOpens() {
        val error = firestoreErrorFrom {
            calendar("mine").set(season(start = "2027-09-30", end = "2027-05-15")).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantStoreADateInAnotherFormat() {
        val error = firestoreErrorFrom { calendar("mine").set(season(start = "May 15, 2027")).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantStartACalendarWithBookedNights() {
        val error = firestoreErrorFrom { calendar("mine").set(calendarFields()).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantChangeTheBookedNights() {
        FirestoreEmulator.setIgnoringRules("campsites/mine/availability/calendar", calendarFields())

        val error = firestoreErrorFrom {
            calendar("mine").set(mapOf("bookedNights" to emptyList<String>()), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun calendarHasToBeCalledCalendar() {
        val error = firestoreErrorFrom {
            campsites.document("mine").collection("availability").document("summer").set(season()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantDeleteTheCalendar() {
        FirestoreEmulator.setIgnoringRules("campsites/mine/availability/calendar", calendarFields())

        val error = firestoreErrorFrom { calendar("mine").delete().await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun calendar(campsiteId: String): DocumentReference =
        campsites.document(campsiteId).collection("availability").document("calendar")

    /** The fields an owner writes to set the season. */
    private fun season(start: String = "2027-05-15", end: String = "2027-09-30"): Map<String, Any?> =
        mapOf("seasonStart" to start, "seasonEnd" to end)

    /** A whole calendar document, with two nights already booked. */
    private fun calendarFields(): Map<String, Any?> =
        season() + mapOf("bookedNights" to listOf("2027-07-10", "2027-07-11"))

    /** Only the campsite fields these rules look at. */
    private fun campsiteFields(ownerId: String): Map<String, Any?> = mapOf(
        "ownerId" to ownerId,
        "name" to "Moose Lake Campsite",
        "published" to true,
    )

    /** A whole owner profile, in the shape the app stores it. */
    private fun profileFields(ownerBanned: Boolean = false): Map<String, Any?> = mapOf(
        "name" to "Alex Kim",
        "email" to "alex@example.com",
        "phone" to null,
        "roles" to listOf("ENTRANT", "OWNER"),
        "notificationsOn" to true,
        "policyVersionAgreed" to null,
        "ownerBanned" to ownerBanned,
        "removed" to false,
        "pushToken" to null,
    )
}
