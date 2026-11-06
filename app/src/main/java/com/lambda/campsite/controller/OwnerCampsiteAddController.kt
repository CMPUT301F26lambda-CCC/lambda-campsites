// AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with this follow-up.
/**
 * Purpose: ViewModel controller managing new campsite registration form state and saving (US 02.01.01, 02.01.02).
 * Handles the owner's campsite draft and its optional photo.
 * Outstanding issues: None known.
 */
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It integrated ProfileRepository to ensure ownerId is populated from the signed-in user,
 * prevented duplicate saves, cached the generated campsite ID across retries, extracted
 * error enums, and added coroutine cancellation re-throwing. Raghav Sethi reviewed,
 * tested, and committed it.
 */
package com.lambda.campsite.controller

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.ImageRepository
import com.lambda.campsite.model.repository.ProfileRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for registering a new campsite (US 02.01.01, US 02.01.02).
 *
 * @property form the shared campsite form data.
 * @property generatedId cached campsite document ID across retry attempts.
 */
data class OwnerCampsiteAddUiState(
    val form: CampsiteFormData = CampsiteFormData(),
    val generatedId: String? = null,
    val selectedPhoto: Bitmap? = null,
)

/**
 * Controller for registering a new campsite (US 02.01.01, US 02.01.02).
 *
 * Enforces validation rules, associates the campsite with the signed-in user's
 * profile ID, persists the campsite and its season calendar, and avoids duplicate
 * creations on double-tap or retries.
 *
 * @param campsiteRepository where campsites and availability calendars are persisted.
 * @param profileRepository provides the signed-in user's profile ID for ownership.
 */
