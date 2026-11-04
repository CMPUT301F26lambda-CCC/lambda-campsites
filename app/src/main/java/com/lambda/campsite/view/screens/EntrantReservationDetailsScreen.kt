/*
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with this screen.
 * Human review is pending.
 */
package com.lambda.campsite.view.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteController
import com.lambda.campsite.controller.EntrantCancellationError
import com.lambda.campsite.controller.EntrantReservationsController
import com.lambda.campsite.controller.EntrantReservationsUiState
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.components.EntrantReservationStatusPill
import com.lambda.campsite.view.components.entrantCancellationDateRange
import com.lambda.campsite.view.components.entrantReservationDateRange
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.CampSurfaceHigh
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Details of the selected entrant reservation, including its cancellation flow. */
@Composable
fun EntrantReservationDetailsScreen(
    reservationId: String,
    controller: EntrantReservationsController,
    campsiteController: CampsiteController?,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val location = campsiteController?.state?.collectAsStateWithLifecycle()?.value?.campsite?.location
    val snackbar = remember { SnackbarHostState() }
    val cancelledMessage = stringResource(R.string.flow10_cancelled_message)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    LaunchedEffect(state.cancelledId) {
        if (state.cancelledId == reservationId) {
            snackbar.showSnackbar(cancelledMessage)
            controller.onCancelResultShown()
        }
    }
    EntrantReservationDetailsContent(reservationId, state, location,
        controller::onCancelRequested, controller::onCancelDismissed, controller::onCancelConfirmed,
        onBack, controller::onRetry, snackbar)
}

/** Full-height Figma detail screen, with the shared navigation bar outside it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrantReservationDetailsContent(
    reservationId: String,
    state: EntrantReservationsUiState,
    location: String?,
    onCancelRequested: (String) -> Unit,
    onCancelDismissed: () -> Unit,
    onCancelConfirmed: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    StatusBarIconStyle(darkIcons = false)
    val reservation = state.reservation(reservationId)
    Scaffold(containerColor = ForestGreen, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.flow10_reservation_title),
            modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
            } }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ForestGreen, titleContentColor = Color.White,
                navigationIconContentColor = Color.White))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(168.dp).background(ForestGreen),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Photo, null, Modifier.size(36.dp), tint = Mint)
                    Text(stringResource(R.string.flow10_photo_placeholder).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall, color = Mint)
                }
            }
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Mint, contentColor = CampOnSurface) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                    state.loadFailed -> Box(Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.flow10_load_failed))
                            Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                        }
                    }
                    reservation == null -> Box(Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.flow10_reservation_unavailable))
                    }
                    else -> ReservationDetails(reservation, state, location, onCancelRequested)
                }
            }
        }
    }
    if (state.confirmingId == reservationId && reservation != null) {
        CancelReservationDialog(reservation, state.cancellingId == reservationId,
            state.cancelError, onCancelDismissed, onCancelConfirmed)
    }
}

/** Reservation facts and cancellation policy shown in the Figma detail card. */
@Composable
private fun ReservationDetails(reservation: Reservation, state: EntrantReservationsUiState,
    location: String?, onCancelRequested: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reservation.campsiteName, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                if (!location.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, Modifier.size(20.dp), tint = CampSecondaryText)
                    Text(location, style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
                }
                EntrantReservationStatusPill(reservation, state.today)
            }
            HorizontalDivider(color = com.lambda.campsite.view.theme.CampOutline)
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReservationFact(R.drawable.flow10_description, R.string.flow10_reservation_number,
                    reservation.number)
                ReservationFact(R.drawable.flow11_home_active, R.string.flow10_dates,
                    entrantReservationDateRange(reservation) + " · " +
                        pluralStringResource(R.plurals.flow10_nights,
                            com.lambda.campsite.view.components.entrantReservationNights(reservation),
                            com.lambda.campsite.view.components.entrantReservationNights(reservation)))
                ReservationFact(R.drawable.flow10_person, R.string.flow10_guests,
                    reservation.guests.toString())
                ReservationFact(R.drawable.flow11_home_past, R.string.flow10_campsite_rules,
                    stringResource(R.string.flow10_agreed_on, reservationLocalDate(reservation.rulesAgreedAt)))
                if (reservation.status == ReservationStatus.CANCELLED && reservation.cancelledAt != null) {
                    ReservationFact(R.drawable.flow10_cancel, R.string.flow10_status_cancelled,
                        reservationLocalDateTime(reservation.cancelledAt))
                }
            }
            Spacer(Modifier.height(if (state.canCancel(reservation)) 104.dp else 24.dp))
        }
        if (state.canCancel(reservation)) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Mint)
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.flow10_cancel_policy), style = MaterialTheme.typography.bodyMedium,
                    color = CampSecondaryText)
                TextButton(onClick = { onCancelRequested(reservation.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))) {
                    Text(stringResource(R.string.flow10_cancel_reservation))
                }
            }
        }
    }
}

/** Label and value row using the exact 24dp Figma glyphs. */
@Composable
private fun ReservationFact(icon: Int, label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = CampSecondaryText)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Confirmation card; Keep is safe to dismiss and Cancel performs the transaction. */
@Composable
private fun CancelReservationDialog(reservation: Reservation, cancelling: Boolean,
    error: EntrantCancellationError?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = { if (!cancelling) onDismiss() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = CampSurfaceHigh) {
            Column(Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.flow10_cancel), null, Modifier.size(24.dp))
                Text(stringResource(R.string.flow10_cancel_title),
                    style = MaterialTheme.typography.headlineSmall, color = CampOnSurface)
                Text(stringResource(R.string.flow10_cancel_explanation,
                    entrantCancellationDateRange(reservation)),
                    style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
                if (error != null) Text(stringResource(when (error) {
                    EntrantCancellationError.NOT_ALLOWED -> R.string.flow10_cancel_not_allowed
                    EntrantCancellationError.NOT_FOUND -> R.string.flow10_cancel_not_found
                    EntrantCancellationError.LOAD_FAILED -> R.string.flow10_cancel_failed
                }), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onConfirm, enabled = !cancelling,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))) {
                    if (cancelling) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.flow10_cancel_reservation))
                }
                TextButton(onClick = onDismiss, enabled = !cancelling,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = ForestGreen)) {
                    Text(stringResource(R.string.flow10_keep_reservation))
                }
            }
        }
    }
}

/** Date of an agreement or cancellation in the entrant's local time zone. */
private fun reservationLocalDate(timestamp: Timestamp): String =
    timestamp.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))

/** Local cancellation time displayed under the cancelled status. */
private fun reservationLocalDateTime(timestamp: Timestamp): String =
    timestamp.toDate().toInstant().atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault()))
