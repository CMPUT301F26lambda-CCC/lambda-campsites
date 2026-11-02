/*
 * Purpose: tests the Firestore security rules for reservations against the
 * emulator (US 01.04.02, 01.06.01, 02.06.01, 02.07.01): who can read one,
 * which queries are allowed, and booking a stay.
 *
 * Outstanding issues:
 * - Cancelling and rejecting have no rules yet, so there are no tests for
 *   them. The rest of #108 adds both.
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
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.repository.AvailabilityFields
import com.lambda.campsite.model.repository.CampsiteFields
import com.lambda.campsite.model.repository.LotteryEntryFields
import com.lambda.campsite.model.repository.LotteryFields
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

/**
 * Tests the security rules for `reservations/{reservationId}` (US 01.04.02,
 * 01.06.01, 02.06.01, 02.07.01), and the calendar and entry rules that go
 * with booking.
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class ReservationRulesTest {

    private val firestore = Firebase.firestore
    private val reservations = firestore.collection("reservations")
    private val calendar = firestore.document("campsites/campsite-1/availability/calendar")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        // A winner, a campsite with a July season, and the drawn lottery they won.
        FirestoreEmulator.setIgnoringRules(
            "profiles/$uid",
            ProfileFields.toMap(Profile(deviceId = uid, name = "Jordan Lee", email = "")),
        )
        FirestoreEmulator.setIgnoringRules(
            "campsites/campsite-1",
            CampsiteFields.toMap(
                Campsite(id = "campsite-1", ownerId = "owner-1", name = "Moose Lake Campsite", location = "AB", maxOccupancy = 4),
            ),
        )
        FirestoreEmulator.setIgnoringRules("lotteries/lottery-1", LotteryFields.toMap(lottery()))
        setUpCalendar(bookedNights = emptyList())
        setUpEntry(EntryStatus.WON)
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

    @Test
    fun winnerCanBookAStay() {
        assertNull(book())
    }

    @Test
    fun bookingWithoutTakingTheNightsIsRefused() {
        assertPermissionDenied(book(takeNights = false))
    }

    @Test
    fun bookingWithoutUsingTheWinIsRefused() {
        assertPermissionDenied(book(useWin = false))
    }

    @Test
    fun bookingANightThatsAlreadyBookedIsRefused() {
        setUpCalendar(bookedNights = listOf("2027-07-03"))

        assertPermissionDenied(book(bookedBefore = listOf("2027-07-03")))
    }

    @Test
    fun takingFewerNightsThanTheStayIsRefused() {
        assertPermissionDenied(book(newNights = listOf("2027-07-02")))
    }

    @Test
    fun bookingWithoutAWinIsRefused() {
        setUpEntry(EntryStatus.WAITING)

        assertPermissionDenied(book())
    }

    @Test
    fun bookingForSomeoneElseIsRefused() {
        assertPermissionDenied(book(stay = stay().copy(guestId = "someone-else")))
    }

    @Test
    fun bookingMoreGuestsThanTheCampsiteHoldsIsRefused() {
        assertPermissionDenied(book(stay = stay().copy(guests = 5)))
    }

    @Test
    fun takingNightsWithoutAReservationIsRefused() {
        val error = firestoreErrorFrom {
            calendar.update(
                mapOf(
                    AvailabilityFields.BOOKED_NIGHTS to listOf("2027-07-02"),
                    AvailabilityFields.LAST_RESERVATION_ID to "made-up",
                ),
            ).await()
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

    /** The stay the booking tests book: July 2 to 4 at campsite-1, with this user's win in lottery-1. */
    private fun stay() = Reservation(
        id = "booked",
        guestId = uid,
        guestName = "Jordan Lee",
        ownerId = "owner-1",
        campsiteId = "campsite-1",
        campsiteName = "Moose Lake Campsite",
        lotteryId = "lottery-1",
        checkIn = LocalDate.parse("2027-07-02"),
        checkOut = LocalDate.parse("2027-07-04"),
        guests = 2,
        rulesAgreedAt = Timestamp(0, 0),
        createdAt = Timestamp(0, 0),
    )

    /**
     * Books [stay] as one batch, the way the app's transaction writes it:
     * the reservation, the calendar with [newNights] added to [bookedBefore],
     * and the entry set to BOOKED. [takeNights] and [useWin] leave out the
     * calendar or the entry, so a test can break one part at a time.
     */
    private fun book(
        stay: Reservation = stay(),
        newNights: List<String> = listOf("2027-07-02", "2027-07-03"),
        bookedBefore: List<String> = emptyList(),
        takeNights: Boolean = true,
        useWin: Boolean = true,
    ): FirebaseFirestoreException? = firestoreErrorFrom {
        val batch = firestore.batch()
        batch.set(reservations.document(stay.id), ReservationFields.newReservationFields(stay))
        if (takeNights) {
            batch.update(
                calendar,
                mapOf(
                    AvailabilityFields.BOOKED_NIGHTS to (bookedBefore + newNights).distinct().sorted(),
                    AvailabilityFields.LAST_RESERVATION_ID to stay.id,
                ),
            )
        }
        if (useWin) {
            batch.update(
                firestore.document("lotteries/lottery-1/entries/$uid"),
                mapOf(LotteryEntryFields.STATUS to "BOOKED", LotteryEntryFields.RESERVATION_ID to stay.id),
            )
        }
        batch.commit().await()
    }

    /** A drawn lottery for campsite-1 whose winners can stay between July 1 and 31. */
    private fun lottery() = Lottery(
        id = "lottery-1",
        ownerId = "owner-1",
        name = "Moose Lake, July 2027",
        campsiteIds = listOf("campsite-1"),
        registrationOpens = Timestamp(1_800_000_000, 0),
        registrationCloses = Timestamp(1_801_000_000, 0),
        stayStart = LocalDate.parse("2027-07-01"),
        stayEnd = LocalDate.parse("2027-07-31"),
        status = LotteryStatus.DRAWN,
    )

    private fun setUpCalendar(bookedNights: List<String>) {
        FirestoreEmulator.setIgnoringRules(
            "campsites/campsite-1/availability/calendar",
            mapOf("seasonStart" to "2027-06-01", "seasonEnd" to "2027-09-01", "bookedNights" to bookedNights),
        )
    }

    private fun setUpEntry(status: EntryStatus) {
        FirestoreEmulator.setIgnoringRules(
            "lotteries/lottery-1/entries/$uid",
            LotteryEntryFields.toMap(
                LotteryEntry(lotteryId = "lottery-1", entrantId = uid, joinedAt = Timestamp(1_800_500_000, 0), status = status),
            ),
        )
    }
}
