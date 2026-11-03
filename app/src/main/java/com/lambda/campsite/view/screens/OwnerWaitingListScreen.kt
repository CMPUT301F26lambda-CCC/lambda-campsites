/*
 * Feature owner: Lalith Valluru. Purpose: Figma-aligned Flow 12 waiting-list screen.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted Lalith with Compose layout,
 * accessibility semantics and callbacks. Human review pending.
 * Reference: Lambda Campsite UI / Sreeman / 59355:5791, 59355:5860, 59355:5935.
 * Names/contact details use an owner-authorized projection; full profiles remain private.
 */
package com.lambda.campsite.view.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerWaitingListController
import com.lambda.campsite.controller.OwnerWaitingListError
import com.lambda.campsite.controller.OwnerWaitingListUiState
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.WaitingListContact
import com.lambda.campsite.view.theme.StatusBarIconStyle
import com.lambda.campsite.view.theme.WaitingListColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Live owner waiting list (US 02.09.01). The host supplies the shared bottom navigation.
 * @param controller supplies owned lotteries, entries and selection/error actions.
 * @param onBack returns to the owner's previous navigation destination.
 */
@Composable
fun OwnerWaitingListScreen(controller: OwnerWaitingListController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerWaitingListContent(state, controller::onSelectLottery, controller::onRetry,
        controller::onOpenEntrant, controller::onDismissEntrant, onBack)
}

