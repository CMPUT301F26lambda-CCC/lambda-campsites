/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write these tests in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the test cases for loading
 * a lottery's details, observing waiting-list changes, error handling and the
 * coroutines rule, following the OwnerCampsiteListControllerTest pattern.
 * Raghav Sethi reviewed, ran, and committed them.
 * Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped test
 * loading a co-owner's campsite by ID; human review pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.FakeLotteryRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerLotteryDetailsController]: one lottery's details for its owner (US 02.04.06). */
class OwnerLotteryDetailsControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val aspen = Campsite(id = "c1", ownerId = "user-1", name = "Aspen Hollow", location = "Hinton, AB")
    private val cedar = Campsite(id = "c2", ownerId = "user-1", name = "Cedar Point", location = "Jasper, AB")

    private val lottery = Lottery(
        id = "l1",
        ownerId = "user-1",
        name = "Aspen Hollow summer lottery",
        campsiteIds = listOf("c1", "c2"),
        registrationOpens = timestamp(1_790_000_000_000),
        registrationCloses = timestamp(1_792_000_000_000),
        stayStart = java.time.LocalDate.of(2027, 5, 15),
        stayEnd = java.time.LocalDate.of(2027, 9, 30),
    )

    /** Builds the controller over [lottery] with [entries] on its waiting list. */
    private fun newController(entries: List<LotteryEntry> = emptyList(), lotteryToLoad: Lottery? = lottery) =
        OwnerLotteryDetailsController(
            lotteryId = lottery.id,
            lotteryRepository = FakeLotteryRepository(
                myUserId = "user-1",
                initial = if (lotteryToLoad == null) emptyList() else listOf(lotteryToLoad),
                initialEntries = entries,
            ),
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen, cedar)),
        )

    @Test
    fun `state shows the lottery, its campsites and its waiting list`() {
        val entry = LotteryEntry(
            lotteryId = lottery.id,
            entrantId = "entrant-1",
            joinedAt = timestamp(1_790_100_000_000),
        )
        val controller = newController(entries = listOf(entry))

        assertEquals(lottery.id, controller.state.value.lottery?.id)
        assertEquals(listOf("Aspen Hollow", "Cedar Point"), controller.state.value.campsites.map { it.name })
        assertEquals(1, controller.state.value.waitingList.size)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun `state reflects an entrant joining elsewhere`() {
        val entry = LotteryEntry(
            lotteryId = lottery.id,
            entrantId = "entrant-1",
            joinedAt = timestamp(1_790_100_000_000),
        )
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(lottery))
        val controller = OwnerLotteryDetailsController(
            lotteryId = lottery.id,
            lotteryRepository = repository,
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen, cedar)),
        )

        assertEquals(0, controller.state.value.waitingList.size)
        repository.changeEntryElsewhere(entry)
        assertEquals(1, controller.state.value.waitingList.size)
    }

    @Test
    fun `a missing lottery shows the not-found error`() {
        val controller = newController(lotteryToLoad = null)

        assertEquals(LotteryDetailsError.LOTTERY_NOT_FOUND, controller.state.value.error)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun `a co-owner's campsite still gets its name`() {
        val coOwnerSite = Campsite(
            id = "c3", ownerId = "user-2", name = "Birch Bay", location = "Slave Lake, AB", published = false,
        )
        val shared = lottery.copy(
            ownerIds = listOf("user-1", "user-2"),
            campsiteIds = listOf("c1", "c3"),
        )
        val controller = OwnerLotteryDetailsController(
            lotteryId = shared.id,
            lotteryRepository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(shared)),
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen, coOwnerSite)),
        )

        assertEquals(listOf("Aspen Hollow", "Birch Bay"), controller.state.value.campsites.map { it.name })
    }

    @Test
    fun `a campsite that no longer loads is left out`() {
        val elsewhere = Lottery(
            id = "l2",
            ownerId = "user-1",
            name = "Ghost lottery",
            campsiteIds = listOf("missing"),
            registrationOpens = timestamp(1_790_000_000_000),
            registrationCloses = timestamp(1_792_000_000_000),
            stayStart = java.time.LocalDate.of(2027, 5, 15),
            stayEnd = java.time.LocalDate.of(2027, 9, 30),
        )
        val controller = OwnerLotteryDetailsController(
            lotteryId = "l2",
            lotteryRepository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(elsewhere)),
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen)),
        )

        assertTrue(controller.state.value.campsites.isEmpty())
    }
}

/** The Firestore timestamp for this many milliseconds since the epoch. */
private fun timestamp(millis: Long): Timestamp = Timestamp(java.util.Date.from(Instant.ofEpochMilli(millis)))
