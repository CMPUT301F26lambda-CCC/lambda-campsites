/*
 * AI use: GPT-6 (OpenAI), through Codex, assisted with the Flow 11 Figma status styling.
 */
package com.lambda.campsite.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.view.theme.Flow11Colors

/** The owner reservation status badge shared by list cards and details. */
@Composable
fun OwnerReservationStatusPill(status: ReservationStatus, completed: Boolean = false) {
    val label = when {
        status == ReservationStatus.CANCELLED -> R.string.reservation_status_cancelled
        status == ReservationStatus.REJECTED -> R.string.reservation_status_rejected
        completed -> R.string.reservation_status_completed
        else -> R.string.reservation_status_confirmed
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            status != ReservationStatus.CONFIRMED -> Flow11Colors.cancelled
            completed -> Flow11Colors.field
            else -> Flow11Colors.selected
        },
        border = if (completed) BorderStroke(1.dp, Flow11Colors.outline) else null,
    ) {
        Text(
            stringResource(label),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Flow11Colors.text,
        )
    }
}
