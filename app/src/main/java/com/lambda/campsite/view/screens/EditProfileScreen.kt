/*
Purpose:
This screen allows users to edit their profile information, including
their name, email and phone number. Each field has its own validation
to ensure the information entered is in the correct format.

Both the Cancel button and the X button at the top have the same
functionality. When clicked, a confirmation dialog appears with two
options: the user can either keep editing or discard their changes
and return to the Profile screen.
When the user clicks Save Changes, the updated name, email and phone
number are passed to the controller, which saves the updated profile
information to Firestore.

Outstanding Issues:
- The Change Photo button is not functional yet. The functionality
  to allow users to upload or change their profile picture still
  needs to be implemented.
*/
/*
 * AI use: Claude Sonnet 5.5 (Anthropic), used through the Claude chat
 * helped with implementing field validation, generating the user's initials dynamically
 * for the profile avatar, and reviewing the KDoc documentation.
 * Harmanjoth reviewed the suggestions and committed them.
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

@Composable
fun EditProfileScreen(
    name: String,
    email: String,
    phone: String?,
    onCancel: () -> Unit,
    onSave: (String, String, String) -> Unit,
    saveFailed: Boolean = false,
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
    val initials = editedName
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }

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
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineLarge,
                    color = DeepGreen,
                )
            }
            Button(
                onClick = { },
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