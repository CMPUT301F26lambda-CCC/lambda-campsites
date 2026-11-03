/*
Purpose:
This screen is connected to ProfileScreen.kt and allows users to turn
notifications on or off. Users can also view the campsite policy at any
time to review the policy they agreed to earlier on FirstLaunchScreen.kt.

If users wish to delete their profile, they can select the Delete Profile
option and proceed with the profile deletion. Once the profile is deleted,
they will be redirected to LaunchScreen.kt.

Outstanding Issues:
1.Clicking on Delete Profile does not currently delete the user's profile
from Firestore. The UI navigation is already implemented, so once the
deletion is completed, the user will be redirected to LaunchScreen.kt.

*/
/*
 * AI use: GPT-6 (OpenAI), used through the ChatGPT website in November 2026,
 * helped with displaying the icons in the Settings screen and suggested
 * creating reusable composable functions (SectionLabel and SettingsRow)
 * to reduce code duplication and maintain consistent UI formatting
 * and improving the KDoc.
 * Harmanjoth Kaur reviewed the suggestions and made the final changes myself.
*/

package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.view.components.AppPolicySheet
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint

/**
 * Displays the user's settings, including notification preferences,
 * campsite policy and profile deletion (US 01.08.01).
 *
 * Allows users to turn notifications on or off, review the campsite policy
 * through [AppPolicySheet], and confirm profile deletion.
 *
 * @param name the user's name, displayed in the campsite policy.
 * @param notificationsOn the current notification preference.
 * @param onNotificationsChange callback when the notification setting changes.
 * @param onBack callback when the user clicks the back button.
 * @param saveFailed indicates whether saving the notification preference failed.
 * @param onDeleteProfile callback when the user confirms profile deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    name: String,
    notificationsOn: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    saveFailed: Boolean = false,
    onDeleteProfile: () -> Unit = {},
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    var switchOn by remember(notificationsOn, saveFailed) { mutableStateOf(notificationsOn) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestGreen),
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
                text = stringResource(R.string.profile_settings),
                style = MaterialTheme.typography.titleLarge,
                color = Mint,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Mint, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_preferences))
            SettingsRow(
                icon = { Icon(Icons.Default.Notifications, null, tint = DeepGreen) },
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_desc),
                trailing = {
                    // Display the user's current notification preference using the switch.
                    // When the user turns notifications on or off, update the UI immediately
                    // and send the new preference to the controller to save the change.
                    Switch(
                        checked = switchOn,
                        onCheckedChange = { enabled ->
                            switchOn = enabled
                            onNotificationsChange(enabled)
                        },
                    )
                    //to handle the error if the notification preference fails to save
                    if (saveFailed) {
                        Text(
                            text = stringResource(R.string.error_save_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
            SettingsRow(
                modifier = Modifier.clickable { showPolicy = true },
                icon = { Icon(Icons.Default.Info, null, tint = DeepGreen) },
                title = stringResource(R.string.settings_policy),
                subtitle = stringResource(R.string.settings_policy_desc),
                trailing = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = DeepGreen)
                },
            )
            HorizontalDivider(color = DeepGreen.copy(alpha = 0.25f))
            SectionLabel(stringResource(R.string.settings_account))
            SettingsRow(
                modifier = Modifier.clickable{ showDeleteDialog = true },
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = stringResource(R.string.settings_delete_profile),
                subtitle = stringResource(R.string.settings_delete_profile_desc),
                titleColor = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (showPolicy) {
        ModalBottomSheet(
            onDismissRequest = { showPolicy = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Mint,
        ) {
            AppPolicySheet(
                name = name,
                CloseAppPolicy = { showPolicy = false },
                showAgree = false,
            )
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Mint,
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DeepGreen) },
            title = { Text(stringResource(R.string.delete_dialog_title), color = DeepGreen) },
            text = { Text(stringResource(R.string.delete_dialog_message), color = DeepGreen) },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel), color = DeepGreen)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteProfile()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
/** Displays a section heading in the Settings screen using consistent styling. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = DeepGreen.copy(alpha = 0.7f),
        modifier = Modifier.semantics { heading() },
    )
}
/**
 * Displays a reusable settings row with an icon, title, subtitle
 * and an optional trailing component,such as a navigation arrow.
 */
@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = DeepGreen,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = DeepGreen.copy(alpha = 0.8f))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}