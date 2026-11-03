/*
 * Purpose: Flow 9 campsite selection, free-date calendar, review and saved receipt.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack/device review remains pending (#126).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with Figma-to-Compose
 * layout, semantics and callbacks; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.*
import com.lambda.campsite.view.components.CampsiteSummaryCard
import com.lambda.campsite.view.components.LotteryCelebrationArtwork
import com.lambda.campsite.view.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Winner-only booking route; the controller owns the wizard and all server operations. */
@Composable
fun WinnerBookingScreen(controller: WinnerBookingController, onClose: () -> Unit, onCampsite: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }
    val back = { if (!controller.onBack()) onClose() }
    BackHandler(onBack = back)
    WinnerBookingContent(state, back, onClose, controller::onRetry, controller::onSelectCampsite,
        controller::onDate, controller::onMonth, controller::onContinue, controller::onGuests,
        controller::onRulesAgreed, controller::onBook, controller::onRequestDecline,
        controller::onCancelDecline, controller::onConfirmDecline, onCampsite,
        controller::onEditCheckIn, controller::onEditCheckOut)
}

/** Stateless Figma layout with scrollable content and actions clear of the app navigation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerBookingContent(
    state: WinnerBookingUiState, onBack: () -> Unit, onClose: () -> Unit, onRetry: () -> Unit,
    onSelectSite: (String) -> Unit, onDate: (LocalDate) -> Unit, onMonth: (Long) -> Unit,
    onContinue: () -> Unit, onGuests: (Int) -> Unit, onRules: (Boolean) -> Unit, onBook: () -> Unit,
    onDecline: () -> Unit, onCancelDecline: () -> Unit, onConfirmDecline: () -> Unit,
    onCampsite: (String) -> Unit = {},
    onEditCheckIn: () -> Unit = {}, onEditCheckOut: () -> Unit = {},
) {
    StatusBarIconStyle(darkIcons = false)
    val scroll = key(state.step) { rememberScrollState() }
    val title = when (state.step) {
        WinnerBookingStep.CAMPSITE -> R.string.flow9_choose_site
        WinnerBookingStep.DATES -> R.string.flow9_choose_dates
        WinnerBookingStep.REVIEW -> R.string.flow9_review
        WinnerBookingStep.CONFIRMED -> R.string.flow9_confirmed
        WinnerBookingStep.DECLINED -> R.string.flow9_history_declined
    }
    Scaffold(containerColor = ForestGreen, topBar = {
        TopAppBar(title = { Text(stringResource(title), Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.flow9_back))
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        val confirmed = state.step == WinnerBookingStep.CONFIRMED && state.receipt != null
        Surface(Modifier.fillMaxSize().padding(padding), color = if (confirmed) ForestGreen else Mint, contentColor = CampOnSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(if (confirmed) 0.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    state.loadFailed -> { Text(stringResource(R.string.flow9_load_error)); Button(onClick = onRetry) { Text(stringResource(R.string.flow9_retry)) } }
                    state.isLoading -> { CircularProgressIndicator(); Text(stringResource(R.string.flow9_loading)) }
                    state.step == WinnerBookingStep.DECLINED -> {
                        Text(stringResource(R.string.flow9_declined), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.flow9_done)) }
                    }
                    state.step == WinnerBookingStep.CONFIRMED -> {
                        val receipt = state.receipt
                        if (receipt == null) { Text(stringResource(R.string.flow9_receipt_pending)); Button(onClick = onRetry) { Text(stringResource(R.string.flow9_retry)) } }
                        else {
                            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LotteryCelebrationArtwork(confirmed = true)
                                Text(stringResource(R.string.flow9_confirmed), color = Mint)
                                Text(stringResource(R.string.flow9_all_set), style = CampBrandName, color = Color.White,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; heading() })
                                Text(receipt.campsiteName, color = Mint)
                            }
                            Surface(Modifier.fillMaxWidth(), color = Mint, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                  Surface(color = CampSurfaceHigh, shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(receipt.campsiteName, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.flow9_reservation_number, receipt.number))
                                    Text(dateRange(receipt.checkIn, receipt.checkOut))
                                    Text(stringResource(R.string.flow9_receipt_guests, receipt.guests))
                                    }
                                  }
                                  Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.flow9_done)) }
                                }
                            }
                        }
                    }
                    !state.canBook -> Text(stringResource(R.string.flow9_winner_only))
                    else -> {
                        when (state.step) {
                            WinnerBookingStep.CAMPSITE -> {
                                Text(stringResource(R.string.flow9_site_intro, state.lottery?.name.orEmpty()))
                                Text(stringResource(R.string.flow9_sites_heading), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() })
                                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    state.sites.forEach { site ->
                                        val camp = site.campsite
                                        if (camp == null) Text(stringResource(R.string.flow9_site_missing))
                                        else Row(Modifier.fillMaxWidth().selectable(selected = camp.id == state.selectedCampsiteId,
                                            role = Role.RadioButton, onClick = { onSelectSite(camp.id) }), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = camp.id == state.selectedCampsiteId, onClick = null)
                                            Column(Modifier.weight(1f)) {
                                                CampsiteSummaryCard(camp.name, camp.location, camp.maxOccupancy, camp.averageRating, camp.ratingCount,
                                                    onDetails = { onCampsite(camp.id) }, onSelect = { onSelectSite(camp.id) })
                                            }
                                        }
                                    }
                                }
                                Text(stringResource(R.string.flow9_other_options), color = CampSecondaryText)
                            }
                            WinnerBookingStep.DATES -> {
                                Text(state.selectedSite?.campsite?.let { "${it.name} · ${it.location}" }.orEmpty())
                                DateValue(stringResource(R.string.flow9_checkin), state.checkIn, onEditCheckIn)
                                DateValue(stringResource(R.string.flow9_checkout), state.checkOut, onEditCheckOut)
                                Text(stringResource(R.string.flow9_dates_heading), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() })
                                if (!state.hasDates) Text(stringResource(R.string.flow9_no_dates), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                BookingCalendar(state, onDate, onMonth)
                            }
                            WinnerBookingStep.REVIEW -> {
                                val camp = state.selectedSite?.campsite
                                Text(camp?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                Text(dateRange(state.checkIn, state.checkOut))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.flow9_guests), Modifier.weight(1f))
                                    IconButton(onClick = { onGuests(state.guests - 1) }, enabled = !state.saving && state.guests > 1) {
                                        val label = stringResource(R.string.flow9_fewer_guests)
                                        Text("−", Modifier.clearAndSetSemantics { contentDescription = label })
                                    }
                                    Text(state.guests.toString(), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                    IconButton(onClick = { onGuests(state.guests + 1) }, enabled = !state.saving && state.guests < (camp?.maxOccupancy ?: 1)) {
                                        val label = stringResource(R.string.flow9_more_guests)
                                        Text("+", Modifier.clearAndSetSemantics { contentDescription = label })
                                    }
                                }
                                Text(stringResource(R.string.flow9_guest_limit, camp?.maxOccupancy ?: 0), color = CampSecondaryText)
                                Text(stringResource(R.string.flow9_rules), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Surface(shape = RoundedCornerShape(12.dp), color = CampField) {
                                    Text(camp?.rules?.ifBlank { stringResource(R.string.flow9_no_rules) } ?: stringResource(R.string.flow9_no_rules), Modifier.fillMaxWidth().padding(16.dp))
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    val label = stringResource(R.string.flow9_agree)
                                    Checkbox(state.rulesAgreed, onCheckedChange = onRules, enabled = !state.saving,
                                        modifier = Modifier.semantics { contentDescription = label })
                                    Text(stringResource(R.string.flow9_agree))
                                }
                            }
                            else -> Unit
                        }
                        state.error?.let { Text(stringResource(bookingError(it)), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        Button(onClick = if (state.step == WinnerBookingStep.REVIEW) onBook else onContinue,
                            enabled = !state.saving && when (state.step) {
                                WinnerBookingStep.CAMPSITE -> state.selectedSite?.campsite != null
                                WinnerBookingStep.DATES -> state.validRange
                                WinnerBookingStep.REVIEW -> state.rulesAgreed && state.validRange
                                else -> false
                            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(if (state.saving) R.string.flow9_saving else if (state.step == WinnerBookingStep.REVIEW) R.string.flow9_book else R.string.flow9_continue))
                        }
                        TextButton(onClick = onDecline, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.flow9_decline), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (state.confirmDecline) AlertDialog(onDismissRequest = onCancelDecline,
        title = { Text(stringResource(R.string.flow9_decline_title)) }, text = { Text(stringResource(R.string.flow9_decline_body)) },
        confirmButton = { TextButton(onClick = onConfirmDecline, enabled = !state.saving) { Text(stringResource(R.string.flow9_decline)) } },
        dismissButton = { TextButton(onClick = onCancelDecline, enabled = !state.saving) { Text(stringResource(R.string.flow9_cancel)) } })
}

@Composable
private fun DateValue(label: String, date: LocalDate?, onEdit: () -> Unit) {
    Surface(onClick = onEdit, shape = RoundedCornerShape(12.dp), color = CampField) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "—")
        }
    }
}

@Composable
private fun BookingCalendar(state: WinnerBookingUiState, onDate: (LocalDate) -> Unit, onMonth: (Long) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = CampSurfaceHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())), Modifier.weight(1f))
                IconButton(onClick = { onMonth(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.flow9_previous_month)) }
                IconButton(onClick = { onMonth(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.flow9_next_month)) }
            }
            Row(Modifier.fillMaxWidth()) { (1..7).forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(java.time.DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall)
                }
            } }
            val offset = state.month.atDay(1).dayOfWeek.value - 1
            repeat((offset + state.month.lengthOfMonth() + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth()) { repeat(7) { weekday ->
                    val number = week * 7 + weekday - offset + 1
                    if (number !in 1..state.month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val date = state.month.atDay(number)
                        val checkout = state.checkIn != null && state.checkOut == null && state.checkoutAvailable(date)
                        val enabled = !state.saving && (state.nightAvailable(date) || checkout)
                        val selected = date == state.checkIn || date == state.checkOut
                        val label = stringResource(if (checkout) R.string.flow9_checkout_date else if (enabled) R.string.flow9_available_date else R.string.flow9_unavailable_date,
                            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)))
                        TextButton(onClick = { onDate(date) }, enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = label; this.selected = selected },
                            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = if (selected) ForestGreen else Color.Transparent,
                                contentColor = if (selected) Color.White else CampOnSurface)) { Text(number.toString()) }
                    }
                } }
            }
            Text(stringResource(R.string.flow9_calendar_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Localized dates shared by review and the stored reservation receipt. */
@Composable
private fun dateRange(start: LocalDate?, end: LocalDate?): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return stringResource(R.string.flow9_dates, start?.format(format).orEmpty(), end?.format(format).orEmpty())
}

/** Resource for a controller error; no exception details are exposed to the user. */
private fun bookingError(error: WinnerBookingError): Int = when (error) {
    WinnerBookingError.DATES_TAKEN -> R.string.flow9_dates_taken
    WinnerBookingError.INVALID_DATES -> R.string.flow9_invalid_dates
    WinnerBookingError.GUESTS -> R.string.flow9_invalid_guests
    WinnerBookingError.NOT_A_WINNER -> R.string.flow9_winner_only
    WinnerBookingError.SAVE_FAILED -> R.string.flow9_save_error
    WinnerBookingError.RULES_REQUIRED -> R.string.flow9_rules_required
}
