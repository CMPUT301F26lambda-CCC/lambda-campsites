/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the controller integration, TalkBack accessibility semantics,
 * season controls, error indicators, string resource integration and KDoc.
 * Raghav Sethi reviewed, tested, and committed it.
 *
 * The photo section is hidden until ImageRepository is ready, so the screen
 * doesn't promise a photo that isn't picked, uploaded or saved (US 02.01.01).
 *
 * The status bar icon styling was added in November 2026 with GLM 5.3 Flash
 * (Z.ai), through OpenCode, at Raghav Sethi's request.
 */

/**
 * Purpose: Displays the form for registering a new campsite.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */

package com.lambda.campsite.view.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import com.lambda.campsite.controller.CampsiteFormError
import com.lambda.campsite.controller.OwnerCampsiteAddController
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.LocalDate

/** The message a screen shows for why a campsite form failed (US 02.01.01, 02.01.03). */
internal fun campsiteFormErrorRes(error: CampsiteFormError): Int = when (error) {
    CampsiteFormError.SAVE_FAILED -> R.string.error_campsite_save_failed
    CampsiteFormError.INVALID_SEASON_RANGE -> R.string.error_season_invalid_range
    CampsiteFormError.INCOMPLETE_SEASON -> R.string.error_season_incomplete
}

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
    val form = state.form
    var newTagInput by remember { mutableStateOf("") }

    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

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
                enabled = !form.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                if (form.isSaving) {
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
            // Details Section
            FormSectionTitle(stringResource(R.string.section_details))
            val nameHasError = CampsiteRequiredField.NAME in form.requiredFieldErrors
            OutlinedTextField(
                value = form.name,
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

            val locationHasError = CampsiteRequiredField.LOCATION in form.requiredFieldErrors
            OutlinedTextField(
                value = form.location,
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
                value = form.description,
                onValueChange = controller::onDescriptionChange,
                label = { Text(stringResource(R.string.label_description)) },
                placeholder = { Text(stringResource(R.string.placeholder_description)) },
                supportingText = { Text(stringResource(R.string.supporting_description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Max Occupancy Card
            val occupancyHasError = CampsiteRequiredField.MAX_OCCUPANCY in form.requiredFieldErrors
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
                        Text(form.maxOccupancy.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalIconButton(
                        onClick = { controller.onOccupancyChange(form.maxOccupancy - 1) },
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_fewer_guests))
                    }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(
                        onClick = { controller.onOccupancyChange(form.maxOccupancy + 1) },
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
                    val seasonText = if (form.seasonStart != null && form.seasonEnd != null) {
                        "Open ${form.seasonStart} – ${form.seasonEnd}"
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
            form.seasonError?.let { seasonError ->
                Text(
                    text = stringResource(campsiteFormErrorRes(seasonError)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            // Map Location Section
            FormSectionTitle(stringResource(R.string.section_map_location))
            OutlinedButton(
                onClick = onPickLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val hasPin = form.latitude != null && form.longitude != null
                val label = if (hasPin) {
                    val placeName = form.locationPinName ?: "${form.latitude}, ${form.longitude}"
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
                form.tags.forEach { tag ->
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
            form.formError?.let { formError ->
                Text(
                    text = stringResource(campsiteFormErrorRes(formError)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
internal fun FormSectionTitle(title: String) = Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.semantics { heading() },
)
