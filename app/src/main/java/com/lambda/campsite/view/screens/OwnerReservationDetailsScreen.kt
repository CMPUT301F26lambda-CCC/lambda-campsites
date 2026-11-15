/*
 * Purpose: Shows an owner's reservation details and the rejection confirmation
 * for US 02.08.01.
 * Outstanding issues: Contact details and messaging belong to US 02.08.02.
 * AI use: GPT-6 (OpenAI), through Codex, assisted with this screen using the
 * read-only Figma rejection mockups. Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerRejectionError
import com.lambda.campsite.controller.OwnerReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.components.OwnerReservationStatusPill
import com.lambda.campsite.view.theme.Flow11Colors
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The selected reservation, with a guarded rejection action for a future stay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerReservationDetailsScreen(
    reservation: Reservation,
    state: OwnerReservationsUiState,
    onBack: () -> Unit,
    onRejectRequested: () -> Unit,
    onRejectDismissed: () -> Unit,
    onRejectConfirmed: () -> Unit,
    onRejectResultShown: () -> Unit,
) {
    BackHandler(enabled = state.confirmingId != reservation.id) { onBack() }
    val snackbar = remember { SnackbarHostState() }
    val rejectedMessage = stringResource(R.string.owner_reservation_rejected)
    LaunchedEffect(state.rejectedId) {
        if (state.rejectedId == reservation.id) {
            onRejectResultShown()
            snackbar.showSnackbar(rejectedMessage)
        }
    }

    StatusBarIconStyle(darkIcons = false)
    Scaffold(
        containerColor = Flow11Colors.header,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reservation_details_title),
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
        },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = Flow11Colors.surface,
            contentColor = Flow11Colors.text,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CampsiteHeading(reservation.campsiteName)
                DetailSection(stringResource(R.string.owner_reservation_entrant)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = Flow11Colors.selected) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Text(initials(reservation.guestName), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Text(reservation.guestName, style = MaterialTheme.typography.titleMedium)
                    }
                }
                DetailSection(stringResource(R.string.owner_reservation_stay)) {
                    DetailValue(stringResource(R.string.reservation_check_in), reservation.checkIn.format(dateFormat()))
                    DetailValue(stringResource(R.string.reservation_check_out), reservation.checkOut.format(dateFormat()))
                    DetailValue(stringResource(R.string.reservation_guests), reservation.guests.toString())
                }
                DetailSection(stringResource(R.string.owner_reservation_status)) {
                    OwnerReservationStatusPill(reservation.status, reservation.isCompleted(state.today))
                    if (reservation.status == ReservationStatus.REJECTED) {
                        Text(stringResource(R.string.owner_rejected_dates_available,
                            reservationDateRange(reservation)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Flow11Colors.secondaryText)
                    }
                }
                DetailSection(stringResource(R.string.owner_reservation_rules)) {
                    Text(stringResource(R.string.owner_reservation_rules_agreed),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (state.canReject(reservation)) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRejectRequested, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.owner_reject_reservation), color = RejectRed)
                    }
                }
                if (state.rejectError != null && state.confirmingId == null) {
                    RejectionError(state.rejectError)
                }
            }
        }
    }

    if (state.confirmingId == reservation.id) {
        val saving = state.rejectingId == reservation.id
        AlertDialog(
            onDismissRequest = { if (!saving) onRejectDismissed() },
            title = { Text(stringResource(R.string.owner_reject_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.owner_reject_confirm_body,
                        reservation.guestName, reservationDateRange(reservation)))
                    state.rejectError?.let { RejectionError(it) }
                }
            },
            confirmButton = {
                TextButton(onClick = onRejectConfirmed, enabled = !saving) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.owner_reject), color = RejectRed)
                }
            },
            dismissButton = {
                TextButton(onClick = onRejectDismissed, enabled = !saving) {
                    Text(stringResource(R.string.owner_reject_keep))
                }
            },
        )
    }
}

private val RejectRed = Color(0xFF9D3030)

@Composable
private fun CampsiteHeading(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(Flow11Colors.selected, CircleShape),
            contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.flow11_tent), null, Modifier.size(24.dp))
        }
        Text(name, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RejectionError(error: OwnerRejectionError) {
    val message = when (error) {
        OwnerRejectionError.NOT_ALLOWED -> R.string.owner_reject_not_allowed
        OwnerRejectionError.NOT_FOUND -> R.string.owner_reject_not_found
        OwnerRejectionError.SAVE_FAILED -> R.string.owner_reject_failed
    }
    Text(stringResource(message), color = RejectRed,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
}

private fun initials(name: String): String = name.split(Regex("\\s+"))
    .filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase(Locale.getDefault()) }

private fun dateFormat(): DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
