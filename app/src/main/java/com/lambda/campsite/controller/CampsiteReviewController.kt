/*
 * Purpose: Loads a campsite's reviews and handles posting after a completed stay.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with the
 * Flow 17 review state, eligibility, and retry behavior. Human review is pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.CampsiteReview
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.repository.CampsiteReviewRepository
import com.lambda.campsite.model.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Validation errors shown beside the review text field. */
enum class ReviewTextError { EMPTY, TOO_LONG }

/**
 * Review list and form state for one campsite (US 01.13.02).
 *
 * @property reviews posted reviews, newest first.
 * @property currentUserId the signed-in entrant, found from their reservations.
 * @property eligibleReservationId a completed stay at this campsite, if any.
 * @property draft text in the review sheet.
 * @property sheetOpen whether the review sheet is shown.
 * @property isLoading whether reviews and reservations are still loading.
 * @property loadFailed whether their live listener failed.
 * @property isSaving whether a post is in progress.
 * @property saveFailed whether the last post failed and can be retried.
 * @property textError local form validation result.
 * @property saved whether a post succeeded and should be announced.
 */
data class CampsiteReviewUiState(
    val reviews: List<CampsiteReview> = emptyList(),
    val currentUserId: String? = null,
    val eligibleReservationId: String? = null,
    val draft: String = "",
    val sheetOpen: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val textError: ReviewTextError? = null,
    val saved: Boolean = false,
) {
    /** This entrant's existing review, if they posted one. */
    val myReview: CampsiteReview?
        get() = reviews.firstOrNull { it.reviewerId == currentUserId }
}

/** Handles written reviews without changing the rating story or its backend. */
class CampsiteReviewController(
    private val campsiteId: String,
    private val reviewRepository: CampsiteReviewRepository,
    private val reservationRepository: ReservationRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CampsiteReviewUiState())
    /** Current review list and form actions for the campsite details screen. */
    val state: StateFlow<CampsiteReviewUiState> = mutableState.asStateFlow()
    private var listener: Job? = null
    private var reservations = emptyList<Reservation>()

    init { observe() }

    /** Refreshes eligibility when the app resumes after check-out. */
    fun onResume() {
        mutableState.update { it.copy(eligibleReservationId = completedStay()?.id) }
    }

    /** Opens the sheet only after this entrant has a completed stay. */
    fun onReviewRequested() {
        mutableState.update { current ->
            if (current.eligibleReservationId == null || current.isSaving) current
            else current.copy(sheetOpen = true, draft = current.myReview?.text.orEmpty(),
                saveFailed = false, textError = null, saved = false)
        }
    }

    /** Closes the sheet without changing the posted review. */
    fun onDismiss() {
        mutableState.update { if (it.isSaving) it else it.copy(sheetOpen = false,
            saveFailed = false, textError = null) }
    }

    /** Keeps the draft until a successful post or an explicit dismissal. */
    fun onDraftChanged(text: String) {
        mutableState.update { it.copy(draft = text, saveFailed = false, textError = null) }
    }

    /** Posts a review, or leaves the sheet open with an error so it can be retried. */
    fun onSubmit() {
        val current = mutableState.value
        val reservationId = current.eligibleReservationId ?: return
        if (!current.sheetOpen || current.isSaving) return
        val text = current.draft.trim()
        val error = when {
            text.isEmpty() -> ReviewTextError.EMPTY
            text.length > MAX_REVIEW_LENGTH -> ReviewTextError.TOO_LONG
            else -> null
        }
        if (error != null) {
            mutableState.update { it.copy(textError = error) }
            return
        }
        mutableState.update { it.copy(isSaving = true, saveFailed = false, textError = null) }
        viewModelScope.launch {
            try {
                reviewRepository.post(campsiteId, reservationId, text)
                mutableState.update { it.copy(isSaving = false, sheetOpen = false,
                    draft = "", saved = true) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                mutableState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Starts the review and reservation listeners again after a load failure. */
    fun onRetry() = observe()

    private fun completedStay(): Reservation? = reservations
        .filter { it.campsiteId == campsiteId && it.isCompleted(today()) }
        .maxByOrNull { it.checkOut }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            combine(reviewRepository.reviews(campsiteId), reservationRepository.myReservations()) {
                    reviews, stays -> reviews to stays
                }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { (reviews, stays) ->
                    reservations = stays
                    mutableState.update { it.copy(reviews = reviews,
                        currentUserId = stays.firstOrNull()?.guestId,
                        eligibleReservationId = completedStay()?.id,
                        isLoading = false, loadFailed = false) }
                }
        }
    }

    companion object {
        private const val MAX_REVIEW_LENGTH = 1000

        /** Supplies the repositories for one campsite details route. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                CampsiteReviewController(campsiteId,
                    app.container.campsiteReviewRepository, app.container.reservationRepository)
            }
        }
    }
}
