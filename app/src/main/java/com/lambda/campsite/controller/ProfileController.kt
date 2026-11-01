/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the ViewModel and
 * StateFlow setup, syntax and KDoc. It also helped add the load error when the
 * profile moved to Firestore. Sashreek Addanki reviewed it, ran the tests and
 * committed it.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.model.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last profile action didn't work. The screen turns each one into a message. */
enum class ProfileError {
    /** The profile doesn't hold the role the user tried to switch to. */
    ROLE_NOT_HELD,

    /** An admin banned this profile from being an owner (US 03.07.01). */
    OWNER_BANNED,

    /** The profile couldn't be saved, for example because the phone is offline. */
    SAVE_FAILED,

    /** The profile couldn't be loaded, for example because Firestore refused to send it. */
    LOAD_FAILED,
}

/**
 * Everything the profile screen and the nav bar need.
 *
 * @property profile the signed-in profile, or `null` while it's still loading.
 * @property activeRole the role the app is being used as, which picks the nav bar.
 * @property error why the last action failed, or `null` if it worked.
 */
data class ProfileUiState(
    val profile: Profile? = null,
    val activeRole: Role = Role.ENTRANT,
    val error: ProfileError? = null,
)

/**
 * Controller for the profile screen and for the role the app is used as
 * (US 03.11.01). It's an Android [ViewModel], so it survives screen rotation.
 *
 * It listens to [repository] for profile changes and publishes [state] for the
 * screens to show. Each `on` method handles one user action.
 *
 * @param repository where the profile is loaded from and saved to.
 */
class ProfileController(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())

    /** The current state. Screens read it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.currentProfile()
                // Without this, a loading error would crash the app.
                .catch { _state.update { it.copy(error = ProfileError.LOAD_FAILED) } }
                .collect { profile ->
                    _state.update { current ->
                        // If the active role was taken away, for example by an admin, go back to entrant.
                        val role = if (profile.hasRole(current.activeRole)) current.activeRole else Role.ENTRANT
                        current.copy(profile = profile, activeRole = role)
                    }
                }
        }
    }

    /** Switches the app to [role], for the "Use the app as" buttons (US 03.11.01). */
    fun onSwitchRole(role: Role) {
        val profile = _state.value.profile ?: return
        if (!profile.hasRole(role)) {
            _state.update { it.copy(error = ProfileError.ROLE_NOT_HELD) }
            return
        }
        _state.update { it.copy(activeRole = role, error = null) }
    }

    /**
     * Adds the owner role and switches to owner mode, for "List your campsite".
     * Refused while an admin's ban is in place (US 03.07.01).
     */
    fun onBecomeOwner() {
        val profile = _state.value.profile ?: return
        if (profile.ownerBanned) {
            _state.update { it.copy(error = ProfileError.OWNER_BANNED) }
            return
        }
        viewModelScope.launch {
            try {
                repository.save(profile.becomeOwner())
                _state.update { it.copy(activeRole = Role.OWNER, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = ProfileError.SAVE_FAILED) }
            }
        }
    }

    /** Clears the error once the screen has shown it. */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        /** Creates a [ProfileController] with the app's repository from the AppContainer. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                ProfileController(application.container.profileRepository)
            }
        }
    }
}
