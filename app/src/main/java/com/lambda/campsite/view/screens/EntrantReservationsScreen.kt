/*
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with this screen.
 * Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.EntrantReservationTab
import com.lambda.campsite.controller.EntrantReservationsController
import com.lambda.campsite.controller.EntrantReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.view.components.EntrantReservationStatusPill
import com.lambda.campsite.view.components.entrantReservationDateRange
import com.lambda.campsite.view.components.entrantReservationNights
import com.lambda.campsite.view.theme.CampField
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampOutline
import com.lambda.campsite.view.theme.CampPositive
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.CampSurfaceHigh
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import com.lambda.campsite.view.theme.StatusBarIconStyle

/** Flow 10's entrant reservation tab, backed by the shared live controller. */
@Composable
fun EntrantReservationsScreen(
    controller: EntrantReservationsController,
    onReservationClick: (String) -> Unit,
    onBrowseCampsites: () -> Unit,
    onWinnerOffers: (() -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    EntrantReservationsContent(state, controller::onTabSelected, onReservationClick,
        onBrowseCampsites, controller::onRetry, onWinnerOffers)
}

/** Figma Active/Past tabs, cards and empty state. The feed owns its loading state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrantReservationsContent(
    state: EntrantReservationsUiState,
    onTabSelected: (EntrantReservationTab) -> Unit,
    onReservationClick: (String) -> Unit,
    onBrowseCampsites: () -> Unit,
    onRetry: () -> Unit,
    onWinnerOffers: (() -> Unit)? = null,
) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = ForestGreen, topBar = {
        CenterAlignedTopAppBar(title = { Text(stringResource(R.string.flow10_reservations_title),
            modifier = Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = ForestGreen, titleContentColor = Color.White))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Mint, contentColor = CampOnSurface) {
            Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                if (onWinnerOffers != null) Button(onClick = onWinnerOffers,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                    Text(stringResource(R.string.flow9_wins_title))
                }
                EntrantReservationSegments(state.selectedTab, onTabSelected,
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                val visible = state.visibleReservations
                if (!state.isLoading && !state.loadFailed && visible.isNotEmpty()) {
                    Text(pluralStringResource(
                        if (state.selectedTab == EntrantReservationTab.ACTIVE) R.plurals.flow10_reservation_count
                        else R.plurals.flow10_past_count, visible.size, visible.size),
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleSmall, color = CampSecondaryText)
                } else Spacer(Modifier.height(20.dp))
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                    state.loadFailed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.flow10_load_failed))
                            Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                        }
                    }
                    visible.isEmpty() -> EntrantReservationsEmpty(state.selectedTab, onBrowseCampsites)
                    else -> LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(visible, key = Reservation::id) { reservation ->
                            EntrantReservationCard(reservation, state.today) { onReservationClick(reservation.id) }
                        }
                    }
                }
            }
        }
    }
}

/** Two Figma segments with an accessible selected state. */
@Composable
private fun EntrantReservationSegments(selected: EntrantReservationTab,
    onSelected: (EntrantReservationTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.clip(shape).border(1.dp, CampOutline, shape).height(40.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EntrantReservationTab.entries.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(1.dp).height(40.dp).background(CampOutline))
            Row(Modifier.weight(1f).fillMaxHeight()
                .background(if (tab == selected) CampPositive else Color.Transparent)
                .clickable(role = Role.Tab) { onSelected(tab) }
                .semantics { this.selected = tab == selected },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (tab == selected) {
                    Image(painterResource(R.drawable.flow11_check), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (tab == EntrantReservationTab.ACTIVE) R.string.flow10_active_tab
                    else R.string.flow10_past_tab), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Reservation card; the tent and chevron are exact shared Figma assets. */
@Composable
private fun EntrantReservationCard(reservation: Reservation, today: java.time.LocalDate,
    onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CampSurfaceHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(CampField),
                contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.flow11_tent), null, Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(reservation.campsiteName, style = MaterialTheme.typography.titleMedium)
                Text(entrantReservationDateRange(reservation), style = MaterialTheme.typography.bodyMedium)
                val nights = entrantReservationNights(reservation)
                Text(pluralStringResource(R.plurals.flow10_nights, nights, nights) + " · " +
                    pluralStringResource(R.plurals.flow10_guest_count, reservation.guests, reservation.guests),
                    style = MaterialTheme.typography.labelSmall, color = CampSecondaryText)
                EntrantReservationStatusPill(reservation, today)
            }
            Image(painterResource(R.drawable.flow11_chevron), null, Modifier.size(24.dp))
        }
    }
}

/** Figma calendar illustration and browse action for an empty tab. */
@Composable
private fun EntrantReservationsEmpty(tab: EntrantReservationTab, onBrowseCampsites: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(CampSurfaceHigh),
                contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.flow10_empty_calendar), null, Modifier.size(40.dp))
            }
            Text(stringResource(if (tab == EntrantReservationTab.ACTIVE) R.string.flow10_no_active
                else R.string.flow10_no_past), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(if (tab == EntrantReservationTab.ACTIVE) R.string.flow10_no_active_hint
                else R.string.flow10_no_past_hint), style = MaterialTheme.typography.bodyMedium,
                color = CampSecondaryText)
            Button(onClick = onBrowseCampsites, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.flow10_browse_campsites))
            }
        }
    }
}
