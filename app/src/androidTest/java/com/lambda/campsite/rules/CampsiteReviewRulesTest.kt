/*
 * Purpose: Checks that only an entrant with a completed stay can write a
 * review, while signed-in users can read campsite reviews.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with these
 * Flow 17 emulator tests. Human review is pending.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.CampsiteReviewFields
import com.lambda.campsite.model.repository.ProfileFields
import com.lambda.campsite.model.repository.ReservationFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the review rules against the Firebase emulator (US 01.13.02). */
@RunWith(AndroidJUnit4::class)
class CampsiteReviewRulesTest {
    private val reviews = Firebase.firestore.collection("campsites").document("site-1")
        .collection("reviews")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/$uid",
            ProfileFields.toMap(Profile(deviceId = uid, name = "Jordan Lee", email = "")))
    }

    @Test
    fun completedGuestCanPostReadAndReplaceTheirReview() {
        stay("past", uid, "site-1", LocalDate.now().minusDays(2))

        val first = firestoreErrorFrom { reviews.document(uid).set(review("past", "Great trails.")).await() }
        val second = firestoreErrorFrom { reviews.document(uid).set(review("past", "Quiet at night.")).await() }
        val document = runBlocking { reviews.document(uid).get(Source.SERVER).await() }

        assertNull(first)
        assertNull(second)
        assertEquals("Quiet at night.", document.getString(CampsiteReviewFields.TEXT))
    }

    @Test
    fun anotherSignedInUserCanReadAReview() {
        FirestoreEmulator.setIgnoringRules("campsites/site-1/reviews/someone-else",
            mapOf("reservationId" to "past", "text" to "Nice place.",
                "postedAt" to Timestamp.now()))

        val error = firestoreErrorFrom { reviews.get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun futureOrCancelledStayCannotBeReviewed() {
        stay("future", uid, "site-1", LocalDate.now().plusDays(5))
        stay("cancelled", uid, "site-1", LocalDate.now().minusDays(2),
            ReservationStatus.CANCELLED)

        assertDenied { reviews.document(uid).set(review("future", "Too soon.")).await() }
        assertDenied { reviews.document(uid).set(review("cancelled", "No stay.")).await() }
    }

    @Test
    fun anotherGuestsStayOrAnotherCampsiteCannotBeUsed() {
        stay("theirs", "someone-else", "site-1", LocalDate.now().minusDays(2))
        stay("other-site", uid, "site-2", LocalDate.now().minusDays(2))

        assertDenied { reviews.document(uid).set(review("theirs", "Not my stay.")).await() }
        assertDenied { reviews.document(uid).set(review("other-site", "Wrong place.")).await() }
    }

    @Test
    fun guestCannotWriteForSomeoneElseOrForgeTheTimestamp() {
        stay("past", uid, "site-1", LocalDate.now().minusDays(2))

        assertDenied { reviews.document("someone-else").set(review("past", "Forged.")).await() }
        assertDenied { reviews.document(uid).set(mapOf("reservationId" to "past",
            "text" to "Forged time.", "postedAt" to Timestamp.now())).await() }
        assertDenied { reviews.document(uid).set(review("past", "")).await() }
    }

    private fun review(reservationId: String, text: String) = mapOf(
        "reservationId" to reservationId, "text" to text,
        "postedAt" to FieldValue.serverTimestamp())

    private fun stay(id: String, guestId: String, campsiteId: String, checkOut: LocalDate,
        status: ReservationStatus = ReservationStatus.CONFIRMED) {
        val reservation = Reservation(id, guestId, "Jordan Lee", "owner-1", campsiteId,
            "Moose Lake Campsite", "lottery-1", checkOut.minusDays(2), checkOut, 2,
            Timestamp.now(), Timestamp.now(), status)
        FirestoreEmulator.setIgnoringRules("reservations/$id", ReservationFields.toMap(reservation))
    }

    private fun assertDenied(block: suspend () -> Unit) {
        val error = firestoreErrorFrom(block)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }
}
