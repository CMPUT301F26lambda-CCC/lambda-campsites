/*
 * AI use: GPT-6 (OpenAI), through Codex, helped build the shared reservation
 * details sheet for Flow 11 (US 02.06.01 and 02.07.01 to 02.07.03).
 * Human review is pending.
 */
package com.lambda.campsite.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Shows all owner-facing details for [reservation] in a modal sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerReservationDetailsSheet(reservation: Reservation, onDismiss: () -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val status = when (reservation.status) {
        ReservationStatus.CONFIRMED -> stringResource(R.string.reservation_status_confirmed)
        ReservationStatus.CANCELLED -> stringResource(R.string.reservation_status_cancelled)
        ReservationStatus.REJECTED -> stringResource(R.string.reservation_status_rejected)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = true,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.reservation_details_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_reservation_details))
                }
            }
            DetailRow(stringResource(R.string.reservation_number), reservation.number)
            DetailRow(stringResource(R.string.reservation_status), status)
            DetailRow(stringResource(R.string.reservation_campsite), reservation.campsiteName)
            DetailRow(stringResource(R.string.reservation_entrant), reservation.guestName)
            DetailRow(stringResource(R.string.reservation_check_in), reservation.checkIn.format(dateFormat))
            DetailRow(stringResource(R.string.reservation_check_out), reservation.checkOut.format(dateFormat))
            DetailRow(stringResource(R.string.reservation_guests), reservation.guests.toString())
            val bookedOn = reservation.createdAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            DetailRow(stringResource(R.string.reservation_booked_on), bookedOn.format(dateFormat))
            reservation.cancelledAt?.let { cancelledAt ->
                val cancelledOn = cancelledAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                DetailRow(stringResource(R.string.reservation_cancelled_on), cancelledOn.format(dateFormat))
            }
        }
    }
}

/** One label and value in the details sheet. */
@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
