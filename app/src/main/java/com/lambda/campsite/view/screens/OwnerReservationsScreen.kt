/*
 * AI use: GPT-6 (OpenAI), through Codex, assisted with the Flow 11 reservation
 * lists, accessible card actions, and read-only Figma alignment.
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.lambda.campsite.controller.OwnerReservationTab
import com.lambda.campsite.controller.OwnerReservationsController
import com.lambda.campsite.controller.OwnerReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.view.components.OwnerReservationDetailsSheet
import com.lambda.campsite.view.components.OwnerReservationStatusPill
import com.lambda.campsite.view.theme.Flow11Colors
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Owner reservation lists matched to the Flow 11 Figma frames (US 02.07.01–03). */
@Composable
fun OwnerReservationsScreen(
    controller: OwnerReservationsController,
    onBack: () -> Unit,
    initialTab: OwnerReservationTab,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    LaunchedEffect(initialTab) { controller.onTabSelected(initialTab) }
    OwnerReservationsContent(
        state = state,
        onTabSelected = controller::onTabSelected,
        onReservationSelected = controller::onReservationSelected,
        onRetry = controller::onRetry,
        onBack = onBack,
    )
    state.selectedReservation?.let { OwnerReservationDetailsSheet(it, controller::onDetailsDismissed) }
}

/** Stateless list presentation with the Figma segmented control and reservation cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerReservationsContent(
    state: OwnerReservationsUiState,
    onTabSelected: (OwnerReservationTab) -> Unit,
    onReservationSelected: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit = {},
) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = Flow11Colors.header, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.owner_reservations_title),
                modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
            } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Flow11Colors.header,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        )
    }) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = Flow11Colors.surface,
            contentColor = Flow11Colors.text,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                OwnerReservationSegments(state.selectedTab, onTabSelected,
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                val visible = state.visibleReservations
                Text(
                    pluralStringResource(R.plurals.flow11_reservation_count, visible.size, visible.size),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleSmall,
                    color = Flow11Colors.secondaryText,
                )
                when {
                    state.isLoading && state.reservations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Flow11Colors.header)
                    }
                    state.loadFailed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.reservations_load_failed))
                            Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                        }
                    }
                    visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        Text(emptyLabel(state.selectedTab), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText)
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(visible, key = { it.id }) { reservation ->
                            OwnerReservationCard(reservation, state.selectedTab == OwnerReservationTab.PAST) {
                                onReservationSelected(reservation.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-width three-way selection used by the owner's reservation lists. */
@Composable
private fun OwnerReservationSegments(
    selectedTab: OwnerReservationTab,
    onSelected: (OwnerReservationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Flow11Colors.outline, RoundedCornerShape(20.dp))
        .height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        OwnerReservationTab.entries.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(1.dp).height(40.dp).background(Flow11Colors.outline))
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(if (tab == selectedTab) Flow11Colors.selected else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelected(tab) }
                    .semantics { selected = tab == selectedTab },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab == selectedTab) {
                    Image(painterResource(R.drawable.flow11_check), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(tabLabel(tab), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** One reservation summary matching the Figma card's content and hierarchy. */
@Composable
private fun OwnerReservationCard(reservation: Reservation, completed: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Flow11Colors.card)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Flow11Colors.field),
                contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.flow11_tent), null, Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reservation.campsiteName, style = MaterialTheme.typography.titleMedium,
                    color = Flow11Colors.text)
                Text(reservationDateRange(reservation), style = MaterialTheme.typography.bodyMedium,
                    color = Flow11Colors.text)
                val nights = java.time.temporal.ChronoUnit.DAYS.between(reservation.checkIn, reservation.checkOut).toInt()
                val nightsLabel = pluralStringResource(R.plurals.flow11_nights, nights, nights)
                val guestsLabel = pluralStringResource(R.plurals.flow11_guests, reservation.guests, reservation.guests)
                Text(stringResource(R.string.reservation_stay_summary, nightsLabel, guestsLabel),
                    style = MaterialTheme.typography.labelSmall, color = Flow11Colors.secondaryText)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(painterResource(R.drawable.flow11_person), null, Modifier.size(16.dp))
                    Text(reservation.guestName, style = MaterialTheme.typography.bodyMedium,
                        color = Flow11Colors.secondaryText)
                }
                OwnerReservationStatusPill(reservation.status, completed)
            }
            Image(painterResource(R.drawable.flow11_chevron), null, Modifier.size(24.dp))
        }
    }
}

/** Dates in the short range format used by the Figma reservation cards. */
internal fun reservationDateRange(reservation: Reservation): String {
    val locale = Locale.getDefault()
    val start = reservation.checkIn
    val end = reservation.checkOut
    val left = start.format(DateTimeFormatter.ofPattern("MMM d", locale))
    val right = end.format(DateTimeFormatter.ofPattern(
        if (start.year == end.year && start.month == end.month) "d, yyyy" else "MMM d, yyyy", locale))
    return "$left – $right"
}

/** Localized label for an owner reservation segment. */
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
