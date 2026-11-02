/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the form state management, validation using Campsite.validate,
 * season validation, repository save with new ID generation, syntax and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
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
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.model.repository.CampsiteRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the campsite registration form (US 02.01.01, 02.01.02).
 *
 * @property name the campsite name entered by the owner.
 * @property location where the campsite is located.
 * @property description optional amenities and description text.
 * @property maxOccupancy maximum number of guests (minimum 1).
 * @property latitude optional geographic latitude from the map pin.
 * @property longitude optional geographic longitude from the map pin.
 * @property locationPinName human-readable name of the pinned location.
 * @property seasonStart date when the campsite season opens.
 * @property seasonEnd date when the campsite season closes.
 * @property tags list of tags aiding search discovery.
 * @property hasPhoto whether a photo has been attached.
 * @property errors required fields that are missing, from [Campsite.validate].
 * @property seasonError message explaining invalid season dates, if any.
 * @property isSaving `true` while the campsite is being saved to Firestore.
 * @property saveFailed `true` if saving failed, for example due to network offline.
 */
data class OwnerCampsiteAddUiState(
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val maxOccupancy: Int = 1,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationPinName: String? = null,
    val seasonStart: LocalDate? = null,
    val seasonEnd: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val hasPhoto: Boolean = false,
    val errors: Set<CampsiteRequiredField> = emptySet(),
    val seasonError: String? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * Controller for registering a new campsite (US 02.01.01, US 02.01.02).
 *
 * It manages draft inputs, enforces validation rules before persistence,
 * requests a unique campsite ID from [repository], and saves the campsite
 * and its season calendar.
 *
 * @param repository where new campsites and their availability calendars are saved.
 */
class OwnerCampsiteAddController(
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteAddUiState())

    /** The current UI state of the registration form. */
    val state: StateFlow<OwnerCampsiteAddUiState> = _state.asStateFlow()

    /** Updates the draft campsite name and clears field validation error if addressed. */
    fun onNameChange(name: String) {
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.NAME in current.errors && name.isNotBlank()) {
                current.errors - CampsiteRequiredField.NAME
            } else current.errors
            current.copy(name = name, errors = newErrors)
        }
    }

    /** Updates the draft location and clears field validation error if addressed. */
    fun onLocationChange(location: String) {
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.LOCATION in current.errors && location.isNotBlank()) {
                current.errors - CampsiteRequiredField.LOCATION
            } else current.errors
            current.copy(location = location, errors = newErrors)
        }
    }

    /** Updates the description / amenities text. */
    fun onDescriptionChange(description: String) {
        _state.update { it.copy(description = description) }
    }

    /** Updates the maximum guest occupancy (coerced to at least 0). */
    fun onOccupancyChange(maxOccupancy: Int) {
        val safeOccupancy = maxOccupancy.coerceAtLeast(0)
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.MAX_OCCUPANCY in current.errors && safeOccupancy >= 1) {
                current.errors - CampsiteRequiredField.MAX_OCCUPANCY
            } else current.errors
            current.copy(maxOccupancy = safeOccupancy, errors = newErrors)
        }
    }

    /** Sets the geographic coordinates and label from the map location picker. */
    fun onLocationPinSet(latitude: Double, longitude: Double, name: String?) {
        _state.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                locationPinName = name,
                location = if (it.location.isBlank() && !name.isNullOrBlank()) name else it.location,
            )
        }
    }

    /** Sets or clears the campsite's operating season dates. */
    fun onSeasonChange(start: LocalDate?, end: LocalDate?) {
        val seasonError = when {
            start != null && end != null && !end.isAfter(start) -> "Season close must be after season open."
            (start != null && end == null) || (start == null && end != null) -> "Both open and close dates are required."
            else -> null
        }
        _state.update { it.copy(seasonStart = start, seasonEnd = end, seasonError = seasonError) }
    }

    /** Adds a tag to the draft campsite. */
    fun onAddTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            if (trimmed !in current.tags) current.copy(tags = current.tags + trimmed) else current
        }
    }

    /** Removes a tag from the draft campsite. */
    fun onRemoveTag(tag: String) {
        _state.update { it.copy(tags = it.tags - tag) }
    }

    /** Sets whether a photo is chosen. */
    fun onPhotoSelected() {
        _state.update { it.copy(hasPhoto = true) }
    }

    /** Removes the chosen photo. */
    fun onPhotoRemoved() {
        _state.update { it.copy(hasPhoto = false) }
    }

    /**
     * Validates required fields and saves the campsite (US 02.01.01).
     *
     * @param onSuccess callback invoked with the created [Campsite] upon successful save.
     */
    fun onSave(onSuccess: (Campsite) -> Unit) {
        val current = _state.value
        val draft = Campsite(
            id = "",
            ownerId = "",
            name = current.name,
            location = current.location,
            description = current.description,
            maxOccupancy = current.maxOccupancy,
            latitude = current.latitude,
            longitude = current.longitude,
            tags = current.tags,
        )

        val missing = draft.validate()
        if (missing.isNotEmpty() || current.seasonError != null) {
            _state.update { it.copy(errors = missing) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveFailed = false) }
            try {
                val newId = repository.newCampsiteId()
                val campsiteToSave = draft.copy(id = newId)
                repository.save(campsiteToSave)

                val start = current.seasonStart
                val end = current.seasonEnd
                if (start != null && end != null && end.isAfter(start)) {
                    repository.setSeason(newId, start, end)
                }

                _state.update { it.copy(isSaving = false, saveFailed = false) }
                onSuccess(campsiteToSave)
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    /** Clears the save failed flag once the error message is presented. */
    fun onErrorDismissed() {
        _state.update { it.copy(saveFailed = false) }
    }

    companion object {
        /** Creates an [OwnerCampsiteAddController] with the app's repository from AppContainer. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerCampsiteAddController(application.container.campsiteRepository)
            }
        }
    }
}
