/*
 * Purpose: Connects the owner campsite registration form to its controller.
 * Outstanding issues: None known.
 */

/**
 * Purpose: Displays the form for registering a new campsite.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */

// AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with campsite registration.
package com.lambda.campsite.view.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFormData
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.model.Campsite

@Composable
fun OwnerCampsiteAddScreen(
    controller: OwnerCampsiteAddController,
    onClose: () -> Unit,
    onPickLocation: () -> Unit,
    onSaved: (Campsite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerCampsiteFormScreen(
        titleRes = R.string.register_campsite_title,
        saveRes = R.string.register_campsite_button,
        form = state.form,
        selectedPhoto = state.selectedPhoto,
        existingPhotoUrl = null,
        actions = OwnerFormActions(
            name = controller::onNameChange, location = controller::onLocationChange,
            description = controller::onDescriptionChange, occupancy = controller::onOccupancyChange,
            season = controller::onSeasonChange, price = controller::onPriceChange,
            rules = controller::onRulesChange, addTag = controller::onAddTag,
            removeTag = controller::onRemoveTag, photo = controller::onPhotoSelected,
            removePhoto = controller::onPhotoRemoved, pickLocation = onPickLocation,
        ),
        isDirty = state.form != CampsiteFormData() || state.selectedPhoto != null,
        onClose = { controller.reset(); onClose() },
        onSave = { controller.onSave(onSaved) },
        modifier = modifier,
    )
}
