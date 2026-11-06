/*
 * Purpose: Provides shared Flow 10 reservation card, status, and date presentation components.
 * Outstanding issues: None known.
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with entrant reservation
 * status styling and date formatting from the read-only Figma mockups.
 * Human review is pending.
 */
package com.lambda.campsite.view.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.theme.CampField
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampOutline
import com.lambda.campsite.view.theme.CampPositive
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The plain-language Figma status for one entrant reservation. */
@StringRes
fun entrantReservationStatusLabel(reservation: Reservation, today: LocalDate): Int = when {
    reservation.status == ReservationStatus.CANCELLED -> R.string.flow10_status_cancelled
    reservation.status == ReservationStatus.REJECTED -> R.string.flow10_status_rejected
    reservation.isCompleted(today) -> R.string.flow10_status_completed
    !today.isBefore(reservation.checkIn) -> R.string.flow10_status_staying_now
    else -> R.string.flow10_status_confirmed
}

/** Status text and colour shared by Flow 10 cards and details. */
@Composable
fun EntrantReservationStatusPill(reservation: Reservation, today: LocalDate) {
    val ended = reservation.isCompleted(today)
    val negative = reservation.status != ReservationStatus.CONFIRMED
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            negative -> Color(0xFFF9DFDA)
            ended -> CampField
            else -> CampPositive
        },
        border = if (ended) BorderStroke(1.dp, CampOutline) else null,
    ) {
        Text(stringResource(entrantReservationStatusLabel(reservation, today)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge, color = CampOnSurface)
    }
}

/** Card date range with both weekdays, matching the entrant Figma list. */
fun entrantReservationDateRange(reservation: Reservation, locale: Locale = Locale.getDefault()): String {
    val startPattern = if (reservation.checkIn.year == reservation.checkOut.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
    val left = reservation.checkIn.format(DateTimeFormatter.ofPattern(startPattern, locale))
    val right = reservation.checkOut.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
    return "$left – $right"
}

/** Short range used in the cancellation confirmation. */
fun entrantCancellationDateRange(reservation: Reservation, locale: Locale = Locale.getDefault()): String {
    val start = reservation.checkIn
    val end = reservation.checkOut
    val left = start.format(DateTimeFormatter.ofPattern(
        if (start.year == end.year) "MMM d" else "MMM d, yyyy", locale))
    val right = end.format(DateTimeFormatter.ofPattern(
        if (start.year == end.year && start.month == end.month) "d, yyyy" else "MMM d, yyyy", locale))
    return "$left – $right"
}

/** Nights in the booked stay, excluding the check-out date. */
fun entrantReservationNights(reservation: Reservation): Int =
    ChronoUnit.DAYS.between(reservation.checkIn, reservation.checkOut).toInt()
