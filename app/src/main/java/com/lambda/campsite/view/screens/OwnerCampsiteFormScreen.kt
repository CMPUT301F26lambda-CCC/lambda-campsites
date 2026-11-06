/*
 * Purpose: Arranges the shared owner campsite form and its submit and navigation actions.
 * Outstanding issues: None known.
 * AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with the owner form layout.
 */
package com.lambda.campsite.view.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFormData
import com.lambda.campsite.controller.CampsiteFormError
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import com.lambda.campsite.view.theme.StatusBarIconStyle

internal fun campsiteFormErrorRes(error: CampsiteFormError): Int = when (error) {
    CampsiteFormError.SAVE_FAILED -> R.string.error_campsite_save_failed
    CampsiteFormError.INVALID_SEASON_RANGE -> R.string.error_season_invalid_range
    CampsiteFormError.INCOMPLETE_SEASON -> R.string.error_season_incomplete
    CampsiteFormError.INVALID_PRICE -> R.string.owner_price_error
}

/** Wraps the shared fields in an owner app bar and save/cancel actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnerCampsiteFormScreen(
    titleRes: Int,
    saveRes: Int,
    form: CampsiteFormData,
    selectedPhoto: android.graphics.Bitmap?,
    existingPhotoUrl: String?,
    actions: OwnerFormActions,
    isDirty: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestClose = { if (isDirty) confirmDiscard = true else onClose() }
    BackHandler { requestClose() }
    StatusBarIconStyle(darkIcons = false)

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.owner_discard_title)) },
        text = { Text(stringResource(R.string.owner_discard_message)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) {
            Text(stringResource(R.string.owner_discard))
        } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) {
            Text(stringResource(R.string.owner_keep_editing))
        } },
    )

    Scaffold(
        modifier = modifier, containerColor = Mint,
        topBar = { TopAppBar(
            title = { Text(stringResource(titleRes)) },
            navigationIcon = { IconButton(onClick = requestClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.owner_cancel))
            } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ForestGreen, titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        ) },
        bottomBar = { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave, enabled = !form.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (form.isSaving) CircularProgressIndicator(color = Color.White)
                else Text(stringResource(saveRes))
            }
            TextButton(onClick = requestClose, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.owner_cancel))
            }
        } },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OwnerCampsiteFormFields(form, selectedPhoto, existingPhotoUrl, actions)
            form.formError?.let { Text(stringResource(campsiteFormErrorRes(it)), color = MaterialTheme.colorScheme.error) }
        }
    }
}
