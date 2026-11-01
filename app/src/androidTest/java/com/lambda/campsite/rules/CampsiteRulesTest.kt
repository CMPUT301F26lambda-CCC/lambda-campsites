/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the campsite rules in
 * docs/architecture.md section 4. It helped with the Kotlin code, syntax and
 * KDoc. Sashreek Addanki reviewed it and committed it.
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
 * Tests the security rules for `campsites/{campsiteId}` (US 01.12.01,
 * 02.01.01, 02.01.03, 02.03.01, 03.07.01).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error. Unless a test says otherwise, the signed-in user
 * is an owner.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteRulesTest {

    private val campsites = Firebase.firestore.collection("campsites")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(roles = listOf("ENTRANT", "OWNER")))
    }

    @Test
    fun userCanReadSomeoneElsesPublishedCampsite() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))

        val error = firestoreErrorFrom { campsites.document("theirs").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanReadTheirOwnUnpublishedCampsite() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid, published = false))

        val error = firestoreErrorFrom { campsites.document("mine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanLookUpACampsiteThatDoesntExist() {
        val error = firestoreErrorFrom { campsites.document("no-such-campsite").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanQueryPublishedCampsites() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))

        val error = firestoreErrorFrom { campsites.whereEqualTo("published", true).get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanQueryTheirOwnCampsites() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid, published = false))

        val error = firestoreErrorFrom { campsites.whereEqualTo("ownerId", uid).get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanListACampsite() {
        val error = firestoreErrorFrom { campsites.document("new").set(campsiteFields(ownerId = uid)).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanEditTheirCampsiteAndKeepItsRatings() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid) + ratingTotals())

        val error = firestoreErrorFrom {
            campsites.document("mine").set(campsiteFields(ownerId = uid, name = "New name"), SetOptions.merge()).await()
        }

        assertNull(error)
    }

    @Test
    fun userCantReadSomeoneElsesUnpublishedCampsite() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else", published = false))

        val error = firestoreErrorFrom { campsites.document("theirs").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun queryWithoutThePublishedFilterIsRefused() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))

        val error = firestoreErrorFrom { campsites.get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantListACampsite() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(roles = listOf("ENTRANT")))

        val error = firestoreErrorFrom { campsites.document("new").set(campsiteFields(ownerId = uid)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun bannedOwnerCantListACampsite() {
        FirestoreEmulator.setIgnoringRules(
            "profiles/$uid",
            profileFields(roles = listOf("ENTRANT", "OWNER"), ownerBanned = true),
        )

        val error = firestoreErrorFrom { campsites.document("new").set(campsiteFields(ownerId = uid)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun removedOwnerCantListACampsite() {
        FirestoreEmulator.setIgnoringRules(
            "profiles/$uid",
            profileFields(roles = listOf("ENTRANT", "OWNER"), removed = true),
        )

        val error = firestoreErrorFrom { campsites.document("new").set(campsiteFields(ownerId = uid)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantListACampsiteAsSomeoneElse() {
        val error = firestoreErrorFrom {
            campsites.document("new").set(campsiteFields(ownerId = "someone-else")).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantListACampsiteWithRatings() {
        val error = firestoreErrorFrom {
            campsites.document("new").set(campsiteFields(ownerId = uid) + ratingTotals()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantEditSomeoneElsesCampsite() {
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))

        val error = firestoreErrorFrom {
            campsites.document("theirs").set(mapOf("name" to "Mine now"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantGiveTheirCampsiteToSomeoneElse() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid))

        val error = firestoreErrorFrom {
            campsites.document("mine").set(mapOf("ownerId" to "someone-else"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantChangeTheRatingTotals() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid) + ratingTotals())

        val error = firestoreErrorFrom {
            campsites.document("mine").set(mapOf("ratingSum" to 50), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantRepublishACampsiteAnAdminUnpublished() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid, published = false))

        val error = firestoreErrorFrom {
            campsites.document("mine").set(mapOf("published" to true), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantDeleteTheirCampsite() {
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid))

        val error = firestoreErrorFrom { campsites.document("mine").delete().await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** A whole campsite document, in the shape the app stores it. */
    private fun campsiteFields(
        ownerId: String,
        name: String = "Moose Lake Campsite",
        published: Boolean = true,
    ): Map<String, Any?> = mapOf(
        "ownerId" to ownerId,
        "name" to name,
        "location" to "Elk Island National Park, AB",
        "description" to "A quiet site by the lake.",
        "amenities" to listOf("Fire pit"),
        "maxOccupancy" to 6,
        "latitude" to 53.6,
        "longitude" to -112.85,
        "tags" to listOf("lakeside"),
        "pricePerNight" to null,
        "rules" to "No pets.",
        "published" to published,
    )

    /** The fields only the `updateRatingStats` Cloud Function writes. */
    private fun ratingTotals(): Map<String, Any?> = mapOf("ratingCount" to 2, "ratingSum" to 9)

    /** A whole profile document, in the shape the app stores it. */
    private fun profileFields(
        roles: List<String>,
        ownerBanned: Boolean = false,
        removed: Boolean = false,
    ): Map<String, Any?> = mapOf(
        "name" to "Alex Kim",
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