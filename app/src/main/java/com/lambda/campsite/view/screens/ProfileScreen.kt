/*
Purpose:
This page is used to display the profile information for any user.
Firstly, it will try to retrieve the profile. If nothing is available,
or it is unable to load the profile, it will simply display an error
message.
Once it is able to retrieve the profile from Firestore, it will
retrieve the user's roles, name, email and phone number. If the person
is an Admin, all three slider options are available. However, if the
person is an Entrant or Owner, or both, the only available options
are Entrant and Owner.
This prevents unauthorized users from selecting Admin access.
This page connects with EditProfileScreen.kt, where users can edit
their name, email and phone number, and SettingsScreen.kt, where they
can turn notifications on or off, view the campsite policy they agreed
to on FirstLaunchScreen, and delete their profile.

Outstanding Issue:
1.When a person clicks on Use App As Entrant, the navigation bar works
correctly. However, when they click on Owner, the navigation bar does
not update. It still needs to be connected to the Owner or Admin
screens for users with those roles.
The UI part is complete, so the slider will show the selected role
change.
2.The back button on the Profile screen needs to navigate to the correct
landing screen based on the user's active role (Entrant, Owner or Admin),
since each role has a different navigation bar and set of screens.
*/

/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Compose code, syntax and KDoc.
 * It also helped show the load error when the profile moved to Firestore.
 * Sashreek Addanki reviewed it and committed it.
 *
 * The status bar padding was added in November 2026 with GLM 5.3 Flash (Z.ai),
 * through OpenCode, at Raghav Sethi's request.
 *
 * AI use: GPT-6 (OpenAI), used through the ChatGPT website in November 2026,
 * helped with retrieving the user's assigned roles from Firestore through
 * ProfileController and ProfileRepository, building the role selection slider
 * UI, and improving the KDoc for ProfileScreen. Harmanjoth Kaur reviewed the suggestions
 * and made the final changes myself.
 */

package com.lambda.campsite.view.screens
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.border
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.campsite.view.theme.SelectedMint
/**
 * Displays the user's profile information, including their name, email,
 * phone number and assigned roles (US 03.11.01). Allows users to select
 * a role in the UI slider and access profile editing and settings.
 *
 * Retrieves the profile information through [ProfileController] and displays
 * a loading or error message if the profile is unavailable. The screen also
 * connects to [EditProfileScreen] for updating personal information and
 * [SettingsScreen] for managing account settings.
 *
 * The Admin option is only selectable for profiles assigned the Admin role.
 *
 * @param controller the profile controller that provides the profile state
 *                   and is shared with the navigation bar.
 * @param modifier the modifier applied to the screen layout.
 * @param onEditProfile callback invoked when the user selects Edit Profile.
 * @param onSettings callback invoked when the user opens Settings.
 * @param onBack callback invoked when the user selects the back button.
 */
@Composable
fun ProfileScreen(controller: ProfileController,
                  modifier: Modifier = Modifier,
                  onEditProfile: () -> Unit = {},
                  onSettings: () -> Unit = {},
                  onBack: () -> Unit = {},
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val profile = state.profile
    var selectedRole by remember {mutableStateOf(Role.ENTRANT)
    }
    // Checking whether the profile has loaded before accessing its properties.
    // If the profile is null, show a loading or error message instead of displaying
    // profile information that is not yet available
    if (profile == null) {
        val loadFailed = state.error == ProfileError.LOAD_FAILED
        Box(
            modifier = modifier.fillMaxSize().background(ForestGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(
                    if (loadFailed) R.string.error_load_failed
                    else R.string.profile_loading
                ),
                color = Mint,
            )
        }
    }   // Once the profile is loaded, we can access the user's name, email, phone number
    // and assigned roles. The Admin option is only selectable for users who have
    // been assigned the Admin role, so other users cannot select Admin access.
    else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Mint),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
                    .background(ForestGreen)
                    // Keeps the back button and heading clear under the clock (US 01.11.01).
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Mint,
                        )
                    }
                    Text(
                        text = stringResource(R.string.tab_profile),
                        style = MaterialTheme.typography.titleLarge,
                        color = Mint,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.name
                            .trim()
                            .split(Regex("\\s+"))
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                            .take(2)
                            .joinToString("")
                            .ifBlank { "?" },
                        style = MaterialTheme.typography.headlineLarge,
                        color = DeepGreen,
                    )
                }
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                //retrieving the roles assigned to a user
                val entrantLabel = stringResource(R.string.role_entrant)
                val ownerLabel = stringResource(R.string.role_owner)
                val adminLabel = stringResource(R.string.role_admin)
                val assignedRoles = Role.entries
                    .filter { profile.hasRole(it) }
                    .map { role ->
                        when (role) {
                            Role.ENTRANT -> entrantLabel
                            Role.OWNER -> ownerLabel
                            Role.ADMIN -> adminLabel
                        }
                    }
                    .joinToString("  ·  ")
                Text(
                    text = assignedRoles,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mint,
                )
                Button(
                    //it will takes the user to Edit Profile Page
                    onClick = onEditProfile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = DeepGreen,
                    ),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.profile_edit))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
                    .background(Mint)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_use_app_as),
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepGreen,
                    modifier = Modifier.semantics { heading() },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Mint)
                        .border(
                            width = 1.dp,
                            color = Color.Black,
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(1.dp)
                            .clip(RoundedCornerShape(50))
                    ) {
                        repeat(3) { index ->
                            val role = Role.entries[index]
                            val isSelected = selectedRole == role
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(if (isSelected) SelectedMint else Color.Transparent)
                                    .clickable {
                                        if (role != Role.ADMIN || profile.hasRole(Role.ADMIN)) {
                                            selectedRole = role
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected role",
                                            tint = DeepGreen.copy(alpha = 0.85f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = roleLabel(role),
                                        color = DeepGreen,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                if (index < 2) {
                                    VerticalDivider(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .align(Alignment.CenterEnd),
                                        color = Color.Black,
                                        thickness = 1.dp,
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = DeepGreen.copy(alpha = 0.25f))
                Text(
                    text = stringResource(R.string.profile_details),
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepGreen,
                    modifier = Modifier.semantics { heading() },
                )
                // Using ProfileInfoRow to display two different pieces of information (email and phone number)
                // with the same UI layout, reducing code duplication. If either value is empty,
                // a fallback message(Not provided) is displayed instead
                ProfileInfoRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = DeepGreen,
                        )
                    },
                    label = stringResource(R.string.profile_email),
                    value = profile.email
                        .takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.profile_phone_not_provided),
                )
                ProfileInfoRow(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = DeepGreen,
                        )
                    },
                    label = stringResource(R.string.profile_phone),
                    value = profile.phone
                        ?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.profile_phone_not_provided),
                )
                HorizontalDivider(color = DeepGreen.copy(alpha = 0.25f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        //This will take user to the SettingsScreen
                        .clickable(onClick = onSettings)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = DeepGreen,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.profile_settings),
                        modifier = Modifier.weight(1f),
                        color = DeepGreen,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = DeepGreen,
                    )
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
/**
 * ProfileInfoRow is used by both the Email and Phone fields in ProfileScreen
 * to display their information using consistent UI formatting.
 * This reusable function reduces code duplication by sharing the same layout
 * between both fields.
 */
@Composable
private fun ProfileInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String){
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = DeepGreen,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = DeepGreen,
            )
        }
    }
}