/*
 * Purpose: This file contains the filter UI and is connected to
 * CampsiteFilterController through SearchScreen.kt.
 * The selections in filter are not applied until the user presses Apply filter.
 * Only then are the search results updated. Clear removes all applied filters.
 * This file does not filter the campsites itself. But there is also some additional
 * date validation in this file, besides the  validation in CampsiteFilterController.
 * Users cannot select a past date for check-in, and check-out must be at least one day
 * after check-in.(US 01.01.02), (US 01.01.03)
 * If the user changes check-in to the same date as check-out or a later date,
 * the check-out date is cleared.
 * It also contains MatchScoreBadge for the WOW factor match score (#71). It shows
 * a campsite's score, like "100% match", next to its lottery status on the search
 * results. The color shows how good the match is: dark green from 90%, light
 * green from 70%, and gray below that. The badge only displays the score;
 * MatchScoreController calculates it.
 * Outstanding issue- None
 */

/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code in November
 * 2026, assisted Harmanjoth Kaur with improving code reusability by suggesting
 * an approach to reuse the same components across all three filters (Location,
 * Dates and Guests). It also helped with Kotlin code, syntax, refining KDoc comments,
 * and help in adding more validation checks including check-in & check-out date rules.
 * Harmanjoth Kaur reviewed the suggestion and implemented them.
*/

// Follow-up #174: Lalith Valluru, 2026-11-04. OpenAI GPT-6 through Codex assisted
// with the first allowed picker month; human review pending.
package com.lambda.campsite.view.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lambda.campsite.R
import com.lambda.campsite.view.theme.CampField
import com.lambda.campsite.view.theme.CampOutline
import com.lambda.campsite.view.theme.CampPositive
import com.lambda.campsite.view.theme.CampSecondaryText
import com.lambda.campsite.view.theme.CampSurfaceHigh
import com.lambda.campsite.view.theme.DeepGreen
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
* The most guests the + button goes up to. It's set to 50 for now, which is
* more than a campsite usually holds, so the counter has a limit. It can be
* changed later, for example to the largest max occupancy of the campsites.
*/
private const val MAX_GUESTS = 50

/**
 * The Location, Dates and Guests chips under the search bar (US 01.01.02).
 * A chip in use is highlighted and shows its value, like "Banff" or
 * "4 guests". Tapping any chip opens the Filters sheet.
 *
 * @param location the applied location filter, or blank for any location.
 * @param checkIn the applied check-in day, or `null` for any dates.
 * @param checkOut the applied check-out day, or `null` for any dates.
 * @param guests the applied number of guests, or `null` for any group size.
 * @param onClick called when the entrant taps a chip.
 */
@Composable
fun CampsiteFilterChips(
    location: String,
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    guests: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortDate = remember { DateTimeFormatter.ofPattern("MMM d") }
    val locationLabel = stringResource(R.string.flow15_location)
    val datesLabel = stringResource(R.string.flow15_dates)
    val guestsLabel = stringResource(R.string.flow15_guests)
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropDownChip(
            text = location.ifBlank { locationLabel },
            name = locationLabel,
            selected = location.isNotBlank(),
            onClick = onClick,
        )
        FilterDropDownChip(
            text = when {
                checkIn != null && checkOut != null ->
                    stringResource(R.string.flow15_date_range, checkIn.format(shortDate), checkOut.format(shortDate))
                checkIn != null -> stringResource(R.string.flow15_from_date, checkIn.format(shortDate))
                checkOut != null -> stringResource(R.string.flow15_until_date, checkOut.format(shortDate))
                else -> datesLabel
            },
            name = datesLabel,
            selected = checkIn != null || checkOut != null,
            onClick = onClick,
        )
        FilterDropDownChip(
            text = guests?.let { pluralStringResource(R.plurals.flow15_guest_count, it, it) } ?: guestsLabel,
            name = guestsLabel,
            selected = guests != null,
            onClick = onClick,
        )
    }
}
// Draws one filter chip. All three chips (Location, Dates and Guests) look and
// behave the same, with only the text and selected state changing, so this
// function will be reused 3 times(Code Reusability
/** One chip with a drop-down arrow. [name] tells TalkBack which filter it opens. */
@Composable
private fun FilterDropDownChip(text: String, name: String, selected: Boolean, onClick: () -> Unit) {
    // TalkBack reads "Open filters, Location" or "Open filters, Location: Banff".
    val description = stringResource(R.string.flow15_open_filters, if (selected) "$name: $text" else name)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CampField,
            labelColor = DeepGreen,
            iconColor = DeepGreen,
            selectedContainerColor = CampPositive,
            selectedLabelColor = DeepGreen,
            selectedTrailingIconColor = DeepGreen,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CampOutline,
            selectedBorderColor = ForestGreen,
        ),
        modifier = Modifier
            .semantics { contentDescription = description },
    )
}

