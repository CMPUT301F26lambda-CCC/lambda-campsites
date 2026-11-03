/*
 * Purpose: shared entrant lottery registration presentation for the pushed details screen.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: manual accessibility checks and team review remain for #124.
 * AI use: OpenAI GPT-6 through Codex helped write this UI and its KDoc.
 * Follow-up human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.controller.LotteryError
import com.lambda.campsite.controller.LotteryNotice
import com.lambda.campsite.controller.LotteryUiState
import com.lambda.campsite.model.Campsite

/**
 * Stateless lottery presentation; local and server rules remain in controller/model/repository.
 * @param state current displayed data, pending write and feedback.
 * @param onJoin requests joining the specified lottery.
 * @param onLeave requests leaving the specified lottery.
 * @param onRetry retries a failed data load without repeating a write.
 * @param onDismissError acknowledges a displayed action error.
 * @param onDismissNotice acknowledges a successful registration action.
 * @param modifier layout supplied by the host, which owns scrolling.
 * @param showTitle whether to show the legacy section heading outside the pushed screen.
 * @param showNotice whether success is inline; the pushed screen uses its SnackbarHost instead.
 * @param campsite selected campsite's readable name/location.
 */
@Composable
fun LotteryContent(
    state: LotteryUiState,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    showNotice: Boolean = true,
    campsite: Campsite? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTitle) Text(stringResource(R.string.entrant_lottery_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        if (state.isLoading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.entrant_lottery_loading))
        }
        state.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(error.messageResource()), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (error == LotteryError.LOAD_FAILED) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.entrant_lottery_retry)) }
                } else {
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.entrant_lottery_dismiss)) }
                }
            }
        }
        state.notice?.takeIf { showNotice }?.let { notice ->
            Text(stringResource(if (notice == LotteryNotice.JOINED) R.string.entrant_lottery_joined else R.string.entrant_lottery_left),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.entrant_lottery_dismiss)) }
        }
        if (!state.isLoading && state.error != LotteryError.LOAD_FAILED && state.lotteries.isEmpty()) {
            Text(stringResource(R.string.entrant_lottery_empty))
        }
        state.lotteries.forEach { lottery ->
            EntrantLotteryInfo(state, lottery, campsite, onJoin, onLeave)
        }
    }
}

private fun LotteryError.messageResource(): Int = when (this) {
    LotteryError.LOAD_FAILED -> R.string.entrant_lottery_load_failed
    LotteryError.NO_SUCH_LOTTERY -> R.string.entrant_lottery_missing
    LotteryError.REGISTRATION_CLOSED -> R.string.entrant_lottery_closed
    LotteryError.FULL -> R.string.entrant_lottery_full
    LotteryError.ALREADY_JOINED -> R.string.entrant_lottery_already_joined
    LotteryError.NOT_JOINED -> R.string.entrant_lottery_not_joined
    LotteryError.SAVE_FAILED -> R.string.entrant_lottery_save_failed
}
