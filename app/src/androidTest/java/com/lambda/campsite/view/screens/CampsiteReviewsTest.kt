/*
 * Purpose: Checks the entrant review section and write sheet in Compose.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with these
 * Flow 17 UI tests. Human review is pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteReviewUiState
import com.lambda.campsite.controller.ReviewTextError
import com.lambda.campsite.model.CampsiteReview
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Verifies review eligibility, posted text, and the write action. */
class CampsiteReviewsTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun completedGuestCanOpenSheetAndEnterReview() {
        val state = mutableStateOf(CampsiteReviewUiState(
            eligibleReservationId = "stay-1", isLoading = false))
        var submitted = false
        compose.setContent { LambdaCampsiteTheme {
            CampsiteReviewsSection(state.value,
                onReviewRequested = { state.value = state.value.copy(sheetOpen = true) },
                onRetry = {})
            CampsiteReviewSheet("Moose Lake", state.value,
                onDraftChanged = { state.value = state.value.copy(draft = it) },
                onDismiss = { state.value = state.value.copy(sheetOpen = false) },
                onSubmit = { submitted = true })
        } }

        compose.onNodeWithText(text(R.string.campsite_reviews_write)).performClick()
        compose.onNodeWithText(text(R.string.campsite_reviews_label)).performTextInput("Great trails.")
        compose.onNodeWithText(text(R.string.campsite_reviews_post)).performClick()

        compose.runOnIdle {
            assertEquals("Great trails.", state.value.draft)
            assertEquals(true, submitted)
        }
    }

    @Test fun guestWithoutCompletedStaySeesReviewsButCannotWrite() {
        val state = CampsiteReviewUiState(isLoading = false,
            reviews = listOf(CampsiteReview("other-guest", "stay-2", "Quiet campsite.",
                Timestamp.now())))
        compose.setContent { LambdaCampsiteTheme {
            CampsiteReviewsSection(state, onReviewRequested = {}, onRetry = {})
        } }

        compose.onNodeWithText("Quiet campsite.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.campsite_reviews_completed_stay)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.campsite_reviews_write)).assertDoesNotExist()
    }

    @Test fun sheetShowsValidationAndSaveFailureWithoutLosingDraft() {
        val state = CampsiteReviewUiState(eligibleReservationId = "stay-1", sheetOpen = true,
            isLoading = false, draft = "My review", textError = ReviewTextError.TOO_LONG)
        compose.setContent { LambdaCampsiteTheme {
            CampsiteReviewSheet("Moose Lake", state, {}, {}, {})
        } }

        compose.onNodeWithText(text(R.string.campsite_reviews_too_long)).assertIsDisplayed()
        compose.onNodeWithText("My review").assertIsDisplayed()
    }
}
