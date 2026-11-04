/*
 * Purpose: results groups, zero entrants, pending/error states and large text for US 02.05.01.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack review remains pending (#122).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with UI cases; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests actual result semantics rather than a manual drawing action. */
class OwnerLotteryResultsScreenTest {
    @get:Rule val compose=createComposeRule()
    private val lottery=Lottery("lot","owner","Demo lottery",listOf("camp"),Timestamp(1,0),Timestamp(2,0),
        LocalDate.of(2027,7,1),LocalDate.of(2027,7,10),status=LotteryStatus.DRAWN,drawnAt=Timestamp(3,0),drawnCampsiteNames=listOf("Demo Campsite"))
    private fun text(id:Int)=InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun state(entries:List<LotteryEntry> = emptyList())=OwnerWaitingListUiState(lotteries=listOf(lottery),selectedLotteryId=lottery.id,entries=entries,loadingLotteries=false)
    @Test fun winnerGroupIncludesBookedAndDeclinedHistoryAndSeparateNonSelected() {
        val entries=listOf(EntryStatus.WON,EntryStatus.BOOKED,EntryStatus.DECLINED,EntryStatus.NOT_SELECTED).mapIndexed{ i,s->LotteryEntry(lottery.id,"entrant-$i",Timestamp(1,0),s)}
        assertEquals(3,drawWinners(entries).size)
        compose.setContent{LambdaCampsiteTheme{OwnerLotteryResultsContent(state(entries),{},{})}}
        compose.onNodeWithText(text(R.string.flow5_winners)).performScrollTo().performClick()
        compose.onNodeWithText("Entrant ID: entrant-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Entrant ID: entrant-3").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow5_not_selected)).performScrollTo().performClick()
        compose.onNodeWithText("Entrant ID: entrant-3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Entrant ID: entrant-0").assertDoesNotExist()
    }
    @Test fun zeroEntrantsAreExplicitAndLoadingOrFailureNeverClaimAZeroDraw() {
        val current=mutableStateOf(state())
        compose.setContent{LambdaCampsiteTheme{OwnerLotteryResultsContent(current.value,{},{})}}
        compose.onNodeWithText(text(R.string.flow5_no_entrants)).assertExists()
        compose.runOnIdle{current.value=state().copy(loadingEntries=true)}
        compose.onNodeWithText(text(R.string.flow5_no_entrants)).assertDoesNotExist()
        compose.runOnIdle{current.value=state().copy(error=OwnerWaitingListError.ACCESS_DENIED)}
        compose.onNodeWithText(text(R.string.flow5_load_error)).assertIsDisplayed()
    }
    @Test fun pendingLotteryHasNoWinnerListAndNoManualDrawButton() {
        compose.setContent{LambdaCampsiteTheme{OwnerLotteryResultsContent(state().copy(lotteries=listOf(lottery.copy(status=LotteryStatus.OPEN))),{},{})}}
        compose.onNodeWithText(text(R.string.flow5_pending)).assertExists()
        compose.onNodeWithText(text(R.string.flow5_winners)).assertDoesNotExist()
        compose.onNodeWithText("Draw Winners").assertDoesNotExist()
    }
    @Test fun deletingAnEntryDoesNotRewriteCompletedDrawCountsOrClaimZeroWinners() {
        val snapshot=lottery.copy(drawnWinnerCount=1,drawnEntryCount=2)
        compose.setContent{LambdaCampsiteTheme{OwnerLotteryResultsContent(state().copy(lotteries=listOf(snapshot)),{},{})}}
        compose.onNodeWithText(text(R.string.flow5_no_entrants)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow5_removed_entries)).assertExists()
        compose.onAllNodesWithText("1 entrant").assertCountEquals(2)
    }
    @Test fun resultsRemainScrollableAtTwoHundredPercentText() {
        compose.setContent{LambdaCampsiteTheme{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){OwnerLotteryResultsContent(state(),{},{})}
        }}
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text(R.string.flow5_not_selected)))
        compose.onNodeWithText(text(R.string.flow5_not_selected)).assertIsDisplayed()
    }
}
