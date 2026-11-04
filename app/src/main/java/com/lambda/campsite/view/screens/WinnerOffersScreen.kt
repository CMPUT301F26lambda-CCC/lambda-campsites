/*
 * Purpose: entrant's own unused/booked/declined wins, opening the Flow 9 wizard.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: general reservation lists remain Flow 10 (#127).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with Compose and semantics; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.WinnerOffersController
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.view.theme.*

/** Real entry point for winner booking; win status remains visible after declining. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerOffersScreen(controller: WinnerOffersController, onOpen: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = ForestGreen, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.flow9_wins_title), Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen, titleContentColor = androidx.compose.ui.graphics.Color.White))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Mint, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    state.loading -> item { CircularProgressIndicator() }
                    state.failed -> item {
                        Text(stringResource(R.string.flow9_load_error))
                        Button(onClick = controller::onRetry) { Text(stringResource(R.string.flow9_retry)) }
                    }
                    state.offers.isEmpty() -> item { Text(stringResource(R.string.flow9_no_wins)) }
                    else -> items(state.offers, key = { it.entry.lotteryId }) { offer ->
                        Card(colors = CardDefaults.cardColors(containerColor = CampSurfaceHigh)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(offer.lottery?.name ?: stringResource(R.string.flow9_site_missing), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(when (offer.entry.status) {
                                    EntryStatus.BOOKED -> R.string.flow9_booked
                                    EntryStatus.DECLINED -> R.string.flow9_history_declined
                                    else -> R.string.flow9_unused
                                }))
                                TextButton(onClick = { onOpen(offer.entry.lotteryId) }, enabled = offer.lottery != null) { Text(stringResource(R.string.flow9_open_win)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
