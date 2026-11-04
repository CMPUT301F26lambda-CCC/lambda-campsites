/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the Compose test setup,
 * the drawn summary assertions, the result row taps and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.LotteryResultsController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.testutil.ScreenFakeCampsites
import com.lambda.campsite.testutil.ScreenFakeLotteries
import com.lambda.campsite.testutil.watch
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [LotteryResultsScreen], the automatic draw's result
 * (US 02.05.01). They check the drawn summary and how the entrants ended up,
 * and that the Winners and Not-selected rows hand their taps back to the app.
 */
class LotteryResultsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val aspen = Campsite(id = "c1", ownerId = "user-1", name = "Aspen Hollow", location = "Hinton, AB")

    /** The taps the screen hands back, recorded separately per action. */
    private val clicked = mutableListOf<String>()

    /** The test's drawn lottery. */
    private val drawn = lottery()

    /** Draws the results screen over [entries], ending the loading state before returning. */
    private fun setContent(entries: List<LotteryEntry>, withLottery: Boolean = true) {
        val controller = LotteryResultsController(
            lotteryId = drawn.id,
            repository = ScreenFakeLotteries(
                initial = if (withLottery) listOf(drawn) else emptyList(),
                initialEntries = entries,
            ),
            campsiteRepository = ScreenFakeCampsites(initial = listOf(aspen)),
        )
        composeRule.setContent {
            LotteryResultsScreen(
                controller = controller,
                onBack = { clicked += "back" },
                onWinnersClick = { clicked += "winners" },
                onNotSelectedClick = { clicked += "notSelected" },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !controller.state.value.isLoading }
        composeRule.watch()
    }

    /** The drawn lot, closing registration Oct 19, 2026. */
    private fun lottery() = Lottery(
        id = "l1",
        ownerId = "user-1",
        name = "Aspen Hollow summer lottery",
        campsiteIds = listOf("c1"),
        registrationOpens = timestamp(LocalDate.of(2026, 10, 6)),
        registrationCloses = timestamp(LocalDate.of(2026, 10, 19)),
        stayStart = LocalDate.of(2027, 5, 15),
        stayEnd = LocalDate.of(2027, 9, 30),
        status = LotteryStatus.DRAWN,
    )

    @Test
    fun resultsScreenShowsTheDrawnSummaryAndHowEntrantsEndedUp() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent(
            entries = listOf(
                entry("e1", EntryStatus.WON),
                entry("e2", EntryStatus.NOT_SELECTED),
                entry("e3", EntryStatus.NOT_SELECTED),
            ),
        )

        composeRule.onNodeWithText("Aspen Hollow summer lottery").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.heading_lottery_summary)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lottery_results_note)).assertIsDisplayed()
        composeRule.onNodeWithText("Aspen Hollow").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_winners)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_not_selected)).assertIsDisplayed()
    }

    @Test
    fun tappingWinnersHandsTheWinnersRowToTheScreen() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent(
            entries = listOf(
                entry("e1", EntryStatus.WON),
                entry("e2", EntryStatus.NOT_SELECTED),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.action_winners)).performClick()
        composeRule.watch()
        composeRule.runOnIdle { assertEquals(listOf("winners"), clicked) }
    }

    @Test
    fun tappingNotSelectedHandsTheRowToTheScreen() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent(
            entries = listOf(
                entry("e1", EntryStatus.WON),
                entry("e2", EntryStatus.NOT_SELECTED),
            ),
        )

        composeRule.onAllNodesWithText(context.getString(R.string.action_not_selected)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.action_not_selected))[0].performClick()
        composeRule.watch()
        composeRule.runOnIdle { assertEquals(listOf("notSelected"), clicked) }
    }

    @Test
    fun aMissingLotteryShowsTheNotFoundMessage() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent(entries = emptyList(), withLottery = false)

        composeRule.onNodeWithText(context.getString(R.string.error_lottery_not_found)).assertIsDisplayed()
    }
}

/** The Firestore timestamp for midnight at the start of [date], UTC. */
private fun timestamp(date: LocalDate): Timestamp =
    Timestamp(java.util.Date.from(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))

/** A waiting-list entry for this test's lottery, graded by the draw. */
private fun entry(entrantId: String, status: EntryStatus): LotteryEntry = LotteryEntry(
    lotteryId = "l1",
    entrantId = entrantId,
    joinedAt = timestamp(LocalDate.of(2026, 10, 2)),
    status = status,
)
