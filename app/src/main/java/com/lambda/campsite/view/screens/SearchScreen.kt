/*
 * Purpose: entrant search and summary cards for Flow 6 (US 01.01.01, 01.01.04).
 * Outstanding issues: structured filters are owned by Flow 15.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose UI and KDoc; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.SearchController
import com.lambda.campsite.controller.SearchUiState
import com.lambda.campsite.view.theme.StatusBarIconStyle

/**
 * Entrant Home: published campsite search and navigation to the selected site's details.
 * @param controller owns live search state.
 * @param onCampsiteClick opens details for the selected ID.
 */
@Composable
fun SearchScreen(controller: SearchController, onCampsiteClick: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    SearchContent(state, controller::onSearch, controller::onRetry, onCampsiteClick)
}

/**
 * Search presentation with named, actionable cards and distinct empty/error states.
 * @param state search input and current live results.
 * @param onSearch passes changed query text to the controller.
 * @param onRetry retries a failed listener.
 * @param onCampsiteClick opens the selected campsite ID.
 */
@Composable
fun SearchContent(state: SearchUiState, onSearch: (String) -> Unit, onRetry: () -> Unit, onCampsiteClick: (String) -> Unit) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = MaterialTheme.colorScheme.primaryContainer,
        topBar = { EntrantCampsiteHeader(stringResource(R.string.flow6_find_title)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(value = state.query, onValueChange = onSearch,
                    label = { Text(stringResource(R.string.flow6_search_label)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            when {
                state.isLoading -> item {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.flow6_loading))
                }
                state.loadFailed -> item { CampsiteLoadError(onRetry) }
                state.results.isEmpty() -> item {
                    Text(stringResource(if (state.query.isBlank()) R.string.flow6_empty else R.string.flow6_no_matches),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                else -> {
                    item { Text(stringResource(R.string.flow6_result_count, state.results.size),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    items(state.results, key = { it.id }) { camp ->
                        Card(onClick = { onCampsiteClick(camp.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(camp.name, style = MaterialTheme.typography.titleMedium)
                                Text(camp.location)
                                Text(stringResource(R.string.flow6_max_guests, camp.maxOccupancy))
                                CampsiteRatingText(camp.averageRating, camp.ratingCount)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shared entrant header; [onBack] is absent on the top-level search screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrantCampsiteHeader(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.camp_logo), contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
        }
    }, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.flow6_back))
        }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
}

/** Shared error text and labeled retry action for search/details. */
@Composable
internal fun CampsiteLoadError(onRetry: () -> Unit) {
    Text(stringResource(R.string.flow6_load_failed), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.flow6_retry)) }
}

/** Accessible rating text, including an explicit unrated state. */
@Composable
internal fun CampsiteRatingText(average: Double?, count: Int) {
    Text(if (average == null) stringResource(R.string.no_ratings_yet)
        else stringResource(R.string.flow6_rating, average, count), style = MaterialTheme.typography.bodyMedium)
}
