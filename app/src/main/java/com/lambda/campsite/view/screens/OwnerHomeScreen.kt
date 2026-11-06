/*
 * Purpose: Shows owner Home reservation shortcuts and counts for Flow 11.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted with the Flow 11 owner Home
 * reservation entry points based on the read-only Figma frame 59354:2621.
 */
package com.lambda.campsite.view.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerReservationTab
import com.lambda.campsite.controller.OwnerReservationsController
import com.lambda.campsite.controller.OwnerReservationsUiState
import com.lambda.campsite.view.theme.Flow11Colors
import com.lambda.campsite.view.theme.CampBrandName
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.util.Locale

/** Owner Home's Flow 11 links to reservation lists and the campsite calendar. */
@Composable
fun OwnerHomeScreen(
    controller: OwnerReservationsController,
    ownerName: String,
    onReservations: (OwnerReservationTab) -> Unit,
    onBookedDates: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    OwnerHomeContent(state, ownerName, onReservations, onBookedDates, controller::onRetry)
}

/** Renders the owner Home reservation shortcuts; lottery content belongs to its own flow. */
@Composable
fun OwnerHomeContent(
    state: OwnerReservationsUiState,
    ownerName: String,
    onReservations: (OwnerReservationTab) -> Unit,
    onBookedDates: () -> Unit,
    onRetry: () -> Unit,
) {
    StatusBarIconStyle(darkIcons = false)
    val firstName = ownerName.trim().substringBefore(' ').ifBlank { stringResource(R.string.owner_name_fallback) }
    val initials = ownerName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase(Locale.getDefault()) }.ifBlank { "O" }
    Column(Modifier.fillMaxSize().background(Flow11Colors.header).statusBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.camp_logo), null, Modifier.size(40.dp))
                Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f),
                    style = CampBrandName, color = Color.White)
                Box(Modifier.size(36.dp).background(Flow11Colors.selected, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initials, style = MaterialTheme.typography.labelLarge, color = Flow11Colors.text)
                }
            }
            Text(stringResource(R.string.owner_home_greeting, firstName),
                Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge,
                color = Flow11Colors.card)
        }
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = Flow11Colors.surface, contentColor = Flow11Colors.text) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.owner_home_reservations),
                    style = MaterialTheme.typography.titleMedium, color = Flow11Colors.header,
                    modifier = Modifier.semantics { heading() })
                if (state.loadFailed) {
                    Text(stringResource(R.string.reservations_load_failed))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                }
                OwnerHomeAction(R.drawable.flow11_home_active, stringResource(R.string.reservation_tab_active),
                    pluralStringResource(R.plurals.flow11_reservation_count, state.active.size, state.active.size),
                    trailingCount = state.active.size) { onReservations(OwnerReservationTab.ACTIVE) }
                OwnerHomeAction(R.drawable.flow11_home_past, stringResource(R.string.reservation_tab_past),
                    pluralStringResource(R.plurals.flow11_past_count, state.past.size, state.past.size)) {
                    onReservations(OwnerReservationTab.PAST)
                }
                OwnerHomeAction(R.drawable.flow11_home_cancelled, stringResource(R.string.reservation_tab_cancelled),
                    pluralStringResource(R.plurals.flow11_cancelled_count, state.cancelled.size, state.cancelled.size)) {
                    onReservations(OwnerReservationTab.CANCELLED)
                }
                OwnerHomeAction(R.drawable.flow11_home_booked, stringResource(R.string.booked_dates_title),
                    stringResource(R.string.owner_home_booked_dates_subtitle)) { onBookedDates() }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** One Figma-style Home shortcut with an icon, supporting text and affordance. */
@Composable
private fun OwnerHomeAction(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    trailingCount: Int? = null,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(68.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(Flow11Colors.selected, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText)
        }
        if (trailingCount != null) {
            Text(trailingCount.toString(), style = MaterialTheme.typography.titleLarge, color = Flow11Colors.secondaryText)
        } else {
            Text("›", style = MaterialTheme.typography.headlineSmall, color = Flow11Colors.secondaryText)
        }
    }
}
