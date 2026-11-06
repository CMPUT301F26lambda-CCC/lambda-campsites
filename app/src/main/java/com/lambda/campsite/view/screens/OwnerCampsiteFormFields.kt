/*
 * Purpose: Provides shared registration and edit fields for owner campsite forms.
 * Outstanding issues: None known.
 * AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with the shared owner form UI.
 */
package com.lambda.campsite.view.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lambda.campsite.R
import com.lambda.campsite.controller.CampsiteFormData
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.view.theme.CampField
import com.lambda.campsite.view.theme.CampOutline
import com.lambda.campsite.view.theme.ForestGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Actions shared by the Figma register and edit form fields (US 02.01). */
internal data class OwnerFormActions(
    val name: (String) -> Unit,
    val location: (String) -> Unit,
    val description: (String) -> Unit,
    val occupancy: (Int) -> Unit,
    val season: (LocalDate?, LocalDate?) -> Unit,
    val price: (String) -> Unit,
    val rules: (String) -> Unit,
    val addTag: (String) -> Unit,
    val removeTag: (String) -> Unit,
    val photo: (Bitmap) -> Unit,
    val removePhoto: () -> Unit,
    val pickLocation: () -> Unit,
)

/** Draws shared owner form fields with the selected photo and current values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OwnerCampsiteFormFields(
    form: CampsiteFormData,
    selectedPhoto: Bitmap?,
    existingPhotoUrl: String?,
    actions: OwnerFormActions,
) {
    var tagInput by remember { mutableStateOf("") }
    var photoError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val photo = withContext(Dispatchers.IO) { runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                    val sample = generateSequence(1) { size -> size * 2 }
                        .first { size -> bounds.outWidth / size <= 1600 && bounds.outHeight / size <= 1600 }
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                }.getOrNull() }
                if (photo != null) { photoError = false; actions.photo(photo) } else photoError = true
            }
        }
    }
    val hasPhoto = selectedPhoto != null || form.imageId != null

    FormSectionTitle(stringResource(R.string.section_photo))
    Box(
        Modifier.fillMaxWidth().height(164.dp)
            .border(1.dp, CampOutline, RoundedCornerShape(16.dp))
            .background(CampField, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selectedPhoto != null -> Image(selectedPhoto.asImageBitmap(),
                stringResource(R.string.owner_selected_photo), Modifier.fillMaxWidth().height(164.dp),
                contentScale = ContentScale.Crop)
            existingPhotoUrl != null -> AsyncImage(existingPhotoUrl,
                stringResource(R.string.owner_campsite_photo, form.name), Modifier.fillMaxWidth().height(164.dp),
                contentScale = ContentScale.Crop)
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, contentDescription = null, tint = ForestGreen)
                Text(stringResource(R.string.add_photo_optional))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = !form.isSaving) {
            Text(stringResource(if (hasPhoto) R.string.replace_photo_button else R.string.add_photo_button))
        }
        if (hasPhoto) TextButton(onClick = actions.removePhoto, enabled = !form.isSaving) {
            Text(stringResource(R.string.remove_photo_button))
        }
    }
    if (photoError) Text(stringResource(R.string.owner_photo_error), color = MaterialTheme.colorScheme.error)
    HorizontalDivider()

    FormSectionTitle(stringResource(R.string.section_details))
    val nameError = CampsiteRequiredField.NAME in form.requiredFieldErrors
    OutlinedTextField(
        value = form.name, onValueChange = actions.name,
        label = { Text(stringResource(R.string.label_name_required)) },
        placeholder = { Text(stringResource(R.string.placeholder_name)) },
        isError = nameError,
        supportingText = { if (nameError) Text(stringResource(R.string.error_enter_name)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        enabled = !form.isSaving,
    )
    val locationError = CampsiteRequiredField.LOCATION in form.requiredFieldErrors
    OutlinedTextField(
        value = form.location, onValueChange = actions.location,
        label = { Text(stringResource(R.string.label_location_required)) },
        placeholder = { Text(stringResource(R.string.placeholder_location)) },
        isError = locationError,
        supportingText = { if (locationError) Text(stringResource(R.string.error_enter_location)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        enabled = !form.isSaving,
    )
    OutlinedTextField(
        value = form.description, onValueChange = actions.description,
        label = { Text(stringResource(R.string.label_description)) },
        placeholder = { Text(stringResource(R.string.placeholder_description)) },
        minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !form.isSaving,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.label_max_occupancy_required), modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { actions.occupancy(form.maxOccupancy - 1) }, enabled = !form.isSaving) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_fewer_guests))
        }
        Text(form.maxOccupancy.toString(), modifier = Modifier.padding(horizontal = 14.dp))
        FilledTonalIconButton(onClick = { actions.occupancy(form.maxOccupancy + 1) }, enabled = !form.isSaving) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_more_guests))
        }
    }
    if (CampsiteRequiredField.MAX_OCCUPANCY in form.requiredFieldErrors) {
        Text(stringResource(R.string.supporting_max_occupancy), color = MaterialTheme.colorScheme.error)
    }
    HorizontalDivider()

    FormSectionTitle(stringResource(R.string.section_availability))
    SeasonDateField(R.string.season_opens_label, form.seasonStart, !form.isSaving) { actions.season(it, form.seasonEnd) }
    SeasonDateField(R.string.season_closes_label, form.seasonEnd, !form.isSaving) { actions.season(form.seasonStart, it) }
    Text(stringResource(R.string.season_range_hint), style = MaterialTheme.typography.bodySmall)
    form.seasonError?.let { Text(stringResource(campsiteFormErrorRes(it)), color = MaterialTheme.colorScheme.error) }
    HorizontalDivider()

    FormSectionTitle(stringResource(R.string.campsite_price_rules_heading))
    OutlinedTextField(
        value = form.pricePerNight, onValueChange = actions.price,
        label = { Text(stringResource(R.string.owner_price_label)) },
        prefix = { Text("$") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = form.checkPriceError() != null,
        enabled = !form.isSaving,
    )
    OutlinedTextField(
        value = form.rules, onValueChange = actions.rules,
        label = { Text(stringResource(R.string.owner_rules_label)) },
        minLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !form.isSaving,
    )
    HorizontalDivider()

    FormSectionTitle(stringResource(R.string.section_map_location))
    Box(
        Modifier.fillMaxWidth().height(128.dp)
            .background(CampField, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (form.latitude != null && form.longitude != null) {
            MapPreviewMini(form.latitude, form.longitude, Modifier.fillMaxWidth().height(128.dp))
        } else {
            Text(form.locationPinName ?: form.location.ifBlank { stringResource(R.string.no_map_pin_set) })
        }
    }
    OutlinedButton(onClick = actions.pickLocation, modifier = Modifier.fillMaxWidth(), enabled = !form.isSaving) {
        Text(stringResource(if (form.latitude == null) R.string.action_set_map_location else R.string.action_change_map_location))
    }
    HorizontalDivider()

    FormSectionTitle(stringResource(R.string.section_tags))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        form.tags.forEach { tag ->
            InputChip(selected = false, onClick = { actions.removeTag(tag) }, enabled = !form.isSaving,
                label = { Text(tag) }, trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.owner_remove_tag, tag), Modifier.size(16.dp))
                })
        }
    }
    OutlinedTextField(
        value = tagInput, onValueChange = { tagInput = it },
        label = { Text(stringResource(R.string.label_add_tag)) },
        trailingIcon = { IconButton(onClick = { if (tagInput.isNotBlank()) { actions.addTag(tagInput); tagInput = "" } }) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_tag))
        } },
        modifier = Modifier.fillMaxWidth(),
        enabled = !form.isSaving,
    )
    Text(stringResource(R.string.qr_auto_generated_note), style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonDateField(labelRes: Int, date: LocalDate?, enabled: Boolean, onSelected: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), enabled = enabled) {
        Text(stringResource(R.string.owner_season_date_format, stringResource(labelRes),
            date?.let(::formatOwnerCampsiteDate) ?: stringResource(R.string.owner_pick_date)))
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                onSelected(pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                open = false
            }) { Text(stringResource(R.string.owner_date_done)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.owner_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
internal fun FormSectionTitle(title: String) = Text(
    text = title, style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.semantics { heading() },
)
