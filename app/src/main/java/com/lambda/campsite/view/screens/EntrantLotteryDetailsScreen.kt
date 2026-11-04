/*
 * Purpose: pushed entrant lottery details and campsite-to-lottery navigation (US 01.01.06–01.02.02).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: Manual TalkBack and physical-device review remain pending.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with Figma layout, registration states
 * and navigation callbacks. Human review pending.
  * Layout follow-up (#154): Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI),
 * through Codex, assisted with Figma layout/status/navigation-color integration
 * or its regression checks. Follow-up human review remains pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.*
import com.lambda.campsite.view.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Live read-only status for search cards and the campsite header; never treats a failed load as no lottery. */
@Composable
fun CampsiteLotteryBadge(controller: LotteryController, showNoActiveHint: Boolean = false) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onRefreshTime() }
    val active = state.lotteries.any { it.isRegistrationOpen(state.observedAt) }
    val label = when {
        state.isLoading -> R.string.campsite_status_loading
        state.error == LotteryError.LOAD_FAILED -> R.string.campsite_status_unknown
        active -> R.string.campsite_open_badge
        else -> R.string.campsite_no_active_badge
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.lambda.campsite.view.components.CampsiteStatusChip(stringResource(label), positive = active)
        if (showNoActiveHint && !state.isLoading && state.error != LotteryError.LOAD_FAILED && !active)
            Text(stringResource(R.string.entrant_campsite_no_active), style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
    }
}

/** Opens a read-only lottery summary from campsite details; registration happens on the pushed screen. */
@Composable
fun CampsiteLotteryLinks(controller: LotteryController, onLotteryClick: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onRefreshTime() }
    when {
        state.isLoading -> Text(stringResource(R.string.entrant_lottery_loading))
        state.error == LotteryError.LOAD_FAILED -> {
            Text(stringResource(R.string.entrant_lottery_load_failed))
            TextButton(onClick = controller::onRetry) { Text(stringResource(R.string.entrant_lottery_retry)) }
        }
        else -> {
            val active = state.lotteries.filter { it.isRegistrationOpen(state.observedAt) }
            active.forEach { lottery ->
                Text(lottery.name, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onLotteryClick(lottery.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.entrant_campsite_join_active))
                }
            }
        }
    }
}

