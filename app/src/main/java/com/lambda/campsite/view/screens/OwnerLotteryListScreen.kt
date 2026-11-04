/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the controller
 * integration, the Active/Upcoming/Completed sections, TalkBack accessibility
 * semantics, empty and error states, strings extraction and KDoc, following
 * the OwnerCampsiteListScreen shape. Raghav Sethi reviewed, tested, and
 * committed it.
 * #169 follow-up, 2026-11-04: GLM 5.3 Flash (Z.ai), through OpenCode, helped
 * switch the registration close date to [Lottery.lastDayToJoin]; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.lambda.campsite.controller.LotteryListError
import com.lambda.campsite.controller.OwnerLotteryListController
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How one registration or stay date reads, like "Oct 6, 2026". */
private val LotteryDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * The owner's lotteries tab screen (US 02.04.01).
 *
 * It connects to [OwnerLotteryListController], collects its state, and renders
 * the owner's lotteries under Active, Upcoming and Completed headings, with a
 * floating button that starts a new lottery.
 *
 * @param controller the ViewModel controller providing lottery list state.
 * @param onLotteryClick invoked when the owner selects a lottery to view details.
 * @param onNewLotteryClick invoked when the owner taps the "New lottery" button.
 * @param modifier optional modifier for the screen layout.
 */
@Composable
fun OwnerLotteryListScreen(
    controller: OwnerLotteryListController,
    onLotteryClick: (Lottery) -> Unit,
    onNewLotteryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    OwnerLotteryListContent(
        active = state.active,
        upcoming = state.upcoming,
        completed = state.completed,
        isLoading = state.isLoading,
        error = state.error,
        onLotteryClick = onLotteryClick,
        onNewLotteryClick = onNewLotteryClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerLotteryListContent(
    active: List<Lottery>,
    upcoming: List<Lottery>,
    completed: List<Lottery>,
    isLoading: Boolean,
    error: LotteryListError?,
    onLotteryClick: (Lottery) -> Unit,
    onNewLotteryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.lotteries_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewLotteryClick,
                containerColor = CampGreenDark,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_lottery_button)) },
            )
        },
    ) { padding ->
        // The headings are resolved outside the list, which isn't composable.
        val activeTitle = stringResource(R.string.lotteries_section_active)
        val upcomingTitle = stringResource(R.string.lotteries_section_upcoming)
        val completedTitle = stringResource(R.string.lotteries_section_completed)
        when {
            isLoading && active.isEmpty() && upcoming.isEmpty() && completed.isEmpty() -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CampGreenDark)
                }
            }
            error != null && active.isEmpty() && upcoming.isEmpty() && completed.isEmpty() -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.error_lottery_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            active.isEmpty() && upcoming.isEmpty() && completed.isEmpty() -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_lotteries_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LotterySection(
                        title = activeTitle,
                        lotteries = active,
                        statusTextRes = R.string.lottery_status_open,
                        onLotteryClick = onLotteryClick,
                    )
                    LotterySection(
                        title = upcomingTitle,
                        lotteries = upcoming,
                        statusTextRes = R.string.lottery_status_upcoming,
                        onLotteryClick = onLotteryClick,
                    )
                    LotterySection(
                        title = completedTitle,
                        lotteries = completed,
                        statusTextRes = R.string.lottery_status_completed,
                        onLotteryClick = onLotteryClick,
                    )
                }
            }
        }
    }
}

/**
 * One section heading and its lottery rows, with the [statusTextRes] every row
 * shows. Nothing is drawn when [lotteries] is empty.
 */
private fun LazyListScope.LotterySection(
    title: String,
    lotteries: List<Lottery>,
    @StringRes statusTextRes: Int,
    onLotteryClick: (Lottery) -> Unit,
) {
    if (lotteries.isEmpty()) return
    item(key = title) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .semantics { heading() },
        )
    }
    items(lotteries, key = { it.id }) { lottery ->
        LotteryRow(lottery, stringResource(statusTextRes)) { onLotteryClick(lottery) }
    }
}

/** One lottery's name, status line, registration period and campsite count. */
@Composable
private fun LotteryRow(lottery: Lottery, statusText: String, onClick: () -> Unit) {
    val registrationText = stringResource(
        R.string.lottery_registration_format,
        lottery.registrationOpens.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(LotteryDate),
        lottery.lastDayToJoin().format(LotteryDate),
    )
    val campsiteText = if (lottery.campsiteIds.size == 1) {
        stringResource(R.string.lottery_count_one_campsite)
    } else {
        stringResource(R.string.lottery_count_many_campsites, lottery.campsiteIds.size)
    }
    val rowDescription = "$statusText, $registrationText, $campsiteText"

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${lottery.name}, $rowDescription"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CampGreenField),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(lottery.name, style = MaterialTheme.typography.bodyMedium)
            Text(statusText, style = MaterialTheme.typography.bodySmall)
            Text(
                text = registrationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = campsiteText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
