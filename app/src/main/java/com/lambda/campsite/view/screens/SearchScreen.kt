/*
 * Purpose: entrant search matched to the Figma brand header and summary cards (#154).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: filters and scanner remain in their separate flows.
 * Flow 8 (#125): the live unread bell opens the notification inbox.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with Compose layout and semantics;
 * human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.view.components.*
import com.lambda.campsite.view.theme.*

/** Entrant Home, with its real profile greeting and lifecycle-owned live lottery badges. */
@Composable
fun SearchScreen(controller: SearchController, onCampsiteClick: (String) -> Unit, profileName: String? = null,
    onInbox: (() -> Unit)? = null, unreadCount: Int = 0) {
    val state by controller.state.collectAsStateWithLifecycle()
    SearchContent(state, controller::onQueryChanged, controller::onRetry, onCampsiteClick,
        onSubmitSearch = controller::onSubmitSearch, onClearSearch = { controller.onSearch("") }, profileName = profileName,
        onInbox = onInbox, unreadCount = unreadCount, status = { camp ->
            val lottery: LotteryController = viewModel(key = "search-lottery-${camp.id}",
                factory = LotteryController.createFactory(camp.id))
            CampsiteLotteryBadge(lottery)
        })
}

/**
 * Stateless search presentation. Draft edits and explicit submission remain separate.
 * @param state current query, submitted query and live results.
 * @param onSearch edits the draft query.
 * @param onRetry retries catalogue loading.
 * @param onCampsiteClick opens the selected campsite.
 * @param onSubmitSearch submits the draft via the button or keyboard.
 * @param onClearSearch clears both draft and submitted query.
 * @param profileName actual profile name, or null for a neutral greeting.
 * @param status optional live status supplied by the screen, never invented by a card.
 */
@Composable
fun SearchContent(state: SearchUiState, onSearch: (String) -> Unit, onRetry: () -> Unit,
    onCampsiteClick: (String) -> Unit, onSubmitSearch: () -> Unit = { onSearch(state.query) },
    onClearSearch: () -> Unit = { onSearch("") }, profileName: String? = null,
    status: @Composable (Campsite) -> Unit = {}, onInbox: (() -> Unit)? = null, unreadCount: Int = 0) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { onSubmitSearch(); keyboard?.hide(); Unit }
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = ForestGreen, topBar = { EntrantSearchHeader(profileName, onInbox, unreadCount) }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Mint, contentColor = CampOnSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.entrant_campsite_find_title), color = ForestGreen,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                item {
                    OutlinedTextField(value = state.query, onValueChange = onSearch,
                        label = { Text(stringResource(R.string.entrant_campsite_search_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = CampSecondaryText) },
                        trailingIcon = { Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.query.isNotEmpty()) IconButton(onClick = onClearSearch) {
                                Icon(Icons.Default.Close, stringResource(R.string.entrant_campsite_clear_search))
                            }
                            FilledIconButton(onClick = submit, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.entrant_campsite_search_action))
                            }
                        } },
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = CampField,
                            focusedContainerColor = CampField, unfocusedBorderColor = CampOutline),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                when {
                    state.isLoading -> item { CircularProgressIndicator(); Text(stringResource(R.string.entrant_campsite_loading)) }
                    state.loadFailed -> item { CampsiteLoadError(onRetry) }
                    state.results.isEmpty() -> item {
                        if (state.submittedQuery.isBlank()) Text(stringResource(R.string.entrant_campsite_empty),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        else Column(Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Surface(color = CampPositive, shape = CircleShape, modifier = Modifier.size(96.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, Modifier.size(40.dp)) }
                            }
                            Text(stringResource(R.string.entrant_no_results_title), style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; heading() })
                            Text(stringResource(R.string.entrant_no_results_body, state.submittedQuery),
                                style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = onClearSearch, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.entrant_campsite_clear_results))
                            }
                        }
                    }
                    else -> {
                        item {
                            Text(if (state.submittedQuery.isBlank()) pluralStringResource(R.plurals.entrant_search_results_count,
                                state.results.size, state.results.size)
                            else pluralStringResource(R.plurals.entrant_search_match_count, state.results.size, state.results.size, state.submittedQuery),
                                style = MaterialTheme.typography.titleSmall, color = CampSecondaryText,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        items(state.results, key = { it.id }) { camp ->
                            CampsiteSummaryCard(camp.name, camp.location, camp.maxOccupancy, camp.averageRating, camp.ratingCount,
                                onDetails = { onCampsiteClick(camp.id) }, status = { status(camp) })
                        }
                    }
                }
            }
        }
    }
}

/** Existing Home brand artwork with the Figma typography and the actual user's greeting. */
@Composable
private fun EntrantSearchHeader(name: String?, onInbox: (() -> Unit)?, unreadCount: Int) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.camp_logo), null, Modifier.size(40.dp).clip(CircleShape))
            Text(stringResource(R.string.app_name), style = CampBrandName, color = Color.White, modifier = Modifier.weight(1f))
            if (onInbox != null) IconButton(onClick = onInbox, modifier = Modifier.size(48.dp)) {
                val description = stringResource(R.string.flow8_open_inbox)
                val unread = stringResource(R.string.flow8_count, unreadCount)
                BadgedBox(badge = { if (unreadCount > 0) Badge() }) {
                    Icon(Icons.Outlined.Notifications, description, tint = Color.White,
                        modifier = Modifier.semantics { stateDescription = unread })
                }
            }
        }
        Text(if (name.isNullOrBlank()) stringResource(R.string.entrant_home_greeting_default)
            else stringResource(R.string.entrant_home_greeting, name), style = MaterialTheme.typography.bodyLarge, color = Mint)
    }
}

/** Shared localized load failure and retry action. */
@Composable
internal fun CampsiteLoadError(onRetry: () -> Unit) {
    Text(stringResource(R.string.entrant_campsite_load_failed), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.entrant_campsite_retry)) }
}
