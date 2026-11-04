/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the controller
 * integration, the summary and result cards, TalkBack accessibility, formatted
 * date ranges, strings extraction and KDoc, following the
 * OwnerLotteryDetailsScreen shape. Raghav Sethi reviewed, tested, and committed it.
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
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
import com.lambda.campsite.controller.LotteryResultsController
import com.lambda.campsite.controller.LotteryResultsError
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How one date reads here, like "Oct 19, 2026". */
private val ResultsDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * The result of a lottery's automatic draw (US 02.05.01).
 *
 * Shown when the lottery has been drawn: a summary of what was given out,
 * then how the entrants ended up, winners first. The draw itself runs in the
 * `drawLotteries` Cloud Function, so this screen only reads the result.
 *
 * @param controller the [LotteryResultsController] providing the result state.
 * @param onBack invoked when navigating back.
 * @param onWinnersClick invoked to see the entrants the draw picked.
 * @param onNotSelectedClick invoked to see the entrants the draw didn't pick.
 * @param modifier optional layout modifier.
 */
@Composable
fun LotteryResultsScreen(
    controller: LotteryResultsController,
    onBack: () -> Unit,
    onWinnersClick: () -> Unit,
    onNotSelectedClick: () -> Unit,
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
            ResultsScreenFrame(onBack = onBack) { padding ->
                Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(
                            when (state.error) {
                                LotteryResultsError.LOTTERY_NOT_FOUND -> R.string.error_lottery_not_found
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
            ResultsScreenFrame(onBack = onBack) { padding ->
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
                            text = stringResource(
                                R.string.lottery_results_status,
                                lottery.registrationCloses.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                    .format(ResultsDate),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.lottery_results_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SummaryCard(stringResource(R.string.heading_lottery_summary)) {
                        val campsiteNames = state.campsiteNames.ifEmpty {
                            listOf(stringResource(R.string.new_lottery_not_set))
                        }
                        Text(campsiteNames.joinToString(separator = ", "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(
                                R.string.lottery_registration_format,
                                lottery.registrationOpens.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                    .format(ResultsDate),
                                lottery.registrationCloses.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                                    .format(ResultsDate),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.lottery_stay_dates_format,
                                lottery.stayStart.format(ResultsDate),
                                lottery.stayEnd.format(ResultsDate),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    FormSectionTitle(stringResource(R.string.lottery_results_heading))

                    ResultRow(
                        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = CampGreenDark) },
                        title = stringResource(R.string.action_winners),
                        subtitle = stringResource(R.string.lottery_winners_row_subtitle, state.winnersCount),
                        onClick = onWinnersClick,
                    )
                    ResultRow(
                        icon = { Icon(Icons.Default.People, contentDescription = null, tint = CampGreenDark) },
                        title = stringResource(R.string.action_not_selected),
                        subtitle = stringResource(R.string.lottery_not_selected_row_subtitle, state.notSelectedCount),
                        onClick = onNotSelectedClick,
                    )
                }
            }
        }
    }
}

/** The screen's frame: the green app bar with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreenFrame(onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lottery_results_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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

/** A titled card holding a short summary of what the lottery gave out. */
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

/** One tappable result row, like the winners or the entrants who weren't picked. */
@Composable
private fun ResultRow(
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
                .clip(CircleShape)
                .background(CampGreenLight),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.size(12.dp))
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
