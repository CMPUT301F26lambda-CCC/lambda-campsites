/*
 * Purpose: Flow 7 controller tests for US 01.01.06, 01.02.01 and 01.02.02.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: manual accessibility verification is tracked separately.
 * UI integration is covered separately by EntrantCampsiteFlowTest and EntrantFirebaseFlowTest.
 * AI use: OpenAI GPT-6 through Codex helped write these test scenarios and code.
 * Follow-up human review remains pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.repository.FakeLotteryRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.WaitingListException
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests local validation, authoritative failures, live state and retry for Flow 7. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LotteryControllerTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val instant = Timestamp(1000, 0)
    private val lottery = Lottery(
        id = "lottery-1", ownerId = "owner-1", name = "Aspen demo",
        campsiteIds = listOf("camp-1"), registrationOpens = Timestamp(900, 0),
        registrationCloses = Timestamp(1100, 0), stayStart = LocalDate.of(2027, 7, 1),
        stayEnd = LocalDate.of(2027, 7, 10), numberOfWinners = 2,
    )

    private fun fake(item: Lottery = lottery) = FakeLotteryRepository(initial = listOf(item), now = { instant })
    private fun controller(repository: LotteryRepository) = LotteryController("camp-1", repository) { instant }

    @Test fun `join eligibility explains unavailable times capacity and drawn state`() {
        listOf(
            lottery.copy(registrationOpens = Timestamp(1001, 0)) to LotteryJoinBlock.UPCOMING,
            lottery.copy(registrationCloses = instant) to LotteryJoinBlock.CLOSED,
            lottery.copy(entryCount = 2, capacity = 2) to LotteryJoinBlock.FULL,
            lottery.copy(status = LotteryStatus.DRAWN) to LotteryJoinBlock.DRAWN,
            lottery.copy(status = LotteryStatus.ENDED) to LotteryJoinBlock.ENDED,
        ).forEach { (item, reason) ->
            val control = controller(fake(item))
            assertEquals(reason, control.state.value.joinBlock(item.id))
            assertFalse(control.state.value.canJoin(item.id))
        }
        assertTrue(controller(fake()).state.value.canJoin(lottery.id))
    }

    @Test fun `clock changes update button eligibility without a Firestore event`() {
        var clock = Timestamp(1000, 0)
        val item = lottery.copy(registrationOpens = Timestamp(1001, 0), registrationCloses = Timestamp(1002, 0))
        val control = LotteryController("camp-1", fake(item)) { clock }
        assertFalse(control.state.value.canJoin(item.id))
        clock = Timestamp(1001, 0)
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(1000)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        assertTrue(control.state.value.canJoin(item.id))
        clock = Timestamp(1002, 0)
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(1000)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        assertFalse(control.state.value.canJoin(item.id))
        assertEquals(LotteryJoinBlock.CLOSED, control.state.value.joinBlock(item.id))
    }

    @Test fun `full list allows current waiting entrant to withdraw until closing`() {
        val repo = fake()
        val control = controller(repo)
        control.onJoin(lottery.id)
        repo.changeElsewhere(lottery.copy(capacity = 1, entryCount = 1))
        assertFalse(control.state.value.canJoin(lottery.id))
        assertTrue(control.state.value.canWithdraw(lottery.id))
        repo.changeElsewhere(lottery.copy(registrationCloses = instant, entryCount = 1))
        assertFalse(control.state.value.canWithdraw(lottery.id))
    }

    @Test fun `join and leave update membership from live data`() {
        val control = controller(fake())
        assertFalse(control.state.value.isLoading)
        control.onJoin(lottery.id)
        assertEquals(1, control.state.value.entries.size)
        assertEquals(1, control.state.value.lotteries.single().entryCount)
        assertEquals(LotteryNotice.JOINED, control.state.value.notice)
        control.onNoticeShown()
        assertNull(control.state.value.notice)
        control.onLeave(lottery.id)
        assertTrue(control.state.value.entries.isEmpty())
        assertEquals(0, control.state.value.lotteries.single().entryCount)
        assertEquals(LotteryNotice.LEFT, control.state.value.notice)
    }

    @Test fun `duplicate join and leaving without an entry are explained`() {
        val control = controller(fake())
        control.onLeave(lottery.id)
        assertEquals(LotteryError.NOT_JOINED, control.state.value.error)
        control.onJoin(lottery.id)
        control.onJoin(lottery.id)
        assertEquals(LotteryError.ALREADY_JOINED, control.state.value.error)
        assertEquals(1, control.state.value.entries.size)
    }

    @Test fun `closed upcoming and drawn lotteries reject joining`() {
        listOf(
            lottery.copy(registrationCloses = instant),
            lottery.copy(registrationOpens = Timestamp(1001, 0)),
            lottery.copy(status = LotteryStatus.DRAWN),
        ).forEach { item ->
            val control = controller(fake(item))
            control.onJoin(item.id)
            assertEquals(LotteryError.REGISTRATION_CLOSED, control.state.value.error)
            assertTrue(control.state.value.entries.isEmpty())
        }
    }

    @Test fun `full list rejects joining and other campsite IDs cannot be acted on`() {
        val control = controller(fake(lottery.copy(capacity = 1, entryCount = 1)))
        control.onJoin(lottery.id)
        assertEquals(LotteryError.FULL, control.state.value.error)
        control.onJoin("unknown")
        assertEquals(LotteryError.NO_SUCH_LOTTERY, control.state.value.error)
    }

    @Test fun `leaving at closing time is rejected and retains membership`() {
        val repo = fake()
        val control = controller(repo)
        control.onJoin(lottery.id)
        repo.changeElsewhere(lottery.copy(registrationCloses = instant, entryCount = 1))
        control.onLeave(lottery.id)
        assertEquals(LotteryError.REGISTRATION_CLOSED, control.state.value.error)
        assertEquals(1, control.state.value.entries.size)
    }

    @Test fun `offline write preserves membership and allows explicit retry`() {
        val repo = fake().apply { failSaves = true }
        val control = controller(repo)
        control.onJoin(lottery.id)
        assertEquals(LotteryError.SAVE_FAILED, control.state.value.error)
        assertNull(control.state.value.notice)
        assertNull(control.state.value.pendingLotteryId)
        assertTrue(control.state.value.entries.isEmpty())
        repo.failSaves = false
        control.onJoin(lottery.id)
        assertEquals(LotteryNotice.JOINED, control.state.value.notice)
        assertNull(control.state.value.error)
    }

    @Test fun `server refusal overrides locally valid snapshot`() {
        WaitingListException.Reason.entries.forEach { reason ->
            val base = fake()
            val repository = object : LotteryRepository by base {
                override suspend fun join(lotteryId: String) { throw WaitingListException(reason) }
            }
            val control = controller(repository)
            control.onJoin(lottery.id)
            assertEquals(LotteryError.valueOf(reason.name), control.state.value.error)
            assertNull(control.state.value.notice)
            assertNull(control.state.value.pendingLotteryId)
        }
    }

    @Test fun `pending write blocks duplicate taps and does not announce success early`() {
        val base = fake()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : LotteryRepository by base {
            override suspend fun join(lotteryId: String) {
                calls++
                gate.await()
                base.join(lotteryId)
            }
        }
        val control = controller(repository)
        control.onJoin(lottery.id)
        control.onJoin(lottery.id)
        control.onLeave(lottery.id)
        assertEquals(1, calls)
        assertEquals(lottery.id, control.state.value.pendingLotteryId)
        assertNull(control.state.value.notice)
        assertTrue(control.state.value.entries.isEmpty())
        gate.complete(Unit)
        assertEquals(LotteryNotice.JOINED, control.state.value.notice)
        assertNull(control.state.value.pendingLotteryId)
    }

    @Test fun `failed listener stops loading and explicit retry restores live state`() {
        val base = fake()
        var failing = true
        val repository = object : LotteryRepository by base {
            override fun lotteriesFor(campsiteId: String): Flow<List<Lottery>> = flow {
                if (failing) throw IOException("Read refused")
                base.lotteriesFor(campsiteId).collect { emit(it) }
            }
        }
        val control = controller(repository)
        assertFalse(control.state.value.isLoading)
        assertEquals(LotteryError.LOAD_FAILED, control.state.value.error)
        control.onErrorShown()
        assertEquals(LotteryError.LOAD_FAILED, control.state.value.error)
        failing = false
        control.onRetry()
        assertNull(control.state.value.error)
        assertEquals(listOf(lottery), control.state.value.lotteries)
        base.changeEntryElsewhere(LotteryEntry(lottery.id, "user-1", instant, EntryStatus.WON))
        assertEquals(EntryStatus.WON, control.state.value.entries.single().status)
        base.changeEntryElsewhere(LotteryEntry("another-lottery", "user-1", instant))
        assertEquals(1, control.state.value.entries.size)
    }
}
