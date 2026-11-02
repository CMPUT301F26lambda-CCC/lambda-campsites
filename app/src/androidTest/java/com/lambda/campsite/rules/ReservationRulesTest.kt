/*
 * Purpose: tests the Firestore security rules for reservations against the
 * emulator (US 01.06.01, 02.06.01, 02.07.01): who can read one, and which
 * queries are allowed.
 *
 * Outstanding issues:
 * - Booking, cancelling and rejecting have no rules yet, so there are no
 *   tests for them. The rest of #108 adds both.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 2, 2026, following
 * CampsiteRulesTest. It helped with the test cases and the Kotlin syntax.
 * Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Reservation
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

/**
 * Tests the security rules for `reservations/{reservationId}` (US 01.06.01,
 * 02.06.01, 02.07.01).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ReservationRulesTest {

    private val reservations = Firebase.firestore.collection("reservations")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
    }

    @Test
    fun guestCanReadTheirReservation() {
        setUpReservation("mine", guestId = uid)

        val error = firestoreErrorFrom { reservations.document("mine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanReadAReservationAtTheirCampsite() {
        setUpReservation("atMine", ownerId = uid)

        val error = firestoreErrorFrom { reservations.document("atMine").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun userCanLookUpAReservationThatDoesntExist() {
        val error = firestoreErrorFrom { reservations.document("no-such-reservation").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun guestCanQueryTheirReservations() {
        setUpReservation("mine", guestId = uid)

        val error = firestoreErrorFrom {
            reservations.whereEqualTo(ReservationFields.GUEST_ID, uid).get(Source.SERVER).await()
        }

        assertNull(error)
    }

    @Test
    fun ownerCanQueryTheReservationsAtOneOfTheirCampsites() {
        setUpReservation("atMine", ownerId = uid)

        val error = firestoreErrorFrom {
            reservations
                .whereEqualTo(ReservationFields.CAMPSITE_ID, "campsite-1")
                .whereEqualTo(ReservationFields.OWNER_ID, uid)
                .get(Source.SERVER)
                .await()
        }

        assertNull(error)
    }

    @Test
    fun userCantReadSomeoneElsesReservation() {
        setUpReservation("theirs")

        val error = firestoreErrorFrom { reservations.document("theirs").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun campsiteQueryWithoutTheOwnerFilterIsRefused() {
        setUpReservation("atMine", ownerId = uid)

        val error = firestoreErrorFrom {
            reservations.whereEqualTo(ReservationFields.CAMPSITE_ID, "campsite-1").get(Source.SERVER).await()
        }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /** Puts a confirmed reservation at `reservations/[id]`, skipping the rules. */
    private fun setUpReservation(id: String, guestId: String = "someone-else", ownerId: String = "another-owner") {
        val reservation = Reservation(
            id = id,
            guestId = guestId,
            guestName = "Jordan Lee",
            ownerId = ownerId,
            campsiteId = "campsite-1",
            campsiteName = "Moose Lake Campsite",
            lotteryId = "lottery-1",
            checkIn = LocalDate.parse("2027-07-02"),
            checkOut = LocalDate.parse("2027-07-04"),
            guests = 2,
            rulesAgreedAt = Timestamp(1_810_000_000, 0),
            createdAt = Timestamp(1_810_000_000, 0),
        )
        FirestoreEmulator.setIgnoringRules("reservations/$id", ReservationFields.toMap(reservation))
    }
}
