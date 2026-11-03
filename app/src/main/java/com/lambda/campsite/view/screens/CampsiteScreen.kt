/*
 * Purpose: entrant campsite details matched to the approved Figma reading layout (#154).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: rating submission and owner-section consolidation remain separate work.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with Compose layout and shared sections;
 * human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.view.components.*
import com.lambda.campsite.view.theme.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Published campsite details with live status and a link to the selected active lottery.
 * @param controller owns the campsite/calendar data and retry action.
 * @param lotteryController owns registration eligibility and live lottery status.
 * @param onBack returns to the retained search.
 * @param onLotteryClick opens the selected active lottery.
 */
@Composable
fun CampsiteScreen(controller: CampsiteController, lotteryController: LotteryController, onBack: () -> Unit,
    onLotteryClick: (String) -> Unit = {}) {
    val state by controller.state.collectAsStateWithLifecycle()
    CampsiteContent(state, controller::onRetry, onBack,
        lotteryStatus = { CampsiteLotteryBadge(lotteryController, showNoActiveHint = true) }) {
        CampsiteLotteryLinks(lotteryController, onLotteryClick)
    }
}

/**
 * Figma photo hero and rounded reading panel. No stock imagery or invented campground data.
 * @param state published campsite/calendar or loading/error state.
 * @param onRetry retries failed reads.
 * @param onBack returns to search.
 * @param lotteryStatus optional live status under the campsite title.
 * @param lotterySection active-lottery navigation after the reading sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteContent(state: CampsiteUiState, onRetry: () -> Unit, onBack: () -> Unit,
    lotteryStatus: @Composable () -> Unit = {}, lotterySection: @Composable () -> Unit) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = ForestGreen, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.campsite_header_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.entrant_campsite_back)) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen,
            titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        if (state.isLoading || state.error != null || state.campsite == null) {
            Surface(Modifier.fillMaxSize().padding(padding), color = Mint, contentColor = CampOnSurface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        state.isLoading -> { CircularProgressIndicator(); Text(stringResource(R.string.entrant_campsite_loading)) }
                        state.error == CampsiteDetailsError.LOAD_FAILED -> CampsiteLoadError(onRetry)
                        else -> Text(stringResource(R.string.entrant_campsite_not_available),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
        } else BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val minimumPanelHeight = (maxHeight - 168.dp).coerceAtLeast(0.dp)
            val camp = state.campsite
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().height(168.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
                        Icon(Icons.Outlined.Photo, null, Modifier.size(32.dp), tint = Mint)
                        Text(stringResource(R.string.entrant_campsite_photo_placeholder), style = MaterialTheme.typography.labelSmall, color = Mint)
                    }
                }
                item {
                    Surface(color = Mint, contentColor = CampOnSurface,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                        Column(Modifier.fillMaxWidth().heightIn(min = minimumPanelHeight).padding(top = 12.dp, bottom = 24.dp)) {
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(camp.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                                CampsiteInfoRow(Icons.Outlined.LocationOn, camp.location)
                                CampsiteRating(camp.averageRating, camp.ratingCount)
                                lotteryStatus()
                            }
                            CampsiteDivider()
                            CampsiteReadingSection(stringResource(R.string.heading_about)) {
                                if (camp.description.isNotBlank()) Text(camp.description, style = MaterialTheme.typography.bodyMedium)
                                CampsiteInfoRow(Icons.Outlined.Person, stringResource(R.string.campsite_guests_details, camp.maxOccupancy))
                                if (camp.amenities.isNotEmpty()) {
                                    Text(stringResource(R.string.heading_amenities), style = MaterialTheme.typography.titleSmall, color = ForestGreen)
                                    CampsiteReadingTags(camp.amenities)
                                }
                            }
                            CampsiteReadingSection(stringResource(R.string.section_availability)) {
                                val dates = state.availability
                                if (dates == null) Text(stringResource(R.string.entrant_campsite_no_season), style = MaterialTheme.typography.bodyMedium)
                                else {
                                    val endFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    val startFormat = if (dates.seasonStart.year == dates.seasonEnd.year)
                                        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) else endFormat
                                    val range = stringResource(R.string.campsite_date_range,
                                        dates.seasonStart.format(startFormat), dates.seasonEnd.format(endFormat))
                                    CampsiteInfoRow(Icons.Outlined.Event, stringResource(R.string.campsite_available_dates, range))
                                    Text(stringResource(R.string.campsite_availability_hint), style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
                                }
                            }
                            if (camp.pricePerNight != null || camp.rules.isNotBlank()) {
                                CampsiteReadingSection(stringResource(R.string.campsite_price_rules_heading)) {
                                    camp.pricePerNight?.let { Text(stringResource(R.string.price_per_night_format, it), style = MaterialTheme.typography.bodyMedium) }
                                    camp.rules.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                        Text(stringResource(R.string.campsite_rule_bullet, line.trim()), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (camp.tags.isNotEmpty()) CampsiteReadingSection(stringResource(R.string.section_tags)) { CampsiteReadingTags(camp.tags) }
                            CampsiteDivider()
                            CampsiteReadingSection(stringResource(R.string.heading_location)) { CampsiteMapPlaceholder() }
                            CampsiteDivider()
                            CampsiteReadingSection(stringResource(R.string.campsite_ratings_heading)) { CampsiteRating(camp.averageRating, camp.ratingCount) }
                            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { lotterySection() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampsiteDivider() { HorizontalDivider(color = CampOutline) }
