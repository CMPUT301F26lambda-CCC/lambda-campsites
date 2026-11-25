/*
 * Purpose: the entrant's profile photo (US 01.08.02, Flow 18). It uploads a
 * photo the entrant picks and saves it as their profile's imageId, and finds
 * the photo's address so the Profile and Edit Profile screens can show it.
 * With no photo, the screens show the entrant's initials.
 * It also removes the photo, so the initials show again.
 *
 * Outstanding issues:
 *1.The photo isn't kept until the backend saves imageId (in ProfileFields
 *  and the profile security rule
 *
 * AI Use: Claude Opus 5.5 (Anthropic), November 2026, helped Harmanjoth Kaur identify the correct
 * photo address and handle the cancellation when the user leaves the screen while a photo is still
 * uploading. It also assisted with Kotlin syntax and refining KDoc comments.
 */

package com.lambda.campsite.controller

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.repository.ImageRepository
import com.lambda.campsite.model.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the profile screens show about the photo (US 01.08.02).
 * @property hasPhoto `true` when the profile has a photo.
 * @property photoUrl where to load the photo from, or `null` while it's loading or
 *   when there's no photo. The screens show the initials until it's set.
 * @property saving `true` while a photo is uploading or being removed
 * @property failed `true` when the last change failed, for example offline.
 */
data class ProfilePhotoUiState(
    val hasPhoto: Boolean = false,
    val photoUrl: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Adds, replaces and removes the entrant's profile photo (US 01.08.02).
 *
 * It listens to the profile like [ProfileController] does, but only for its
 * photo, so ProfileController doesn't change. A new photo is uploaded through
 * [ImageRepository] and its ID saved as the profile's `imageId`. Storage
 * doesn't allow overwriting a photo, so replacing one uploads a new image and
 * points `imageId` at it.
 *
 * @param profiles where the profile is loaded from and saved to.
 * @param images where photos are uploaded and found.
 */
class ProfilePhotoController(
    private val profiles: ProfileRepository,
    private val images: ImageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfilePhotoUiState())
    private var profile: Profile? = null
    private var shownImageId: String? = null

    /** State collected by the Profile and Edit Profile screens. */
    val state: StateFlow<ProfilePhotoUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            // ProfileController already shows an error when the profile can't load.
            profiles.currentProfile().catch { }.collect { loaded ->
                profile = loaded
                mutableState.update { it.copy(hasPhoto = loaded.imageId != null) }
                if (loaded.imageId != shownImageId) {
                    shownImageId = loaded.imageId
                    findPhoto(loaded.imageId)
                }
            }
        }
    }

    /**
     * Uploads [photo] and makes it the profile photo, replacing any earlier one.
     * Does nothing while another upload is still running.
     */
    fun onPhotoPicked(photo: Bitmap) {
        if (profile == null || state.value.saving) return
        save { images.upload(photo) }

    }

    /**
     * Removes the profile photo, so the screens show the initials again.
     * Does nothing when there's no photo or while another change is saving.
     */
    fun onRemovePhoto() {
        if (profile?.imageId == null || state.value.saving) return
        save { null }
    }

    private fun save(newImageId: suspend () -> String?)  {
        mutableState.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val imageId = newImageId()
                // Uses the newest profile, in case it changed while the photo uploaded.
                val current = checkNotNull(profile)
                profiles.save(current.copy(imageId = imageId))
                mutableState.update { it.copy(saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    // Offline, the photo address can't be found, so the initials stay.
    private fun findPhoto(imageId: String?) {
        if (imageId == null) {
            mutableState.update { it.copy(photoUrl = null) }
            return
        }
        viewModelScope.launch {
            val url = try {
                images.downloadUrl(imageId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (shownImageId == imageId) mutableState.update { it.copy(photoUrl = url) }
        }
    }

    companion object {
        /** Creates the controller with the app's profile and image repositories. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                ProfilePhotoController(app.container.profileRepository, app.container.imageRepository)
            }
        }
    }
}