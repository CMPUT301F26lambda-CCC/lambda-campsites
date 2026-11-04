// Issue #150: GPT-6 (OpenAI), through Codex, assisted me with campsite editing.
package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerCampsiteEditController
import com.lambda.campsite.view.theme.ForestGreen

@Composable
fun OwnerCampsiteEditScreen(
    controller: OwnerCampsiteEditController,
    onClose: () -> Unit,
    onPickLocation: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ForestGreen)
        }
        return
    }
    if (state.loadFailed) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.error_campsite_load_failed))
            Button(onClick = controller::retryLoad) { Text(stringResource(R.string.owner_retry)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.owner_cancel)) }
        }
        return
    }
    val photoUrl by produceState<String?>(null, state.imageId) {
        value = state.imageId?.let { runCatching { controller.photoUrl(it) }.getOrNull() }
    }
    OwnerCampsiteFormScreen(
        titleRes = R.string.edit_campsite_title,
        saveRes = R.string.save_changes_button,
        form = state.form,
        selectedPhoto = state.selectedPhoto,
        existingPhotoUrl = photoUrl,
        actions = OwnerFormActions(
            name = controller::onNameChange, location = controller::onLocationChange,
            description = controller::onDescriptionChange, occupancy = controller::onOccupancyChange,
            season = controller::onSeasonChange, price = controller::onPriceChange,
            rules = controller::onRulesChange, addTag = controller::onAddTag,
            removeTag = controller::onRemoveTag, photo = controller::onPhotoSelected,
            removePhoto = controller::onPhotoRemoved, pickLocation = onPickLocation,
        ),
        isDirty = controller.hasUnsavedChanges(),
        onClose = onClose,
        onSave = { controller.onSave(onSaved) },
        modifier = modifier,
    )
}
