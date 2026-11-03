/*
 * Purpose: Flow 7's embeddable lottery registration UI.
 * Outstanding issues: manual accessibility checks and team review remain for #124.
 * AI use: OpenAI GPT-6 through Codex helped write this UI and its KDoc.
 * Human review is pending; this file has not been published.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.LotteryController
import com.lambda.campsite.controller.LotteryError
import com.lambda.campsite.controller.LotteryNotice
import com.lambda.campsite.controller.LotteryUiState
import com.lambda.campsite.model.EntryStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Lottery section for campsite details (US 01.01.06, 01.02.01, 01.02.02).
 * The host supplies scrolling and the shared header/navigation. This section has no Scaffold
 * or Firestore calls. Create [controller] with a campsite-specific ViewModel key/factory.
 * @param controller observes that campsite's lotteries and handles registration actions.
 * @param modifier layout supplied by the campsite-details host.
 */
@Composable
fun LotteryScreen(controller: LotteryController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    LotteryContent(
        state = state, onJoin = controller::onJoin, onLeave = controller::onLeave,
        onRetry = controller::onRetry, onDismissError = controller::onErrorShown,
        onDismissNotice = controller::onNoticeShown, modifier = modifier,
    )
}

/**
 * Stateless lottery presentation; local and server rules remain in controller/model/repository.
 * @param state current displayed data, pending write and feedback.
 * @param onJoin requests joining the specified lottery.
 * @param onLeave requests leaving the specified lottery.
 * @param onRetry retries a failed data load without repeating a write.
 * @param onDismissError acknowledges a displayed action error.
 * @param onDismissNotice acknowledges a successful registration action.
 * @param modifier layout supplied by the host, which owns scrolling.
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
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.flow7_title), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        if (state.isLoading) {
            CircularProgressIndicator()
            Text(stringResource(R.string.flow7_loading))
        }
        state.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(error.messageResource()), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (error == LotteryError.LOAD_FAILED) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.flow7_retry)) }
                } else {
                    TextButton(onClick = onDismissError) { Text(stringResource(R.string.flow7_dismiss)) }
                }
            }
        }
        state.notice?.let { notice ->
            Text(stringResource(if (notice == LotteryNotice.JOINED) R.string.flow7_joined else R.string.flow7_left),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = onDismissNotice) { Text(stringResource(R.string.flow7_dismiss)) }
        }
        if (!state.isLoading && state.error != LotteryError.LOAD_FAILED && state.lotteries.isEmpty()) {
            Text(stringResource(R.string.flow7_empty))
        }
        state.lotteries.forEach { lottery ->
            val entry = state.entries.firstOrNull { it.lotteryId == lottery.id }
            val enabled = !state.isLoading && state.pendingLotteryId == null && state.error != LotteryError.LOAD_FAILED
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(lottery.name, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() })
                    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
                    Text(stringResource(R.string.flow7_registration,
                        formatter.format(lottery.registrationOpens.toDate().toInstant()),
                        formatter.format(lottery.registrationCloses.toDate().toInstant()),
                        ZoneId.systemDefault().id))
                    Text(stringResource(R.string.flow7_stays, lottery.stayStart.toString(), lottery.stayEnd.toString()))
                    entry?.let {
                        Text(stringResource(when (it.status) {
                            EntryStatus.WAITING -> R.string.flow7_waiting
                            EntryStatus.WON -> R.string.flow7_won
                            EntryStatus.NOT_SELECTED -> R.string.flow7_not_selected
                            EntryStatus.DECLINED -> R.string.flow7_declined
                            EntryStatus.BOOKED -> R.string.flow7_booked
                        }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (state.pendingLotteryId == lottery.id) {
                        Text(stringResource(R.string.flow7_saving), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (entry == null) {
                        Button(onClick = { onJoin(lottery.id) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.flow7_join))
                        }
                    } else if (entry.status == EntryStatus.WAITING) {
                        OutlinedButton(onClick = { onLeave(lottery.id) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.flow7_leave))
                        }
                    }
                }
            }
        }
    }
}

private fun LotteryError.messageResource(): Int = when (this) {
    LotteryError.LOAD_FAILED -> R.string.flow7_load_failed
    LotteryError.NO_SUCH_LOTTERY -> R.string.flow7_missing
    LotteryError.REGISTRATION_CLOSED -> R.string.flow7_closed
    LotteryError.FULL -> R.string.flow7_full
    LotteryError.ALREADY_JOINED -> R.string.flow7_already_joined
    LotteryError.NOT_JOINED -> R.string.flow7_not_joined
    LotteryError.SAVE_FAILED -> R.string.flow7_save_failed
}
