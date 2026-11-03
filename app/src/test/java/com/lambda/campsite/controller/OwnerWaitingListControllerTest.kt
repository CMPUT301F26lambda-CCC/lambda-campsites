/*
 * Feature owner: Lalith Valluru. Purpose: Flow 12 / US 02.09.01 read-only owner tests.
 * AI use: OpenAI GPT-6 through Codex assisted with cases and fixtures. Human review pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Ownership, live join order, listener switching, failure recovery and sheet tests for #129. */
class OwnerWaitingListControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val owner = Profile("owner", "Owner", "", roles = setOf(Role.OWNER))
    private fun sampleLottery(id: String, name: String = id, ownerId: String = "owner") = Lottery(
        id, ownerId, name, listOf("camp"), Timestamp(1, 0), Timestamp(100, 0),
        LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 31))
    private fun entry(id: String, at: Long = 5, lotteryId: String = "a") =
        LotteryEntry(lotteryId, id, Timestamp(at, 0))

    private class Reads : LotteryRepository by FakeLotteryRepository() {
        val choices = MutableStateFlow<Result<List<Lottery>>>(Result.success(emptyList()))
        val lists = mutableMapOf<String, MutableStateFlow<Result<List<LotteryEntry>>>>()
        val requested = mutableListOf<String>()
        override fun myLotteries(): Flow<List<Lottery>> = choices.map { it.getOrThrow() }
        override fun waitingList(lotteryId: String): Flow<List<LotteryEntry>> {
            requested += lotteryId
            return lists.getOrPut(lotteryId) { MutableStateFlow(Result.success(emptyList())) }
                .map { it.getOrThrow() }
        }
        fun rows(id: String, vararg entries: LotteryEntry) {
            lists.getOrPut(id) { MutableStateFlow(Result.success(emptyList())) }.value = Result.success(entries.toList())
        }
    }

    @Test fun `only owned choices are shown and entries are sorted with stable ties`() {
        val repo = Reads().apply {
            choices.value = Result.success(listOf(sampleLottery("b", "Zebra"), sampleLottery("a", "Alpha"), sampleLottery("foreign", ownerId = "other")))
            rows("a", entry("late", 9), entry("z", 2), entry("a", 2), entry("wrong", lotteryId = "b"))
        }
        val profile = FakeProfileRepository(owner)
        val control = OwnerWaitingListController(repo, profile)
        assertEquals(listOf("a", "b"), control.state.value.lotteries.map { it.id })
        assertEquals(listOf("a", "z", "late"), control.state.value.entries.map { it.entrantId })
        control.onSelectLottery("foreign")
        assertEquals(listOf("a"), repo.requested)
        assertTrue(profile.saved.isEmpty())
    }

    @Test fun `foreign initial destination is never queried`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner), "foreign")
        assertEquals(OwnerWaitingListError.LOTTERY_UNAVAILABLE, control.state.value.error)
        assertTrue(repo.requested.isEmpty())
        control.onSelectLottery("a")
        assertEquals(listOf("a"), repo.requested)
        assertNull(control.state.value.error)
    }

    @Test fun `no lotteries and empty successful waiting list are distinct successful states`() {
        val repo = Reads()
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        assertFalse(control.state.value.loadingLotteries)
        assertNull(control.state.value.selectedLottery)
        assertNull(control.state.value.error)
        repo.choices.value = Result.success(listOf(sampleLottery("a")))
        assertEquals("a", control.state.value.selectedLotteryId)
        assertFalse(control.state.value.loadingEntries)
        assertTrue(control.state.value.entries.isEmpty())
        assertNull(control.state.value.error)
    }

    @Test fun `live join leave updates the list and removal dismisses selected sheet`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("first", 1)) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        control.onOpenEntrant("missing")
        assertNull(control.state.value.selectedEntrant)
        control.onOpenEntrant("first")
        assertEquals("first", control.state.value.selectedEntrant?.entrantId)
        repo.rows("a", entry("second", 2), entry("first", 1))
        assertEquals(listOf("first", "second"), control.state.value.entries.map { it.entrantId })
        repo.rows("a", entry("second", 2))
        assertNull(control.state.value.selectedEntrantId)
        control.onOpenEntrant("second")
        control.onDismissEntrant()
        assertNull(control.state.value.selectedEntrant)
    }

    @Test fun `switch cancels old listener and metadata updates do not restart it`() {
        val a = sampleLottery("a")
        val repo = Reads().apply { choices.value = Result.success(listOf(a, sampleLottery("b"))); rows("a", entry("old")) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        control.onSelectLottery("b")
        assertTrue(control.state.value.entries.isEmpty())
        repo.rows("a", entry("late-old"))
        assertTrue(control.state.value.entries.isEmpty())
        repo.rows("b", entry("new", lotteryId = "b"))
        repo.choices.value = Result.success(listOf(a, sampleLottery("b").copy(entryCount = 1)))
        assertEquals(listOf("a", "b"), repo.requested)
        assertEquals("new", control.state.value.entries.single().entrantId)
        control.onSelectLottery("a")
        assertEquals("late-old", control.state.value.entries.single().entrantId)
    }

    @Test fun `loss of ownership clears entries and refuses removed selection`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("private")) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        control.onOpenEntrant("private")
        repo.choices.value = Result.success(listOf(sampleLottery("a", ownerId = "other")))
        assertTrue(control.state.value.entries.isEmpty())
        assertNull(control.state.value.selectedEntrant)
        assertEquals(OwnerWaitingListError.LOTTERY_UNAVAILABLE, control.state.value.error)
        control.onSelectLottery("a")
        assertEquals(listOf("a"), repo.requested)
    }

    @Test fun `banned removed and nonowner profiles cannot read a list`() {
        listOf(owner.copy(ownerBanned = true), owner.copy(removed = true), owner.copy(roles = setOf(Role.ENTRANT))).forEach { denied ->
            val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))) }
            val control = OwnerWaitingListController(repo, FakeProfileRepository(denied))
            assertEquals(OwnerWaitingListError.ACCESS_DENIED, control.state.value.error)
            assertTrue(repo.requested.isEmpty())
        }
    }

    @Test fun `role revocation and identity change clear previously loaded private rows`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("private")) }
        val profiles = FakeProfileRepository(owner)
        val control = OwnerWaitingListController(repo, profiles)
        profiles.changeElsewhere(owner.copy(ownerBanned = true))
        assertTrue(control.state.value.entries.isEmpty())
        assertEquals(OwnerWaitingListError.ACCESS_DENIED, control.state.value.error)
        profiles.changeElsewhere(owner)
        assertEquals(1, control.state.value.entries.size)
        profiles.changeElsewhere(owner.copy(deviceId = "different"))
        assertTrue(control.state.value.entries.isEmpty())
    }

    @Test fun `entry failure clears stale data and retry recovers same selected lottery`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("first")) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        repo.lists.getValue("a").value = Result.failure(IOException("Offline"))
        assertTrue(control.state.value.entries.isEmpty())
        assertEquals(OwnerWaitingListError.LOAD_ENTRIES, control.state.value.error)
        repo.rows("a", entry("recovered"))
        control.onRetry()
        assertEquals("recovered", control.state.value.entries.single().entrantId)
        assertNull(control.state.value.error)
    }

    @Test fun `lottery failure is not empty success and retry restores subscriptions`() {
        val repo = Reads().apply { choices.value = Result.failure(IOException("Offline")) }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner))
        assertEquals(OwnerWaitingListError.LOAD_LOTTERIES, control.state.value.error)
        repo.choices.value = Result.success(listOf(sampleLottery("a")))
        control.onRetry()
        assertEquals("a", control.state.value.selectedLotteryId)
        assertNull(control.state.value.error)
    }

    @Test fun `authorized names and contacts load and leaving removes personal data`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("first")) }
        val contact = WaitingListContact("first", "Alex Demo", "alex@example.com", "780-555-0101")
        val contacts = WaitingListContactRepository { id, ids ->
            assertEquals("a", id)
            if ("first" in ids) mapOf("first" to contact) else emptyMap()
        }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner), contactRepository = contacts)
        assertEquals(contact, control.state.value.contacts["first"])
        assertFalse(control.state.value.loadingContacts)
        repo.rows("a")
        assertTrue(control.state.value.contacts.isEmpty())
    }

    @Test fun `contact failure retains registration list and retry recovers names`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"))); rows("a", entry("first")) }
        var fail = true
        val contacts = WaitingListContactRepository { _, _ ->
            if (fail) throw IOException("Unavailable")
            mapOf("first" to WaitingListContact("first", "Alex Demo", null, null))
        }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner), contactRepository = contacts)
        assertTrue(control.state.value.contactsFailed)
        assertEquals(1, control.state.value.entries.size)
        fail = false
        control.onRetry()
        assertFalse(control.state.value.contactsFailed)
        assertEquals("Alex Demo", control.state.value.contacts["first"]?.name)
    }

    @Test fun `pending contact request cannot leak across lottery selection`() {
        val repo = Reads().apply { choices.value = Result.success(listOf(sampleLottery("a"), sampleLottery("b"))); rows("a", entry("private")) }
        val pending = CompletableDeferred<Map<String, WaitingListContact>>()
        val contacts = WaitingListContactRepository { id, _ -> if (id == "a") pending.await() else emptyMap() }
        val control = OwnerWaitingListController(repo, FakeProfileRepository(owner), contactRepository = contacts)
        assertTrue(control.state.value.loadingContacts)
        control.onSelectLottery("b")
        pending.complete(mapOf("private" to WaitingListContact("private", "Private name", null, null)))
        assertEquals("b", control.state.value.selectedLotteryId)
        assertTrue(control.state.value.contacts.isEmpty())
        assertFalse(control.state.value.loadingContacts)
    }
}

