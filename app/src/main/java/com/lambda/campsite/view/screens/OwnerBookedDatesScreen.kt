/*
 * AI use: GPT-6 (OpenAI), through Codex, helped implement the live, accessible
 * owner booking calendar for Flow 11 (US 02.06.01). Human review is pending.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerBookedDatesController
import com.lambda.campsite.controller.OwnerBookedDatesUiState
import com.lambda.campsite.view.components.OwnerReservationDetailsSheet
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Live booked-date calendar for one campsite (US 02.06.01).
 * A booked day opens the reservation's details; month controls call the controller.
 */
@Composable
fun OwnerBookedDatesScreen(controller: OwnerBookedDatesController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerBookedDatesContent(
        state = state,
        onBack = onBack,
        onPreviousMonth = controller::onPreviousMonth,
        onNextMonth = controller::onNextMonth,
        onBookedDateSelected = controller::onBookedDateSelected,
        onRetry = controller::onRetry,
    )
    state.selectedReservation?.let { OwnerReservationDetailsSheet(it, controller::onDetailsDismissed) }
}

/** Renders the calendar for [state] with clearly labelled booked nights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerBookedDatesContent(
    state: OwnerBookedDatesUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onBookedDateSelected: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(
        containerColor = CampGreenLight,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        state.campsite?.name ?: stringResource(R.string.booked_dates_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CampGreenDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.campsite == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadFailed -> Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.reservations_load_failed), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry_button)) }
                }
            }
            state.campsite == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_campsite_not_found))
            }
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.booked_dates_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                MonthPicker(state.month, onPreviousMonth, onNextMonth)
                CalendarMonth(state.month, state.bookedThisMonth, onBookedDateSelected)
                if (state.bookedThisMonth.isEmpty()) {
                    Text(stringResource(R.string.no_bookings_this_month), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    Text(stringResource(R.string.booked_dates_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Month heading and adjacent month controls. */
@Composable
private fun MonthPicker(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
        }
        Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

/** Seven-column calendar, Monday first. Booked cells are actionable and announced as booked. */
@Composable
private fun CalendarMonth(month: YearMonth, booked: Set<LocalDate>, onBookedDateSelected: (LocalDate) -> Unit) {
    val firstOffset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = firstOffset + month.lengthOfMonth()
    val weeks = (cells + 6) / 7
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { weekday ->
                    val dayNumber = week * 7 + weekday - firstOffset + 1
                    if (dayNumber !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                    } else {
                        val date = month.atDay(dayNumber)
                        val isBooked = date in booked
                        val label = stringResource(
                            if (isBooked) R.string.booked_date_description else R.string.available_date_description,
                            date.format(dateFormat),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .background(if (isBooked) CampGreenDark else CampGreenField, RoundedCornerShape(8.dp))
                                .then(if (isBooked) Modifier.clickable { onBookedDateSelected(date) } else Modifier)
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(dayNumber.toString(), color = if (isBooked) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
