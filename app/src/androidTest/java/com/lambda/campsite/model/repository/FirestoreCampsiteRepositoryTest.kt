/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases, the coroutines
 * code and the Kotlin syntax. It also helped add the availability tests in
 * November 2026. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Profile
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.firestoreErrorFrom
import com.lambda.campsite.testutil.signInAsNewUser
import java.time.LocalDate
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreCampsiteRepository] against the Firestore emulator
 * (US 02.01.01, 02.03.01). Each test signs in as a new user who is an owner.
 *
 * The phone's Firestore cache is shared by every user, so it can still hold
 * campsites from earlier tests. A query sends those first and then the
 * server's answer, so the query tests wait for the list they expect.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCampsiteRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreCampsiteRepository(Firebase.auth, firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
        val owner = Profile(deviceId = uid, name = "Alex Kim", email = "alex@example.com").becomeOwner()
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(owner))
    }

    @Test
    fun savedCampsiteReadsBackFromTheServer() {
        val campsite = campsite(id = repository.newCampsiteId(), ownerId = uid)

        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(campsite) } }

        assertEquals(campsite, campsiteOnServer(campsite.id))
        assertEquals(campsite, firstValue(repository.campsite(campsite.id)))
    }

    @Test
    fun campsiteThatDoesntExistReadsAsNull() {
        assertNull(firstValue(repository.campsite("no-such-campsite")))
    }

    @Test
    fun publishedCampsitesLeavesOutUnpublishedOnes() {
        val published = campsite(id = "published", ownerId = "someone-else")
        val unpublished = campsite(id = "unpublished", ownerId = "someone-else", published = false)
        setUpOnServer(published, unpublished)

        assertEventuallySends(listOf(published), repository.publishedCampsites())
    }

    @Test
    fun myCampsitesHasOnlyMineIncludingUnpublishedOnes() {
        val mine = campsite(id = "mine", ownerId = uid)
        val myUnpublished = campsite(id = "my-unpublished", ownerId = uid, published = false)
        val theirs = campsite(id = "theirs", ownerId = "someone-else")
        setUpOnServer(mine, myUnpublished, theirs)

        assertEventuallySends(setOf(mine, myUnpublished), repository.myCampsites().map { it.toSet() })
    }

    @Test
    fun savingKeepsTheRatingTotals() {
        val rated = campsite(id = "mine", ownerId = uid).copy(ratingCount = 2, ratingSum = 9)
        setUpOnServer(rated)

        // Saved with zeros, like a copy loaded before the latest rating came in.
        val edited = rated.copy(name = "Moose Lake North", ratingCount = 0, ratingSum = 0)
        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(edited) } }

        assertEquals(edited.copy(ratingCount = 2, ratingSum = 9), campsiteOnServer("mine"))
    }

    @Test
    fun savingSomeoneElsesCampsiteThrows() {
        val theirs = campsite(id = "theirs", ownerId = "someone-else")
        setUpOnServer(theirs)

        val error = firestoreErrorFrom { repository.save(theirs.copy(name = "Mine now")) }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    @Test
    fun seasonSetByTheOwnerReadsBack() {
        setUpOnServer(campsite(id = "mine", ownerId = uid))

        runBlocking { withTimeout(TIMEOUT_MS) { repository.setSeason("mine", date("2027-05-15"), date("2027-09-30")) } }

        val expected = Availability(date("2027-05-15"), date("2027-09-30"))
        assertEquals(expected, calendarOnServer("mine"))
        assertEquals(expected, firstValue(repository.availability("mine")))
    }

    @Test
    fun calendarThatDoesntExistReadsAsNull() {
        assertNull(firstValue(repository.availability("campsite-with-no-calendar")))
    }

    @Test
    fun calendarSendsTheSeasonWhenAnotherPhoneSetsIt() {
        val expected = Availability(date("2027-06-01"), date("2027-08-31"))

        runBlocking {
            withTimeout(TIMEOUT_MS) {
                repository.availability("watched").first { availability ->
                    // Once the server says there's no calendar yet, the owner
                    // sets the season from another phone.
                    if (availability == null) {
                        FirestoreEmulator.setIgnoringRules(
                            "campsites/watched/availability/calendar",
                            AvailabilityFields.seasonFields(expected.seasonStart, expected.seasonEnd),
                        )
                    }
                    availability == expected
                }
            }
        }
    }

    @Test
    fun settingTheSeasonKeepsTheBookedNights() {
        setUpOnServer(campsite(id = "mine", ownerId = uid))
        FirestoreEmulator.setIgnoringRules(
            "campsites/mine/availability/calendar",
            AvailabilityFields.seasonFields(date("2027-05-15"), date("2027-09-30")) +
                mapOf(AvailabilityFields.BOOKED_NIGHTS to listOf("2027-07-10")),
        )

        runBlocking { withTimeout(TIMEOUT_MS) { repository.setSeason("mine", date("2027-06-01"), date("2027-09-30")) } }

        val expected = Availability(date("2027-06-01"), date("2027-09-30"), setOf(date("2027-07-10")))
        assertEquals(expected, calendarOnServer("mine"))
    }

    @Test
    fun settingTheSeasonOfSomeoneElsesCampsiteThrows() {
        setUpOnServer(campsite(id = "theirs", ownerId = "someone-else"))

        val error = firestoreErrorFrom { repository.setSeason("theirs", date("2027-05-15"), date("2027-09-30")) }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    @Test
    fun seasonThatClosesBeforeItOpensIsRefusedRightAway() {
        setUpOnServer(campsite(id = "mine", ownerId = uid))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setSeason("mine", date("2027-09-30"), date("2027-05-15")) }
        }
    }

    private fun campsite(id: String, ownerId: String, published: Boolean = true) = Campsite(
        id = id,
        ownerId = ownerId,
        name = "Moose Lake Campsite",
        location = "Elk Island National Park, AB",
        amenities = listOf("Fire pit"),
        maxOccupancy = 6,
        latitude = 53.6,
        longitude = -112.85,
        tags = listOf("lakeside"),
        pricePerNight = 32.5,
        published = published,
    )

    /** Writes [campsites] straight to the emulator, rating totals included. */
    private fun setUpOnServer(vararg campsites: Campsite) {
        for (campsite in campsites) {
            val fields = CampsiteFields.toMap(campsite) + mapOf(
                CampsiteFields.RATING_COUNT to campsite.ratingCount,
                CampsiteFields.RATING_SUM to campsite.ratingSum,
            )
            FirestoreEmulator.setIgnoringRules("campsites/${campsite.id}", fields)
        }
    }

    private fun <T> firstValue(flow: Flow<T>): T = runBlocking { withTimeout(TIMEOUT_MS) { flow.first() } }

    /** Waits for [flow] to send [expected], and fails with the last value it sent if it never does. */
    private fun <T> assertEventuallySends(expected: T, flow: Flow<T>) {
        var last: T? = null
        try {
            runBlocking { withTimeout(TIMEOUT_MS) { flow.first { last = it; it == expected } } }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Expected $expected, but the last value sent was $last", e)
        }
    }

    /** The campsite as the server has it, skipping the phone's cache. */
    private fun campsiteOnServer(id: String): Campsite = runBlocking {
        val snapshot = firestore.document("campsites/$id").get(Source.SERVER).await()
        CampsiteFields.fromMap(id, snapshot.data.orEmpty())
    }

    /** The calendar as the server has it, skipping the phone's cache. */
    private fun calendarOnServer(campsiteId: String): Availability? = runBlocking {
        val snapshot = firestore.document("campsites/$campsiteId/availability/calendar").get(Source.SERVER).await()
        AvailabilityFields.fromMap(snapshot.data.orEmpty())
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}