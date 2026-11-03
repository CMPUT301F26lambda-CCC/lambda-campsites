/*
 * AI use: GPT-6 (OpenAI), through Codex, assisted with the Flow 11 reservation
 * details and the read-only Figma booked-day sheet (frame 59355:2804).
 */
package com.lambda.campsite.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.view.theme.Flow11Colors
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Owner reservation details. Calendar taps first show the compact Figma day sheet.
 * @param bookedDate the night tapped in the calendar, or null for full details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerReservationDetailsSheet(
    reservation: Reservation,
    onDismiss: () -> Unit,
    bookedDate: LocalDate? = null,
) {
    var showFull by remember(reservation.id, bookedDate) { mutableStateOf(bookedDate == null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Flow11Colors.card,
        contentColor = Flow11Colors.text,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        properties = ModalBottomSheetProperties(
            isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = true,
        ),
    ) {
        if (showFull) FullOwnerReservationDetails(reservation, onDismiss)
        else if (bookedDate != null) BookedDaySummary(reservation, bookedDate) { showFull = true }
    }
}

/** Compact sheet shown after tapping a peach date in the calendar. */
@Composable
private fun BookedDaySummary(reservation: Reservation, bookedDate: LocalDate, onView: () -> Unit) {
    val day = bookedDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.reservation_on_date, day), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntrantAvatar(reservation.guestName)
            Text(reservation.guestName, style = MaterialTheme.typography.bodyLarge)
        }
        Text(pluralStringResource(R.plurals.flow11_guests, reservation.guests, reservation.guests),
            style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText)
        Text(bookingDateRange(reservation), style = MaterialTheme.typography.bodyMedium,
            color = Flow11Colors.secondaryText)
        OwnerReservationStatusPill(reservation.status)
        Button(onClick = onView, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Flow11Colors.header)) {
            Text(stringResource(R.string.view_reservation))
        }
    }
}

/** Full set of owner-visible reservation fields, shared by list and calendar. */
@Composable
private fun FullOwnerReservationDetails(reservation: Reservation, onDismiss: () -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.reservation_details_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_reservation_details))
            }
        }
        Text(reservation.campsiteName, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EntrantAvatar(reservation.guestName)
            Text(reservation.guestName, style = MaterialTheme.typography.bodyLarge)
        }
        DetailRow(stringResource(R.string.reservation_number), reservation.number)
        DetailRow(stringResource(R.string.reservation_check_in), reservation.checkIn.format(dateFormat))
        DetailRow(stringResource(R.string.reservation_check_out), reservation.checkOut.format(dateFormat))
        DetailRow(stringResource(R.string.reservation_guests), reservation.guests.toString())
        val bookedOn = reservation.createdAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        DetailRow(stringResource(R.string.reservation_booked_on), bookedOn.format(dateFormat))
        reservation.cancelledAt?.let { cancelledAt ->
            val cancelledOn = cancelledAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            DetailRow(stringResource(R.string.reservation_cancelled_on), cancelledOn.format(dateFormat))
        }
        OwnerReservationStatusPill(reservation.status)
    }
}

/** Initials badge from the Figma reservation sheet. */
@Composable
private fun EntrantAvatar(name: String) {
    val initials = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase(Locale.getDefault()) }
    Surface(shape = CircleShape, color = Flow11Colors.selected, contentColor = Flow11Colors.text) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(initials, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One label and value in the details sheet. */
@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Short date range for the booked-day preview. */
private fun bookingDateRange(reservation: Reservation): String {
    val locale = Locale.getDefault()
    val start = reservation.checkIn
    val end = reservation.checkOut
    val left = start.format(DateTimeFormatter.ofPattern("MMM d", locale))
    val right = end.format(DateTimeFormatter.ofPattern(
        if (start.year == end.year && start.month == end.month) "d, yyyy" else "MMM d, yyyy", locale))
    return "$left – $right"
}
