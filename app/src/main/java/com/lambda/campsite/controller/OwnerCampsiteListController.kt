/*
 * Purpose: Loads the current owner's campsite list and its UI state.
 * Outstanding issues: None known.
 */
// AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with this follow-up.
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the ViewModel and StateFlow setup, error handling, syntax and KDoc,
 * following the architecture guide. Raghav Sethi reviewed, tested, and committed it.
 */

 /**
 * Purpose: Loads and manages the campsites registered by the owner.
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
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val photoUrls: Map<String, String> = emptyMap(),
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
    private val imageRepository: ImageRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteListUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<OwnerCampsiteListUiState> = _state.asStateFlow()

    private var listJob: Job? = null

    init { retry() }

    /** Reconnects the live Firestore listener after a failure. */
    fun retry() {
        listJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        listJob = viewModelScope.launch {
            repository.myCampsites()
                .catch { _state.update { it.copy(isLoading = false, error = CampsiteListError.LOAD_FAILED) } }
                .collect { list ->
                    _state.update { it.copy(campsites = list, isLoading = false, error = null) }
                    list.filter { it.imageId != null }.forEach { campsite ->
                        if (campsite.imageId !in _state.value.photoUrls) {
                            viewModelScope.launch {
                                try {
                                    val url = imageRepository?.downloadUrl(campsite.imageId!!)
                                    if (url != null) _state.update { current ->
                                        current.copy(photoUrls = current.photoUrls + (campsite.imageId!! to url))
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) { /* Keep the no-photo placeholder while offline. */ }
                            }
                        }
                    }
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
                OwnerCampsiteListController(application.container.campsiteRepository, application.container.imageRepository)
            }
        }
    }
}
