/*
 * Purpose: entrant campsite details and season state for Flow 6 (US 01.01.05).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: joining is delegated to LotteryController (Flow 7).
 * AI use: OpenAI GPT-6 through Codex assisted with state/listener logic and KDoc; human review pending.
 */

 /**
 * Purpose: Loads and manages the owner's list of registered campsites.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Distinguishes unavailable sites from network/permission failures. */
enum class CampsiteDetailsError { NOT_AVAILABLE, LOAD_FAILED }

/**
 * Entrant details state.
 * @property campsite published campsite, or null until available.
 * @property availability season and booked nights, or null if no season is set.
 * @property isLoading whether a live result has not yet arrived.
 * @property error unavailable or failed-to-load state; never present another owner's controls.
 */
data class CampsiteUiState(
    val campsite: Campsite? = null, val availability: Availability? = null,
    val isLoading: Boolean = true, val error: CampsiteDetailsError? = null,
)

/**
 * Loads entrant-visible details using the existing repository and supports retry.
 * @param campsiteId selected campsite ID from navigation.
 * @param repository shared campsite/availability repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CampsiteController(private val campsiteId: String, private val repository: CampsiteRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CampsiteUiState())
    private var listener: Job? = null
    /** Current published campsite and calendar for the view. */
    val state: StateFlow<CampsiteUiState> = mutableState.asStateFlow()
    init { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.value = CampsiteUiState()
        listener = viewModelScope.launch {
            repository.campsite(campsiteId).flatMapLatest { campsite ->
                // A removed/unpublished site must not wait for a calendar read to finish.
                if (campsite == null || !campsite.published) {
                    flowOf(CampsiteUiState(isLoading = false, error = CampsiteDetailsError.NOT_AVAILABLE))
                } else {
                    repository.availability(campsiteId).map { calendar ->
                        CampsiteUiState(campsite = campsite, availability = calendar, isLoading = false)
                    }
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                val unavailable = failure is FirebaseFirestoreException &&
                    failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                mutableState.value = CampsiteUiState(isLoading = false,
                    error = if (unavailable) CampsiteDetailsError.NOT_AVAILABLE else CampsiteDetailsError.LOAD_FAILED)
            }.collect { mutableState.value = it }
        }
    }

    /** Retries details loading; does not perform any data write. */
    fun onRetry() = observe()

    companion object {
        /** Supplies the selected [campsiteId] and shared app repository. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                CampsiteController(campsiteId, app.container.campsiteRepository)
            }
        }
    }
}
