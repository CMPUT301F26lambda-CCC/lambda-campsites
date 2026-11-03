/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the ViewModel and StateFlow setup, error handling, syntax and KDoc,
 * following the architecture guide. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why loading the campsite list failed. */
enum class CampsiteListError {
    /** The campsite list couldn't be loaded from Firestore. */
    LOAD_FAILED,
}

/**
 * State for the owner's campsite list screen (US 02.03.01).
 *
 * @property campsites the list of campsites owned by this user.
 * @property isLoading `true` while the campsites are still loading.
 * @property error why the last load failed, or `null` if loading succeeded.
 */
data class OwnerCampsiteListUiState(
    val campsites: List<Campsite> = emptyList(),
    val isLoading: Boolean = true,
    val error: CampsiteListError? = null,
)

/**
 * Controller for the owner's campsite list (US 02.03.01). It is an Android [ViewModel]
 * that survives screen rotation.
 *
 * It listens to [repository] for the owner's campsites and publishes [state]
 * for [com.lambda.campsite.view.screens.OwnerCampsiteListScreen].
 *
 * @param repository where campsites are loaded from.
 */
class OwnerCampsiteListController(
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteListUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<OwnerCampsiteListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.myCampsites()
                .catch { _state.update { it.copy(isLoading = false, error = CampsiteListError.LOAD_FAILED) } }
                .collect { list ->
                    _state.update { it.copy(campsites = list, isLoading = false, error = null) }
                }
        }
    }

    /** Clears the load error once the screen has displayed it. */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        /** Creates an [OwnerCampsiteListController] using the campsite repository from [LambdaApplication.container]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerCampsiteListController(application.container.campsiteRepository)
            }
        }
    }
}
