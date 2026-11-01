/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Compose code, syntax and KDoc.
 * It also helped show the load error when the profile moved to Firestore.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.ProfileController
import com.lambda.campsite.controller.ProfileError
import com.lambda.campsite.model.Role

/**
 * The profile tab: who's signed in, which role the app is used as (US 03.11.01),
 * and "List your campsite" for profiles that aren't owners yet.
 *
 * This is the template for every screen: it reads the controller's state with
 * `collectAsStateWithLifecycle()` and calls the controller's `on` methods from
 * its buttons. It never talks to a repository.
 *
 * @param controller the profile controller, shared with the nav bar.
 */
@Composable
fun ProfileScreen(controller: ProfileController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_profile),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        val profile = state.profile
        if (profile == null) {
            // Say so if loading failed, instead of showing "Loading" forever.
            val loadFailed = state.error == ProfileError.LOAD_FAILED
            Text(stringResource(if (loadFailed) R.string.error_load_failed else R.string.profile_loading))
            return@Column
        }

        Text(profile.name, style = MaterialTheme.typography.titleLarge)
        Text(profile.email, style = MaterialTheme.typography.bodyMedium)

        Text(
            text = stringResource(R.string.profile_use_app_as),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Role.entries.filter { profile.hasRole(it) }.forEach { role ->
                FilterChip(
                    selected = state.activeRole == role,
                    onClick = { controller.onSwitchRole(role) },
                    label = { Text(roleLabel(role)) },
                )
            }
        }

        if (!profile.hasRole(Role.OWNER) && !profile.ownerBanned) {
            Button(onClick = controller::onBecomeOwner) {
                Text(stringResource(R.string.profile_list_your_campsite))
            }
        }

        state.error?.let { error ->
            Text(
                text = errorMessage(error),
                color = MaterialTheme.colorScheme.error,
                // Read the message out with TalkBack as soon as it appears.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The name of [role] shown to the user. */
@Composable
private fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.ENTRANT -> R.string.role_entrant
        Role.OWNER -> R.string.role_owner
        Role.ADMIN -> R.string.role_admin
    },
)

/** The message shown for [error]. */
@Composable
private fun errorMessage(error: ProfileError): String = stringResource(
    when (error) {
        ProfileError.ROLE_NOT_HELD -> R.string.error_role_not_held
        ProfileError.OWNER_BANNED -> R.string.error_owner_banned
        ProfileError.SAVE_FAILED -> R.string.error_save_failed
        ProfileError.LOAD_FAILED -> R.string.error_load_failed
    },
)
