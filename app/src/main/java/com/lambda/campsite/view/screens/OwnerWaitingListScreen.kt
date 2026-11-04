/*
 * Last updated: 2026-11-03 (Flow 12).
 * Outstanding issues: Manual TalkBack and physical-device review remain pending (#36).
 * Feature owner: Lalith Valluru. Purpose: Figma-aligned Flow 12 waiting-list screen.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted Lalith with Compose layout,
 * accessibility semantics and callbacks. Human review pending.
 * Reference: Lambda Campsite UI / Sreeman / 59355:5791, 59355:5860, 59355:5935.
 * Names/contact details use an owner-authorized projection; full profiles remain private.
 * Theme follow-up (#154), Lalith Valluru, 2026-11-03: OpenAI GPT-6 (OpenAI),
 * through Codex, assisted with replacing duplicated colors by the shared scheme.
 * Layout is unchanged; follow-up human review remains pending.
 */
// Flow 5: Lalith Valluru, 2026-11-03. OpenAI GPT-6 (OpenAI), through Codex,
// assisted with read-only draw metadata/results integration; human review pending.
// Issue #151: GPT-6 (OpenAI), through Codex, assisted me with the read-only
// Figma contact sheet alignment and labeled contact-copy actions.
package com.lambda.campsite.view.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.OwnerWaitingListController
import com.lambda.campsite.controller.OwnerWaitingListError
import com.lambda.campsite.controller.OwnerWaitingListUiState
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.WaitingListContact
import com.lambda.campsite.view.theme.StatusBarIconStyle
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
fun OwnerWaitingListScreen(controller: OwnerWaitingListController, onBack: () -> Unit, onResults: ((String) -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    OwnerWaitingListContent(state, controller::onSelectLottery, controller::onRetry,
        controller::onOpenEntrant, controller::onDismissEntrant, onBack, onResults)
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
    onResults: ((String) -> Unit)? = null,
) {
    StatusBarIconStyle(darkIcons = false)
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val emailCopied = stringResource(R.string.owner_waiting_list_email_copied)
    val phoneCopied = stringResource(R.string.owner_waiting_list_phone_copied)
    var choosing by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.primary, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.owner_waiting_list_title),
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.owner_waiting_list_back))
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
    }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loadingLotteries) item { LoadingWaitingList(R.string.owner_waiting_list_loading_lotteries) }
                else {
                    if (state.lotteries.isNotEmpty()) item {
                        Box {
                            Surface(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(stringResource(R.string.owner_waiting_list_lottery), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(state.selectedLottery?.name ?: stringResource(R.string.owner_waiting_list_choose),
                                            style = MaterialTheme.typography.bodyLarge)
                                    }
                                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(24.dp))
                                }
                            }
                            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
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
                        Text(stringResource(R.string.owner_waiting_list_no_lotteries), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.owner_waiting_list_no_lotteries_help), style = MaterialTheme.typography.bodyMedium)
                    }
                    else if (state.loadingEntries) item { LoadingWaitingList(R.string.owner_waiting_list_loading_entries) }
                    else if (state.selectedLottery != null) {
                        if (onResults != null) item {
                            TextButton(onClick = { state.selectedLotteryId?.let(onResults) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.flow5_view_results))
                            }
                        }
                        item { Text(pluralStringResource(R.plurals.owner_waiting_list_count, state.entries.size, state.entries.size),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (state.entries.isEmpty()) item {
                            Text(stringResource(R.string.owner_waiting_list_empty), style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        if (state.loadingContacts) item { LoadingWaitingList(R.string.owner_waiting_list_loading_contacts) }
                        if (state.contactsFailed) item {
                            Text(stringResource(R.string.owner_waiting_list_contacts_error), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.owner_waiting_list_retry))
                            }
                        }
                        items(state.entries, key = { it.entrantId }) { entry ->
                            WaitingListEntrantRow(entry, state.contacts[entry.entrantId]) { onOpenEntrant(entry.entrantId) }
                        }
                        if (state.entries.isNotEmpty()) item {
                            Text(stringResource(R.string.owner_waiting_list_updates), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.owner_waiting_list_contacts_private), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    state.selectedEntrant?.let { entry ->
        val contact = state.contacts[entry.entrantId]
        ModalBottomSheet(onDismissRequest = onDismissEntrant, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
            properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false,
                isAppearanceLightNavigationBars = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(contact?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.owner_waiting_list_sheet_title),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (state.loadingContacts && contact == null) LoadingWaitingList(R.string.owner_waiting_list_loading_contacts)
                if (state.contactsFailed && contact == null) {
                    Text(stringResource(R.string.owner_waiting_list_contacts_error))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.owner_waiting_list_retry)) }
                } else if (contact != null || !state.loadingContacts) {
                    ContactCopyRow(contact?.email, stringResource(R.string.owner_waiting_list_copy_email),
                        stringResource(R.string.owner_waiting_list_not_provided)) { value ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("email", value))
                        Toast.makeText(context, emailCopied, Toast.LENGTH_SHORT).show()
                    }
                    ContactCopyRow(contact?.phone, stringResource(R.string.owner_waiting_list_copy_phone),
                        stringResource(R.string.owner_waiting_list_not_provided)) { value ->
                        clipboard.setPrimaryClip(ClipData.newPlainText("phone", value))
                        Toast.makeText(context, phoneCopied, Toast.LENGTH_SHORT).show()
                    }
                }
                Text(joinedDate(entry), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A Figma contact line with a separate, accessible action for copying its value. */
@Composable
private fun ContactCopyRow(value: String?, copyLabel: String, missingLabel: String,
    onCopy: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value?.takeIf { it.isNotBlank() } ?: missingLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        IconButton(onClick = { value?.takeIf { it.isNotBlank() }?.let(onCopy) },
            enabled = !value.isNullOrBlank(), modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = copyLabel)
        }
    }
}

@Composable
private fun WaitingListEntrantRow(entry: LotteryEntry, contact: WaitingListContact?, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.size(40.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    Text((contact?.name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.map { it.first() }?.joinToString("")
                        ?.takeIf { it.isNotBlank() } ?: entry.entrantId.take(2)).uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.owner_waiting_list_entrant, entry.entrantId), style = MaterialTheme.typography.bodyLarge)
                Text(joinedDate(entry), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingWaitingList(label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(stringResource(label), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun WaitingListFailure(error: OwnerWaitingListError, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(when (error) {
            OwnerWaitingListError.LOAD_LOTTERIES -> R.string.owner_waiting_list_load_lotteries_error
            OwnerWaitingListError.LOAD_ENTRIES -> R.string.owner_waiting_list_load_entries_error
            OwnerWaitingListError.ACCESS_DENIED -> R.string.owner_waiting_list_denied
            OwnerWaitingListError.LOTTERY_UNAVAILABLE -> R.string.owner_waiting_list_unavailable
        }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (error == OwnerWaitingListError.LOAD_LOTTERIES || error == OwnerWaitingListError.LOAD_ENTRIES) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.owner_waiting_list_retry)) }
        }
    }
}

@Composable
private fun joinedDate(entry: LotteryEntry): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault())
    return stringResource(R.string.owner_waiting_list_joined, format.format(entry.joinedAt.toDate().toInstant()))
}
