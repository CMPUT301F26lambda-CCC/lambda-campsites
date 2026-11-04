/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the Compose test setup,
 * finding text by resource, the controller-backed screen content and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerLotteryListController
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.testutil.ScreenFakeLotteries
import com.lambda.campsite.testutil.watch
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [OwnerLotteryListScreen], the owner's lotteries tab
 * (US 02.04.01). They test the screen's main paths with the screen backed by
 * its real controller and an in-memory repository, so no emulator data is
 * needed to see it draw.
 */
class OwnerLotteryListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The test's controller, built in [setContent]. */
    private lateinit var controller: OwnerLotteryListController

    /** The lotteries tapped, recorded by the screen's click callback. */
    private val tapped = mutableListOf<Lottery>()

    /** Draws the screen over [lotteries], ending the loading state before returning. */
    private fun setContent(lotteries: List<Lottery>) {
        val repository = ScreenFakeLotteries(initial = lotteries)
        val now = timestamp(LocalDate.of(2026, 10, 15))
        controller = OwnerLotteryListController(repository, now = { now })
        composeRule.setContent {
            OwnerLotteryListScreen(
                controller = controller,
                onLotteryClick = { tapped += it },
                onNewLotteryClick = { },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !controller.state.value.isLoading }
        composeRule.watch()
    }

    @Test
    fun listScreenShowsLotteriesUnderTheirSectionHeadings() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val upcoming = lottery("upcoming", opens = LocalDate.of(2026, 10, 20))
        val completed = lottery("drawn", status = LotteryStatus.DRAWN)

        setContent(lotteries = listOf(lottery("active"), upcoming, completed))

        composeRule.onNodeWithText(context.getString(R.string.lotteries_section_active)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lotteries_section_upcoming)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.lotteries_section_completed)).assertIsDisplayed()
        // Each lottery shows by its name, whatever bucket it sits under.
        composeRule.onNodeWithText("Lottery active").assertIsDisplayed()
        composeRule.onNodeWithText("Lottery upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Lottery drawn").assertIsDisplayed()
    }

    @Test
    fun anEmptySectionLeavesOnlyItsHeading() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent(lotteries = listOf(lottery("active")))

        composeRule.onNodeWithText(context.getString(R.string.lotteries_section_active)).assertIsDisplayed()
        composeRule.onNodeWithText("Lottery active").assertIsDisplayed()
    }

    @Test
    fun tappingLotteryRowHandsTheLotteryToTheScreen() {
        val active = lottery("active")

        setContent(lotteries = listOf(active))

        composeRule.onNodeWithText("Lottery active").performClick()
        composeRule.watch()
        composeRule.runOnIdle { assertEquals(listOf(active), tapped) }
    }
}

/** An OPEN lottery that opened at 2026-10-01 and closes 2026-11-01, unless values are given. */
private fun lottery(
    id: String,
    opens: LocalDate = LocalDate.of(2026, 10, 1),
    status: LotteryStatus = LotteryStatus.OPEN,
) = Lottery(
    id = id,
    ownerId = "user-1",
    name = "Lottery $id",
    campsiteIds = listOf("c1"),
    registrationOpens = timestamp(opens),
    registrationCloses = timestamp(LocalDate.of(2026, 11, 1)),
    stayStart = LocalDate.of(2027, 5, 15),
    stayEnd = LocalDate.of(2027, 9, 30),
    status = status,
)

/** The Firestore timestamp for midnight at the start of [date], UTC. */
private fun timestamp(date: LocalDate): Timestamp =
    Timestamp(java.util.Date.from(date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))
