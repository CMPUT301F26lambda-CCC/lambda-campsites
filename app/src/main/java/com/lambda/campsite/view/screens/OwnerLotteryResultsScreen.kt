/*
 * Purpose: owner-only live automatic lottery results (US 02.05.01 / Flow 5).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: deployment of draw metadata and manual TalkBack review remain pending (#122).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with the Figma results layout
 * and existing ownership-checked controller integration; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerWaitingListController
import com.lambda.campsite.controller.OwnerWaitingListUiState
import com.lambda.campsite.model.*
import com.lambda.campsite.view.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Reuses the ownership guards/listeners of Flow 12; never exposes a draw action. */
@Composable
fun OwnerLotteryResultsScreen(controller: OwnerWaitingListController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerLotteryResultsContent(state, controller::onRetry, onBack)
}

/** Original winners include people who subsequently booked or declined; their draw result is immutable. */
internal fun drawWinners(entries: List<LotteryEntry>): List<LotteryEntry> = entries.filter {
    it.status in setOf(EntryStatus.WON, EntryStatus.BOOKED, EntryStatus.DECLINED)
}

/** Stateless results and expandable entrant-ID lists, with loading distinct from zero entrants. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerLotteryResultsContent(state: OwnerWaitingListUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    StatusBarIconStyle(darkIcons = false)
    var group by remember(state.selectedLotteryId) { mutableStateOf<String?>(null) }
    val lottery = state.selectedLottery
    Scaffold(containerColor = ForestGreen, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.flow5_title), Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.flow5_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Mint, contentColor = CampOnSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    state.loadingLotteries || state.loadingEntries -> item { CircularProgressIndicator() }
                    state.error != null || lottery == null -> item {
                        Text(stringResource(R.string.flow5_load_error))
                        Button(onClick = onRetry) { Text(stringResource(R.string.flow5_retry)) }
                    }
                    else -> {
                        item { Text(lottery.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
                        item {
                            Surface(color = CampPositive, shape = RoundedCornerShape(8.dp)) {
                                val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
                                Text(when (lottery.status) {
                                    LotteryStatus.DRAWN -> lottery.drawnAt?.let { stringResource(R.string.flow5_drawn, time.format(it.toDate().toInstant())) }
                                        ?: stringResource(R.string.flow5_draw_time_unknown)
                                    LotteryStatus.OPEN, LotteryStatus.UPCOMING -> stringResource(R.string.flow5_waiting)
                                    LotteryStatus.ENDED -> stringResource(R.string.flow5_ended)
                                }, Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                        item { Text(stringResource(if (lottery.status == LotteryStatus.DRAWN) R.string.flow5_automatic else R.string.flow5_pending)) }
                        item {
                            Surface(color = CampSurfaceHigh, shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.flow5_summary), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                    Text(if (lottery.drawnCampsiteNames.isNotEmpty()) lottery.drawnCampsiteNames.joinToString(" · ")
                                        else stringResource(R.string.flow5_campsite_ids, lottery.campsiteIds.joinToString(" · ")))
                                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                                    Text(stringResource(R.string.flow5_registration, date.format(lottery.registrationOpens.toDate().toInstant()), date.format(lottery.registrationCloses.toDate().toInstant())))
                                    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    Text(stringResource(R.string.flow5_stay, lottery.stayStart.format(day), lottery.stayEnd.format(day)))
                                }
                            }
                        }
                        if (lottery.status == LotteryStatus.DRAWN) {
                            val winners = drawWinners(state.entries)
                            val others = state.entries.filter { it.status == EntryStatus.NOT_SELECTED }
                            item { Text(stringResource(R.string.flow5_results), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                            if (state.entries.isEmpty()) item { Text(stringResource(R.string.flow5_no_entrants), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                            item { ResultGroup(R.string.flow5_winners, winners.size, true, group == "winners") { group = if (group == "winners") null else "winners" } }
                            item { ResultGroup(R.string.flow5_not_selected, others.size, false, group == "others") { group = if (group == "others") null else "others" } }
                            if (group != null) {
                                val entries = if (group == "winners") winners else others
                                if (entries.isEmpty()) item { Text(stringResource(R.string.flow5_no_group)) }
                                items(entries, key = { it.entrantId }) { entry ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.flow5_entrant, entry.entrantId))
                                        Text(stringResource(when (entry.status) {
                                            EntryStatus.BOOKED -> R.string.flow5_booked
                                            EntryStatus.DECLINED -> R.string.flow5_declined
                                            EntryStatus.WON -> R.string.flow5_won
                                            else -> R.string.flow5_not_selected
                                        }), color = CampSecondaryText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultGroup(label: Int, count: Int, winner: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val expansion = stringResource(if (expanded) R.string.flow5_expanded else R.string.flow5_collapsed)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onClick)
        .semantics { stateDescription = expansion }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = if (winner) Color(0xFFFFDBCD) else CampField) {
            Icon(if (winner) Icons.Default.Star else Icons.Default.PersonOutline, null, Modifier.size(40.dp).padding(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.flow5_count, count), color = CampSecondaryText)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}
