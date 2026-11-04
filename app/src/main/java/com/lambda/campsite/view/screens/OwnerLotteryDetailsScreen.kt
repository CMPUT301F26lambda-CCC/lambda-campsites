/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the controller
 * integration, the summary cards, the waiting-list action rows, TalkBack
 * accessibility, formatted date ranges, strings extraction and KDoc, following
 * the OwnerCampsiteDetailsScreen shape. Raghav Sethi reviewed, tested, and
 * committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.LotteryDetailsError
import com.lambda.campsite.controller.OwnerLotteryDetailsController
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How one date reads here, like "Oct 6, 2026". */
private val DetailsDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * Everything about one lottery for its owner (US 02.04.06).
 *
 * Takes [controller] to display the lottery's campsites, dates and settings
 * live, and navigation actions for editing it, opening the waiting list,
 * messaging it or reading the messages sent to it.
 *
 * @param controller the [OwnerLotteryDetailsController] managing details state.
 * @param onBack invoked when the owner navigates back.
 * @param onEditClick invoked to edit the lottery.
 * @param onWaitingListClick invoked to see the entrants on the waiting list.
 * @param onMessageWaitingListClick invoked to message everyone on the waiting list.
 * @param onSentMessagesClick invoked to read the messages sent to this lottery.
 * @param onResultsClick invoked to see the draw's results, offered once the
 *   lottery has been drawn (US 02.05.01).
 * @param modifier optional layout modifier.
 *
 * #169 follow-up, 2026-11-04: GLM 5.3 Flash (Z.ai), through OpenCode, helped
 * switch the registration dates to [Lottery.lastDayToJoin]; human review pending.
 */
@Composable
fun OwnerLotteryDetailsScreen(
    controller: OwnerLotteryDetailsController,
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onWaitingListClick: () -> Unit,
    onMessageWaitingListClick: () -> Unit,
    onSentMessagesClick: () -> Unit,
    onResultsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    // While loading there's no dark green app bar yet, so the clock stays dark.
    StatusBarIconStyle(darkIcons = state.isLoading)

    when {
        state.isLoading -> {
            Scaffold(containerColor = CampGreenLight) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CampGreenDark)
                }
            }
        }
        state.lottery == null -> {
            LotteryScreenFrame(title = stringResource(R.string.lottery_details_title), onBack = onBack) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(
                            when (state.error) {
                                LotteryDetailsError.LOTTERY_NOT_FOUND -> R.string.error_lottery_not_found
                                else -> R.string.error_lottery_load_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        else -> {
            val lottery = state.lottery ?: return
            LotteryScreenFrame(title = stringResource(R.string.lottery_details_title), onBack = onBack) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column {
                        Text(
                            text = lottery.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = lotteryStatusLine(lottery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (lottery.status == LotteryStatus.OPEN) {
                        Button(
                            onClick = onEditClick,
                            colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.action_edit_lottery))
                        }
                    }

                    SummaryCard(stringResource(R.string.heading_lottery_campsites)) {
                        state.campsites.forEach { campsite ->
                            Text(campsite.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    SummaryCard(stringResource(R.string.heading_lottery_dates)) {
                        DetailLine(
                            stringResource(R.string.new_lottery_registration_opens),
                            lottery.registrationOpens.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                .format(DetailsDate),
                        )
                        DetailLine(
                            stringResource(R.string.new_lottery_registration_closes),
                            lottery.lastDayToJoin().format(DetailsDate),
                        )
                        DetailLine(
                            stringResource(R.string.lottery_stay_dates),
                            stringResource(
                                R.string.lottery_stay_dates_format,
                                lottery.stayStart.format(DetailsDate),
                                lottery.stayEnd.format(DetailsDate),
                            ),
                        )
                    }

                    SummaryCard(stringResource(R.string.heading_lottery_settings)) {
                        val settingsText = if (lottery.capacity != null) {
                            stringResource(
                                R.string.new_lottery_review_settings_limited,
                                lottery.numberOfWinners,
                                lottery.capacity ?: 0,
                            )
                        } else {
                            stringResource(R.string.new_lottery_review_settings_unlimited, lottery.numberOfWinners)
                        }
                        Text(settingsText, style = MaterialTheme.typography.bodyMedium)
                    }

                    FormSectionTitle(stringResource(R.string.heading_waiting_list))
                    if (lottery.status == LotteryStatus.DRAWN) {
                        // After the draw the waiting list becomes the results:
                        // every entrant is a winner or was not selected (US 02.05.01).
                        val winners = state.waitingList.count {
                            it.status == EntryStatus.WON || it.status == EntryStatus.BOOKED
                        }
                        ActionRow(
                            icon = { Icon(Icons.Default.Star, contentDescription = null) },
                            title = stringResource(R.string.action_lottery_results),
                            subtitle = stringResource(R.string.lottery_results_row_subtitle, winners),
                            onClick = onResultsClick,
                        )
                    } else {
                        ActionRow(
                            icon = { Icon(Icons.Default.People, contentDescription = null) },
                            title = stringResource(R.string.action_waiting_list),
                            subtitle = stringResource(R.string.waiting_list_row_subtitle, state.waitingList.size),
                            onClick = onWaitingListClick,
                        )
                    }
                    ActionRow(
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                        title = stringResource(R.string.action_message_waiting_list),
                        subtitle = stringResource(R.string.message_waiting_list_row_subtitle),
                        onClick = onMessageWaitingListClick,
                    )
                    ActionRow(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        title = stringResource(R.string.action_sent_messages),
                        subtitle = stringResource(R.string.sent_messages_row_subtitle),
                        onClick = onSentMessagesClick,
                    )
                }
            }
        }
    }
}

/** The screen's shared frame: the green app bar with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotteryScreenFrame(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        content(padding)
    }
}

/** A titled card holding a short summary, like the dates or the settings. */
@Composable
private fun SummaryCard(heading: String, body: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CampGreenField)
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            body()
        }
    }
}

/** One "label value" line of a summary card. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

/** One tappable waiting-list action, like messaging the entrants on it. */
@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(CampGreenField)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CampGreenLight),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** The lottery's status line, like "Active · Registration open". */
@Composable
private fun lotteryStatusLine(lottery: Lottery): String {
    val now = com.google.firebase.Timestamp.now()
    return when (lottery.statusAt(now)) {
    LotteryStatus.OPEN -> if (now >= lottery.registrationCloses) {
        stringResource(R.string.entrant_lottery_status_closed)
    } else {
        stringResource(R.string.lottery_status_open)
    }
    LotteryStatus.UPCOMING -> stringResource(R.string.lottery_status_upcoming)
    LotteryStatus.DRAWN -> stringResource(R.string.lottery_status_drawn)
    LotteryStatus.ENDED -> stringResource(R.string.lottery_status_ended)
    }
}
