/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write these tests in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the test cases for
 * counting the draw's winners and not-selected entrants, error handling and
 * the coroutines rule, following the OwnerCampsiteListControllerTest pattern.
 * Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.FakeLotteryRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Tests for [LotteryResultsController]: what the automatic draw decided (US 02.05.01). */
class LotteryResultsControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val aspen = Campsite(id = "c1", ownerId = "user-1", name = "Aspen Hollow", location = "Hinton, AB")

    /** The test's lottery, drawn once registration closed. */
    private val lottery = Lottery(
        id = "l1",
        ownerId = "user-1",
        name = "Aspen Hollow summer lottery",
        campsiteIds = listOf("c1"),
        registrationOpens = timestamp(1_790_000_000_000),
        registrationCloses = timestamp(1_792_000_000_000),
        stayStart = java.time.LocalDate.of(2027, 5, 15),
        stayEnd = java.time.LocalDate.of(2027, 9, 30),
        status = LotteryStatus.DRAWN,
    )

    /** Builds the controller over [lottery] with [entries] the draw has graded. */
    private fun newController(entries: List<LotteryEntry> = emptyList(), lotteryToLoad: Lottery? = lottery) =
        LotteryResultsController(
            lotteryId = lottery.id,
            repository = FakeLotteryRepository(
                myUserId = "user-1",
                initial = if (lotteryToLoad == null) emptyList() else listOf(lotteryToLoad),
                initialEntries = entries,
            ),
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen)),
        )

    @Test
    fun `state counts the winners and the not-selected entrants`() {
        val entries = listOf(
            entry("e1", EntryStatus.WON),
            entry("e2", EntryStatus.NOT_SELECTED),
            entry("e3", EntryStatus.NOT_SELECTED),
            entry("e4", EntryStatus.NOT_SELECTED),
        )
        val controller = newController(entries)

        assertEquals(1, controller.state.value.winnersCount)
        assertEquals(3, controller.state.value.notSelectedCount)
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a winner who booked still counts as a winner`() {
        val controller = newController(listOf(entry("e1", EntryStatus.BOOKED)))

        assertEquals(1, controller.state.value.winnersCount)
        assertEquals(0, controller.state.value.notSelectedCount)
    }

    @Test
    fun `an entrant still waiting counts for neither row`() {
        val controller = newController(listOf(entry("e1", EntryStatus.WAITING)))

        assertEquals(0, controller.state.value.winnersCount)
        assertEquals(0, controller.state.value.notSelectedCount)
    }

    @Test
    fun `state reflects the draw changing a result elsewhere`() {
        val repository = FakeLotteryRepository(
            myUserId = "user-1",
            initial = listOf(lottery),
            initialEntries = listOf(entry("e1", EntryStatus.WAITING)),
        )
        val controller = LotteryResultsController(
            lotteryId = lottery.id,
            repository = repository,
            campsiteRepository = FakeCampsiteRepository(myUserId = "user-1", initial = listOf(aspen)),
        )

        assertEquals(0, controller.state.value.winnersCount)

        // The drawLotteries Cloud Function sets the results the way this fake does.
        repository.changeEntryElsewhere(entry("e1", EntryStatus.WON))
        repository.changeEntryElsewhere(entry("e2", EntryStatus.NOT_SELECTED))

        assertEquals(1, controller.state.value.winnersCount)
        assertEquals(1, controller.state.value.notSelectedCount)
    }

    @Test
    fun `the summary names the lottery's campsites`() {
        val controller = newController()

        assertEquals(listOf("Aspen Hollow"), controller.state.value.campsiteNames)
        assertEquals(lottery.id, controller.state.value.lottery?.id)
    }

    @Test
    fun `a missing lottery shows the not-found error`() {
        val controller = newController(lotteryToLoad = null)

        assertEquals(LotteryResultsError.LOTTERY_NOT_FOUND, controller.state.value.error)
        assertFalse(controller.state.value.isLoading)
    }
}

/** A waiting-list entry for this test's lottery, graded by the draw. */
private fun entry(entrantId: String, status: EntryStatus): LotteryEntry = LotteryEntry(
    lotteryId = "l1",
    entrantId = entrantId,
    joinedAt = timestamp(1_790_100_000_000),
    status = status,
)

/** The Firestore timestamp for this many milliseconds since the epoch. */
private fun timestamp(millis: Long): Timestamp = Timestamp(java.util.Date.from(Instant.ofEpochMilli(millis)))
