/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the controller
 * integration, the four wizard steps, date-picker dialogs, TalkBack
 * accessibility, saving feedback, strings extraction and KDoc, following the
 * OwnerCampsiteEditScreen shape. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.NewLotteryController
import com.lambda.campsite.controller.NewLotteryError
import com.lambda.campsite.controller.NewLotteryUiState
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** How a date reads in the wizard, like "Oct 6, 2026". */
private val WizardDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** The four wizard steps, in order. Index 0 is step 1. */
private val WizardSteps = listOf("Campsites", "Dates", "Settings", "Review")

/** Which date field a picker is open for. */
private enum class WizardDateField {
    REGISTRATION_OPENS,
    REGISTRATION_CLOSES,
    STAY_START,
    STAY_END,
}

/**
 * The four-step "New lottery" wizard (US 02.04.01 to 02.04.07).
 *
 * It connects to [NewLotteryController] and shows one step at a time: the
 * owner's campsites, the registration and booking periods, the lottery's name
 * and waiting-list settings, then a review with Edit buttons.
 *
 * @param controller the [NewLotteryController] managing the wizard's values.
 * @param onClose invoked when the owner closes the wizard without saving.
 * @param onCreated invoked with the lottery once Firestore accepts it.
 * @param modifier optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLotteryScreen(
    controller: NewLotteryController,
    onClose: () -> Unit,
    onCreated: (Lottery) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    // The dark green app bar runs under the clock, so the clock draws white.
    StatusBarIconStyle(darkIcons = false)

    Scaffold(
        modifier = modifier,
        containerColor = CampGreenLight,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.new_lottery_title),
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CampGreenDark,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
                WizardStepHeader(state.step)
            }
        },
        bottomBar = {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        if (state.step < WizardSteps.size) {
                            controller.onNext()
                        } else {
                            controller.onCreateLottery { lottery -> onCreated(lottery) }
                        }
                    },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = CampGreenDark),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            stringResource(
                                if (state.step < WizardSteps.size) R.string.action_continue else R.string.create_lottery_button,
                            ),
                        )
                    }
                }
                TextButton(onClick = { if (state.step > 1) controller.onBack() else onClose() }) {
                    Text(stringResource(R.string.action_back))
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
            when (state.step) {
                1 -> CampsitesStep(controller, state.selectedCampsiteIds, state.campsites)
                2 -> DatesStep(controller, state)
                3 -> SettingsStep(controller, state)
                else -> ReviewStep(controller, state)
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(newLotteryErrorRes(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.error_lottery_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** The step counter and the bar filling up as steps complete. */
@Composable
private fun WizardStepHeader(step: Int) {
    Column(Modifier.fillMaxWidth().background(CampGreenDark).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(
                R.string.new_lottery_step_format,
                step,
                WizardSteps.size,
                stringResource(wizardStepNameRes(step)),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        Spacer(Modifier.size(4.dp))
        LinearProgressIndicator(
            progress = { step.toFloat() / WizardSteps.size },
            modifier = Modifier.fillMaxWidth(),
            color = CampGreenLight,
            trackColor = CampGreenField,
        )
    }
}

@Composable
private fun CampsitesStep(
    controller: NewLotteryController,
    selectedCampsiteIds: List<String>,
    campsites: List<Campsite>,
) {
    FormSectionTitle(stringResource(R.string.new_lottery_select_campsites))
    campsites.forEach { campsite ->
        CampsitePickRow(
            campsite = campsite,
            selected = campsite.id in selectedCampsiteIds,
            onClick = { controller.onCampsiteToggled(campsite.id) },
        )
    }
    Spacer(Modifier.size(4.dp))
    Text(
        text = if (selectedCampsiteIds.size == 1) {
            stringResource(R.string.new_lottery_count_one_campsite)
        } else {
            stringResource(R.string.new_lottery_count_many_campsites, selectedCampsiteIds.size)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One owner campsite with the checkbox that picks it for the lottery (US 02.04.02). */
@Composable
private fun CampsitePickRow(campsite: Campsite, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f)) {
                Text(campsite.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = campsite.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesStep(controller: NewLotteryController, state: NewLotteryUiState) {
    var openPickerFor by remember { mutableStateOf<WizardDateField?>(null) }

    FormSectionTitle(stringResource(R.string.new_lottery_registration_period))
    WizardDateField(
        label = stringResource(R.string.new_lottery_registration_opens),
        value = state.registrationOpens,
        onClick = { openPickerFor = WizardDateField.REGISTRATION_OPENS },
    )
    WizardDateField(
        label = stringResource(R.string.new_lottery_registration_closes),
        value = state.registrationCloses,
        onClick = { openPickerFor = WizardDateField.REGISTRATION_CLOSES },
    )

    FormSectionTitle(stringResource(R.string.new_lottery_booking_period))
    WizardDateField(
        label = stringResource(R.string.new_lottery_check_in),
        value = state.stayStart,
        onClick = { openPickerFor = WizardDateField.STAY_START },
    )
    WizardDateField(
        label = stringResource(R.string.new_lottery_check_out),
        value = state.stayEnd,
        onClick = { openPickerFor = WizardDateField.STAY_END },
    )
    Text(
        text = stringResource(R.string.new_lottery_dates_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    openPickerFor?.let { field ->
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { openPickerFor = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            // DatePicker works in UTC, so its millis land on the
                            // right calendar day only through UTC.
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            when (field) {
                                WizardDateField.REGISTRATION_OPENS -> controller.onRegistrationOpensChange(date)
                                WizardDateField.REGISTRATION_CLOSES -> controller.onRegistrationClosesChange(date)
                                WizardDateField.STAY_START -> controller.onStayStartChange(date)
                                WizardDateField.STAY_END -> controller.onStayEndChange(date)
                            }
                        }
                        openPickerFor = null
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { openPickerFor = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** A read-only date field that opens a calendar picker when tapped (US 02.04.03, 02.04.04). */
@Composable
private fun WizardDateField(label: String, value: LocalDate?, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value?.format(WizardDate) ?: stringResource(R.string.new_lottery_pick_date),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsStep(controller: NewLotteryController, state: NewLotteryUiState) {
    FormSectionTitle(stringResource(R.string.new_lottery_settings))
    OutlinedTextField(
        value = state.name,
        onValueChange = controller::onNameChange,
        label = { Text(stringResource(R.string.new_lottery_name_required)) },
        placeholder = { Text(stringResource(R.string.new_lottery_name_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    StepperCard(
        label = stringResource(R.string.new_lottery_number_of_winners),
        value = state.numberOfWinners,
        onDecrease = { controller.onWinnersChange(state.numberOfWinners - 1) },
        onIncrease = { controller.onWinnersChange(state.numberOfWinners + 1) },
    )

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.new_lottery_limit_waiting_list),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.limitWaitingList, onCheckedChange = controller::onLimitWaitingListChange)
        }
    }

    if (state.limitWaitingList) {
        StepperCard(
            label = stringResource(R.string.new_lottery_waiting_list_capacity),
            value = state.capacity,
            onDecrease = { controller.onCapacityChange(state.capacity - 1) },
            onIncrease = { controller.onCapacityChange(state.capacity + 1) },
        )
        Text(
            text = stringResource(R.string.new_lottery_unlimited_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A "+ / -" number stepper, like the campsite form's max occupancy card. */
@Composable
private fun StepperCard(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalIconButton(onClick = onDecrease) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_decrease))
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onIncrease) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_increase))
            }
        }
    }
}

@Composable
private fun ReviewStep(controller: NewLotteryController, state: NewLotteryUiState) {
    FormSectionTitle(stringResource(R.string.new_lottery_review))
    ReviewCard(
        heading = stringResource(R.string.new_lottery_review_campsites),
        onEdit = { controller.onStepSelected(1) },
        body = {
            state.campsites
                .filter { it.id in state.selectedCampsiteIds }
                .forEach { campsite -> Text(campsite.name, style = MaterialTheme.typography.bodyMedium) }
        },
    )
    ReviewCard(
        heading = stringResource(R.string.new_lottery_review_dates),
        onEdit = { controller.onStepSelected(2) },
        body = {
            ReviewLine(stringResource(R.string.new_lottery_registration_opens), state.registrationOpens)
            ReviewLine(stringResource(R.string.new_lottery_registration_closes), state.registrationCloses)
            ReviewLine(stringResource(R.string.new_lottery_check_in), state.stayStart)
            ReviewLine(stringResource(R.string.new_lottery_check_out), state.stayEnd)
        },
    )
    ReviewCard(
        heading = stringResource(R.string.new_lottery_settings),
        onEdit = { controller.onStepSelected(3) },
        body = {
            val settingsText = if (state.limitWaitingList) {
                stringResource(
                    R.string.new_lottery_review_settings_limited,
                    state.numberOfWinners,
                    state.capacity,
                )
            } else {
                stringResource(R.string.new_lottery_review_settings_unlimited, state.numberOfWinners)
            }
            Text(settingsText, style = MaterialTheme.typography.bodyMedium)
        },
    )
    Text(
        text = stringResource(R.string.new_lottery_create_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One review summary, with the Edit button that jumps back to its step. */
@Composable
private fun ReviewCard(
    heading: String,
    onEdit: () -> Unit,
    body: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            }
            body()
        }
    }
}

/** One labelled line of a review summary, or "not set" while the date is missing. */
@Composable
private fun ReviewLine(label: String, value: LocalDate?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value?.format(WizardDate) ?: stringResource(R.string.new_lottery_not_set),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The string for [error], so the screen can show why the wizard refused something. */
internal fun newLotteryErrorRes(error: NewLotteryError): Int = when (error) {
    NewLotteryError.NO_CAMPSITES_SELECTED -> R.string.error_lottery_no_campsites
    NewLotteryError.TOO_MANY_CAMPSITES -> R.string.error_lottery_too_many_campsites
    NewLotteryError.MISSING_DATES -> R.string.error_lottery_missing_dates
    NewLotteryError.INVALID_DATES -> R.string.error_lottery_invalid_dates
    NewLotteryError.MISSING_NAME -> R.string.error_lottery_missing_name
    NewLotteryError.SAVE_FAILED -> R.string.error_lottery_save_failed
}

/** The name of [step], shown in the "Step 1 of 4 · Campsites" header. */
internal fun wizardStepNameRes(step: Int): Int = when (step) {
    1 -> R.string.new_lottery_step_campsites
    2 -> R.string.new_lottery_step_dates
    3 -> R.string.new_lottery_step_settings
    else -> R.string.new_lottery_step_review
}
