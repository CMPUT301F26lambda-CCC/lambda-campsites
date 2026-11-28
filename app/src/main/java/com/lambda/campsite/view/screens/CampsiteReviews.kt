/*
 * Purpose: Shows posted campsite reviews and the entrant's review sheet.
 * Outstanding issues: The team's rating sheet is a separate story (#26).
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Flow 17 review UI, following the read-only Figma sheet style. Human review
 * is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteReviewUiState
import com.lambda.campsite.controller.ReviewTextError
import com.lambda.campsite.model.CampsiteReview
import com.lambda.campsite.view.components.CampsiteReadingSection
import com.lambda.campsite.view.theme.CampOnSurface
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.CampSurfaceHigh
import com.lambda.campsite.view.theme.CampSurfaceLow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Review list and write action inside the entrant campsite details page. */
@Composable
fun CampsiteReviewsSection(
    state: CampsiteReviewUiState,
    onReviewRequested: () -> Unit,
    onRetry: () -> Unit,
) {
    CampsiteReadingSection(stringResource(R.string.campsite_reviews_heading)) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.loadFailed -> {
                Text(stringResource(R.string.campsite_reviews_load_failed),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
            }
            else -> {
                if (state.eligibleReservationId != null) {
                    Button(onClick = onReviewRequested) {
                        Text(stringResource(if (state.myReview == null)
                            R.string.campsite_reviews_write else R.string.campsite_reviews_edit))
                    }
                } else {
                    Text(stringResource(R.string.campsite_reviews_completed_stay),
                        style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
                }
                if (state.saved) {
                    Text(stringResource(R.string.campsite_reviews_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.reviews.isEmpty()) {
                    Text(stringResource(R.string.campsite_reviews_empty),
                        style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
                } else state.reviews.forEach { review ->
                    ReviewCard(review, review.reviewerId == state.currentUserId)
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: CampsiteReview, mine: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = CampSurfaceHigh, contentColor = CampOnSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (mine) R.string.campsite_reviews_yours
                else R.string.campsite_reviews_camper), style = MaterialTheme.typography.titleSmall)
            Text(review.text, style = MaterialTheme.typography.bodyMedium)
            Text(review.postedAt.toDate().toInstant().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.labelSmall, color = CampSecondaryText)
        }
    }
}

/** Bottom sheet styled after the Figma rating sheet, with text instead of stars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteReviewSheet(
    campsiteName: String,
    state: CampsiteReviewUiState,
    onDraftChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    if (!state.sheetOpen) return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CampSurfaceLow) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.campsite_reviews_sheet_title, campsiteName),
                style = MaterialTheme.typography.headlineSmall)
            TextField(value = state.draft, onValueChange = onDraftChanged,
                label = { Text(stringResource(R.string.campsite_reviews_label)) },
                placeholder = { Text(stringResource(R.string.campsite_reviews_hint)) },
                minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving,
                isError = state.textError != null || state.saveFailed)
            Text(stringResource(R.string.campsite_reviews_count, state.draft.length),
                style = MaterialTheme.typography.labelSmall, color = CampSecondaryText)
            val error = when {
                state.textError == ReviewTextError.EMPTY -> R.string.campsite_reviews_required
                state.textError == ReviewTextError.TOO_LONG -> R.string.campsite_reviews_too_long
                state.saveFailed -> R.string.campsite_reviews_save_failed
                else -> null
            }
            if (error != null) Text(stringResource(error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Text(stringResource(R.string.campsite_reviews_latest),
                style = MaterialTheme.typography.bodyMedium, color = CampSecondaryText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                    Text(stringResource(R.string.campsite_reviews_cancel))
                }
                Button(onClick = onSubmit, enabled = !state.isSaving) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(if (state.myReview == null)
                        R.string.campsite_reviews_post else R.string.campsite_reviews_save))
                }
            }
        }
    }
}
