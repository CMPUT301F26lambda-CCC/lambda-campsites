/*
 * Purpose: Flow 9 selection, review, decline and confirmation UI (US 01.04.01–03, 01.05.01).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack review remains pending (#126).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with UI regression tests; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Taps the real booking screen/controller with fakes, including large-text scrolling. */
class WinnerBookingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2027, 7, 1)
    private val camp = Campsite("camp", "owner", "Aspen Hollow", "Alberta", maxOccupancy = 4, rules = "Keep fires in the fire pit.")
    private val lottery = Lottery("lot", "owner", "Demo summer lottery", listOf(camp.id), Timestamp(1, 0), Timestamp(2, 0),
        today, today.plusDays(10), status = LotteryStatus.DRAWN)
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun controller(multi: Boolean = false, status: EntryStatus = EntryStatus.WON): WinnerBookingController {
        val second = camp.copy(id = "second", name = "Cedar Point")
        return WinnerBookingController(lottery.id,
            FakeLotteryRepository(initial = listOf(if (multi) lottery.copy(campsiteIds = listOf(camp.id, second.id)) else lottery),
                initialEntries = listOf(LotteryEntry(lottery.id, "user-1", Timestamp(1, 0), status))),
            FakeCampsiteRepository(initial = listOf(camp, second), initialCalendars = mapOf(camp.id to Availability(today, today.plusDays(10)), second.id to Availability(today, today.plusDays(10)))),
            FakeReservationRepository(campsites = listOf(camp, second), wins = listOf(lottery.id))) { today }
    }
    private fun launch(control: WinnerBookingController, large: Boolean = false) {
        compose.setContent { LambdaCampsiteTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
                WinnerBookingScreen(control, {}, {})
            }
        } }
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun chooseDatesReviewRulesAndSavedConfirmationAtLargeFont() {
        val control = controller()
        launch(control, large = true)
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        compose.onNodeWithContentDescription(today.format(format), substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription(today.plusDays(2).format(format), substring = true).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_continue)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_book)).performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_book)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_all_set)).assertExists()
        compose.onNodeWithText("Reservation number:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow9_done)).performScrollTo().assertIsDisplayed()
        assertNotNull(control.state.value.receipt)
        screenshot("flow9-confirmed-large.png")
    }
    @Test fun multipleSitesAreSelectableAndDatesScreenKeepsSelection() {
        val control = controller(multi = true)
        launch(control)
        compose.onAllNodes(isSelectable()).onLast().performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_continue)).performScrollTo().performClick()
        assertEquals("second", control.state.value.selectedCampsiteId)
        screenshot("flow9-calendar.png")
        compose.onNodeWithText("Cedar Point · Alberta").assertIsDisplayed()
    }
    @Test fun declineCanBeCancelledThenConfirmed() {
        val control = controller()
        launch(control)
        compose.onNodeWithText(text(R.string.flow9_decline)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow9_cancel)).performClick()
        assertNotEquals(WinnerBookingStep.DECLINED, control.state.value.step)
        compose.onNodeWithText(text(R.string.flow9_decline)).performClick()
        compose.onAllNodesWithText(text(R.string.flow9_decline)).onLast().performClick()
        compose.onNodeWithText(text(R.string.flow9_declined)).assertIsDisplayed()
        assertNull(control.state.value.receipt)
    }
    @Test fun nonWinnerCannotSeeCalendarOrConfirmButton() {
        launch(controller(status = EntryStatus.NOT_SELECTED))
        compose.onNodeWithText(text(R.string.flow9_winner_only)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow9_book)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.flow9_next_month)).assertDoesNotExist()
    }
}
