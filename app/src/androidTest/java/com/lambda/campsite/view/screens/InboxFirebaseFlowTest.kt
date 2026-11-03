/*
 * Purpose: real Flow 8 app navigation, notification isolation and read persistence on local emulators.
 * AI use: OpenAI GPT-6 through Codex assisted with test fixtures/assertions; human review pending.
 */
package com.lambda.campsite.view.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.lambda.campsite.R
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.NotificationFields
import com.lambda.campsite.model.repository.ProfileFields
import com.lambda.campsite.testutil.FirestoreEmulator
import com.lambda.campsite.testutil.signInAsNewUser
import com.lambda.campsite.view.LambdaCampsiteApp
import com.lambda.campsite.view.theme.LambdaCampsiteTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** No production writes: refuses any Firestore host except the local Android emulator bridge. */
class InboxFirebaseFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var uid: String
    private val suffix = UUID.randomUUID().toString().take(8)
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun awaitText(value: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Uses test-only records and disables pushes to verify the inbox remains available. */
    @Before fun seedProfile() {
        check(Firebase.firestore.firestoreSettings.host == "10.0.2.2:8080") { "Refusing non-emulator Firestore test" }
        FirestoreEmulator.clear()
        uid = runBlocking { withTimeout(15_000) { signInAsNewUser() } }
        FirestoreEmulator.setIgnoringRules("profiles/$uid", ProfileFields.toMap(
            Profile(deviceId = uid, name = "Inbox demo entrant", email = "demo@example.com", notificationsOn = false, policyVersionAgreed = 1)))
    }

    private fun notice(id: String, recipient: String = uid, type: NotificationType, secondsAgo: Long) =
        Notification(id, recipient, type, "${type.name} demo lottery $suffix", "${type.name} fictional result message",
            Timestamp(Instant.now().minusSeconds(secondsAgo)))

    private fun seed(record: Notification) = FirestoreEmulator.setIgnoringRules("notifications/${record.id}", NotificationFields.toMap(record))

    private fun launchInbox() {
        compose.setContent { LambdaCampsiteTheme { LambdaCampsiteApp() } }
        compose.onNodeWithContentDescription(text(R.string.flow8_open_inbox)).performClick()
    }

    @Test fun winLoseNoticesRemainVisibleWithPushOffAndOpenedNoticePersistsRead() {
        val win = notice("win-$suffix", type = NotificationType.WIN, secondsAgo = 10)
        val lose = notice("lose-$suffix", type = NotificationType.LOSE, secondsAgo = 60)
        val privateNotice = notice("private-$suffix", recipient = "different-demo-user", type = NotificationType.WIN, secondsAgo = 0)
            .copy(title = "Private other-user notice $suffix")
        seed(win); seed(lose); seed(privateNotice)
        launchInbox()
        awaitText(win.title)
        compose.onNodeWithText(lose.title, substring = true).assertIsDisplayed()
        compose.onNodeWithText(privateNotice.title, substring = true).assertDoesNotExist()
        compose.onNodeWithText(win.title, substring = true).performClick()
        compose.onNodeWithText(text(R.string.flow8_won)).assertIsDisplayed()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text(R.string.flow8_read), substring = false).fetchSemanticsNodes().isNotEmpty() }
        val read = runBlocking { withTimeout(15_000) {
            Firebase.firestore.waitForPendingWrites().await(); Firebase.firestore.document("notifications/${win.id}").get(Source.SERVER).await().getBoolean("read")
        } }
        assertEquals(true, read)
        compose.onNodeWithContentDescription(text(R.string.flow8_back_inbox)).performClick()
        compose.onNodeWithText(lose.title, substring = true).performClick()
        compose.onNodeWithText(text(R.string.flow8_lost)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.flow8_browse)).performClick()
        compose.onNodeWithContentDescription(text(R.string.flow8_open_inbox)).assertIsDisplayed()
    }

    @Test fun emptyInboxReceivesNewResultLiveWithoutReopening() {
        launchInbox()
        awaitText(text(R.string.flow8_empty))
        val win = notice("live-$suffix", type = NotificationType.WIN, secondsAgo = 0)
        seed(win)
        awaitText(win.title)
        compose.onNodeWithText(text(R.string.flow8_empty)).assertDoesNotExist()
        compose.onNodeWithText(win.title, substring = true).assertIsDisplayed()
    }
}
