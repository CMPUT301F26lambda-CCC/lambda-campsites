/*
 * Purpose: Lets owners send a waiting-list message and view their sent history.
 * Outstanding issues: Manual TalkBack and deployed push checks remain.
 * Lalith Valluru, November 2026. GPT-6 through Codex assisted with #37 and #38.
 */
package com.lambda.campsite.view.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.WaitingListMessagesController
import com.lambda.campsite.controller.WaitingListSendError
import com.lambda.campsite.view.theme.ForestGreen
import com.lambda.campsite.view.theme.Mint
import androidx.compose.ui.graphics.Color
import java.text.DateFormat

/** Composes a message for the lottery selected on the owner lottery screen. */
@Composable
fun ComposeWaitingListMessage(
    lotteryId: String,
    controller: WaitingListMessagesController,
    onBack: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    MessageScreen(R.string.waitlist_message_compose, onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = state.draft, onValueChange = controller::changeDraft,
                    label = { Text(stringResource(R.string.waitlist_message_label)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !state.sending,
                )
            }
            item {
                Button(onClick = { confirming = true }, enabled = state.draft.isNotBlank() && !state.sending) {
                    Text(stringResource(R.string.waitlist_message_send))
                }
            }
            if (state.sending) item { Text(stringResource(R.string.waitlist_message_sending)) }
            if (state.sent) item {
                Text(stringResource(R.string.waitlist_message_sent),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            state.sendError?.let { error ->
                item {
                    Text(stringResource(when (error) {
                        WaitingListSendError.EMPTY -> R.string.waitlist_message_empty
                        WaitingListSendError.FAILED -> R.string.waitlist_message_failed
                    }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.waitlist_message_confirm_title)) },
            text = { Text(stringResource(R.string.waitlist_message_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; controller.send(lotteryId) }) {
                    Text(stringResource(R.string.waitlist_message_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.waitlist_message_keep)) }
            },
        )
    }
}

/** Displays all messages sent by the signed-in owner and their recorded recipients. */
@Composable
fun SentWaitingListMessages(controller: WaitingListMessagesController, onBack: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.history?.firstOrNull { it.id == selectedId }
    LaunchedEffect(controller) { controller.loadHistory() }
    var retryRecipients by remember { mutableIntStateOf(0) }
    val recipientNames by produceState<Result<List<String>>?>(null, selected, retryRecipients) {
        value = null
        val message = selected ?: return@produceState
        value = try {
            Result.success(controller.recipientNames(message))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    BackHandler(selected != null) { selectedId = null }
    MessageScreen(R.string.waitlist_message_history, { if (selected != null) selectedId = null else onBack() }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val history = state.history
            when {
                state.historyFailed -> item {
                    Text(stringResource(R.string.waitlist_message_history_failed))
                    TextButton(onClick = controller::loadHistory) { Text(stringResource(R.string.retry_button)) }
                }
                history == null -> item { CircularProgressIndicator() }
                selected != null -> {
                    item {
                        Text(selected.lotteryName, style = MaterialTheme.typography.titleLarge)
                        Text(DateFormat.getDateTimeInstance().format(selected.sentAt.toDate()))
                        Text(selected.message)
                        if (!selected.complete) {
                            PartialSendStatus(selected.recipientCount)
                            Button(onClick = { controller.resume(selected) },
                                enabled = state.resumingId == null && selected.lotteryId.isNotBlank()) {
                                Text(stringResource(if (state.resumingId == selected.id)
                                    R.string.waitlist_message_sending else R.string.waitlist_message_resume))
                            }
                            if (state.resumeFailedId == selected.id) {
                                Text(stringResource(R.string.waitlist_message_resume_failed),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                        }
                        Text(stringResource(R.string.waitlist_message_recipients),
                            style = MaterialTheme.typography.titleMedium)
                    }
                    val recipients = recipientNames
                    when {
                        recipients == null -> item { CircularProgressIndicator() }
                        recipients.isFailure -> item {
                            Text(stringResource(R.string.waitlist_message_recipients_failed))
                            TextButton(onClick = { retryRecipients++ }) {
                                Text(stringResource(R.string.retry_button))
                            }
                        }
                        else -> items(recipients.getOrThrow()) { name -> Text(name) }
                    }
                }
                history.isEmpty() -> item { Text(stringResource(R.string.waitlist_message_history_empty)) }
                else -> items(history, key = { it.id }) { message ->
                    Card(onClick = { selectedId = message.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(message.lotteryName, style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance().format(message.sentAt.toDate()))
                            if (!message.complete) PartialSendStatus(message.recipientCount)
                            Text(message.message, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartialSendStatus(count: Int) {
    Text(pluralStringResource(R.plurals.waitlist_message_partial, count, count))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageScreen(title: Int, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = Mint, topBar = {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button))
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = ForestGreen,
            titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }, content = content)
}
