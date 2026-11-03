/*
 * Feature owner: Lalith Valluru. Purpose: Flow 12 / US 02.09.01 presentation tests.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose assertions. Human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.firebase.Timestamp
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Selector, full IDs, sheet, back, retries and large-font scrolling for issue #129. */
class OwnerWaitingListScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lottery = Lottery("a", "owner", "October Camping Lottery", listOf("camp"),
        Timestamp(1, 0), Timestamp(2, 0), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8))
    private val entry = LotteryEntry("a", "sample-entrant-001", Timestamp(1792000000, 0))
    private fun state() = OwnerWaitingListUiState(lotteries = listOf(lottery, lottery.copy(id = "b", name = "November Lottery")),
        selectedLotteryId = "a", entries = listOf(entry), loadingLotteries = false)

    @Test fun selectorSheetCloseAndBackHaveActions() {
        val state = mutableStateOf(state())
        var selected: String? = null
        var backs = 0
        compose.setContent { LambdaCampsiteTheme { OwnerWaitingListContent(state.value,
            { selected = it }, {}, { state.value = state.value.copy(selectedEntrantId = it) },
            { state.value = state.value.copy(selectedEntrantId = null) }, { backs++ }) } }
        compose.onNodeWithText("1 entrant").assertIsDisplayed()
        compose.onNodeWithText("Entrant ID: sample-entrant-001").performClick()
        compose.onNodeWithText("Entrant details").assertIsDisplayed()
        compose.onNodeWithText("Status: On the waiting list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close entrant details").performClick()
        compose.onNodeWithText("Entrant details").assertDoesNotExist()
        compose.onNodeWithText(lottery.name).performClick()
        compose.onNodeWithText("November Lottery").performClick()
        compose.runOnIdle { assertEquals("b", selected) }
        compose.onNodeWithContentDescription("Back to Owner Home").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun loadingEmptyNoLotteriesAndFailureAreDifferentAndRetryWorks() {
        val state = mutableStateOf(OwnerWaitingListUiState())
        var retries = 0
        compose.setContent { LambdaCampsiteTheme { OwnerWaitingListContent(state.value, {}, { retries++ }, {}, {}, {}) } }
        compose.onNodeWithText("Loading your lotteries…").assertIsDisplayed()
        compose.runOnIdle { state.value = OwnerWaitingListUiState(loadingLotteries = false) }
        compose.onNodeWithText("You have no lotteries yet.").assertIsDisplayed()
        compose.runOnIdle { state.value = state().copy(entries = emptyList()) }
        compose.onNodeWithText("0 entrants").assertIsDisplayed()
        compose.onNodeWithText("No entrants on the waiting list.").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = OwnerWaitingListError.LOAD_ENTRIES) }
        compose.onNodeWithText("No entrants on the waiting list.").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun longIdsAndManyRowsRemainReachableAtDoubleFontSize() {
        val lastId = "sample-entrant-with-a-long-identifier-00000030"
        val entries = (1..29).map { entry.copy(entrantId = "sample-entrant-$it") } + entry.copy(entrantId = lastId)
        compose.setContent { LambdaCampsiteTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                OwnerWaitingListContent(state().copy(entries = entries), {}, {}, {}, {}, {})
            }
        } }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Entrant ID: $lastId"))
        compose.onNodeWithText("Entrant ID: $lastId").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription("Back to Owner Home").assertIsDisplayed()
    }

    @Test fun contactSheetScrollsAtDoubleFontAndMissingValuesAreExplicit() {
        val state = mutableStateOf(state().copy(selectedEntrantId = entry.entrantId,
            contacts = mapOf(entry.entrantId to WaitingListContact(entry.entrantId,
                "Alexandria Demo With A Long Display Name", "long.email.for.demo@example.com", null))))
        compose.setContent { LambdaCampsiteTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                OwnerWaitingListContent(state.value, {}, {}, {},
                    { state.value = state.value.copy(selectedEntrantId = null) }, {})
            }
        } }
        compose.onNodeWithText("Email: long.email.for.demo@example.com").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Phone: Not provided").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Status: On the waiting list").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close entrant details").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Phone: Not provided").assertDoesNotExist()
    }
}
