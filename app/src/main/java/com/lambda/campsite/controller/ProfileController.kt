/*
 * Purpose: the controller (an Android ViewModel) for the profile screen and
 * for which role the app is used as. It sits between the profile screens
 * and ProfileRepository in the app's MVC design.
 *
 * Outstanding issues:
 * - onPushTokenAvailable isn't a user action like the other `on` methods.
 *   It lives here because this controller already owns the profile.
 *   Running it from LambdaApplication instead would also run it during
 *   every instrumented test, racing the tests' own profile writes.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the ViewModel and
 * StateFlow setup, syntax and KDoc. It also helped add the load error when the
 * profile moved to Firestore. Sashreek Addanki reviewed it, ran the tests and
 * committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add onPushTokenAvailable, on November 1, 2026. Sashreek
 * Addanki reviewed it.
 *
 * Flow 14: Harmanjoth Kaur added onFinishFirstLaunch, onSaveProfile, onEditProfileClosed and
 *  onSetNotificationsOn (November 2026).
 * AI use: GPT-6 (OpenAI), used through the ChatGPT website in November 2026 connect
 * onSetNotificationsOn to the Settings screen's notifications toggle, resolve an error with the
 * toggle and KDoc. Harmanjoth Kaur reviewed it, ran and committed it.
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
import kotlinx.coroutines.flow.first
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
 * screens to show. Each `on` method handles one user action, except
 * [onPushTokenAvailable], which `MainActivity` calls when the app starts.
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

    /**
     * Saves [token], this device's current FCM token, if the profile doesn't
     * hold it yet (US 01.10.01). `MainActivity` calls it every time the app
     * starts, so a token `FcmTokenService` couldn't save, or one the first
     * launch's profile create wiped, is put back.
     *
     * A failed save shows no error, since the user didn't ask for it. The
     * next start tries again.
     */
    fun onPushTokenAvailable(token: String) {
        viewModelScope.launch {
            try {
                val profile = repository.currentProfile().first()
                if (profile.pushToken != token) repository.savePushToken(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing to show the user. The next start tries again.
            }
        }
    }

    /**
     * Saves the user's name and their agreement to the current app policy
     * when they finish the first-launch screen (US 01.11.01).
     *
     * @param name the name entered by the user.
     */
    fun onFinishFirstLaunch(name: String) {

        val profile = _state.value.profile ?: return

        viewModelScope.launch {
            try {
                repository.save(
                    profile.copy(
                        name = name.trim(),
                        policyVersionAgreed = 1
                    )
                )
                _state.update { it.copy(error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = ProfileError.SAVE_FAILED)
                }
            }
        }
    }

    /**
     * Saves the updated profile information after the user edits their profile.
     *(US 01.07.03 – Update personal information, #51).
     * Checks whether the name, email or phone number has changed before saving.
     * If no changes are detected, invokes [onSaved] without making an unnecessary
     * repository update.
     *
     * If changes are detected, creates an updated copy of the existing profile
     * and saves it through [ProfileRepository]. On successful saving, clears
     * any existing error and invokes [onSaved] so the screen can return to
     * the Profile screen. If saving fails, updates the controller state with
     * [ProfileError.SAVE_FAILED].
     *
     * @param name the updated name entered by the user.
     * @param email the updated email address entered by the user.
     * @param phone the updated phone number entered by the user.
     * @param onSaved callback invoked after a successful save or when no
     *                profile changes are detected.
     */
    fun onSaveProfile(name: String, email: String, phone: String, onSaved: () -> Unit = {}) {
        val profile = _state.value.profile ?: return
        if (name.trim() == profile.name &&
            email.trim() == profile.email &&
            phone.trim() == profile.phone.orEmpty()
        ) {
            onSaved()
            return
        }
        viewModelScope.launch {
            try {
                repository.save(
                    profile.copy(name = name.trim(), email = email.trim(), phone = phone.trim()),
                )
                _state.update { it.copy(error = null) }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = ProfileError.SAVE_FAILED) }
            }
        }
    }
    /**
     * Clears any existing profile error when the user closes the Edit Profile screen.
     *US 01.07.03
     * Resets the error field in [ProfileUiState] to null while preserving
     * the remaining state, preventing previous error messages from appearing
     * when the user opens the Edit Profile screen again.
     */
    fun onEditProfileClosed() {
        _state.update { it.copy(error = null) }
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
    /**
     * Updates the user's notification preference
     * (US 01.10.01)
     * Checks whether the user's notification preference has changed before
     * saving. If the preference is already set to the requested value,
     * no unnecessary repository update is performed.
     *
     * Creates an updated copy of the existing profile with the new
     * notification preference and saves it through [ProfileRepository].
     * On successful saving, clears any existing error. If saving fails,
     * updates the controller state with [ProfileError.SAVE_FAILED].
     *
     * @param enabled true to enable notifications or false to opt out
     *                 of notifications.
     */
    fun onSetNotificationsOn(enabled: Boolean) {
        val profile = _state.value.profile ?: return
        if (profile.notificationsOn == enabled) return
        viewModelScope.launch {
            try {
                repository.save(profile.copy(notificationsOn = enabled))
                _state.update { it.copy(error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = ProfileError.SAVE_FAILED) }
            }
        }
    }
}
