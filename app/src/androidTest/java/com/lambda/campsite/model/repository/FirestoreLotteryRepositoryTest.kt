/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases, the coroutines
 * code and the Kotlin syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.Profile
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.firestoreErrorFrom
import com.lambda.campsite.testutil.signInAsNewUser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
 * Tests [FirestoreLotteryRepository] against the Firestore emulator
 * (US 01.02.01, 01.02.02, 02.04.01, 02.04.06, 02.09.01). Each test signs in as
 * a new user who is an owner, and `campsites/mine` is theirs. Unless a test
 * says otherwise, registration opened yesterday and closes tomorrow.
 *
 * The phone's Firestore cache is shared by every user, so it can still hold
 * lotteries from earlier tests. The query tests wait for the list they
 * expect.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreLotteryRepositoryTest {

    private val firestore = Firebase.firestore
    private val repository = FirestoreLotteryRepository(Firebase.auth, firestore)
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { signInAsNewUser() }
        val owner = Profile(deviceId = uid, name = "Alex Kim", email = "alex@example.com").becomeOwner()
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(owner))
        FirestoreEmulator.setIgnoringRules(
            "campsites/mine",
            mapOf("ownerId" to uid, "name" to "Moose Lake Campsite", "published" to true),
        )
    }

    @Test
    fun savedLotteryReadsBackFromTheServer() {
        val lottery = lottery(id = repository.newLotteryId())

        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(lottery) } }

        assertEquals(lottery, lotteryOnServer(lottery.id))
        assertEquals(lottery, firstValue(repository.lottery(lottery.id)))
    }

    @Test
    fun lotteryThatDoesntExistReadsAsNull() {
        assertNull(firstValue(repository.lottery("no-such-lottery")))
    }

    @Test
    fun lotterySendsTheNewCountWhenSomeoneElseJoins() {
        setUpOnServer(lottery(id = "watched", ownerId = "someone-else"))

        runBlocking {
            withTimeout(TIMEOUT_MS) {
                repository.lottery("watched").first { lottery ->
                    // Once the server's copy arrives, someone joins on another phone.
                    if (lottery?.entryCount == 0) {
                        setUpOnServer(lottery(id = "watched", ownerId = "someone-else"), entryCount = 1)
                    }
                    lottery?.entryCount == 1
                }
            }
        }
    }

    @Test
    fun savingKeepsTheEntryCount() {
        setUpOnServer(lottery(id = "mine"), entryCount = 4)

        val edited = lottery(id = "mine").copy(name = "Moose Lake, summer 2027")
        runBlocking { withTimeout(TIMEOUT_MS) { repository.save(edited) } }

        assertEquals(edited.copy(entryCount = 4), lotteryOnServer("mine"))
    }

    @Test
    fun savingADrawnLotteryThrows() {
        setUpOnServer(lottery(id = "mine").copy(status = LotteryStatus.DRAWN))

        val error = firestoreErrorFrom { repository.save(lottery(id = "mine").copy(name = "Renamed")) }

        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    @Test
    fun lotteriesForHasOnlyThatCampsitesLotteries() {
        val here = lottery(id = "here", ownerId = "someone-else", campsiteIds = listOf("lakeside", "hilltop"))
        val elsewhere = lottery(id = "elsewhere", ownerId = "someone-else", campsiteIds = listOf("riverside"))
        setUpOnServer(here)
        setUpOnServer(elsewhere)

        assertEventuallySends(listOf(here), repository.lotteriesFor("hilltop"))
    }

    @Test
    fun myLotteriesHasOnlyMine() {
        val mine = lottery(id = "mine")
        val theirs = lottery(id = "theirs", ownerId = "someone-else")
        setUpOnServer(mine)
        setUpOnServer(theirs)

        assertEventuallySends(listOf(mine), repository.myLotteries())
    }

    @Test
    fun waitingListIsInTheOrderPeopleJoined() {
        setUpOnServer(lottery(id = "mine"), entryCount = 2)
        val second = entry("mine", "entrant-b", joinedSecondsAgo = 60)
        val first = entry("mine", "entrant-a", joinedSecondsAgo = 120)
        setUpOnServer(second)
        setUpOnServer(first)

        assertEventuallySends(listOf(first, second), repository.waitingList("mine"))
    }

    @Test
    fun myEntriesFindsMyEntriesInEveryLottery() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"), entryCount = 2)
        setUpOnServer(lottery(id = "august", ownerId = "someone-else"), entryCount = 1)
        val july = entry("july", uid)
        val august = entry("august", uid).copy(status = EntryStatus.WON)
        setUpOnServer(july)
        setUpOnServer(august)
        setUpOnServer(entry("july", "someone-else"))

        assertEventuallySends(setOf(july, august), repository.myEntries().map { it.toSet() })
    }

    @Test
    fun joiningAddsMeToTheWaitingListAndCountsMe() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"))

        runBlocking { withTimeout(TIMEOUT_MS) { repository.join("july") } }

        assertEquals(1, lotteryOnServer("july")?.entryCount)
        assertEquals(EntryStatus.WAITING, entryOnServer("july", uid)?.status)
    }

    @Test
    fun twoPeopleJoiningMakesTheCountTwo() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"))
        runBlocking { withTimeout(TIMEOUT_MS) { repository.join("july") } }

        // A second person, on their own phone.
        val secondUid = runBlocking { signInAsNewUser() }
        val second = Profile(deviceId = secondUid, name = "Jordan Lee", email = "jordan@example.com")
        FirestoreEmulator.setIgnoringRules("profiles/$secondUid", ProfileFields.toMap(second))
        runBlocking { withTimeout(TIMEOUT_MS) { repository.join("july") } }

        assertEquals(2, lotteryOnServer("july")?.entryCount)
    }

    @Test
    fun joiningAfterRegistrationClosesIsRefused() {
        setUpOnServer(lottery(id = "closed", ownerId = "someone-else", opensInDays = -2, closesInDays = -1))

        val error = refusalFrom { repository.join("closed") }

        assertEquals(WaitingListException.Reason.REGISTRATION_CLOSED, error.reason)
        assertEquals(0, lotteryOnServer("closed")?.entryCount)
    }

    @Test
    fun joiningAFullWaitingListIsRefused() {
        setUpOnServer(lottery(id = "full", ownerId = "someone-else").copy(capacity = 2), entryCount = 2)

        val error = refusalFrom { repository.join("full") }

        assertEquals(WaitingListException.Reason.FULL, error.reason)
    }

    @Test
    fun joiningTwiceIsRefused() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"), entryCount = 1)
        setUpOnServer(entry("july", uid))

        val error = refusalFrom { repository.join("july") }

        assertEquals(WaitingListException.Reason.ALREADY_JOINED, error.reason)
    }

    @Test
    fun joiningALotteryThatDoesntExistIsRefused() {
        val error = refusalFrom { repository.join("no-such-lottery") }

        assertEquals(WaitingListException.Reason.NO_SUCH_LOTTERY, error.reason)
    }

    @Test
    fun leavingTakesMeOffTheListAndLowersTheCount() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"), entryCount = 2)
        setUpOnServer(entry("july", uid))

        runBlocking { withTimeout(TIMEOUT_MS) { repository.leave("july") } }

        assertEquals(1, lotteryOnServer("july")?.entryCount)
        assertNull(entryOnServer("july", uid))
    }

    @Test
    fun leavingAfterRegistrationClosesIsRefused() {
        setUpOnServer(
            lottery(id = "closed", ownerId = "someone-else", opensInDays = -2, closesInDays = -1),
            entryCount = 1,
        )
        setUpOnServer(entry("closed", uid))

        val error = refusalFrom { repository.leave("closed") }

        assertEquals(WaitingListException.Reason.REGISTRATION_CLOSED, error.reason)
    }

    @Test
    fun leavingAListImNotOnIsRefused() {
        setUpOnServer(lottery(id = "july", ownerId = "someone-else"))

        val error = refusalFrom { repository.leave("july") }

        assertEquals(WaitingListException.Reason.NOT_JOINED, error.reason)
    }

    private fun lottery(
        id: String,
        ownerId: String = uid,
        campsiteIds: List<String> = listOf("mine"),
        opensInDays: Long = -1,
        closesInDays: Long = 1,
    ) = Lottery(
        id = id,
        ownerId = ownerId,
        name = "Moose Lake, July 2027",
        campsiteIds = campsiteIds,
        registrationOpens = Timestamp(Instant.now().plus(Duration.ofDays(opensInDays))),
        registrationCloses = Timestamp(Instant.now().plus(Duration.ofDays(closesInDays))),
        stayStart = LocalDate.parse("2027-07-01"),
        stayEnd = LocalDate.parse("2027-07-31"),
        numberOfWinners = 10,
    )

    private fun entry(lotteryId: String, entrantId: String, joinedSecondsAgo: Long = 60) = LotteryEntry(
        lotteryId = lotteryId,
        entrantId = entrantId,
        // Firestore keeps times to the microsecond, so this one reads back the same.
        joinedAt = Timestamp(Instant.now().minusSeconds(joinedSecondsAgo).truncatedTo(ChronoUnit.MICROS)),
    )

    /** Writes [lottery] straight to the emulator, with its entry count. */
    private fun setUpOnServer(lottery: Lottery, entryCount: Int = 0) {
        FirestoreEmulator.setIgnoringRules(
            "lotteries/${lottery.id}",
            LotteryFields.toMap(lottery) + mapOf(LotteryFields.ENTRY_COUNT to entryCount),
        )
    }

    /** Writes [entry] straight to the emulator. */
    private fun setUpOnServer(entry: LotteryEntry) {
        FirestoreEmulator.setIgnoringRules(
            "lotteries/${entry.lotteryId}/entries/${entry.entrantId}",
            LotteryEntryFields.toMap(entry),
        )
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

    /** Runs [block] and returns the [WaitingListException] it threw, failing if it threw nothing. */
    private fun refusalFrom(block: suspend () -> Unit): WaitingListException =
        assertThrows(WaitingListException::class.java) { runBlocking { withTimeout(TIMEOUT_MS) { block() } } }

    /** The lottery as the server has it, skipping the phone's cache. */
    private fun lotteryOnServer(id: String): Lottery? = runBlocking {
        val snapshot = firestore.document("lotteries/$id").get(Source.SERVER).await()
        LotteryFields.fromMap(id, snapshot.data.orEmpty())
    }

    /** The entry as the server has it, or `null` if there isn't one. */
    private fun entryOnServer(lotteryId: String, entrantId: String): LotteryEntry? = runBlocking {
        val snapshot = firestore.document("lotteries/$lotteryId/entries/$entrantId").get(Source.SERVER).await()
        snapshot.data?.let { LotteryEntryFields.fromMap(lotteryId, entrantId, it) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
