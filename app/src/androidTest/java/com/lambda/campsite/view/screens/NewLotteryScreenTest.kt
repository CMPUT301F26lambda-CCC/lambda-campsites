/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the Compose test setup,
 * the wizard's step-by-step interactions, the calendar picker test, the
 * end-to-end create path and KDoc. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lambda.campsite.R
import com.lambda.campsite.controller.NewLotteryController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.testutil.ScreenFakeCampsites
import com.lambda.campsite.testutil.ScreenFakeLotteries
import com.lambda.campsite.testutil.ScreenFakeProfile
import com.lambda.campsite.testutil.watch
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [NewLotteryScreen], the four-step "New lottery" wizard
 * (US 02.04.01 to 02.04.07). They walk the step headers, refuse files without
 * selections, and run the main path from a picked campsite to a created
 * lottery.
 */
class NewLotteryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campsite = Campsite(id = "c1", ownerId = "user-1", name = "Aspen Hollow", location = "Hinton, AB")

    /** The test's controller, built in [setContent]. */
    private lateinit var controller: NewLotteryController

    /** The repository the wizard saves into, built alongside the controller. */
    private lateinit var lotteries: ScreenFakeLotteries

    /** The IDs the screen handed back after a create, in order. */
    private val createdIds = mutableListOf<String>()

    /** Draws the wizard whose owner owns [campsites]. */
    private fun setContent(campsites: List<Campsite> = listOf(campsite)) {
        lotteries = ScreenFakeLotteries()
        controller = NewLotteryController(
            lotteryRepository = lotteries,
            campsiteRepository = ScreenFakeCampsites(initial = campsites),
            profileRepository = ScreenFakeProfile(),
        )
        composeRule.setContent {
            NewLotteryScreen(
                controller = controller,
                onClose = { },
                onCreated = { lottery: Lottery -> createdIds += lottery.id },
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { !controller.state.value.isLoadingCampsites }
        composeRule.watch()
    }

    /** The text of the step header for step [step], like "Step 1 of 4 · Campsites". */
    private fun stepHeader(step: Int): String {
        val context: Context = ApplicationProvider.getApplicationContext()
        val stepName = when (step) {
            1 -> context.getString(R.string.new_lottery_step_campsites)
            2 -> context.getString(R.string.new_lottery_step_dates)
            3 -> context.getString(R.string.new_lottery_step_settings)
            else -> context.getString(R.string.new_lottery_step_review)
        }
        return String.format(context.getString(R.string.new_lottery_step_format), step, 4, stepName)
    }

    /** Sets the registration and booking periods step 2 asks for. */
    private fun setDates(controller: NewLotteryController) {
        val opens = LocalDate.of(2026, 10, 6)
        val closes = LocalDate.of(2026, 10, 18)
        val checkIn = LocalDate.of(2027, 5, 15)
        val checkOut = LocalDate.of(2027, 9, 30)
        controller.onRegistrationOpensChange(opens)
        controller.onRegistrationClosesChange(closes)
        controller.onStayStartChange(checkIn)
        controller.onStayEndChange(checkOut)
    }

    @Test
    fun wizardOpensOnTheCampsitesStep() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent()

        composeRule.onNodeWithText(stepHeader(1)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_lottery_select_campsites)).assertIsDisplayed()
        composeRule.onNodeWithText(campsite.name).assertIsDisplayed()
    }

    @Test
    fun continueStepsWithoutCampsitesRefusesAndSaysWhy() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent()

        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()
        composeRule.onNodeWithText(context.getString(R.string.error_lottery_no_campsites)).assertIsDisplayed()
        composeRule.onNodeWithText(stepHeader(1)).assertIsDisplayed()
    }

    @Test
    fun pickingACampsiteAndContinuingMovesToTheDatesStep() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent()

        composeRule.onNodeWithText(campsite.name).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()
        composeRule.onNodeWithText(stepHeader(2)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_lottery_registration_period)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_lottery_booking_period)).assertIsDisplayed()
    }

    @Test
    fun aDateFieldOpensTheCalendarPickerAndCancelClosesIt() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setContent()
        composeRule.onNodeWithText(campsite.name).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()

        composeRule.onNodeWithText(context.getString(R.string.new_lottery_registration_opens)).performClick()
        composeRule.watch()
        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        composeRule.watch()

        // Back on the dates step with no date planted: all four fields read "Pick a date".
        composeRule.onNodeWithText(stepHeader(2)).assertIsDisplayed()
        assertEquals(
            4,
            composeRule.onAllNodesWithText(context.getString(R.string.new_lottery_pick_date)).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theWholeWizardCreatesTheLotteryAndHandsItBack() {
        val context: Context = ApplicationProvider.getApplicationContext()

        setContent()
        composeRule.onNodeWithText(campsite.name).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()

        composeRule.runOnIdle { setDates(controller) }
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()
        composeRule.onNodeWithText(stepHeader(3)).assertIsDisplayed()
        composeRule.watch()

        composeRule.onNodeWithText(context.getString(R.string.new_lottery_name_required)).performTextInput(
            "Aspen Hollow summer lottery",
        )
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.watch()
        composeRule.onNodeWithText(stepHeader(4)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.new_lottery_review_campsites)).assertIsDisplayed()
        composeRule.watch()

        composeRule.onNodeWithText(context.getString(R.string.create_lottery_button)).performClick()
        composeRule.watch()
        composeRule.waitUntil(timeoutMillis = 5_000) { lotteries.saved.isNotEmpty() }

        val saved = lotteries.saved.single()
        assertEquals("Aspen Hollow summer lottery", saved.name)
        assertEquals(listOf(campsite.id), saved.campsiteIds)
        assertEquals(20, saved.numberOfWinners)
        assertEquals(150, saved.capacity)
        composeRule.runOnIdle { assertEquals(listOf(saved.id), createdIds) }
    }

    @Test
    fun reviewStepShowsUpEditButtonsThatReturnToAStep() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setContent()
        composeRule.onNodeWithText(campsite.name).performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.runOnIdle { setDates(controller) }
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.new_lottery_name_required)).performTextInput(
            "Aspen Hollow summer lottery",
        )
        composeRule.onNodeWithText(context.getString(R.string.action_continue)).performClick()
        composeRule.onNodeWithText(stepHeader(4)).assertIsDisplayed()

        composeRule.onAllNodesWithText(context.getString(R.string.action_edit))[0].performClick()
        composeRule.watch()

        composeRule.onNodeWithText(stepHeader(1)).assertIsDisplayed()
    }
}