/**
 * The Filters bottom sheet (US 01.01.02): a location, check-in and check-out
 * days, and a number of guests. The entrant's choices stay a draft until they
 * press Apply filter, so the list only changes when they ask it to. Each
 * filter left empty matches every campsite, so an entrant can use one, two
 * or all three.
 *
 * Like every shared component, it takes plain values and callbacks, never a
 * model or controller.
 *
 * @param location the applied location filter, or blank for any location.
 * @param checkIn the applied check-in day, or `null` for any dates.
 * @param checkOut the applied check-out day, or `null` for any dates.
 * @param guests the applied number of guests, or `null` for any group size.
 * @param onApply called with the entrant's location, check-in, check-out and
 *   guests when they press Apply filter.
 * @param onClear called when the entrant presses Clear.
 * @param onDismiss called when the sheet closes without applying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsiteFilterSheet(
    location: String,
    checkIn: LocalDate?,
    checkOut: LocalDate?,
    guests: Int?,
    onApply: (location: String, checkIn: LocalDate?, checkOut: LocalDate?, guests: Int?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draftLocation by rememberSaveable { mutableStateOf(location) }
    var checkInDay by rememberSaveable { mutableStateOf(checkIn?.toEpochDay()) }
    var checkOutDay by rememberSaveable { mutableStateOf(checkOut?.toEpochDay()) }
    var draftGuests by rememberSaveable { mutableStateOf(guests) }
    var pickingCheckIn by rememberSaveable { mutableStateOf(false) }
    var pickingCheckOut by rememberSaveable { mutableStateOf(false) }

    val draftCheckIn = checkInDay?.let(LocalDate::ofEpochDay)
    val draftCheckOut = checkOutDay?.let(LocalDate::ofEpochDay)
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CampSurfaceHigh,
        contentColor = DeepGreen,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.flow15_filters),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )

            OutlinedTextField(
                value = draftLocation,
                onValueChange = { draftLocation = it },
                label = { Text(stringResource(R.string.flow15_location)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.flow15_dates))
            DateField(
                label = stringResource(R.string.flow15_check_in),
                value = draftCheckIn?.format(formatter),
                onClick = { pickingCheckIn = true },
            )
            DateField(
                label = stringResource(R.string.flow15_check_out),
                value = draftCheckOut?.format(formatter),
                onClick = { pickingCheckOut = true },
            )

            SectionLabel(stringResource(R.string.flow15_guests))
            FieldBox {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.flow15_number_of_guests),
                        style = MaterialTheme.typography.labelSmall,
                        color = CampSecondaryText,
                    )
                    Text(
                        text = draftGuests?.toString() ?: stringResource(R.string.flow15_any_guests),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                GuestStepButton(
                    icon = { Icon(Icons.Default.Remove, stringResource(R.string.flow15_fewer_guests)) },
                    onClick = { draftGuests = draftGuests?.let { if (it <= 1) null else it - 1 } },
                    enabled = draftGuests != null,
                )
                GuestStepButton(
                    icon = { Icon(Icons.Default.Add, stringResource(R.string.flow15_more_guests)) },
                    onClick = { draftGuests = ((draftGuests ?: 0) + 1).coerceAtMost(MAX_GUESTS) },
                    enabled = (draftGuests ?: 0) < MAX_GUESTS,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        draftLocation = ""
                        checkInDay = null
                        checkOutDay = null
                        draftGuests = null
                        onClear()
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.flow15_clear), color = DeepGreen)
                }
                Button(
                    onClick = { onApply(draftLocation.trim(), draftCheckIn, draftCheckOut, draftGuests) },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = Mint),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.flow15_apply))
                }
            }
        }
    }
    // To prevent users from choosing a check-in date in the past
    // and to ensure that the check-in date comes before the check-out date.
    if (pickingCheckIn) {
        StayDateDialog(
            initial = draftCheckIn,
            earliest = LocalDate.now(),
            onDismiss = { pickingCheckIn = false },
            onConfirm = { day ->
                checkInDay = day.toEpochDay()
                if (draftCheckOut != null && !draftCheckOut.isAfter(day)) checkOutDay = null
                pickingCheckIn = false
            },
        )
    }
    // To prevent users from selecting the same check-in and check-out date.
    // The check-out date must be at least one day after the check-in date.
    if (pickingCheckOut) {
        StayDateDialog(
            initial = draftCheckOut,
            earliest = (draftCheckIn ?: LocalDate.now()).plusDays(1),
            onDismiss = { pickingCheckOut = false },
            onConfirm = { day ->
                checkOutDay = day.toEpochDay()
                pickingCheckOut = false
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
    )
}

/** The rounded box the date and guests fields share, matching the text field above them. */
@Composable
private fun FieldBox(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val border = BorderStroke(1.dp, CampOutline)
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 64.dp)
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = CampField, border = border) {
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    } else {
        Surface(shape = shape, color = CampField, border = border) {
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
private fun DateField(label: String, value: String?, onClick: () -> Unit) {
    val selectDate = stringResource(R.string.flow15_select_date)
    FieldBox(onClick = onClick) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = CampSecondaryText)
            Text(
                text = value ?: selectDate,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) CampSecondaryText else DeepGreen,
            )
        }
        Icon(Icons.Outlined.CalendarMonth, null, tint = DeepGreen, modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun GuestStepButton(icon: @Composable () -> Unit, onClick: () -> Unit, enabled: Boolean) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = CampPositive, contentColor = DeepGreen),
        modifier = Modifier.size(48.dp),
        content = icon,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = CampField,
    focusedContainerColor = CampField,
    unfocusedBorderColor = CampOutline,
    focusedBorderColor = ForestGreen,
)