/**
 * Stateless Figma waiting-list presentation with a local selector popup.
 * @param state only successfully authorized entries are exposed by the controller.
 * @param onSelectLottery selects an owned lottery by ID.
 * @param onRetry retries the failed read, never a write.
 * @param onOpenEntrant opens current registration details, not a private profile.
 * @param onDismissEntrant closes the sheet.
 * @param onBack leaves this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerWaitingListContent(
    state: OwnerWaitingListUiState,
    onSelectLottery: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenEntrant: (String) -> Unit,
    onDismissEntrant: () -> Unit,
    onBack: () -> Unit,
) {
    StatusBarIconStyle(darkIcons = false)
    var choosing by remember { mutableStateOf(false) }
    Scaffold(containerColor = WaitingListColors.header, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.flow12_title),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.flow12_back))
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = WaitingListColors.header,
                titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = WaitingListColors.surface,
            contentColor = WaitingListColors.text,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loadingLotteries) item { LoadingWaitingList(R.string.flow12_loading_lotteries) }
                else {
                    if (state.lotteries.isNotEmpty()) item {
                        Box {
                            Surface(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(12.dp), color = WaitingListColors.field,
                                border = BorderStroke(1.dp, WaitingListColors.outline)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(R.string.flow12_lottery), style = MaterialTheme.typography.labelSmall,
                                            color = WaitingListColors.secondaryText)
                                        Text(state.selectedLottery?.name ?: stringResource(R.string.flow12_choose),
                                            style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp))
                                }
                            }
                            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false },
                                containerColor = WaitingListColors.field) {
                                state.lotteries.forEach { lottery ->
                                    DropdownMenuItem(text = { Text(lottery.name) },
                                        modifier = Modifier.semantics { selected = lottery.id == state.selectedLotteryId },
                                        onClick = { choosing = false; onSelectLottery(lottery.id) })
                                }
                            }
                        }
                    }
                    if (state.error != null) item { WaitingListFailure(state.error, onRetry) }
                    else if (state.lotteries.isEmpty()) item {
                        Text(stringResource(R.string.flow12_no_lotteries), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.flow12_no_lotteries_help), style = MaterialTheme.typography.bodyMedium)
                    }
                    else if (state.loadingEntries) item { LoadingWaitingList(R.string.flow12_loading_entries) }
                    else if (state.selectedLottery != null) {
                        item { Text(pluralStringResource(R.plurals.flow12_count, state.entries.size, state.entries.size),
                            style = MaterialTheme.typography.titleSmall, color = WaitingListColors.secondaryText,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (state.entries.isEmpty()) item {
                            Text(stringResource(R.string.flow12_empty), style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        if (state.loadingContacts) item { LoadingWaitingList(R.string.flow12_loading_contacts) }
                        if (state.contactsFailed) item {
                            Text(stringResource(R.string.flow12_contacts_error), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.flow12_retry))
                            }
                        }
                        items(state.entries, key = { it.entrantId }) { entry ->
                            WaitingListEntrantRow(entry, state.contacts[entry.entrantId]) { onOpenEntrant(entry.entrantId) }
                        }
                        if (state.entries.isNotEmpty()) item {
                            Text(stringResource(R.string.flow12_updates), style = MaterialTheme.typography.bodyMedium,
                                color = WaitingListColors.secondaryText)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.flow12_contacts_private), style = MaterialTheme.typography.bodySmall,
                                color = WaitingListColors.secondaryText)
                        }
                    }
                }
            }
        }
    }
    state.selectedEntrant?.let { entry ->
        val contact = state.contacts[entry.entrantId]
        ModalBottomSheet(onDismissRequest = onDismissEntrant, containerColor = WaitingListColors.sheet,
            contentColor = WaitingListColors.text, scrimColor = Color.Black.copy(alpha = 0.32f),
            properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false,
                isAppearanceLightNavigationBars = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(contact?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.flow12_sheet_title), style = MaterialTheme.typography.titleLarge,
                        maxLines = Int.MAX_VALUE, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismissEntrant, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.flow12_close_sheet))
                    }
                }
                Text(stringResource(R.string.flow12_entrant, entry.entrantId), style = MaterialTheme.typography.bodyLarge)
                if (state.loadingContacts) LoadingWaitingList(R.string.flow12_loading_contacts)
                if (state.contactsFailed) {
                    Text(stringResource(R.string.flow12_contacts_error))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.flow12_retry)) }
                } else if (!state.loadingContacts) {
                    Text(stringResource(R.string.flow12_email, contact?.email ?: stringResource(R.string.flow12_not_provided)))
                    Text(stringResource(R.string.flow12_phone, contact?.phone ?: stringResource(R.string.flow12_not_provided)))
                }
                Text(joinedDate(entry), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.flow12_status, stringResource(when (entry.status) {
                    EntryStatus.WAITING -> R.string.flow12_waiting
                    EntryStatus.WON -> R.string.flow12_won
                    EntryStatus.NOT_SELECTED -> R.string.flow12_not_selected
                    EntryStatus.DECLINED -> R.string.flow12_declined
                    EntryStatus.BOOKED -> R.string.flow12_booked
                })), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.flow12_contacts_private), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun WaitingListEntrantRow(entry: LotteryEntry, contact: WaitingListContact?, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = WaitingListColors.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = WaitingListColors.avatar, contentColor = WaitingListColors.text) {
                Box(Modifier.size(40.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    Text((contact?.name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.map { it.first() }?.joinToString("")
                        ?.takeIf { it.isNotBlank() } ?: entry.entrantId.take(2)).uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.flow12_entrant, entry.entrantId), style = MaterialTheme.typography.bodyLarge)
                Text(joinedDate(entry), style = MaterialTheme.typography.bodyMedium, color = WaitingListColors.secondaryText)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = WaitingListColors.secondaryText)
        }
    }
}

@Composable
private fun LoadingWaitingList(label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = WaitingListColors.header)
        Text(stringResource(label), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun WaitingListFailure(error: OwnerWaitingListError, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(when (error) {
            OwnerWaitingListError.LOAD_LOTTERIES -> R.string.flow12_load_lotteries_error
            OwnerWaitingListError.LOAD_ENTRIES -> R.string.flow12_load_entries_error
            OwnerWaitingListError.ACCESS_DENIED -> R.string.flow12_denied
            OwnerWaitingListError.LOTTERY_UNAVAILABLE -> R.string.flow12_unavailable
        }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (error == OwnerWaitingListError.LOAD_LOTTERIES || error == OwnerWaitingListError.LOAD_ENTRIES) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.flow12_retry)) }
        }
    }
}

@Composable
private fun joinedDate(entry: LotteryEntry): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
    return stringResource(R.string.flow12_joined, format.format(entry.joinedAt.toDate().toInstant()))
}
