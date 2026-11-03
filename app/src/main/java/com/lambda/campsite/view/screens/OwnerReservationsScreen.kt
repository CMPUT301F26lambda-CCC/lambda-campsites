/*
 * AI use: GPT-6 (OpenAI), through Codex, helped implement the owner reservation
 * lists, empty states and accessible details entry for Flow 11
 * (US 02.07.01 to 02.07.03). Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerReservationTab
import com.lambda.campsite.controller.OwnerReservationsController
import com.lambda.campsite.controller.OwnerReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.view.components.OwnerReservationDetailsSheet
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Owner's active, past and cancelled reservation lists (US 02.07.01 to 02.07.03). */
@Composable
fun OwnerReservationsScreen(controller: OwnerReservationsController) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    OwnerReservationsContent(
        state = state,
        onTabSelected = controller::onTabSelected,
        onReservationSelected = controller::onReservationSelected,
        onRetry = controller::onRetry,
    )
    state.selectedReservation?.let { OwnerReservationDetailsSheet(it, controller::onDetailsDismissed) }
}

/** Renders the owner reservation lists from [state]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerReservationsContent(
    state: OwnerReservationsUiState,
    onTabSelected: (OwnerReservationTab) -> Unit,
    onReservationSelected: (String) -> Unit,
    onRetry: () -> Unit,
) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(
        containerColor = CampGreenLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.owner_reservations_title), modifier = Modifier.semantics { heading() }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CampGreenDark, titleContentColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = OwnerReservationTab.entries
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(state.selectedTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tabLabel(tab)) },
                    )
                }
            }
            when {
                state.isLoading && state.reservations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadFailed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.reservations_load_failed), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                    }
                }
                state.visibleReservations.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(emptyLabel(state.selectedTab), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visibleReservations, key = { it.id }) { reservation ->
                        OwnerReservationCard(reservation) { onReservationSelected(reservation.id) }
                    }
                }
            }
        }
    }
}

/** One reservation summary, with a single TalkBack stop and details action. */
@Composable
private fun OwnerReservationCard(reservation: Reservation, onClick: () -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val dates = stringResource(
        R.string.reservation_dates_format,
        reservation.checkIn.format(dateFormat),
        reservation.checkOut.format(dateFormat),
    )
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(reservation.campsiteName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reservation_entrant_format, reservation.guestName))
            Text(dates)
            Text(stringResource(R.string.reservation_number_format, reservation.number), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Localized label for an owner reservation tab. */
@Composable
private fun tabLabel(tab: OwnerReservationTab): String = stringResource(when (tab) {
    OwnerReservationTab.ACTIVE -> R.string.reservation_tab_active
    OwnerReservationTab.PAST -> R.string.reservation_tab_past
    OwnerReservationTab.CANCELLED -> R.string.reservation_tab_cancelled
})

/** Empty message required by the selected user story. */
@Composable
private fun emptyLabel(tab: OwnerReservationTab): String = stringResource(when (tab) {
    OwnerReservationTab.ACTIVE -> R.string.no_active_reservations
    OwnerReservationTab.PAST -> R.string.no_past_reservations
    OwnerReservationTab.CANCELLED -> R.string.no_cancelled_reservations
})
