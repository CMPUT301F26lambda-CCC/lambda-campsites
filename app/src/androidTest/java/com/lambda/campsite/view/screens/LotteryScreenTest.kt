/*
 * Purpose: isolated Compose tests for Flow 7's join/leave panel (US 01.01.06–01.02.02).
 * Outstanding issue: full campsite navigation and Firestore integration remain in #124.
 * AI use: OpenAI GPT-6 through Codex helped write the UI tests. Human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.LotteryError
import com.lambda.campsite.controller.LotteryNotice
import com.lambda.campsite.controller.LotteryUiState
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Checks visible feedback, action routing and duplicate-tap prevention without Firebase writes. */
class LotteryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val item = Lottery("lottery-demo", "owner-demo", "Aspen demo lottery", listOf("camp-demo"),
        Timestamp(1000, 0), Timestamp(2000, 0), LocalDate.of(2027, 7, 1), LocalDate.of(2027, 7, 8))
    private val state = mutableStateOf(LotteryUiState(lotteries = listOf(item), isLoading = false))
    private var joined: String? = null
    private var left: String? = null
    private var retries = 0
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun launch() {
        compose.setContent {
            LambdaCampsiteTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LotteryContent(state.value,
                        onJoin = { joined = it }, onLeave = { left = it }, onRetry = { retries++ },
                        onDismissError = { state.value = state.value.copy(error = null) },
                        onDismissNotice = { state.value = state.value.copy(notice = null) })
                }
            }
        }
    }

    @Test fun joinAndLeaveRouteTheCorrectLotteryAndShowAcknowledgements() {
        launch()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(item.id, joined)
            state.value = state.value.copy(
                entries = listOf(LotteryEntry(item.id, "entrant-demo", Timestamp(1500, 0))),
                notice = LotteryNotice.JOINED)
        }
        compose.onNodeWithText(text(R.string.flow7_joined)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow7_leave)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(item.id, left)
            state.value = state.value.copy(entries = emptyList(), notice = LotteryNotice.LEFT)
        }
        compose.onNodeWithText(text(R.string.flow7_left)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().assertIsEnabled()
    }

    @Test fun pendingWriteDisablesJoinAndErrorIsReadable() {
        state.value = state.value.copy(pendingLotteryId = item.id)
        launch()
        compose.onNodeWithText(text(R.string.flow7_join)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(pendingLotteryId = null, error = LotteryError.FULL) }
        compose.onNodeWithText(text(R.string.flow7_full)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow7_dismiss)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow7_full)).assertDoesNotExist()
    }

    @Test fun failedLoadOffersRetryAndEmptyStateHasNoJoinButton() {
        state.value = LotteryUiState(isLoading = false, error = LotteryError.LOAD_FAILED)
        launch()
        compose.onNodeWithText(text(R.string.flow7_retry)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries); state.value = LotteryUiState(isLoading = false) }
        compose.onNodeWithText(text(R.string.flow7_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow7_join)).assertDoesNotExist()
    }

    @Test fun drawnEntryShowsOutcomeWithoutLeaveAction() {
        state.value = state.value.copy(entries = listOf(LotteryEntry(item.id, "entrant-demo", Timestamp(1500, 0), EntryStatus.WON)))
        launch()
        compose.onNodeWithText(text(R.string.flow7_won)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow7_leave)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow7_join)).assertDoesNotExist()
    }
}
