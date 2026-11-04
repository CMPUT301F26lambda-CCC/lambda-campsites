/*
 * Purpose: tests FirestoreReservationRepository against the Firestore
 * emulator (US 01.04.02, 01.04.03, 01.06.01, 01.07.01, 02.06.01, 02.07.01,
 * 02.08.01): which reservations each of the three lists shows, booking a
 * stay, including two phones booking the same night at once, and
 * cancelling, rejecting and declining.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write these tests on November 2, 2026, following
 * FirestoreLotteryRepositoryTest. It helped with the test cases, the
 * coroutines code, the second Firebase app for the two-phone test and the
 * Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.initialize
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.LambdaTestRunner
import com.lambda.campsite.testutil.signInAsNewUser
import java.time.LocalDate
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [FirestoreReservationRepository] against the Firestore emulator
 * (US 01.04.02, 01.06.01, 02.06.01, 02.07.01). Each test signs in as a new
 * user, so nothing from another test is left in the phone's cache.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreReservationRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreReservationRepository(Firebase.auth, firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
        // A campsite with a summer season, and a drawn lottery for it that this user won.
        FirestoreEmulator.setIgnoringRules(
            "campsites/campsite-1",
            CampsiteFields.toMap(
                Campsite(id = "campsite-1", ownerId = "owner-1", name = "Moose Lake Campsite", location = "AB", maxOccupancy = 4),
            ),
        )
        FirestoreEmulator.setIgnoringRules("lotteries/lottery-1", LotteryFields.toMap(lottery()))
        setUpCalendar()
        setUpWinner(uid)
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

    @Test
    fun bookingSavesTheStayTakesItsNightsAndUsesTheWin() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { book() } }

        val saved = requireNotNull(reservationOnServer(id))
        val expected = reservation(id, guestId = uid, ownerId = "owner-1")
            // The server sets both times, so take them from what it saved.
            .copy(rulesAgreedAt = saved.rulesAgreedAt, createdAt = saved.createdAt)
        assertEquals(expected, saved)
        assertNotEquals(Timestamp(0, 0), saved.createdAt)
        assertEquals(listOf("2027-07-02", "2027-07-03"), bookedNightsOnServer())
        assertEquals(EntryStatus.BOOKED, entryOnServer(uid)?.status)
        assertEquals(id, entryOnServer(uid)?.reservationId)
    }

    @Test
    fun bookingWithoutAWinIsRefused() {
        setUpWinner(uid, EntryStatus.WAITING)

        assertEquals(ReservationException.Reason.NOT_A_WINNER, refusalFrom { book() }.reason)
    }

    @Test
    fun bookingACampsiteThatsNotInTheLotteryIsRefused() {
        assertEquals(ReservationException.Reason.NOT_IN_LOTTERY, refusalFrom { book(campsiteId = "campsite-2") }.reason)
    }

    @Test
    fun bookingOutsideTheLotteryStayIsRefused() {
        assertEquals(ReservationException.Reason.OUTSIDE_STAY, refusalFrom { book(checkIn = "2027-06-28") }.reason)
    }

    @Test
    fun bookingANightThatsTakenIsRefused() {
        setUpCalendar(bookedNights = listOf("2027-07-03"))

        assertEquals(ReservationException.Reason.NIGHTS_NOT_FREE, refusalFrom { book() }.reason)
    }

    @Test
    fun bookingMoreGuestsThanTheCampsiteHoldsIsRefused() {
        assertEquals(ReservationException.Reason.GUESTS_DONT_FIT, refusalFrom { book(guests = 5) }.reason)
    }

    @Test
    fun twoPhonesBookingTheSameNightOnlyOneGetsIt() {
        val (secondAuth, secondFirestore) = secondPhone()
        val secondUid = runBlocking {
            secondAuth.signOut()
            requireNotNull(secondAuth.signInAnonymously().await().user).uid
        }
        setUpWinner(secondUid)
        val secondRepository = FirestoreReservationRepository(secondAuth, secondFirestore)

        // Both phones ask for July 2 to 4 at the same moment.
        val results = runBlocking {
            withTimeout(TIMEOUT_MS) {
                listOf(repository, secondRepository)
                    .map { phone -> async { runCatching { book(phone) } } }
                    .awaitAll()
            }
        }

        assertEquals(1, results.count { it.isSuccess })
        val refusal = results.single { it.isFailure }.exceptionOrNull()
        assertEquals(
            "The other phone was refused with $refusal",
            ReservationException.Reason.NIGHTS_NOT_FREE,
            (refusal as? ReservationException)?.reason,
        )
        val reservations = firstValue(repository.myReservations()) + firstValue(secondRepository.myReservations())
        assertEquals(1, reservations.size)
        assertEquals(listOf("2027-07-02", "2027-07-03"), bookedNightsOnServer())
    }

    @Test
    fun cancellingFreesTheNightsAndKeepsTheWinUsed() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { book() } }

        runBlocking { withTimeout(TIMEOUT_MS) { repository.cancel(id) } }

        val saved = requireNotNull(reservationOnServer(id))
        assertEquals(ReservationStatus.CANCELLED, saved.status)
        assertNotEquals(null, saved.cancelledAt)
        assertEquals(emptyList<String>(), bookedNightsOnServer())
        assertEquals(EntryStatus.BOOKED, entryOnServer(uid)?.status)
    }

    @Test
    fun rejectingFreesTheNights() {
        FirestoreEmulator.setIgnoringRules(
            "profiles/$uid",
            ProfileFields.toMap(Profile(deviceId = uid, name = "Alex Kim", email = "").becomeOwner()),
        )
        setUpOnServer(reservation("atMine", ownerId = uid))
        setUpCalendar(bookedNights = listOf("2027-07-02", "2027-07-03"))

        runBlocking { withTimeout(TIMEOUT_MS) { repository.reject("atMine") } }

        assertEquals(ReservationStatus.REJECTED, reservationOnServer("atMine")?.status)
        assertEquals(emptyList<String>(), bookedNightsOnServer())
    }

    @Test
    fun cancellingSomeoneElsesStayIsRefused() {
        setUpOnServer(reservation("theirs"))

        assertEquals(
            ReservationException.Reason.NO_SUCH_RESERVATION,
            refusalFrom { repository.cancel("theirs") }.reason,
        )
    }

    @Test
    fun cancellingTwiceIsRefused() {
        val id = runBlocking { withTimeout(TIMEOUT_MS) { book() } }
        runBlocking { withTimeout(TIMEOUT_MS) { repository.cancel(id) } }

        assertEquals(ReservationException.Reason.CANT_CHANGE, refusalFrom { repository.cancel(id) }.reason)
    }

    @Test
    fun cancellingAStayThatsStartedIsRefused() {
        setUpOnServer(reservation("started", guestId = uid, checkIn = "2020-07-02", checkOut = "2020-07-04"))

        assertEquals(ReservationException.Reason.CANT_CHANGE, refusalFrom { repository.cancel("started") }.reason)
    }

    @Test
    fun decliningUsesUpTheWinWithoutTakingNights() {
        runBlocking { withTimeout(TIMEOUT_MS) { repository.decline("lottery-1") } }

        assertEquals(EntryStatus.DECLINED, entryOnServer(uid)?.status)
        assertEquals(emptyList<String>(), bookedNightsOnServer())
        assertEquals(ReservationException.Reason.NOT_A_WINNER, refusalFrom { book() }.reason)
    }

    @Test
    fun decliningTwiceIsRefused() {
        runBlocking { withTimeout(TIMEOUT_MS) { repository.decline("lottery-1") } }

        assertEquals(ReservationException.Reason.NOT_A_WINNER, refusalFrom { repository.decline("lottery-1") }.reason)
    }

    /** Books July 2 to 4 at campsite-1 with the lottery-1 win, changing one part at a time. */
    private suspend fun book(
        phone: ReservationRepository = repository,
        campsiteId: String = "campsite-1",
        checkIn: String = "2027-07-02",
        checkOut: String = "2027-07-04",
        guests: Int = 2,
    ): String = phone.book("lottery-1", campsiteId, LocalDate.parse(checkIn), LocalDate.parse(checkOut), guests)

    /**
     * A second phone: its own Firebase app with its own sign-in, talking to
     * the same emulators. Firebase only accepts useEmulator before an app's
     * first call, so it's set up once and reused by later tests.
     */
    private fun secondPhone(): Pair<FirebaseAuth, FirebaseFirestore> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == SECOND_PHONE }
            ?: Firebase.initialize(context, Firebase.app.options, SECOND_PHONE).also {
                Firebase.auth(it).useEmulator(LambdaTestRunner.HOST, LambdaTestRunner.AUTH_PORT)
                Firebase.firestore(it).useEmulator(LambdaTestRunner.HOST, LambdaTestRunner.FIRESTORE_PORT)
            }
        return Firebase.auth(app) to Firebase.firestore(app)
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

    /** Gives [userId] a profile and an entry with [status] in lottery-1. */
    private fun setUpWinner(userId: String, status: EntryStatus = EntryStatus.WON) {
        FirestoreEmulator.setIgnoringRules(
            "profiles/$userId",
            ProfileFields.toMap(Profile(deviceId = userId, name = "Jordan Lee", email = "")),
        )
        FirestoreEmulator.setIgnoringRules(
            "lotteries/lottery-1/entries/$userId",
            LotteryEntryFields.toMap(
                LotteryEntry(lotteryId = "lottery-1", entrantId = userId, joinedAt = Timestamp(1_800_500_000, 0), status = status),
            ),
        )
    }

    private fun setUpCalendar(bookedNights: List<String> = emptyList()) {
        FirestoreEmulator.setIgnoringRules(
            "campsites/campsite-1/availability/calendar",
            mapOf("seasonStart" to "2027-06-01", "seasonEnd" to "2027-09-01", "bookedNights" to bookedNights),
        )
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

    private fun reservationOnServer(id: String): Reservation? = runBlocking {
        val snapshot = firestore.document("reservations/$id").get(Source.SERVER).await()
        ReservationFields.fromMap(id, snapshot.data.orEmpty())
    }

    private fun bookedNightsOnServer(): List<String> = runBlocking {
        val snapshot = firestore.document("campsites/campsite-1/availability/calendar").get(Source.SERVER).await()
        (snapshot.get(AvailabilityFields.BOOKED_NIGHTS) as? List<*>).orEmpty().map { it.toString() }
    }

    private fun entryOnServer(userId: String): LotteryEntry? = runBlocking {
        val snapshot = firestore.document("lotteries/lottery-1/entries/$userId").get(Source.SERVER).await()
        LotteryEntryFields.fromMap("lottery-1", userId, snapshot.data.orEmpty())
    }

    private fun refusalFrom(block: suspend () -> Unit): ReservationException =
        assertThrows(ReservationException::class.java) { runBlocking { withTimeout(TIMEOUT_MS) { block() } } }

    private fun <T> firstValue(flow: Flow<T>): T = runBlocking { withTimeout(TIMEOUT_MS) { flow.first() } }

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
        const val SECOND_PHONE = "second-phone"
    }
}
