/*
 * Purpose: tests FirestoreReservationRepository against the Firestore
 * emulator (US 01.06.01, 02.06.01, 02.07.01): which reservations each of
 * the three lists shows, and in what order.
 *
 * Outstanding issues:
 * - book, cancel, reject and decline aren't tested yet, because their rules
 *   come in the next steps of #108.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 2, 2026, following
 * FirestoreLotteryRepositoryTest. It helped with the test cases, the
 * coroutines code and the Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.signInAsNewUser
import java.time.LocalDate
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreReservationRepository] against the Firestore emulator
 * (US 01.06.01, 02.06.01, 02.07.01). Each test signs in as a new user, so
 * nothing from another test is left in the phone's cache.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreReservationRepositoryTest {

    private val repository = FirestoreReservationRepository(Firebase.auth, Firebase.firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
    }

    @Test
    fun myReservationsAreOnlyMineEarliestFirst() {
        val later = reservation("later", guestId = uid, checkIn = "2027-07-10", checkOut = "2027-07-12")
        val sooner = reservation("sooner", guestId = uid, checkIn = "2027-07-02", checkOut = "2027-07-04")
        setUpOnServer(later)
        setUpOnServer(sooner)
        setUpOnServer(reservation("theirs", guestId = "someone-else"))

        assertEventuallySends(listOf(sooner, later), repository.myReservations())
    }

    @Test
    fun ownerReservationsAreTheOnesAtMyCampsites() {
        val atMine = reservation("atMine", ownerId = uid)
        setUpOnServer(atMine)
        setUpOnServer(reservation("atTheirs", ownerId = "another-owner"))

        assertEventuallySends(listOf(atMine), repository.ownerReservations())
    }

    @Test
    fun campsiteReservationsAreOnlyThatCampsites() {
        val here = reservation("here", ownerId = uid, campsiteId = "campsite-1")
        setUpOnServer(here)
        setUpOnServer(reservation("elsewhere", ownerId = uid, campsiteId = "campsite-2"))

        assertEventuallySends(listOf(here), repository.campsiteReservations("campsite-1"))
    }

    @Test
    fun aReservationTheAppCantReadIsLeftOut() {
        val readable = reservation("readable", guestId = uid)
        setUpOnServer(readable)
        FirestoreEmulator.setIgnoringRules(
            "reservations/unknownStatus",
            ReservationFields.toMap(reservation("unknownStatus", guestId = uid)) +
                (ReservationFields.STATUS to "ON_HOLD"),
        )

        assertEventuallySends(listOf(readable), repository.myReservations())
    }

    private fun reservation(
        id: String,
        guestId: String = "someone-else",
        ownerId: String = "another-owner",
        campsiteId: String = "campsite-1",
        checkIn: String = "2027-07-02",
        checkOut: String = "2027-07-04",
    ) = Reservation(
        id = id,
        guestId = guestId,
        guestName = "Jordan Lee",
        ownerId = ownerId,
        campsiteId = campsiteId,
        campsiteName = "Moose Lake Campsite",
        lotteryId = "lottery-1",
        checkIn = LocalDate.parse(checkIn),
        checkOut = LocalDate.parse(checkOut),
        guests = 2,
        rulesAgreedAt = Timestamp(1_810_000_000, 0),
        createdAt = Timestamp(1_810_000_000, 0),
    )

    private fun setUpOnServer(reservation: Reservation) {
        FirestoreEmulator.setIgnoringRules("reservations/${reservation.id}", ReservationFields.toMap(reservation))
    }

    /** Waits for [flow] to send [expected], and fails with the last value it sent if it never does. */
    private fun <T> assertEventuallySends(expected: T, flow: Flow<T>) {
        var last: T? = null
        try {
            runBlocking { withTimeout(TIMEOUT_MS) { flow.first { last = it; it == expected } } }
        } catch (e: TimeoutCancellationException) {
            assertEquals(expected, last)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