/**
 * Figma's pushed Lottery screen. The parent owns bottom navigation and the selected campsite stack.
 * @param controller observes current lotteries/entry state and performs guarded transactions.
 * @param campsiteController observes the selected campsite's name/location and availability.
 * @param lotteryId destination selected from Join active lottery.
 * @param onBack returns to the same campsite details screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrantLotteryDetailsScreen(controller: LotteryController, campsiteController: CampsiteController,
    lotteryId: String, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val campsite by campsiteController.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onRefreshTime() }
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.notice, currentContext) {
        state.notice?.let { notice ->
            snackbar.showSnackbar(currentContext.getString(if (notice == LotteryNotice.JOINED) R.string.entrant_lottery_joined else R.string.entrant_lottery_left))
            controller.onNoticeShown()
        }
    }
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = ForestGreen, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.entrant_lottery_details_title)) }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.entrant_lottery_details_back))
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen,
            titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Mint, contentColor = CampOnSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                if (!state.isLoading && state.error != LotteryError.LOAD_FAILED && state.lotteries.none { it.id == lotteryId }) {
                    Text(stringResource(R.string.entrant_lottery_missing))
                } else if (campsite.error == CampsiteDetailsError.LOAD_FAILED) {
                    CampsiteLoadError(campsiteController::onRetry)
                } else if (!campsite.isLoading && campsite.campsite == null) {
                    Text(stringResource(R.string.entrant_campsite_not_available))
                } else {
                    LotteryContent(state.copy(lotteries = state.lotteries.filter { it.id == lotteryId }),
                        controller::onJoin, controller::onLeave, controller::onRetry, controller::onErrorShown,
                        controller::onNoticeShown, showTitle = false, showNotice = false, campsite = campsite.campsite, feedback = { SnackbarHost(snackbar) })
                }
            }
        }
    }
}

/** Shared details body and guarded actions; used by the screen and isolated presentation tests. */
@Composable
internal fun EntrantLotteryInfo(state: LotteryUiState, lottery: Lottery, campsite: Campsite?,
    onJoin: (String) -> Unit, onLeave: (String) -> Unit, feedback: @Composable () -> Unit = {}) {
    var confirming by remember(lottery.id) { mutableStateOf(false) }
    val entry = state.entries.firstOrNull { it.lotteryId == lottery.id }
    val block = state.joinBlock(lottery.id)
    val label = if (entry?.status == EntryStatus.WAITING) R.string.entrant_lottery_waiting else when (block) {
        null -> R.string.entrant_lottery_status_open
        LotteryJoinBlock.UPCOMING -> R.string.entrant_lottery_status_upcoming
        LotteryJoinBlock.CLOSED -> R.string.entrant_lottery_status_closed
        LotteryJoinBlock.FULL -> R.string.entrant_lottery_status_full
        LotteryJoinBlock.DRAWN -> R.string.entrant_lottery_status_drawn
        LotteryJoinBlock.ENDED -> R.string.entrant_lottery_status_ended
        LotteryJoinBlock.UNAVAILABLE -> R.string.entrant_lottery_missing
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(lottery.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        campsite?.let { camp -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Landscape, null, Modifier.size(20.dp), tint = CampSecondaryText)
            Text(stringResource(R.string.entrant_lottery_campsite_summary, camp.name, camp.location),
                style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
        } }
        Surface(color = if (block == LotteryJoinBlock.FULL) MaterialTheme.colorScheme.errorContainer
            else if (block == null || entry?.status == EntryStatus.WAITING) CampPositive else CampField,
            shape = RoundedCornerShape(8.dp)) {
            Text(stringResource(label), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider(color = CampOutline)
        LotterySectionHeading(R.string.entrant_lottery_registration_heading)
        LotteryInfoRow(Icons.Outlined.Event, R.string.entrant_lottery_opens_label, registrationDate(lottery.registrationOpens))
        LotteryInfoRow(Icons.Outlined.Event, R.string.entrant_lottery_deadline_label, lastJoinDate(lottery))
        LotterySectionHeading(R.string.entrant_lottery_booking_heading)
        val endFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val startFormat = if (lottery.stayStart.year == lottery.stayEnd.year)
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) else endFormat
        LotteryInfoRow(Icons.Outlined.Event, R.string.entrant_lottery_stay_dates_label,
            "${lottery.stayStart.format(startFormat)} – ${lottery.stayEnd.format(endFormat)}")
        Text(stringResource(R.string.entrant_lottery_booking_hint), style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
        LotterySectionHeading(R.string.entrant_lottery_winners_heading)
        LotteryInfoRow(Icons.Outlined.Person, R.string.entrant_lottery_winner_count_label, lottery.numberOfWinners.toString())
        LotteryInfoRow(Icons.Outlined.Person, R.string.entrant_lottery_count_label, lottery.capacity?.let {
            stringResource(R.string.entrant_lottery_count_limited, lottery.entryCount, it)
        } ?: stringResource(R.string.entrant_lottery_count_unlimited, lottery.entryCount))
        Text(stringResource(R.string.entrant_lottery_draw_automatic), style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
        feedback()
        Spacer(Modifier.height(4.dp))
        if (entry == null) {
            Button(onClick = { onJoin(lottery.id) }, enabled = state.canJoin(lottery.id),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.entrant_lottery_join)) }
            block?.let { Text(joinBlockText(it, lottery), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        } else if (entry.status == EntryStatus.WAITING) {
            FilledTonalButton(onClick = { confirming = true }, enabled = state.canWithdraw(lottery.id),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = CampPositive, contentColor = CampOnPositive),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.entrant_lottery_leave)) }
            Text(if (state.canWithdraw(lottery.id)) stringResource(R.string.entrant_lottery_withdraw_before, lastJoinDate(lottery))
                else stringResource(R.string.entrant_lottery_withdraw_closed),
                style = MaterialTheme.typography.bodyMedium)
        } else Text(stringResource(when (entry.status) {
            EntryStatus.WON -> R.string.entrant_lottery_won
            EntryStatus.NOT_SELECTED -> R.string.entrant_lottery_not_selected
            EntryStatus.DECLINED -> R.string.entrant_lottery_declined
            EntryStatus.BOOKED -> R.string.entrant_lottery_booked
            EntryStatus.WAITING -> R.string.entrant_lottery_waiting
        }))
        if (state.pendingLotteryId == lottery.id) Text(stringResource(R.string.entrant_lottery_saving))
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        containerColor = CampSurfaceHigh,
        title = { Text(stringResource(R.string.entrant_lottery_withdraw_title)) },
        text = { Text(stringResource(R.string.entrant_lottery_withdraw_message)) },
        confirmButton = { TextButton(enabled = state.canWithdraw(lottery.id), onClick = {
            confirming = false; onLeave(lottery.id)
        }) { Text(stringResource(R.string.entrant_lottery_confirm_withdraw), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.entrant_lottery_cancel)) } })
}

@Composable
private fun LotterySectionHeading(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.titleMedium, color = ForestGreen,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun LotteryInfoRow(icon: ImageVector, label: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = CampSecondaryText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = CampSecondaryText)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun registrationDate(value: Timestamp): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(value.toDate().toInstant())

/** Registration closes at midnight after the final calendar day shown to entrants. */
private fun lastJoinDate(lottery: Lottery): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .format(lottery.registrationCloses.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1))

@Composable
private fun joinBlockText(block: LotteryJoinBlock, lottery: Lottery): String = when (block) {
    LotteryJoinBlock.UPCOMING -> stringResource(R.string.entrant_lottery_upcoming_reason, registrationDate(lottery.registrationOpens))
    LotteryJoinBlock.CLOSED -> stringResource(R.string.entrant_lottery_closed_reason, lastJoinDate(lottery))
    LotteryJoinBlock.FULL -> stringResource(R.string.entrant_lottery_full)
    LotteryJoinBlock.DRAWN -> stringResource(R.string.entrant_lottery_drawn_reason)
    LotteryJoinBlock.ENDED -> stringResource(R.string.entrant_lottery_ended_reason)
    LotteryJoinBlock.UNAVAILABLE -> stringResource(R.string.entrant_lottery_missing)
}
