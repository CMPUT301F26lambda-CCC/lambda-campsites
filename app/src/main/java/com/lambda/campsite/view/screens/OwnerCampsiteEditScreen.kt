/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with pre-filling existing campsite data, TalkBack accessibility
 * semantics, save button feedback, string extraction and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 *
 * The photo section is hidden until ImageRepository is ready, so the screen
 * doesn't promise a photo that isn't picked, uploaded or saved (US 02.01.03).
 *
 * The status bar icon styling was added in November 2026 with GLM 5.3 Flash
 * (Z.ai), through OpenCode, at Raghav Sethi's request.
 */

/**
 * Purpose: Displays the form for editing an existing campsite's information.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */

package com.lambda.campsite.view.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerCampsiteEditController
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.LocalDate

/**
 * Edit campsite screen (US 02.01.03).
 *
 * Pre-fills the registration form with the current details of the campsite.
 * Saving preserves the original campsite document ID so all existing QR codes
 * remain valid.
 *
 * @param controller the [OwnerCampsiteEditController] managing editable values.
 * @param onClose invoked when closing without saving.
 * @param onPickLocation invoked to open the map location selector.
 * @param onSaved invoked when updates are successfully committed to Firestore.
 * @param modifier optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OwnerCampsiteEditScreen(
    controller: OwnerCampsiteEditController,
    onClose: () -> Unit,
    onPickLocation: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var newTagInput by remember { mutableStateOf("") }

    // While loading there's no dark green app bar yet, so the clock stays dark.
    StatusBarIconStyle(darkIcons = state.isLoading)

    if (state.isLoading) {
        Scaffold(containerColor = CampGreenLight) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CampGreenDark)
            }
        }
        return
    }

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.edit_campsite_title),
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
                    Text(stringResource(R.string.save_changes_button))
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
            // Details section
            FormSectionTitle(stringResource(R.string.section_details))
            val nameError = CampsiteRequiredField.NAME in state.errors
            OutlinedTextField(
                value = state.name,
                onValueChange = controller::onNameChange,
                label = { Text(stringResource(R.string.label_name_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_name)) },
                isError = nameError,
                supportingText = {
                    if (nameError) Text(stringResource(R.string.error_enter_name), color = MaterialTheme.colorScheme.error)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val locationError = CampsiteRequiredField.LOCATION in state.errors
            OutlinedTextField(
                value = state.location,
                onValueChange = controller::onLocationChange,
                label = { Text(stringResource(R.string.label_location_required)) },
                placeholder = { Text(stringResource(R.string.placeholder_location)) },
                isError = locationError,
                supportingText = {
                    if (locationError) Text(stringResource(R.string.error_enter_location), color = MaterialTheme.colorScheme.error)
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

            // Max Occupancy
            val occupancyError = CampsiteRequiredField.MAX_OCCUPANCY in state.errors
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
                color = if (occupancyError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Availability
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
            state.seasonError?.let { seasonError ->
                Text(
                    text = stringResource(campsiteFormErrorRes(seasonError)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            // Map location
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

            // Tags
            FormSectionTitle(stringResource(R.string.section_tags))
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
