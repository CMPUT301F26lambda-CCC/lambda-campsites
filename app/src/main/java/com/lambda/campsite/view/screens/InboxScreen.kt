/*
 * Purpose: Flow 8 in-app inbox and win/lose notice details (US 01.03.01/.02).
 * Outstanding issues: manual TalkBack/visual review and booking deep links need later verification.
 * AI use: OpenAI GPT-6 through Codex assisted with Compose UI, navigation callbacks and KDoc; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import com.lambda.campsite.view.theme.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambda.campsite.R
import com.lambda.campsite.controller.NotificationController
import com.lambda.campsite.controller.NotificationError
import com.lambda.campsite.controller.NotificationUiState
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import com.lambda.campsite.view.AppTab
import com.lambda.campsite.view.theme.StatusBarIconStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Inbox route with live notices and non-blocking read acknowledgements.
 * @param controller supplies own-profile notices and marks opened records read.
 * @param onBack leaves the route and returns to Home.
 * @param onBrowse opens campsite browsing, without guessing a lottery ID.
 */
@Composable
fun InboxScreen(controller: NotificationController, onBack: () -> Unit, onBrowse: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.selectedId != null) { controller.onBackToInbox() }
    InboxContent(state, controller::onOpen, controller::onRetry, controller::onRetryMarkRead,
        controller::onErrorShown, controller::onBackToInbox, onBack, onBrowse)
}

/**
 * Stateless inbox/details presentation. All actions call the controller through callbacks.
 * @param state live records, selection and loading/action status.
 * @param onOpen selects a notice ID.
 * @param onRetry retries loading.
 * @param onRetryRead retries read acknowledgement.
 * @param onDismissError acknowledges a displayed action error.
 * @param onBackToInbox returns from a notice to the list.
 * @param onBack leaves the inbox route.
 * @param onBrowse returns to campsite browsing.
 */
@Composable
fun InboxContent(
    state: NotificationUiState, onOpen: (String) -> Unit, onRetry: () -> Unit,
    onRetryRead: () -> Unit, onDismissError: () -> Unit, onBackToInbox: () -> Unit,
    onBack: () -> Unit, onBrowse: () -> Unit,
) {
    StatusBarIconStyle(darkIcons = false)
    val showingDetail = state.selectedId != null
    val back = if (showingDetail) onBackToInbox else onBack
    val win = showingDetail && state.selected?.type == NotificationType.WIN
    val lose = showingDetail && state.selected?.type == NotificationType.LOSE
    val today = LocalDate.now(ZoneId.systemDefault())
    Scaffold(containerColor = MaterialTheme.colorScheme.primary,
        bottomBar = { if (lose) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.flow8_browse))
            }
        } },
        topBar = { NoticeHeader(stringResource(if (showingDetail) R.string.flow8_result_title else R.string.flow8_inbox),
            onBack = back, backDescription = stringResource(if (showingDetail) R.string.flow8_back_inbox else R.string.flow8_back_home)) }) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding),
            shape = RoundedCornerShape(topStart = if (win) 0.dp else 28.dp, topEnd = if (win) 0.dp else 28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (win) 0.dp else 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {
            when {
                state.isLoading -> item { Column(Modifier.padding(24.dp)) { CircularProgressIndicator(); Text(stringResource(R.string.flow8_loading)) } }
                state.loadFailed -> item {
                    Text(stringResource(R.string.flow8_load_failed), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.flow8_retry)) }
                }
                showingDetail -> {
                    val notice = state.selected
                    if (notice == null) item { Text(stringResource(R.string.flow8_missing)) }
                    else item { NoticeDetails(notice, notice.id in state.pendingReadIds, onBrowse) }
                }
                state.notifications.isEmpty() -> item { Text(stringResource(R.string.flow8_empty), Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                else -> {
                    val (recent, earlier) = state.notifications.partition {
                        it.sentAt.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() == today
                    }
                    if (recent.isNotEmpty()) {
                        item { NoticeGroupTitle(stringResource(R.string.flow8_today)) }
                        items(recent, key = { it.id }) { notice -> NoticeRow(notice, true) { onOpen(notice.id) } }
                    }
                    if (earlier.isNotEmpty()) {
                        item { NoticeGroupTitle(stringResource(R.string.flow8_earlier)) }
                        items(earlier, key = { it.id }) { notice -> NoticeRow(notice, false) { onOpen(notice.id) } }
                    }
                }
            }
            state.error?.let { error -> item {
                Text(stringResource(if (error == NotificationError.MARK_READ_FAILED) R.string.flow8_read_failed else R.string.flow8_missing),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (error == NotificationError.MARK_READ_FAILED && state.selected != null) {
                    OutlinedButton(onClick = onRetryRead, enabled = state.selectedId !in state.pendingReadIds) {
                        Text(stringResource(R.string.flow8_retry_read))
                    }
                }
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.flow8_dismiss)) }
            } }
        }
    }
}

}

