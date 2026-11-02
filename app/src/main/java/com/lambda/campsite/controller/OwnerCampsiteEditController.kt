/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with prefilling existing campsite data, field validation, preserving
 * the existing campsite ID so QR codes stay valid (US 02.01.03), syntax and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.model.repository.CampsiteRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for editing an existing campsite (US 02.01.03).
 *
 * @property campsiteId the document ID of the campsite being edited.
 * @property name campsite name.
 * @property location campsite location text.
 * @property description description and amenities.
 * @property maxOccupancy maximum guests allowed.
 * @property latitude map pin latitude.
 * @property longitude map pin longitude.
 * @property locationPinName name of the pinned place.
 * @property seasonStart date the operating season opens.
 * @property seasonEnd date the operating season closes.
 * @property tags search tags.
 * @property hasPhoto whether a photo is set.
 * @property errors missing required fields.
 * @property seasonError error message for invalid season dates.
 * @property isLoading `true` while the campsite is being retrieved initially.
 * @property isSaving `true` while updates are being saved to Firestore.
 * @property saveFailed `true` if updating Firestore failed.
 */
data class OwnerCampsiteEditUiState(
    val campsiteId: String = "",
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
    val hasPhoto: Boolean = true,
    val errors: Set<CampsiteRequiredField> = emptySet(),
    val seasonError: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/**
 * Controller for editing an existing campsite (US 02.01.03).
 *
 * Pre-fills the registration form with current details. When saving, it preserves
 * the original campsite ID and owner ID so that already printed or distributed
 * QR codes remain completely valid.
 *
 * @param campsiteId ID of the campsite to edit.
 * @param repository where changes are persisted.
 */
class OwnerCampsiteEditController(
    private val campsiteId: String,
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteEditUiState(campsiteId = campsiteId))

    /** The current UI state of the campsite edit screen. */
    val state: StateFlow<OwnerCampsiteEditUiState> = _state.asStateFlow()

    private var originalCampsite: Campsite? = null

    init {
        viewModelScope.launch {
            val campsite = repository.campsite(campsiteId).firstOrNull()
            val availability = repository.availability(campsiteId).firstOrNull()

            if (campsite != null) {
                originalCampsite = campsite
                _state.update {
                    it.copy(
                        name = campsite.name,
                        location = campsite.location,
                        description = campsite.description,
                        maxOccupancy = campsite.maxOccupancy,
                        latitude = campsite.latitude,
                        longitude = campsite.longitude,
                        tags = campsite.tags,
                        seasonStart = availability?.seasonStart,
                        seasonEnd = availability?.seasonEnd,
                        hasPhoto = true,
                        isLoading = false,
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false, saveFailed = true) }
            }
        }
    }

    /** Updates the name field. */
    fun onNameChange(name: String) {
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.NAME in current.errors && name.isNotBlank()) {
                current.errors - CampsiteRequiredField.NAME
            } else current.errors
            current.copy(name = name, errors = newErrors)
        }
    }

    /** Updates the location field. */
    fun onLocationChange(location: String) {
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.LOCATION in current.errors && location.isNotBlank()) {
                current.errors - CampsiteRequiredField.LOCATION
            } else current.errors
            current.copy(location = location, errors = newErrors)
        }
    }

    /** Updates description. */
    fun onDescriptionChange(description: String) {
        _state.update { it.copy(description = description) }
    }

    /** Updates max occupancy. */
    fun onOccupancyChange(occupancy: Int) {
        val safe = occupancy.coerceAtLeast(0)
        _state.update { current ->
            val newErrors = if (CampsiteRequiredField.MAX_OCCUPANCY in current.errors && safe >= 1) {
                current.errors - CampsiteRequiredField.MAX_OCCUPANCY
            } else current.errors
            current.copy(maxOccupancy = safe, errors = newErrors)
        }
    }

    /** Sets coordinates and label from the map picker. */
    fun onLocationPinSet(lat: Double, lng: Double, name: String?) {
        _state.update {
            it.copy(
                latitude = lat,
                longitude = lng,
                locationPinName = name,
                location = if (it.location.isBlank() && !name.isNullOrBlank()) name else it.location,
            )
        }
    }

    /** Sets operating season range. */
    fun onSeasonChange(start: LocalDate?, end: LocalDate?) {
        val seasonError = when {
            start != null && end != null && !end.isAfter(start) -> "Season close must be after season open."
            (start != null && end == null) || (start == null && end != null) -> "Both open and close dates are required."
            else -> null
        }
        _state.update { it.copy(seasonStart = start, seasonEnd = end, seasonError = seasonError) }
    }

    /** Adds a tag. */
    fun onAddTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            if (trimmed !in current.tags) current.copy(tags = current.tags + trimmed) else current
        }
    }

    /** Removes a tag. */
    fun onRemoveTag(tag: String) {
        _state.update { it.copy(tags = it.tags - tag) }
    }

    /** Photo replaced. */
    fun onPhotoReplaced() {
        _state.update { it.copy(hasPhoto = true) }
    }

    /** Photo removed. */
    fun onPhotoRemoved() {
        _state.update { it.copy(hasPhoto = false) }
    }

    /**
     * Validates and saves edits, maintaining the original campsite ID (US 02.01.03).
     */
    fun onSave(onSuccess: () -> Unit) {
        val current = _state.value
        val original = originalCampsite ?: return

        val updated = original.copy(
            name = current.name,
            location = current.location,
            description = current.description,
            maxOccupancy = current.maxOccupancy,
            latitude = current.latitude,
            longitude = current.longitude,
            tags = current.tags,
        )

        val missing = updated.validate()
        if (missing.isNotEmpty() || current.seasonError != null) {
            _state.update { it.copy(errors = missing) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveFailed = false) }
            try {
                repository.save(updated)

                val start = current.seasonStart
                val end = current.seasonEnd
                if (start != null && end != null && end.isAfter(start)) {
                    repository.setSeason(campsiteId, start, end)
                }

                _state.update { it.copy(isSaving = false, saveFailed = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        /** Factory creating [OwnerCampsiteEditController] for [campsiteId]. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerCampsiteEditController(campsiteId, application.container.campsiteRepository)
            }
        }
    }
}
