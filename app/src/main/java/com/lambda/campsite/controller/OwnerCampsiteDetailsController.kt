/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with ViewModel setup, StateFlow combining campsite and availability flows,
 * error handling, syntax and KDoc. Raghav Sethi reviewed, tested, and committed it.
 */

 /**
 * Purpose: Loads campsite details and availability for the details and QR code screens.
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the owner's campsite details screen (US 02.01.04).
 *
 * @property campsite the campsite details, or `null` while loading or if not found.
 * @property availability the campsite's seasonal calendar and booked nights.
 * @property isLoading `true` while the campsite is being retrieved from Firestore.
 * @property error load error message, if retrieval failed.
 */
data class OwnerCampsiteDetailsUiState(
    val campsite: Campsite? = null,
    val availability: Availability? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Controller for viewing all details of a campsite for its owner (US 02.01.04).
 *
 * It listens to [CampsiteRepository.campsite] and [CampsiteRepository.availability] for live updates
 * to ratings, calendar availability, and details.
 *
 * @param campsiteId document ID of the campsite to display.
 * @param repository where campsite and availability data are loaded from.
 */
class OwnerCampsiteDetailsController(
    private val campsiteId: String,
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteDetailsUiState())

    /** The current UI state of the campsite details screen. */
    val state: StateFlow<OwnerCampsiteDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.campsite(campsiteId),
                repository.availability(campsiteId),
            ) { camp, avail ->
                OwnerCampsiteDetailsUiState(
                    campsite = camp,
                    availability = avail,
                    isLoading = false,
                    error = if (camp == null) "Campsite not found." else null,
                )
            }.catch { e ->
                _state.update { it.copy(isLoading = false, error = "Failed to load campsite details.") }
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    companion object {
        /** Creates a [ViewModelProvider.Factory] supplying [campsiteId] to [OwnerCampsiteDetailsController]. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerCampsiteDetailsController(campsiteId, application.container.campsiteRepository)
            }
        }
    }
}
