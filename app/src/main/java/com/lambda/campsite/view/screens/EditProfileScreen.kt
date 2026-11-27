/*
Purpose:
This screen allows users to edit their profile information, including
their name, email and phone number. Each field has its own validation
to ensure the information entered is in the correct format.(US 01.07.03)

Both the Cancel button and the X button at the top have the same
functionality. When clicked, a confirmation dialog appears with two
options: the user can either keep editing or discard their changes
and return to the Profile screen.
When the user clicks Save Changes, the updated name, email and phone
number are passed to the controller, which saves the updated profile
information to Firestore.
Change Photo opens the phone's gallery. The picked photo is uploaded
and saved straight away, and the avatar shows it (US 01.08.02).
Remove photo takes the photo off, so the initials show again.

Outstanding Issues:
- None
*/
/*
 * AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat
 * helped with implementing field validation, generating the user's initials dynamically
 * for the profile avatar, connect the Change Photo button to the photo picker
 * and reviewing the KDoc documentation. Harmanjoth reviewed the suggestions and committed them.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import android.graphics.Bitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.lambda.campsite.view.components.PHOTO_TYPES
import com.lambda.campsite.view.components.ProfileAvatar
import com.lambda.campsite.view.components.rememberPhotoPicker
/**
 * Lets the entrant edit their name, email and phone number (US 01.07.03).
 *
 * The fields start with the profile's current values. Each field is checked
 * when the entrant presses Save Changes: the name can't be blank, the email
 * must look like `name@example.com`, and the phone number must have exactly
 * 10 digits. An error message is shown under each field that fails, and
 * nothing is saved until all three are valid.
 *
 * Cancel and the X button both open a dialog that asks whether to discard
 * the changes. Keep editing closes the dialog. Discard calls [onCancel].
 *
 * The avatar shows the profile photo, or the initials of the name being
 * typed when there's no photo. Change Photo opens the phone's gallery, and
 * the picked photo is saved straight away (US 01.08.02).
 *
 * @param name the profile's current name, shown in the Name field.
 * @param email the profile's current email address, shown in the Email field.
 * @param phone the profile's current phone number, or `null` if none is saved,
 *   in which case the Phone field starts empty.
 * @param onCancel called when the entrant confirms they want to discard
 *   their changes.
 * @param onSave called with the trimmed name, email and phone number when
 *   the entrant presses Save Changes and every field is valid.
 * @param saveFailed `true` if the last save failed, which shows an error
 *   message above the Save Changes button.
 * @param photoUrl where to load the profile photo from, or `null` to show the initials.
 * @param photoSaving `true` while a photo is uploading, which turns off Change Photo.
 * @param photoFailed `true` if the last photo upload failed, which shows an error.
 * @param onPhotoPicked called with the photo the entrant picked.
 * @param hasPhoto `true` when the profile has a photo, which shows Remove photo.
 * @param onRemovePhoto called when the entrant taps Remove photo.
 */
@Composable
fun EditProfileScreen(
    name: String,
    email: String,
    phone: String?,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
    saveFailed: Boolean = false,
    photoUrl: String? = null,
    photoSaving: Boolean = false,
    photoFailed: Boolean = false,
    onPhotoPicked: (Bitmap) -> Unit = {},
    hasPhoto: Boolean = false,
    onRemovePhoto: () -> Unit = {},
) {
    var editedName by rememberSaveable(name) { mutableStateOf(name) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val requestCancel = {showDiscardDialog = true }
    var editedEmail by rememberSaveable(email) {mutableStateOf(email) }
    var editedPhone by rememberSaveable(phone) { mutableStateOf(phone.orEmpty()) }
    var triedToSave by rememberSaveable { mutableStateOf(false) }
    val emailRegex = remember { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") }
    val phoneRegex = remember { Regex("^\\+?[0-9 ()-]+$") }
    // Track validation errors for each individual field so that the corresponding
    // error message is displayed only after the user clicks Save Changes and the
    // field fails its validation. This prevents error messages from appearing
    // before the user has attempted to save the profile.
    val nameError = triedToSave && editedName.isBlank()
    val emailError = triedToSave && !emailRegex.matches(editedEmail.trim())
    val phoneError = triedToSave && (!phoneRegex.matches(editedPhone.trim()) ||
            editedPhone.count { it.isDigit() } != 10)
    // The photo is saved as soon as it's picked (US 01.08.02).
    var pickFailed by remember { mutableStateOf(false) }
    val photoPicker = rememberPhotoPicker(
        onPicked = {
            pickFailed = false
            onPhotoPicked(it)
        },
        onFailed = { pickFailed = true },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mint),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
                .background(ForestGreen)
                .statusBarsPadding()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    android.util.Log.d("EditProfile", "X tapped")
                    showDiscardDialog = true
                }){
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close edit profile",
                        tint = Mint,
                    )
                }

                Text(
                    text = stringResource(R.string.profile_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Mint,
                )
            }
            ProfileAvatar(name = editedName, photoUrl = photoUrl)
            Button(
                onClick = { photoPicker.launch(PHOTO_TYPES) },
                enabled = !photoSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mint,
                    contentColor = DeepGreen,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(Modifier.width(8.dp))

                Text(stringResource(R.string.profile_change_photo))
            }
            if (hasPhoto) {
                TextButton(
                    onClick = onRemovePhoto,
                    enabled = !photoSaving,
                ) {
                    Text(
                        text = stringResource(R.string.profile_remove_photo),
                        color = Mint,
                    )
                }
            }
            if (photoFailed || pickFailed) {
                Text(
                    text = stringResource(R.string.profile_photo_failed),
                    color = Mint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.60f)
                .background(
                    Mint,
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text(stringResource(R.string.profile_name) + "*") },
                isError = nameError,
                supportingText = { if (nameError) Text(stringResource(R.string.error_name_required)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            OutlinedTextField(
                value = editedEmail,
                onValueChange = { editedEmail = it },
                label = { Text(stringResource(R.string.profile_email) + "*") },
                isError = emailError,
                supportingText = { if (emailError) Text(stringResource(R.string.error_email_invalid)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            OutlinedTextField(
                value = editedPhone,
                onValueChange = { editedPhone = it },
                label = { Text(stringResource(R.string.profile_phone) + "*") },
                isError = phoneError,
                supportingText = { if (phoneError) Text(stringResource(R.string.error_phone_invalid)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            if (saveFailed) {
                Text(
                    text = stringResource(R.string.error_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    triedToSave = true
                    val valid = editedName.isNotBlank() &&
                            emailRegex.matches(editedEmail.trim()) &&
                            phoneRegex.matches(editedPhone.trim()) &&
                            editedPhone.count { it.isDigit() } == 10
                    if (valid) {
                        onSave(editedName.trim(), editedEmail.trim(), editedPhone.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestGreen,
                    contentColor = Mint,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(stringResource(R.string.profile_save_changes))
            }
            TextButton(
                onClick = { requestCancel() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.profile_cancel),
                    color = DeepGreen,
                )
            }
        }
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
            },
            title = {
                Text(stringResource(R.string.profile_discard_title))
            },
            text = {
                Text(stringResource(R.string.profile_discard_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancel()
                    }
                ) {
                    Text(stringResource(R.string.profile_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                    }
                ) {
                    Text(stringResource(R.string.profile_keep_editing))
                }
            },
            containerColor = Mint,
            titleContentColor = DeepGreen,
            textContentColor = DeepGreen
        )
    }
}