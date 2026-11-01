/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the lottery rules in
 * firestore.rules. It helped with the test cases and the Kotlin syntax.
 * Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.repository.LotteryFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.awaitSignIn
import com.lambda.campsite.testutil.firestoreErrorFrom
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
 * Tests the security rules for `lotteries/{lotteryId}` (US 01.01.06,
 * 02.04.01 to 02.04.07).
 *
 * The first tests check that normal use is allowed, so the rules aren't just
 * denying everything. The rest each try one thing a modified app might do and
 * expect a permission error. The signed-in user is an owner.
 * `campsites/mine` and `campsites/mine-too` are theirs, and `campsites/theirs`
 * belongs to someone else. The lotteries are written with [LotteryFields], so
 * these tests also check that the rules accept what the app saves.
 *
 * Runs against the Firebase emulators. See `docs/testing.md`.
 */
@RunWith(AndroidJUnit4::class)
class LotteryRulesTest {

    private val lotteries = Firebase.firestore.collection("lotteries")
    private lateinit var uid: String

    @Before
    fun setUp() {
        FirestoreEmulator.clear()
        uid = runBlocking { awaitSignIn() }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields())
        FirestoreEmulator.setIgnoringRules("campsites/mine", campsiteFields(ownerId = uid))
        FirestoreEmulator.setIgnoringRules("campsites/mine-too", campsiteFields(ownerId = uid))
        FirestoreEmulator.setIgnoringRules("campsites/theirs", campsiteFields(ownerId = "someone-else"))
    }

    @Test
    fun userCanReadSomeoneElsesLottery() {
        setUpOnServer("theirs", lottery(ownerId = "someone-else", campsiteIds = listOf("theirs")))

        val error = firestoreErrorFrom { lotteries.document("theirs").get(Source.SERVER).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanRunALotteryForTheirCampsites() {
        val lottery = lottery(campsiteIds = listOf("mine", "mine-too"))

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(lottery)).await() }

        assertNull(error)
    }

    @Test
    fun ownerCanEditTheirLotteryAndKeepTheEntryCount() {
        setUpOnServer("mine", lottery(), entryCount = 3)
        val edited = lottery().copy(name = "Moose Lake, summer 2027", capacity = 40)

        val error = firestoreErrorFrom {
            lotteries.document("mine").set(LotteryFields.toMap(edited), SetOptions.merge()).await()
        }

        assertNull(error)
    }

    @Test
    fun ownerCantRunALotteryForSomeoneElsesCampsite() {
        val lottery = lottery(campsiteIds = listOf("mine", "theirs"))

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(lottery)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantPutMoreThanFiveCampsitesInALottery() {
        val campsiteIds = (1..6).map { "extra-$it" }
        campsiteIds.forEach { FirestoreEmulator.setIgnoringRules("campsites/$it", campsiteFields(ownerId = uid)) }

        val error = firestoreErrorFrom {
            lotteries.document().set(LotteryFields.toMap(lottery(campsiteIds = campsiteIds))).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantRunALotteryInSomeoneElsesName() {
        val error = firestoreErrorFrom {
            lotteries.document().set(LotteryFields.toMap(lottery(ownerId = "someone-else"))).await()
        }

        assertPermissionDenied(error)
    }

    @Test
    fun bannedOwnerCantRunALottery() {
        FirestoreEmulator.setIgnoringRules("profiles/$uid", profileFields(ownerBanned = true))

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(lottery())).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantStartALotteryThatsAlreadyDrawn() {
        val drawn = lottery().copy(status = LotteryStatus.DRAWN)

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(drawn)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantStartALotteryWithPeopleOnTheWaitingList() {
        val fields = LotteryFields.toMap(lottery()) + mapOf(LotteryFields.ENTRY_COUNT to 5)

        val error = firestoreErrorFrom { lotteries.document().set(fields).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantSetRegistrationThatClosesBeforeItOpens() {
        val backwards = lottery().copy(registrationCloses = time("2027-04-01T15:00:00Z"))

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(backwards)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantRunALotteryWithNoWinners() {
        val noWinners = lottery().copy(numberOfWinners = 0)

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(noWinners)).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantChangeTheEntryCount() {
        setUpOnServer("mine", lottery(), entryCount = 3)

        val error = firestoreErrorFrom { lotteries.document("mine").update(LotteryFields.ENTRY_COUNT, 0).await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantEditALotteryThatsBeenDrawn() {
        setUpOnServer("mine", lottery().copy(status = LotteryStatus.DRAWN))

        val error = firestoreErrorFrom { lotteries.document("mine").update(LotteryFields.NAME, "Renamed").await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantEditSomeoneElsesLottery() {
        setUpOnServer("theirs", lottery(ownerId = "someone-else", campsiteIds = listOf("theirs")))

        val error = firestoreErrorFrom { lotteries.document("theirs").update(LotteryFields.NAME, "Mine now").await() }

        assertPermissionDenied(error)
    }

    @Test
    fun ownerCantDeleteALottery() {
        setUpOnServer("mine", lottery())

        val error = firestoreErrorFrom { lotteries.document("mine").delete().await() }

        assertPermissionDenied(error)
    }

    private fun assertPermissionDenied(error: FirebaseFirestoreException?) {
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error?.code)
    }

    /**
     * A lottery for July 2027 at the signed-in owner's campsite, with
     * registration in May. Its ID isn't used, since tests pick the document.
     */
    private fun lottery(ownerId: String = uid, campsiteIds: List<String> = listOf("mine")) = Lottery(
        id = "unused",
        ownerId = ownerId,
        name = "Moose Lake, July 2027",
        campsiteIds = campsiteIds,
        registrationOpens = time("2027-05-01T15:00:00Z"),
        registrationCloses = time("2027-06-01T15:00:00Z"),
        stayStart = LocalDate.parse("2027-07-01"),
        stayEnd = LocalDate.parse("2027-07-31"),
        numberOfWinners = 10,
    )

    /** Writes [lottery] to `lotteries/{id}` with its entry count, skipping the rules. */
    private fun setUpOnServer(id: String, lottery: Lottery, entryCount: Int = 0) {
        val fields = LotteryFields.toMap(lottery) + mapOf(LotteryFields.ENTRY_COUNT to entryCount)
        FirestoreEmulator.setIgnoringRules("lotteries/$id", fields)
    }

    private fun time(text: String): Timestamp = Timestamp(Instant.parse(text))

    /** Only the campsite fields these rules look at. */
    private fun campsiteFields(ownerId: String): Map<String, Any?> = mapOf(
        "ownerId" to ownerId,
        "name" to "Moose Lake Campsite",
        "published" to true,
    )

    /** A whole owner profile, in the shape the app stores it. */
    private fun profileFields(ownerBanned: Boolean = false): Map<String, Any?> = mapOf(
        "name" to "Alex Kim",
        "email" to "alex@example.com",
        "phone" to null,
        "roles" to listOf("ENTRANT", "OWNER"),
        "notificationsOn" to true,
        "policyVersionAgreed" to null,
        "ownerBanned" to ownerBanned,
        "removed" to false,
        "pushToken" to null,
    )
}
