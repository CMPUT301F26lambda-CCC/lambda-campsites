/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the controller integration, TalkBack accessibility semantics,
 * season controls, error indicators, string resource integration and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import java.time.LocalDate

/**
 * Register campsite screen (US 02.01.01, US 02.01.02).
 *
 * Takes [controller] to collect state and submit the new campsite draft.
 *
 * @param controller the [OwnerCampsiteAddController] managing the registration form.
 * @param onClose invoked when the owner cancels or closes the screen.
 * @param onPickLocation invoked to open the map location selector screen.
 * @param onSaved invoked when registration successfully completes.
 * @param modifier optional layout modifier.
 */
@Composable
fun OwnerCampsiteAddScreen(
    controller: OwnerCampsiteAddController,
    onClose: () -> Unit,
    onPickLocation: () -> Unit,
    onSaved: (Campsite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var newTagInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.register_campsite_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = { controller.onSave(onSaved) },
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.register_campsite_button))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Photo Section
            FormSectionTitle(stringResource(R.string.section_photo))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CampGreenDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .background(CampGreenField),
                contentAlignment = Alignment.Center,
            ) {
                if (state.hasPhoto) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = CampGreenDark, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.photo_selected), style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.add_photo_optional), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = {
                    if (state.hasPhoto) controller.onPhotoRemoved() else controller.onPhotoSelected()
                }) {
                    Icon(Icons.Default.Image, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.hasPhoto) stringResource(R.string.replace_photo_button) else stringResource(R.string.add_photo_button))
                }
                if (state.hasPhoto) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = controller::onPhotoRemoved) {
                        Text(stringResource(R.string.remove_photo_button), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Details Section
            FormSectionTitle(stringResource(R.string.section_details))
            val nameHasError = CampsiteRequiredField.NAME in state.errors
            OutlinedTextField(
                value = state.name,
                onValueChange = controller::onNameChange,
                label = { Text(stringResource(R.string.label_name_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_name)) },
                isError = nameHasError,
                supportingText = {
                    if (nameHasError) {
                        Text(stringResource(R.string.error_enter_name), color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val locationHasError = CampsiteRequiredField.LOCATION in state.errors
            OutlinedTextField(
                value = state.location,
                onValueChange = controller::onLocationChange,
                label = { Text(stringResource(R.string.label_location_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_location)) },
                isError = locationHasError,
                supportingText = {
                    if (locationHasError) {
                        Text(stringResource(R.string.error_enter_location), color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = controller::onDescriptionChange,
                label = { Text(stringResource(R.string.label_description)) },
                placeholder = { Text(stringResource(R.string.placeholder_description)) },
                supportingText = { Text(stringResource(R.string.supporting_description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Max Occupancy Card
            val occupancyHasError = CampsiteRequiredField.MAX_OCCUPANCY in state.errors
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_max_occupancy_required), style = MaterialTheme.typography.labelMedium)
                        Text(state.maxOccupancy.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalIconButton(
                        onClick = { controller.onOccupancyChange(state.maxOccupancy - 1) },
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_fewer_guests))
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(
                        onClick = { controller.onOccupancyChange(state.maxOccupancy + 1) },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more_guests))
                    }
                }
            }
            Text(
                stringResource(R.string.supporting_max_occupancy),
                style = MaterialTheme.typography.bodySmall,
                color = if (occupancyHasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Availability Section
            FormSectionTitle(stringResource(R.string.section_availability))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val currentYear = LocalDate.now().year
                        val defaultStart = LocalDate.of(currentYear, 5, 1)
                        val defaultEnd = LocalDate.of(currentYear, 10, 12)
                        controller.onSeasonChange(defaultStart, defaultEnd)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val seasonText = if (state.seasonStart != null && state.seasonEnd != null) {
                        "Open ${state.seasonStart} – ${state.seasonEnd}"
                    } else {
                        "Set default season (May – Oct)"
                    }
                    Text(seasonText, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(R.string.season_range_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.seasonError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Map Location Section
            FormSectionTitle(stringResource(R.string.section_map_location))
            OutlinedButton(
                onClick = onPickLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val hasPin = state.latitude != null && state.longitude != null
                val label = if (hasPin) {
                    val placeName = state.locationPinName ?: "${state.latitude}, ${state.longitude}"
                    "Pin set ($placeName) · change"
                } else {
                    stringResource(R.string.action_set_map_location)
                }
                Text(label)
            }

            // Tags Section
            FormSectionTitle(stringResource(R.string.section_tags))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { controller.onRemoveTag(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove $tag", Modifier.size(14.dp))
                        },
                    )
                }
            }
            OutlinedTextField(
                value = newTagInput,
                onValueChange = { newTagInput = it },
                label = { Text(stringResource(R.string.label_add_tag)) },
                placeholder = { Text(stringResource(R.string.placeholder_add_tag)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val trimmed = newTagInput.trim()
                        if (trimmed.isNotEmpty()) {
                            controller.onAddTag(trimmed)
                            newTagInput = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_tag))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Save error banner
            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.error_campsite_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * Backward-compatible stateless overload for existing callers of [OwnerCampsiteAddScreen].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerCampsiteAddScreen(
    initial: Campsite,
    isEdit: Boolean,
    onClose: () -> Unit,
    onSave: (Campsite) -> Unit,
    onPickLocation: () -> Unit,
    modifier: Modifier = Modifier,
    hasPhoto: Boolean = false,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    var campsite by remember { mutableStateOf(initial) }
    var errors by remember { mutableStateOf(emptySet<CampsiteRequiredField>()) }
    var newTag by remember { mutableStateOf("") }

    fun update(new: Campsite) {
        campsite = new
        if (errors.isNotEmpty()) errors = new.validate()
    }

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) stringResource(R.string.edit_campsite_title) else stringResource(R.string.register_campsite_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    val missing = campsite.validate()
                    errors = missing
                    if (missing.isEmpty()) onSave(campsite)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text(if (isEdit) stringResource(R.string.save_changes_button) else stringResource(R.string.register_campsite_button)) }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormSectionTitle(stringResource(R.string.section_photo))
            Box(
                Modifier.fillMaxWidth().height(140.dp)
                    .clip(RoundedCornerShape(12.dp)).background(CampGreenField),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPhoto) {
                    Text(stringResource(R.string.photo_selected))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null)
                        Text(stringResource(R.string.add_photo_optional), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onPickPhoto) {
                    Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasPhoto) stringResource(R.string.replace_photo_button) else stringResource(R.string.add_photo_button))
                }
                if (hasPhoto) TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.remove_photo_button)) }
            }

            FormSectionTitle(stringResource(R.string.section_details))
            OutlinedTextField(
                value = campsite.name,
                onValueChange = { update(campsite.copy(name = it)) },
                label = { Text(stringResource(R.string.label_name_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_name)) },
                isError = CampsiteRequiredField.NAME in errors,
                supportingText = { if (CampsiteRequiredField.NAME in errors) Text(stringResource(R.string.error_enter_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = campsite.location,
                onValueChange = { update(campsite.copy(location = it)) },
                label = { Text(stringResource(R.string.label_location_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_location)) },
                isError = CampsiteRequiredField.LOCATION in errors,
                supportingText = { if (CampsiteRequiredField.LOCATION in errors) Text(stringResource(R.string.error_enter_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = campsite.description,
                onValueChange = { update(campsite.copy(description = it)) },
                label = { Text(stringResource(R.string.label_description)) },
                placeholder = { Text(stringResource(R.string.placeholder_description)) },
                supportingText = { Text(stringResource(R.string.supporting_description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            val occupancyError = CampsiteRequiredField.MAX_OCCUPANCY in errors
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_max_occupancy_required), style = MaterialTheme.typography.labelSmall)
                        Text(campsite.maxOccupancy.toString())
                    }
                    FilledTonalIconButton(
                        onClick = { update(campsite.copy(maxOccupancy = (campsite.maxOccupancy - 1).coerceAtLeast(0))) },
                    ) { Icon(Icons.Default.Remove, stringResource(R.string.action_fewer_guests)) }
                    FilledTonalIconButton(
                        onClick = { update(campsite.copy(maxOccupancy = campsite.maxOccupancy + 1)) },
                    ) { Icon(Icons.Default.Add, stringResource(R.string.action_more_guests)) }
                }
            }
            Text(
                stringResource(R.string.supporting_max_occupancy),
                style = MaterialTheme.typography.bodySmall,
                color = if (occupancyError) MaterialTheme.colorScheme.error else Color.Unspecified,
            )

            FormSectionTitle(stringResource(R.string.section_availability))
            Text(stringResource(R.string.season_range_hint), style = MaterialTheme.typography.bodySmall)

            FormSectionTitle(stringResource(R.string.section_map_location))
            OutlinedButton(onClick = onPickLocation, modifier = Modifier.fillMaxWidth()) {
                val pinned = campsite.latitude != null && campsite.longitude != null
                Text(if (pinned) stringResource(R.string.action_change_map_location) else stringResource(R.string.action_set_map_location))
            }

            FormSectionTitle(stringResource(R.string.section_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                campsite.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { update(campsite.copy(tags = campsite.tags - tag)) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Default.Close, "Remove $tag", Modifier.size(14.dp)) },
                    )
                }
            }
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text(stringResource(R.string.label_add_tag)) },
                placeholder = { Text(stringResource(R.string.placeholder_add_tag)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val t = newTag.trim()
                            if (t.isNotEmpty() && t !in campsite.tags) {
                                update(campsite.copy(tags = campsite.tags + t))
                            }
                            newTag = ""
                        },
                    ) { Icon(Icons.Default.Add, stringResource(R.string.action_add_tag)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun FormSectionTitle(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.semantics { heading() },
)
