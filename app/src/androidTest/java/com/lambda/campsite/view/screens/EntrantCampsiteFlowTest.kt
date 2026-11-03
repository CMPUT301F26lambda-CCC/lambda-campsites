/*
 * Purpose: Flow 6 search/details and Flow 7 join/leave UI integration tests using local fakes.
 * Outstanding issues: emulator-backed Firebase/security and manual TalkBack checks remain.
 * AI use: OpenAI GPT-6 through Codex assisted with fixtures and tests; human review pending.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Layout follow-up (#154): Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI),
 * through Codex, assisted with Figma layout/status/navigation-color integration
 * or its regression checks. Follow-up human review remains pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests real controllers/screens together, with fake repositories and no production writes. */
class EntrantCampsiteFlowTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val camp = Campsite("camp-demo", "owner-demo", "Aspen Hollow", "Hinton, Alberta",
        description = "Quiet demo campsite", amenities = listOf("Fire pit", "Tennis"), maxOccupancy = 6,
        ratingCount = 2, ratingSum = 9, rules = "Respect quiet hours.")
    private val cedar = camp.copy(id = "cedar-demo", name = "Cedar Point", location = "British Columbia", ratingCount = 0, ratingSum = 0)
    private val sites = MutableStateFlow(listOf(camp, cedar))
    private val entries = MutableStateFlow<List<LotteryEntry>>(emptyList())
    private val date = LocalDate.of(2027, 7, 1)
    private val now = Timestamp(1500, 0)
    private val lotteries = MutableStateFlow(listOf(Lottery("lottery-demo", "owner-demo", "Aspen summer demo",
        listOf(camp.id), Timestamp(1000, 0), Timestamp(2000, 0), date, date.plusDays(10))))
    private var failLoad = false

    private val campsiteRepository = object : CampsiteRepository {
        override fun publishedCampsites(): Flow<List<Campsite>> = flow {
            if (failLoad) throw java.io.IOException("Demo offline")
            emitAll(sites.map { all -> all.filter { it.published } })
        }
        override fun myCampsites() = sites
        override fun campsite(id: String) = sites.map { all -> all.firstOrNull { it.id == id } }
        override fun newCampsiteId() = "unused"
        override suspend fun save(campsite: Campsite) = error("Read-only fixture")
        override fun availability(campsiteId: String) = flowOf(Availability(date, date.plusDays(30)))
        override suspend fun setSeason(campsiteId: String, seasonStart: LocalDate, seasonEnd: LocalDate) = error("Read-only fixture")
    }
    private val lotteryRepository = object : LotteryRepository {
        override fun lottery(id: String) = lotteries.map { all -> all.firstOrNull { it.id == id } }
        override fun lotteriesFor(campsiteId: String) = lotteries.map { all -> all.filter { campsiteId in it.campsiteIds } }
        override fun myLotteries() = lotteries
        override fun newLotteryId() = "unused"
        override suspend fun save(lottery: Lottery) = error("Unused fixture operation")
        override fun waitingList(lotteryId: String) = entries.map { all -> all.filter { it.lotteryId == lotteryId } }
        override fun myEntries() = entries
        override suspend fun join(lotteryId: String) {
            if (entries.value.any { it.lotteryId == lotteryId }) throw WaitingListException(WaitingListException.Reason.ALREADY_JOINED)
            entries.value += LotteryEntry(lotteryId, "entrant-demo", now)
        }
        override suspend fun leave(lotteryId: String) { entries.value = entries.value.filterNot { it.lotteryId == lotteryId } }
    }

    private fun text(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private inline fun <reified T : ViewModel> controller(key: String, crossinline create: () -> T): T =
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        })[key, T::class.java]

    private fun launch() {
        compose.setContent {
            val selected = remember { mutableStateOf<String?>(null) }
            val selectedLottery = remember { mutableStateOf<String?>(null) }
            val search = remember { controller("search") { SearchController(campsiteRepository) } }
            LambdaCampsiteTheme {
                if (selected.value == null) {
                    SearchScreen(search, onCampsiteClick = { selected.value = it })
                } else {
                    val id = selected.value!!
                    val details = remember(id) { controller("details-$id") { CampsiteController(id, campsiteRepository) } }
                    val lottery = remember(id) { controller("lotteries-$id") { LotteryController(id, lotteryRepository) { now } } }
                    if (selectedLottery.value == null) CampsiteScreen(details, lottery,
                        onBack = { selected.value = null }, onLotteryClick = { selectedLottery.value = it })
                    else EntrantLotteryDetailsScreen(lottery, details, selectedLottery.value!!,
                        onBack = { selectedLottery.value = null })
                }
            }
        }
    }

    /** Clears all ViewModels and their listeners after each test. */
    @After fun clearControllers() { compose.runOnIdle { store.clear() } }

    @Test fun searchSummaryDetailsJoinLeaveAndBackWorkTogether() {
        launch()
        compose.onNodeWithText(text(R.string.entrant_campsite_search_label)).performTextInput("Aspen")
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_search_action)).performClick()
        compose.onNodeWithText("Cedar Point").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.campsite_guest_summary, 6), substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.campsite_rating_count, 2, 4.5, 2)).assertIsDisplayed()
        compose.onNodeWithText("Aspen Hollow", substring = true).performClick()
        compose.onNodeWithText("Quiet demo campsite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_campsite_join_active)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_joined)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_lottery_leave)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_confirm_withdraw)).performClick()
        compose.onNodeWithText(text(R.string.entrant_lottery_left)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(entries.value.isEmpty()) }
        compose.onNodeWithContentDescription(text(R.string.entrant_lottery_details_back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_back)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_search_label)).assertTextContains("Aspen")
        compose.onNodeWithText("Cedar Point").assertDoesNotExist()
    }

    @Test fun searchNoMatchesAndClearQueryRestoreResults() {
        launch()
        val input = compose.onNodeWithText(text(R.string.entrant_campsite_search_label))
        input.performTextInput("does not exist")
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_search_action)).performClick()
        compose.onNodeWithText(text(R.string.entrant_no_results_title)).assertIsDisplayed()
        input.performTextClearance()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_search_action)).performClick()
        compose.onNodeWithText("Aspen Hollow", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cedar Point", substring = true).assertIsDisplayed()
    }

    @Test fun loadFailureHasRetryAndEmptyCatalogueIsDistinct() {
        failLoad = true
        launch()
        compose.onNodeWithText(text(R.string.entrant_campsite_load_failed)).assertIsDisplayed()
        compose.runOnIdle { failLoad = false; sites.value = emptyList() }
        compose.onNodeWithText(text(R.string.entrant_campsite_retry)).performClick()
        compose.onNodeWithText(text(R.string.entrant_campsite_empty)).assertIsDisplayed()
    }

    @Test fun unpublishedSiteDisappearsFromOpenDetailsAndHidesJoin() {
        launch()
        compose.onNodeWithText("Aspen Hollow", substring = true).performClick()
        compose.runOnIdle { sites.value = listOf(camp.copy(published = false), cedar) }
        compose.onNodeWithText(text(R.string.entrant_campsite_not_available)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.entrant_lottery_join)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.entrant_campsite_back)).performClick()
        compose.onNodeWithText("Aspen Hollow", substring = true).assertDoesNotExist()
    }
}
