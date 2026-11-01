/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the waiting-list rules in
 * firestore.rules. It helped with the test cases and the Kotlin syntax.
 * Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.repository.LotteryEntryFields
import com.lambda.campsite.model.repository.LotteryFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the security rules for `lotteries/{lotteryId}/entries/{entrantId}`,
 * and for the change to `entryCount` that goes with joining and leaving
 * (US 01.02.01, 01.02.02, 02.04.07, 02.09.01).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error. Unless a test says otherwise, `lotteries/open`
 * belongs to someone else and is taking entries now. These tests join and
 * leave with a batch. The app uses a transaction, and the rules treat both
 * the same way.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class LotteryEntryRulesTest {

    private val firestore = Firebase.firestore
    private val lotteries = firestore.collection("lotteries")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())
    }

    @Test
    fun entrantCanJoinAWaitingList() {
        setUpLottery("open", entryCount = 0)

        val error = firestoreErrorFrom { join("open", entryCount = 1) }

        assertNull(error)
    }

    @Test
    fun entrantCanLeaveAWaitingList() {
        setUpLottery("open", entryCount = 1)
        setUpEntry("open")

        val error = firestoreErrorFrom { leave("open", entryCount = 0) }

        assertNull(error)
    }

    @Test
    fun entrantCanCheckWhetherTheyveJoined() {
        setUpLottery("open")

        val error = firestoreErrorFrom { entry("open", uid).get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun entrantCanFindEveryWaitingListTheyreOn() {
        setUpLottery("open")
        setUpLottery("another")
        setUpEntry("open")
        setUpEntry("another")

        val error = firestoreErrorFrom {
            firestore.collectionGroup("entries")
                .whereEqualTo(LotteryEntryFields.ENTRANT_ID, uid)
                .get(Source.SERVER)
                .await()
        }

        assertNull(error)
    }

    @Test
    fun ownerCanSeeTheirLotterysWaitingList() {
        setUpLottery("mine", ownerId = uid)
        setUpEntry("mine", entrantId = "someone-else")

        val error = firestoreErrorFrom { lotteries.document("mine").collection("entries").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun entrantCantJoinWithoutCountingTheirEntry() {
        setUpLottery("open")

        val error = firestoreErrorFrom { entry("open", uid).set(LotteryEntryFields.newEntryFields(uid)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantRaiseTheCountWithoutJoining() {
        setUpLottery("open", entryCount = 0)

        val error = firestoreErrorFrom { lotteries.document("open").update(LotteryFields.ENTRY_COUNT, 1).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantJoinBeforeRegistrationOpens() {
        setUpLottery("upcoming", opens = daysFromNow(1), closes = daysFromNow(2))

        val error = firestoreErrorFrom { join("upcoming", entryCount = 1) }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantJoinAfterRegistrationCloses() {
        setUpLottery("closed", opens = daysFromNow(-2), closes = daysFromNow(-1))

        val error = firestoreErrorFrom { join("closed", entryCount = 1) }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantJoinAFullWaitingList() {
        setUpLottery("full", capacity = 2, entryCount = 2)

        val error = firestoreErrorFrom { join("full", entryCount = 3) }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantAddSomeoneElse() {
        setUpLottery("open")

        val error = firestoreErrorFrom { join("open", entryCount = 1, entrantId = "someone-else") }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantJoinAsAWinner() {
        setUpLottery("open")
        val fields = LotteryEntryFields.newEntryFields(uid) + mapOf(LotteryEntryFields.STATUS to "WON")

        val error = firestoreErrorFrom { join("open", entryCount = 1, fields = fields) }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantPickTheirOwnJoinTime() {
        setUpLottery("open")
        val early = Timestamp(Instant.parse("2020-01-01T00:00:00Z"))
        val fields = LotteryEntryFields.newEntryFields(uid) + mapOf(LotteryEntryFields.JOINED_AT to early)

        val error = firestoreErrorFrom { join("open", entryCount = 1, fields = fields) }

        assertPermissionDenied(error)
    }

    @Test
    fun removedProfileCantJoin() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(removed = true))
        setUpLottery("open")

        val error = firestoreErrorFrom { join("open", entryCount = 1) }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantChangeTheirResult() {
        setUpLottery("open", entryCount = 1)
        setUpEntry("open")

        val error = firestoreErrorFrom { entry("open", uid).update(LotteryEntryFields.STATUS, "WON").await() }

        assertPermissionDenied(error)
    }

    @Test
    fun entrantCantLeaveAfterRegistrationCloses() {
        setUpLottery("closed", opens = daysFromNow(-2), closes = daysFromNow(-1), entryCount = 1)
        setUpEntry("closed")

        val error = firestoreErrorFrom { leave("closed", entryCount = 0) }

        assertPermissionDenied(error)
    }

    @Test
    fun userCantSeeSomeoneElsesEntry() {
        setUpLottery("open")
        setUpEntry("open", entrantId = "someone-else")

        val error = firestoreErrorFrom { entry("open", "someone-else").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun queryForEveryonesEntriesIsRefused() {
        val error = firestoreErrorFrom { firestore.collectionGroup("entries").get(Source.SERVER).await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    private fun entry(lotteryId: String, entrantId: String): DocumentReference =
        lotteries.document(lotteryId).collection("entries").document(entrantId)

    /** Adds an entry and sets the lottery's count to [entryCount] in one write, the way joining does. */
    private suspend fun join(
        lotteryId: String,
        entryCount: Int,
        entrantId: String = uid,
        fields: Map<String, Any> = LotteryEntryFields.newEntryFields(entrantId),
    ) {
        firestore.batch()
            .set(entry(lotteryId, entrantId), fields)
            .update(lotteries.document(lotteryId), LotteryFields.ENTRY_COUNT, entryCount)
            .commit()
            .await()
    }

    /** Deletes the user's entry and sets the lottery's count to [entryCount] in one write, the way leaving does. */
    private suspend fun leave(lotteryId: String, entryCount: Int) {
        firestore.batch()
            .delete(entry(lotteryId, uid))
            .update(lotteries.document(lotteryId), LotteryFields.ENTRY_COUNT, entryCount)
            .commit()
            .await()
    }

    /**
     * Writes a lottery to `lotteries/{id}`, skipping the rules. By default it
     * belongs to someone else, its registration opened yesterday and closes
     * tomorrow, and its waiting list has no limit.
     */
    private fun setUpLottery(
        id: String,
        ownerId: String = "someone-else",
        opens: Timestamp = daysFromNow(-1),
        closes: Timestamp = daysFromNow(1),
        capacity: Int? = null,
        entryCount: Int = 0,
    ) {
        val lottery = Lottery(
            id = id,
            ownerId = ownerId,
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("campsite-1"),
            registrationOpens = opens,
            registrationCloses = closes,
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            capacity = capacity,
        )
        val fields = LotteryFields.toMap(lottery) + mapOf(LotteryFields.ENTRY_COUNT to entryCount)
        FirestoreEmulator.setIgnoringRules("lotteries/$id", fields)
    }

    /** Writes [entrantId]'s waiting entry in [lotteryId], skipping the rules. */
    private fun setUpEntry(lotteryId: String, entrantId: String = uid) {
        val entry = LotteryEntry(lotteryId = lotteryId, entrantId = entrantId, joinedAt = Timestamp.now())
        FirestoreEmulator.setIgnoringRules("lotteries/$lotteryId/entries/$entrantId", LotteryEntryFields.toMap(entry))
    }

    private fun daysFromNow(days: Long): Timestamp = Timestamp(Instant.now().plus(Duration.ofDays(days)))

    /** A whole profile, in the shape the app stores it. */
    private fun profileFields(removed: Boolean = false): Map<String, Any?> = mapOf(
        "name" to "Alex Kim",
        "email" to "alex@example.com",
        "phone" to null,
        "roles" to listOf("ENTRANT", "OWNER"),
        "notificationsOn" to true,
        "policyVersionAgreed" to null,
        "ownerBanned" to false,
        "removed" to removed,
        "pushToken" to null,
    )
}
