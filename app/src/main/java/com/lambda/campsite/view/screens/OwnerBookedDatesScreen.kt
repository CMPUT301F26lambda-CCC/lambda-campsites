/*
 * AI use: GPT-6 (OpenAI), through Codex, assisted with the Flow 11 booked-dates
 * calendar and read-only Figma alignment for frame 59355:2649.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerBookedDatesController
import com.lambda.campsite.controller.OwnerBookedDatesUiState
import com.lambda.campsite.view.components.OwnerReservationDetailsSheet
import com.lambda.campsite.view.theme.Flow11Colors
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Owner calendar for one selected campsite, with live updates (US 02.06.01). */
@Composable
fun OwnerBookedDatesScreen(controller: OwnerBookedDatesController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerBookedDatesContent(state, onBack, controller::onPreviousMonth, controller::onNextMonth,
        controller::onBookedDateSelected, controller::onRetry, controller::onCampsiteSelected)
    state.selectedReservation?.let { reservation ->
        OwnerReservationDetailsSheet(reservation, controller::onDetailsDismissed, state.selectedDate)
    }
}

/** Stateless Figma calendar, campsite selector, legend and empty state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerBookedDatesContent(
    state: OwnerBookedDatesUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBookedDateSelected: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onCampsiteSelected: (String) -> Unit = {},
) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(containerColor = Flow11Colors.header, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.booked_dates_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
            } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Flow11Colors.header,
                titleContentColor = Color.White, navigationIconContentColor = Color.White),
        )
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = Flow11Colors.surface,
            contentColor = Flow11Colors.text,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            when {
                state.isLoading && state.campsite == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Flow11Colors.header)
                }
                state.loadFailed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.reservations_load_failed))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                    }
                }
                state.campsite == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.owner_home_no_campsites))
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CampsiteSelector(state, onCampsiteSelected)
                    if (state.isLoading) {
                        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = Flow11Colors.header)
                                Text(stringResource(R.string.calendar_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(16.dp), color = Flow11Colors.card) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MonthPicker(state.month, onPreviousMonth, onNextMonth)
                                CalendarMonth(state, onBookedDateSelected)
                                Text(stringResource(if (state.bookedThisMonth.isEmpty()) R.string.calendar_empty_hint
                                    else R.string.calendar_booked_hint), style = MaterialTheme.typography.labelSmall,
                                    color = Flow11Colors.secondaryText)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CalendarLegend(Flow11Colors.booked, stringResource(R.string.booked_legend))
                            if (state.availability?.hasValidSeason() == true) {
                                CalendarLegend(Flow11Colors.field, stringResource(R.string.available_legend))
                            }
                            CalendarLegend(Flow11Colors.outsideSeason, stringResource(R.string.unavailable_legend))
                        }
                        Text(
                            stringResource(if (state.bookedThisMonth.isEmpty()) R.string.no_bookings_this_month else R.string.calendar_updates),
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium, color = Flow11Colors.secondaryText,
                            textAlign = if (state.bookedThisMonth.isEmpty()) TextAlign.Center else TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}

/** Figma campsite field with an owner-only menu of calendar choices. */
@Composable
private fun CampsiteSelector(state: OwnerBookedDatesUiState, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = state.campsites.ifEmpty { listOfNotNull(state.campsite) }
    Box {
        Surface(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(12.dp), color = Flow11Colors.field,
            border = androidx.compose.foundation.BorderStroke(1.dp, Flow11Colors.outline)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.campsite_field), style = MaterialTheme.typography.labelSmall,
                        color = Flow11Colors.secondaryText)
                    Text(state.campsite?.name ?: stringResource(R.string.choose_campsite),
                        style = MaterialTheme.typography.bodyLarge)
                }
                Image(painterResource(R.drawable.flow11_dropdown), null, Modifier.size(24.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = Flow11Colors.field) {
            options.forEach { campsite ->
                DropdownMenuItem(text = { Text(campsite.name) },
                    modifier = Modifier.semantics { selected = campsite.id == state.selectedCampsiteId },
                    onClick = { expanded = false; onSelected(campsite.id) })
            }
        }
    }
}

/** Current month and accessible adjacent-month actions. */
@Composable
private fun MonthPicker(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
            modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onPrevious) {
            Image(painterResource(R.drawable.flow11_month_left),
                contentDescription = stringResource(R.string.previous_month), modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onNext) {
            Image(painterResource(R.drawable.flow11_month_right),
                contentDescription = stringResource(R.string.next_month), modifier = Modifier.size(24.dp))
        }
    }
}

/** Seven-column month. A peach day is a 48 dp action with its full date announced. */
@Composable
private fun CalendarMonth(state: OwnerBookedDatesUiState, onBookedDateSelected: (LocalDate) -> Unit) {
    val month = state.month
    val booked = state.bookedThisMonth
    val firstOffset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val weeks = (firstOffset + month.lengthOfMonth() + 6) / 7
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = Flow11Colors.secondaryText, textAlign = TextAlign.Center)
            }
        }
        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { weekday ->
                    val dayNumber = week * 7 + weekday - firstOffset + 1
                    if (dayNumber !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(48.dp))
                    } else {
                        val date = month.atDay(dayNumber)
                        val isBooked = date in booked
                        val isAvailable = state.isAvailableNight(date)
                        val label = stringResource(when {
                            isBooked -> R.string.booked_date_description
                            state.availability == null -> R.string.no_season_date_description
                            isAvailable -> R.string.available_date_description
                            else -> R.string.outside_season_date_description
                        }, date.format(dateFormat))
                        Box(Modifier.weight(1f).height(48.dp)
                            .background(when {
                                isBooked -> Flow11Colors.booked
                                isAvailable -> Color.Transparent
                                else -> Flow11Colors.outsideSeason
                            }, RoundedCornerShape(8.dp))
                            .then(if (isBooked) Modifier.clickable(role = Role.Button) { onBookedDateSelected(date) } else Modifier)
                            .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                            Text(dayNumber.toString(), style = MaterialTheme.typography.bodyMedium,
                                color = Flow11Colors.text)
                        }
                    }
                }
            }
        }
    }
}

/** One colour and label in the booked/available legend. */
@Composable
private fun CalendarLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Flow11Colors.secondaryText)
    }
}
