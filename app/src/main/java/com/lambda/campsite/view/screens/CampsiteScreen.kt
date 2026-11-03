/*
 * Purpose: published campsite details with Flow 7 registration actions (US 01.01.05).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: booking is Flow 9; this screen does not reserve dates.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose integration and KDoc; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Entrant details for the selected campsite; owner edit/QR controls stay on the owner screen.
 * @param controller observes the selected published site's information and calendar.
 * @param lotteryController handles this site's Flow 7 join/leave section.
 * @param onBack returns to search without discarding its controller/query.
 * @param onLotteryClick opens the selected active lottery as a pushed screen.
 */
@Composable
fun CampsiteScreen(controller: CampsiteController, lotteryController: LotteryController, onBack: () -> Unit,
    onLotteryClick: (String) -> Unit = {}) {
    val state by controller.state.collectAsStateWithLifecycle()
    CampsiteContent(state, controller::onRetry, onBack) {
        CampsiteLotteryLinks(lotteryController, onLotteryClick)
    }
}

/**
 * Scrollable details presentation with an injectable registration section.
 * @param state live campsite/calendar data, or a loading/error state.
 * @param onRetry retries failed reads.
 * @param onBack returns to the previous screen.
 * @param lotterySection content mounted only after the campsite is available.
 */
@Composable
fun CampsiteContent(state: CampsiteUiState, onRetry: () -> Unit, onBack: () -> Unit, lotterySection: @Composable () -> Unit) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = MaterialTheme.colorScheme.primaryContainer,
        topBar = { EntrantCampsiteHeader(stringResource(R.string.campsite_details_title), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.isLoading -> item { CircularProgressIndicator(); Text(stringResource(R.string.entrant_campsite_loading)) }
                state.error == CampsiteDetailsError.LOAD_FAILED -> item { CampsiteLoadError(onRetry) }
                state.campsite == null -> item {
                    Text(stringResource(R.string.entrant_campsite_not_available), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                else -> {
                    val camp = state.campsite
                    item {
                        // The existing owner UI also uses placeholders; do not invent a campsite photo.
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().height(140.dp), verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(40.dp))
                                Text(stringResource(R.string.entrant_campsite_photo_placeholder))
                            }
                        }
                    }
                    item {
                        Text(camp.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(camp.location)
                        Text(stringResource(R.string.entrant_campsite_max_guests, camp.maxOccupancy))
                        CampsiteRatingText(camp.averageRating, camp.ratingCount)
                        camp.pricePerNight?.let { Text(stringResource(R.string.price_per_night_format, it)) }
                    }
                    if (camp.description.isNotBlank()) item { DetailSection(stringResource(R.string.heading_about), camp.description) }
                    if (camp.amenities.isNotEmpty()) item { DetailChips(stringResource(R.string.heading_amenities), camp.amenities) }
                    if (camp.tags.isNotEmpty()) item { DetailChips(stringResource(R.string.section_tags), camp.tags) }
                    item {
                        val dates = state.availability
                        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        DetailSection(stringResource(R.string.section_availability),
                            if (dates == null) stringResource(R.string.entrant_campsite_no_season)
                            else stringResource(R.string.entrant_campsite_season, dates.seasonStart.format(format), dates.seasonEnd.format(format)))
                    }
                    if (camp.rules.isNotBlank()) item { DetailSection(stringResource(R.string.entrant_campsite_rules), camp.rules) }
                    item {
                        val lat = camp.latitude
                        val lng = camp.longitude
                        DetailSection(stringResource(R.string.heading_location),
                            if (lat == null || lng == null) camp.location
                            else stringResource(R.string.entrant_campsite_coordinates, lat, lng))
                    }
                    item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); lotterySection() }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(body)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailChips(title: String, values: List<String>) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Text(value, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        } }
    }
}
