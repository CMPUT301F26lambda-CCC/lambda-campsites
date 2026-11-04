/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write these tests in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the test cases for bucket
 * splitting, observing repository changes, error handling and the coroutines
 * rule, following the OwnerCampsiteListControllerTest pattern.
 * Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.repository.FakeLotteryRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerLotteryListController]: the buckets the lotteries tab shows (US 02.04.01). */
class OwnerLotteryListControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Tests treat October 15, 2026 as now. */
    private val now: Timestamp = timestamp(LocalDate.of(2026, 10, 15))

    /** Builds an OPEN lottery whose registration sits relative to the test's now. */
    private fun lottery(
        id: String,
        opens: LocalDate = LocalDate.of(2026, 10, 1),
        closes: LocalDate = LocalDate.of(2026, 11, 1),
        status: LotteryStatus = LotteryStatus.OPEN,
        ownerId: String = "user-1",
    ) = Lottery(
        id = id,
        ownerId = ownerId,
        name = "Lottery $id",
        campsiteIds = listOf("c1"),
        registrationOpens = timestamp(opens),
        registrationCloses = timestamp(closes),
        stayStart = LocalDate.of(2027, 5, 15),
        stayEnd = LocalDate.of(2027, 9, 30),
        status = status,
    )

    @Test
    fun `state sorts lotteries into active, upcoming and completed buckets`() {
        val active = lottery("active", opens = LocalDate.of(2026, 10, 1), closes = LocalDate.of(2026, 11, 1))
        val upcoming = lottery("upcoming", opens = LocalDate.of(2026, 10, 20), closes = LocalDate.of(2026, 11, 1))
        val completed = lottery("completed", status = LotteryStatus.DRAWN)
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(active, upcoming, completed))

        val controller = OwnerLotteryListController(repository, now = { now })

        assertEquals(listOf(active), controller.state.value.active)
        assertEquals(listOf(upcoming), controller.state.value.upcoming)
        assertEquals(listOf(completed), controller.state.value.completed)
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a drawn lottery is never active even while registration is open`() {
        val drawn = lottery("drawn", status = LotteryStatus.DRAWN)
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(drawn))

        val controller = OwnerLotteryListController(repository, now = { now })

        assertTrue(controller.state.value.active.isEmpty())
        assertEquals(listOf(drawn), controller.state.value.completed)
    }

    @Test
    fun `state reflects a lottery created elsewhere`() {
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = emptyList())
        val controller = OwnerLotteryListController(repository, now = { now })

        assertTrue(controller.state.value.upcoming.isEmpty())
        repository.changeElsewhere(lottery("new", opens = LocalDate.of(2026, 10, 20)))
        assertEquals(1, controller.state.value.upcoming.size)
    }

    @Test
    fun `lotteries run by another owner are left out`() {
        val other = lottery("other", ownerId = "someone-else")
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(other))

        val controller = OwnerLotteryListController(repository, now = { now })

        assertTrue(controller.state.value.active.isEmpty())
        assertTrue(controller.state.value.error == null)
    }

    @Test
    fun `onErrorShown resets error state`() {
        val repository = FakeLotteryRepository(myUserId = "user-1", initial = listOf(lottery("active")))
        val controller = OwnerLotteryListController(repository, now = { now })

        controller.onErrorShown()
        assertNull(controller.state.value.error)
    }
}

/** The Firestore timestamp for midnight at the start of [date], UTC. */
private fun timestamp(date: LocalDate): Timestamp =
    Timestamp(Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()))