/**
 * Temporary Home header entry while Home still uses the main branch's placeholder.
 * The bell callback can move to the real search header after Flow 6 merges.
 * @param onOpenInbox opens the pushed Notifications route without changing bottom tabs.
 */
@Composable
fun InboxHomePlaceholder(onOpenInbox: () -> Unit) {
    StatusBarIconStyle(darkIcons = false)
    Scaffold(topBar = { NoticeHeader(stringResource(R.string.tab_home), onOpenInbox = onOpenInbox) }) { padding ->
        PlaceholderScreen(AppTab.ENTRANT_HOME, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeHeader(title: String, onBack: (() -> Unit)? = null, backDescription: String = "", onOpenInbox: (() -> Unit)? = null) {
    TopAppBar(title = {
        if (onBack != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.camp_logo), null, Modifier.size(40.dp).clip(CircleShape))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
        }
    }, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, backDescription) }
    }, actions = {
        if (onOpenInbox != null) IconButton(onClick = onOpenInbox, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Notifications, stringResource(R.string.flow8_open_inbox))
        }
    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimary))
}

@Composable
private fun NoticeGroupTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = InboxSecondaryText,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun NoticeRow(notice: Notification, today: Boolean, onOpen: () -> Unit) {
    val readLabel = stringResource(if (notice.read) R.string.flow8_read else R.string.flow8_unread)
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen)
        .semantics { stateDescription = readLabel },
        color = if (notice.read) MaterialTheme.colorScheme.primaryContainer else InboxUnreadSurface,
        contentColor = InboxText) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = if (notice.type == NotificationType.WIN) InboxWinIconSurface else InboxNeutralSurface) {
                if (notice.type == NotificationType.LOSE) Image(painterResource(R.drawable.flow8_tent), null, Modifier.size(40.dp).padding(9.dp)) else Icon(if (notice.type == NotificationType.WIN) Icons.Default.Star else Icons.Default.Info,
                    contentDescription = null, modifier = Modifier.size(40.dp).padding(9.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(notice.title.ifBlank { stringResource(typeTitle(notice.type)) }, style = MaterialTheme.typography.bodyLarge)
                Text(notice.body.ifBlank { stringResource(R.string.flow8_no_body) },
                    style = MaterialTheme.typography.bodyMedium, color = InboxSecondaryText)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val format = if (today) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) else DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                Text(format.withZone(ZoneId.systemDefault()).format(notice.sentAt.toDate().toInstant()),
                    style = MaterialTheme.typography.labelSmall, color = InboxSecondaryText)
                if (!notice.read) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun WinnerArtwork() {
    // Original Figma artwork exports, positioned relative to the 412dp reference hero.
    BoxWithConstraints(Modifier.fillMaxWidth().height(164.dp)) {
        val scale = maxWidth.value / 412f
        Image(painterResource(R.drawable.flow8_art_2702), null,
            Modifier.offset((81f * scale).dp, (-59f * scale).dp).size((250f * scale).dp))
        Image(painterResource(R.drawable.flow8_art_2691), null,
            Modifier.offset((148f * scale).dp, (8f * scale).dp).size((116f * scale).dp, (122f * scale).dp).clip(CircleShape))
        val pieces = listOf(
            Triple(R.drawable.flow8_art_2703, 44f, 18f), Triple(R.drawable.flow8_art_2704, 86f, 54f),
            Triple(R.drawable.flow8_art_2705, 62f, 104f), Triple(R.drawable.flow8_art_2706, 30f, 150f),
            Triple(R.drawable.flow8_art_2707, 120f, 12f), Triple(R.drawable.flow8_art_2708, 104f, 136f),
            Triple(R.drawable.flow8_art_2709, 140f, 70f), Triple(R.drawable.flow8_art_2710, 368f, 22f),
            Triple(R.drawable.flow8_art_2711, 326f, 60f), Triple(R.drawable.flow8_art_2712, 350f, 108f),
            Triple(R.drawable.flow8_art_2713, 380f, 150f), Triple(R.drawable.flow8_art_2714, 292f, 14f),
            Triple(R.drawable.flow8_art_2715, 306f, 132f), Triple(R.drawable.flow8_art_2716, 274f, 76f),
            Triple(R.drawable.flow8_art_2717, 20f, 76f), Triple(R.drawable.flow8_art_2718, 392f, 80f))
        val renderBounds = listOf(floatArrayOf(39.775f,18.563f,9.301f,14.082f), floatArrayOf(86f,54f,7f,7f), floatArrayOf(62f,104f,14f,14f), floatArrayOf(30.785f,147.344f,11.374f,13.339f), floatArrayOf(120f,12f,14f,14f), floatArrayOf(94.093f,136.817f,12.946f,11.960f), floatArrayOf(140f,70f,7f,7f), floatArrayOf(368.657f,20.122f,10.040f,13.909f), floatArrayOf(326f,60f,7f,7f), floatArrayOf(350f,108f,14f,14f), floatArrayOf(372.755f,150.785f,11.374f,13.339f), floatArrayOf(292f,14f,14f,14f), floatArrayOf(306.732f,127.536f,13.661f,10.732f), floatArrayOf(274f,76f,7f,7f), floatArrayOf(20f,76f,7f,7f), floatArrayOf(392f,80f,7f,7f))
        pieces.forEachIndexed { index, (art, _, _) ->
            val (x, y, w, h) = renderBounds[index]
            Image(painterResource(art), null, Modifier.offset((x * scale).dp, (y * scale).dp)
                .size((w * scale).dp, (h * scale).dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun NoticeDetails(notice: Notification, pending: Boolean, onBrowse: () -> Unit) {
    val won = notice.type == NotificationType.WIN
    Column(Modifier.fillMaxWidth().background(if (won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)) {
        if (won) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WinnerArtwork()
            Text(stringResource(R.string.flow8_won), color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = FontFamily(Font(R.font.fraunces, weight = FontWeight.SemiBold)), fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp,
                modifier = Modifier.semantics { heading() })
            if (notice.title.isNotBlank()) Text(notice.title, color = MaterialTheme.colorScheme.primaryContainer,
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp))
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = InboxText, shape = RoundedCornerShape(topStart = if (won) 28.dp else 0.dp, topEnd = if (won) 28.dp else 0.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!won && notice.title.isNotBlank()) Text(notice.title, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() })
                if (!won) Surface(shape = RoundedCornerShape(8.dp), color = InboxNeutralSurface,
                    border = BorderStroke(1.dp, InboxOutline), contentColor = InboxSecondaryText) {
                    Text(stringResource(typeTitle(notice.type)), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                if (!won) HorizontalDivider(color = InboxOutline)
                Text(notice.body.ifBlank { stringResource(R.string.flow8_no_body) }, style = MaterialTheme.typography.bodyMedium)
                Text(sentAt(notice), style = MaterialTheme.typography.labelSmall, color = InboxSecondaryText)
                Text(stringResource(if (notice.read) R.string.flow8_read else R.string.flow8_unread),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (pending) Text(stringResource(R.string.flow8_saving_read))

            }
        }
    }
}

private fun typeTitle(type: NotificationType): Int = when (type) {
    NotificationType.WIN -> R.string.flow8_won
    NotificationType.LOSE -> R.string.flow8_lost
    NotificationType.RESERVATION_CONFIRMED -> R.string.flow8_confirmed
    NotificationType.CAMPSITE_REMOVED -> R.string.flow8_removed
}

@Composable
private fun sentAt(notice: Notification): String {
    val zone = ZoneId.systemDefault()
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault()).withZone(zone)
    return stringResource(R.string.flow8_sent_at, format.format(notice.sentAt.toDate().toInstant()), zone.id)
}
