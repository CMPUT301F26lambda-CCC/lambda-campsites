/*
 * Purpose: tests the admin parts of the Firestore security rules against the
 * emulator (#113): admins read every profile, campsite and notification,
 * delete campsites and photos, and ban or remove other profiles, and nobody
 * else can. It also covers deleting your own profile (US 01.08.01, 03.01.01
 * to 03.05.01, 03.07.01, 03.08.01, 03.11.01).
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file on November 27, 2026, following the admin rules in
 * firestore.rules. It helped with the test cases, the Kotlin code and KDoc.
 * Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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
 * Checks what an admin can and can't do, and that the same writes are refused
 * for everyone else. The signed-in test user is made an admin, or an
 * entrant, by writing their profile straight to the emulator.
 */
@RunWith(AndroidJUnit4::class)
class AdminRulesTest {

    private val db = Firebase.firestore
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/other", mapOf(
            "name" to "Olive Owner", "email" to "olive@example.com",
            "roles" to listOf("ENTRANT", "OWNER"), "ownerBanned" to false, "removed" to false,
        ))
    }

    // Reading

    @Test
    fun adminCanReadSomeoneElsesProfile() {
        makeMeAdmin()

        val error = firestoreErrorFrom { db.document("profiles/other").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun adminCanListEveryProfile() {
        makeMeAdmin()

        val error = firestoreErrorFrom { db.collection("profiles").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun entrantCantListEveryProfile() {
        makeMeEntrant()

        val error = firestoreErrorFrom { db.collection("profiles").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun removedAdminCantReadSomeoneElsesProfile() {
        makeMeAdmin(removed = true)

        val error = firestoreErrorFrom { db.document("profiles/other").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun adminCanReadAnUnpublishedCampsite() {
        makeMeAdmin()
        FirestoreEmulator.setIgnoringRules("campsites/draft", campsiteFields(published = false))

        val error = firestoreErrorFrom { db.document("campsites/draft").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun adminCanReadEveryNotification() {
        makeMeAdmin()
        FirestoreEmulator.setIgnoringRules("notifications/theirs", notificationFields(recipientId = "other"))

        val error = firestoreErrorFrom { db.collection("notifications").get(Source.SERVER).await() }

        assertNull(error)
    }

    // Removing campsites and photos

    @Test
    fun adminCanDeleteACampsite() {
        makeMeAdmin()
        FirestoreEmulator.setIgnoringRules("campsites/pine", campsiteFields(published = true))

        val error = firestoreErrorFrom { db.document("campsites/pine").delete().await() }

        assertNull(error)
    }

    @Test
    fun entrantCantDeleteACampsite() {
        makeMeEntrant()
        FirestoreEmulator.setIgnoringRules("campsites/pine", campsiteFields(published = true))

        val error = firestoreErrorFrom { db.document("campsites/pine").delete().await() }

        assertPermissionDenied(error)
    }

    @Test
    fun adminCanDeleteAPhoto() {
        makeMeAdmin()
        FirestoreEmulator.setIgnoringRules("images/photo", mapOf("ownerId" to "other"))

        val error = firestoreErrorFrom { db.document("images/photo").delete().await() }

        assertNull(error)
    }

    // Banning and removing profiles

    @Test
    fun adminCanBanAnOwnerAndTakeAwayTheOwnerRole() {
        makeMeAdmin()

        val error = firestoreErrorFrom {
            db.document("profiles/other").set(
                mapOf("ownerBanned" to true, "roles" to listOf("ENTRANT")), SetOptions.merge(),
            ).await()
        }

        assertNull(error)
    }

    @Test
    fun adminCanRemoveAProfile() {
        makeMeAdmin()

        val error = firestoreErrorFrom {
            db.document("profiles/other").set(mapOf("removed" to true), SetOptions.merge()).await()
        }

        assertNull(error)
    }

    @Test
    fun adminCantChangeSomeoneElsesDetails() {
        makeMeAdmin()

        val error = firestoreErrorFrom {
            db.document("profiles/other").set(mapOf("name" to "Changed"), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun adminCantGiveSomeoneTheAdminRole() {
        makeMeAdmin()

        val error = firestoreErrorFrom {
            db.document("profiles/other").set(
                mapOf("roles" to listOf("ENTRANT", "OWNER", "ADMIN")), SetOptions.merge(),
            ).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun adminCantRemoveThemselves() {
        makeMeAdmin()

        val error = firestoreErrorFrom {
            db.document("profiles/$uid").set(mapOf("removed" to true), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantBanSomeone() {
        makeMeEntrant()

        val error = firestoreErrorFrom {
            db.document("profiles/other").set(mapOf("ownerBanned" to true), SetOptions.merge()).await()
        }

        assertPermissionDenied(error)
    }

    // Deleting your own profile

    @Test
    fun userCanDeleteTheirOwnProfile() {
        makeMeEntrant()

        val error = firestoreErrorFrom { db.document("profiles/$uid").delete().await() }

        assertNull(error)
    }

    @Test
    fun userCantDeleteSomeoneElsesProfile() {
        makeMeEntrant()

        val error = firestoreErrorFrom { db.document("profiles/other").delete().await() }

        assertPermissionDenied(error)
    }

    /** Makes the signed-in user an admin, or a removed admin when [removed] is `true`. */
    private fun makeMeAdmin(removed: Boolean = false) {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", mapOf(
            "name" to "Ada Admin", "roles" to listOf("ENTRANT", "ADMIN"), "removed" to removed,
        ))
    }

    /** Makes the signed-in user a plain entrant. */
    private fun makeMeEntrant() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", mapOf(
            "name" to "Eve Entrant", "roles" to listOf("ENTRANT"),
        ))
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** A campsite owned by the other user, published or not. */
    private fun campsiteFields(published: Boolean): Map<String, Any?> = mapOf(
        "ownerId" to "other",
        "name" to "Pine Ridge",
        "location" to "Banff, AB",
        "maxOccupancy" to 4,
        "published" to published,
    )

    /** A whole notification document, in the shape the app stores it. */
    private fun notificationFields(recipientId: String): Map<String, Any?> = mapOf(
        "recipientId" to recipientId,
        "type" to "WIN",
        "title" to "You won the lottery",
        "body" to "You won the lottery for Pine Ridge.",
        "sentAt" to Timestamp(1_000, 0),
        "read" to false,
    )
}