class OwnerCampsiteAddController(
    private val campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteAddUiState())

    /** The current UI state of the registration form. */
    val state: StateFlow<OwnerCampsiteAddUiState> = _state.asStateFlow()

    /** Updates the campsite name and clears NAME error if populated. */
    fun onNameChange(name: String) {
        _state.update { current ->
            val updatedForm = current.form.copy(
                name = name,
                requiredFieldErrors = if (name.isNotBlank()) {
                    current.form.requiredFieldErrors - CampsiteRequiredField.NAME
                } else current.form.requiredFieldErrors,
            )
            current.copy(form = updatedForm)
        }
    }

    /** Updates the campsite location and clears LOCATION error if populated. */
    fun onLocationChange(location: String) {
        _state.update { current ->
            val updatedForm = current.form.copy(
                location = location,
                requiredFieldErrors = if (location.isNotBlank()) {
                    current.form.requiredFieldErrors - CampsiteRequiredField.LOCATION
                } else current.form.requiredFieldErrors,
            )
            current.copy(form = updatedForm)
        }
    }

    /** Updates the description / amenities text. */
    fun onDescriptionChange(description: String) {
        _state.update { it.copy(form = it.form.copy(description = description)) }
    }

    fun onPriceChange(price: String) {
        _state.update { it.copy(form = it.form.copy(pricePerNight = price, formError = null)) }
    }

    fun onRulesChange(rules: String) {
        _state.update { it.copy(form = it.form.copy(rules = rules)) }
    }

    fun onPhotoSelected(photo: Bitmap) {
        _state.update { it.copy(selectedPhoto = photo, form = it.form.copy(imageId = null)) }
    }

    fun onPhotoRemoved() {
        _state.update { it.copy(selectedPhoto = null, form = it.form.copy(imageId = null)) }
    }

    /** Updates maximum guest occupancy (minimum 0). */
    fun onOccupancyChange(maxOccupancy: Int) {
        val safe = maxOccupancy.coerceAtLeast(0)
        _state.update { current ->
            val updatedForm = current.form.copy(
                maxOccupancy = safe,
                requiredFieldErrors = if (safe >= 1) {
                    current.form.requiredFieldErrors - CampsiteRequiredField.MAX_OCCUPANCY
                } else current.form.requiredFieldErrors,
            )
            current.copy(form = updatedForm)
        }
    }

    /** Updates coordinates and label from the map location picker. */
    fun onLocationPinSet(lat: Double, lng: Double, name: String?) {
        _state.update { current ->
            val updatedForm = current.form.copy(
                latitude = lat,
                longitude = lng,
                locationPinName = name,
                location = if (current.form.location.isBlank() && !name.isNullOrBlank()) name else current.form.location,
            )
            current.copy(form = updatedForm)
        }
    }

    /** Sets operating season range and validates date order. */
    fun onSeasonChange(start: LocalDate?, end: LocalDate?) {
        _state.update { current ->
            val tempForm = current.form.copy(seasonStart = start, seasonEnd = end)
            val seasonError = tempForm.checkSeasonError()
            current.copy(form = tempForm.copy(seasonError = seasonError))
        }
    }

    /** Adds a tag to the draft campsite. */
    fun onAddTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            val tags = current.form.tags
            if (trimmed !in tags) {
                current.copy(form = current.form.copy(tags = tags + trimmed))
            } else current
        }
    }

    /** Removes a tag from the draft campsite. */
    fun onRemoveTag(tag: String) {
        _state.update { current ->
            current.copy(form = current.form.copy(tags = current.form.tags - tag))
        }
    }

    /** Resets the registration form to blank state. */
    fun reset() {
        _state.value = OwnerCampsiteAddUiState()
    }

    /**
     * Validates and saves the new campsite (US 02.01.01).
     *
     * Prevents duplicate in-flight saves, sets ownerId from the signed-in user,
     * keeps the generated campsite ID on retry if season save fails, and calls
     * [onSuccess] on complete persistence.
     *
     * @param onSuccess invoked with the saved [Campsite].
     */
    fun onSave(onSuccess: (Campsite) -> Unit) {
        if (_state.value.form.isSaving) return

        val current = _state.value
        val (draft, missingFields) = current.form.toCampsiteDraft(
            id = current.generatedId ?: "",
            ownerId = "",
        )
        val seasonError = current.form.checkSeasonError()
        val priceError = current.form.checkPriceError()

        if (missingFields.isNotEmpty() || seasonError != null || priceError != null) {
            _state.update {
                it.copy(
                    form = it.form.copy(
                        requiredFieldErrors = missingFields,
                        seasonError = seasonError,
                        formError = priceError,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(form = it.form.copy(isSaving = true, formError = null)) }
            try {
                // Ensure ownerId is the signed-in user's profile ID
                val profile = profileRepository.currentProfile().first()
                val targetId = _state.value.generatedId ?: campsiteRepository.newCampsiteId()
                _state.update { it.copy(generatedId = targetId) }

                val imageId = current.form.imageId ?: current.selectedPhoto?.let { photo ->
                    requireNotNull(imageRepository) { "Photo repository unavailable" }.upload(photo).also { uploadedId ->
                        _state.update { it.copy(form = it.form.copy(imageId = uploadedId)) }
                    }
                }
                val campsiteToSave = draft.copy(id = targetId, ownerId = profile.deviceId, imageId = imageId)
                campsiteRepository.save(campsiteToSave)

                val start = current.form.seasonStart
                val end = current.form.seasonEnd
                if (start != null && end != null && end.isAfter(start)) {
                    campsiteRepository.setSeason(targetId, start, end)
                }

                // Reset form on success so subsequent registers start fresh
                _state.value = OwnerCampsiteAddUiState()
                onSuccess(campsiteToSave)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(form = it.form.copy(isSaving = false, formError = CampsiteFormError.SAVE_FAILED))
                }
            }
        }
    }

    /** Clears save error once displayed. */
    fun onErrorDismissed() {
        _state.update { it.copy(form = it.form.copy(formError = null)) }
    }

    companion object {
        /** Factory creating [OwnerCampsiteAddController] with repositories from AppContainer. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                OwnerCampsiteAddController(
                    campsiteRepository = app.container.campsiteRepository,
                    profileRepository = app.container.profileRepository,
                    imageRepository = app.container.imageRepository,
                )
            }
        }
    }
}
