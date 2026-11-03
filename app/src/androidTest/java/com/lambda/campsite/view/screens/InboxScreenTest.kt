/*
 * Purpose: isolated Flow 8 Compose inbox/detail states and accessible actions.
 * AI use: OpenAI GPT-6 through Codex assisted with test code; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.lambda.campsite.R
import com.lambda.campsite.controller.NotificationError
import com.lambda.campsite.controller.NotificationUiState
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verifies win/lose copy, read state, error/empty feedback and navigation callbacks. */
class InboxScreenTest {
    @get:Rule val compose = createComposeRule()
    private val win = Notification("win", "demo", NotificationType.WIN, "Won demo summer lottery", "Demo win message", Timestamp.now())
    private val lose = Notification("lose", "demo", NotificationType.LOSE, "Not selected for demo spring lottery", "Demo lose message", Timestamp(1000, 0), read = true)
    private val state = mutableStateOf(NotificationUiState(notifications = listOf(win, lose), isLoading = false))
    private var retries = 0
    private var readRetries = 0
    private var browses = 0
    private var backs = 0
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun launch() {
        compose.setContent { LambdaCampsiteTheme {
            InboxContent(state.value,
                onOpen = { id -> state.value = state.value.copy(selectedId = id) },
                onRetry = { retries++ }, onRetryRead = { readRetries++ },
                onDismissError = { state.value = state.value.copy(error = null) },
                onBackToInbox = { state.value = state.value.copy(selectedId = null) },
                onBack = { backs++ }, onBrowse = { browses++ })
        } }
    }

    @Test fun winAndLoseRowsOpenTheirDetailsAndBackReturnsToInbox() {
        launch()
        snapshot("inbox")
        compose.onNodeWithText(win.title, substring = true).performClick()
        compose.onNodeWithText(text(R.string.flow8_won)).assertIsDisplayed()
        compose.onNodeWithText(win.body).assertIsDisplayed()
        snapshot("win")
        compose.onNodeWithContentDescription(text(R.string.flow8_back_inbox)).performClick()
        compose.onNodeWithText(lose.title, substring = true).performClick()
        compose.onNodeWithText(text(R.string.flow8_lost)).assertIsDisplayed()
        snapshot("not-selected")
        compose.onNodeWithText(text(R.string.flow8_browse)).performClick()
        compose.runOnIdle { assertEquals(1, browses) }
        compose.onNodeWithContentDescription(text(R.string.flow8_back_inbox)).performClick()
        compose.onNodeWithContentDescription(text(R.string.flow8_back_home)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun emptyInboxAndLoadFailureHaveDifferentFeedbackAndRetry() {
        state.value = NotificationUiState(isLoading = false)
        launch()
        compose.onNodeWithText(text(R.string.flow8_empty)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loadFailed = true) }
        compose.onNodeWithText(text(R.string.flow8_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow8_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow8_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun failedReadAcknowledgementKeepsMessageVisibleAndAllowsRetry() {
        state.value = state.value.copy(selectedId = win.id, error = NotificationError.MARK_READ_FAILED)
        launch()
        compose.onNodeWithText(win.body).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow8_read_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow8_retry_read)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, readRetries) }
        compose.onNodeWithText(text(R.string.flow8_dismiss)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.flow8_read_failed)).assertDoesNotExist()
    }

    @Test fun pendingReadLeavesBackAccessibleAndShowsItsState() {
        state.value = state.value.copy(selectedId = win.id, pendingReadIds = setOf(win.id))
        launch()
        compose.onNodeWithText(text(R.string.flow8_saving_read)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.flow8_back_inbox)).performClick()
        compose.onNodeWithText(win.title, substring = true).assertIsDisplayed()
    }

    @Test fun notificationRowGroupsItsMessageAndReadStateWithAnAccessibleTouchTarget() {
        launch()
        compose.onNodeWithContentDescription(text(R.string.flow8_back_home))
            .assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(win.title, substring = true)
            .assertHasClickAction().assertTextContains(win.body)
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, text(R.string.flow8_unread))).assertHeightIsAtLeast(48.dp)
    }

    @Test fun enlargedTextKeepsResultMessageAndBackReachable() {
        state.value = state.value.copy(selectedId = win.id)
        compose.setContent { LambdaCampsiteTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                InboxContent(state.value, onOpen = {}, onRetry = {}, onRetryRead = {},
                    onDismissError = {}, onBackToInbox = { backs++ }, onBack = {}, onBrowse = {})
            }
        } }
        compose.onNodeWithText(win.body).assertIsDisplayed()
        snapshot("win")
        compose.onNodeWithContentDescription(text(R.string.flow8_back_inbox)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    /** Saves local visual evidence on the isolated test emulator only. */
    private fun snapshot(name: String) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "flow8-review")
        folder.mkdirs()
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun inboxGroupsActualTodayAndEarlierDatesWithoutShowingEmptyGroups() {
        launch()
        compose.onNodeWithText(text(R.string.flow8_today)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow8_earlier)).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(notifications = listOf(lose)) }
        compose.onNodeWithText(text(R.string.flow8_today)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.flow8_earlier)).assertIsDisplayed()
    }
}