/**
 * Picks one day in a calendar pop-up. Days before [earliest] are greyed out,
 * which is how check-in stays from today onwards and check-out stays after
 * check-in. OK is disabled until a day is picked, and [initial] is only
 * pre-selected if it's still allowed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StayDateDialog(
    initial: LocalDate?,
    earliest: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val earliestMillis = remember(earliest) { earliest.toUtcMillis() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.takeIf { !it.isBefore(earliest) }?.toUtcMillis(),
        initialDisplayedMonthMillis = (initial?.takeIf { !it.isBefore(earliest) } ?: earliest).toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= earliestMillis
        },
    )
    val picked = pickerState.selectedDateMillis?.toLocalDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picked?.let(onConfirm) }, enabled = picked != null) {
                Text(stringResource(R.string.flow15_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.flow15_cancel)) }
        },
    ) {
        DatePicker(state = pickerState, showModeToggle = false)
    }
}


// UTC is used as a consistent reference when converting dates
// it prevents date shifts across time zones.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * The match score pill shown next to a campsite's lottery status (#71),
 * like "100% match". Its colour shows how good the match is: dark green from
 * 90%, light green from 70%, and grey below that.
 *
 * @param score the campsite's match score, from 0 to 100.
 */
@Composable
fun MatchScoreBadge(score: Int) {
    val (background, text) = when {
        score >= 90 -> ForestGreen to Mint
        score >= 70 -> CampPositive to DeepGreen
        else -> CampField to CampSecondaryText
    }
    val description = stringResource(R.string.match_score_description, score)
    Surface(
        color = background,
        contentColor = text,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = stringResource(R.string.match_score, score),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}