/*
 * Purpose: tests the security rules for lotteries/{id}.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the lottery rules in
 * firestore.rules. It helped with the test cases and the Kotlin syntax.
 * Sashreek Addanki reviewed them.
 * Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped extend
 * the rules tests to multi-owner lotteries: co-owner create/edit/entry read,
 * and refusal when a campsite's owner isn't listed; human review pending.
 * PR review follow-up: GLM 5.3 Flash (Z.ai), through OpenCode, helped test that
 * every listed owner owns something in the lottery, so a stranger can't join
 * the owner list to read the waiting list; human review pending.
 */
// Follow-up #172: Lalith Valluru, 2026-11-04. OpenAI GPT-6 through Codex assisted
// with race/refusal and access-rule regression tests; human review pending.
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


    @Test fun joinedEntrantsLockCampsitesAndBothRegistrationDates() {
        setUpOnServer("locked", lottery(), entryCount = 1)
        val ref = lotteries.document("locked")
        for (update in listOf(
            mapOf("campsiteIds" to listOf("mine-too")),
            mapOf("registrationOpens" to time("2027-05-02T15:00:00Z")),
            mapOf("registrationCloses" to time("2027-06-02T15:00:00Z")))) {
            assertPermissionDenied(firestoreErrorFrom { ref.update(update).await() })
        }
    }

    @Test fun capacityCannotFallBelowJoinedCountButCanBeRaisedOrUnlimited() {
        setUpOnServer("locked", lottery(), entryCount = 3)
        val ref = lotteries.document("locked")
        assertPermissionDenied(firestoreErrorFrom { ref.update("capacity", 2).await() })
        assertNull(firestoreErrorFrom { ref.update("capacity", 3).await() })
        assertNull(firestoreErrorFrom { ref.update("capacity", 20).await() })
        assertNull(firestoreErrorFrom { ref.update(mapOf("capacity" to null)).await() })
    }

    @Test fun noEntrantsAllowsCampsiteAndRegistrationEdits() {
        setUpOnServer("empty", lottery(), entryCount = 0)
        assertNull(firestoreErrorFrom { lotteries.document("empty").update(mapOf(
            "campsiteIds" to listOf("mine-too"),
            "registrationOpens" to time("2027-05-02T15:00:00Z"),
            "registrationCloses" to time("2027-06-02T15:00:00Z"))).await() })
    }

    @Test fun coOwnerCanRunALotteryCoveringTheOtherOwnersCampsite() {
        // US 02.04.08: an owner can list another owner's campsite in the same
        // lottery by naming them in ownerIds; no approval step is required.
        val shared = Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid, "someone-else"),
            name = "Two owners, one lottery",
            campsiteIds = listOf("mine", "theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        )

        assertNull(firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(shared)).await() })
    }

    @Test fun creatorCanRunALotteryBuiltEntirelyFromOtherOwnersCampsites() {
        // The creator names themselves plus the campsite's owner; the owner
        // list may be longer than the campsite list (US 02.04.08).
        val shared = Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid, "someone-else"),
            name = "Their campsite, my lottery",
            campsiteIds = listOf("theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        )

        assertNull(firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(shared)).await() })
    }

    @Test fun coOwnerCanEditTheirCoOwnedLottery() {
        setUpOnServer("shared", Lottery(
            id = "unused",
            ownerId = "someone-else",
            ownerIds = listOf(uid, "someone-else"),
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("theirs", "mine"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ), entryCount = 0)

        assertNull(firestoreErrorFrom { lotteries.document("shared").update(
            mapOf("name" to "Renamed lottery", "ownerIds" to listOf(uid, "someone-else"))).await() })
    }

    @Test fun coOwnerWithACampsiteCanReadTheWaitingList() {
        setUpOnServer("shared", Lottery(
            id = "unused",
            ownerId = "someone-else",
            ownerIds = listOf(uid, "someone-else"),
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("theirs", "mine"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ))
        seedEntry("shared", "entrant-1")

        val error = firestoreErrorFrom {
            lotteries.document("shared").collection("entries").document("entrant-1")
                .get(Source.SERVER).await()
        }

        assertNull(error)
    }

    @Test fun creatorOfAnAllOtherOwnersLotteryCanReadTheWaitingList() {
        // The creator owns none of the campsites, but the creator always
        // keeps access (US 02.04.08).
        val shared = Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid, "someone-else"),
            name = "Their campsite, my lottery",
            campsiteIds = listOf("theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        )
        assertNull(firestoreErrorFrom { lotteries.document("all-theirs").set(LotteryFields.toMap(shared)).await() })
        seedEntry("all-theirs", "entrant-1")

        val error = firestoreErrorFrom {
            lotteries.document("all-theirs").collection("entries").document("entrant-1")
                .get(Source.SERVER).await()
        }

        assertNull(error)
    }

    @Test fun ownerCantListSomeoneWhoOwnsNothingInTheLottery() {
        // Every listed owner has to be the creator or own one of the
        // lottery's campsites, so a stranger can't be added to the owner list
        // to read the waiting list (US 02.04.08).
        val exploited = Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid, "someone-else"),
            name = "Two owners, one lottery",
            campsiteIds = listOf("mine", "mine-too"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        )

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(exploited)).await() }

        assertPermissionDenied(error)
    }

    @Test fun someoneWhoOwnsNothingCantReadTheWaitingList() {
        // A document saved before the stricter rules could name someone who
        // owns nothing in it. That person still can't read the waiting list,
        // unless the creator picked one of their campsites (US 02.04.08).
        setUpOnServer("exploited", Lottery(
            id = "unused",
            ownerId = "someone-else",
            ownerIds = listOf("someone-else", uid),
            name = "Exploited lottery",
            campsiteIds = listOf("theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ))
        seedEntry("exploited", "entrant-1")

        val error = firestoreErrorFrom {
            lotteries.document("exploited").collection("entries").document("entrant-1")
                .get(Source.SERVER).await()
        }

        assertPermissionDenied(error)
    }

    @Test fun coOwnerCantAddASomeoneWhoOwnsNothingOnAnEdit() {
        setUpOnServer("shared", Lottery(
            id = "unused",
            ownerId = "someone-else",
            ownerIds = listOf(uid, "someone-else"),
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("theirs", "mine"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ), entryCount = 0)

        val error = firestoreErrorFrom { lotteries.document("shared").update(
            mapOf("ownerIds" to listOf(uid, "someone-else", "stranger"))).await() }

        assertPermissionDenied(error)
    }


    @Test fun unlistedUserCantEditASomeoneElsesLottery() {
        setUpOnServer("shared", Lottery(
            id = "unused",
            ownerId = "someone-else",
            ownerIds = listOf("someone-else"),
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ), entryCount = 0)

        val error = firestoreErrorFrom { lotteries.document("shared").update(
            mapOf("name" to "Renamed lottery")).await() }

        assertPermissionDenied(error)
    }

    @Test fun campsiteWhoseOwnerIsNotListedIsRefused() {
        // Including a campsite whose owner isn't in ownerIds is refused, even
        // when the creator is listed.
        val shared = Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid),
            name = "Two owners, one lottery",
            campsiteIds = listOf("mine", "theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        )

        val error = firestoreErrorFrom { lotteries.document().set(LotteryFields.toMap(shared)).await() }

        assertPermissionDenied(error)
    }

    @Test fun creatorIsAlwaysKeptInTheOwnerList() {
        // The creator can't drop themselves from ownerIds.
        setUpOnServer("shared", Lottery(
            id = "unused",
            ownerId = uid,
            ownerIds = listOf(uid, "someone-else"),
            name = "Moose Lake, July 2027",
            campsiteIds = listOf("mine", "theirs"),
            registrationOpens = time("2027-05-01T15:00:00Z"),
            registrationCloses = time("2027-06-01T15:00:00Z"),
            stayStart = LocalDate.parse("2027-07-01"),
            stayEnd = LocalDate.parse("2027-07-31"),
            numberOfWinners = 10,
        ), entryCount = 0)

        val error = firestoreErrorFrom { lotteries.document("shared").update(
            mapOf("ownerIds" to listOf("someone-else"))).await() }

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

    /** Writes a waiting-list entry to `lotteries/{id}/entries/{entrantId}`, skipping the rules. */
    private fun seedEntry(lotteryId: String, entrantId: String) {
        FirestoreEmulator.setIgnoringRules(
            "lotteries/$lotteryId/entries/$entrantId",
            mapOf(
                "entrantId" to entrantId,
                "joinedAt" to time("2027-05-02T15:00:00Z"),
                "status" to "WAITING",
            ),
        )
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
