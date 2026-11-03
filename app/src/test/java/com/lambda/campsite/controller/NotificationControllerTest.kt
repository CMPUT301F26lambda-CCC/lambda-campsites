/*
 * Purpose: Flow 8 notice loading, selection, read acknowledgements and retries.
 * AI use: OpenAI GPT-6 through Codex assisted with test cases and code; human review pending.
 */
package com.lambda.campsite.controller

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import com.lambda.campsite.model.repository.FakeNotificationRepository
import com.lambda.campsite.model.repository.NotificationRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Tests US 01.03.01/.02 without Firebase and without creating notices from the controller. */
class NotificationControllerTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val win = Notification("win", "entrant-1", NotificationType.WIN, "Won demo lottery", "Choose your stay", Timestamp(2000, 0))
    private val lose = Notification("lose", "entrant-1", NotificationType.LOSE, "Not selected for demo lottery", "Thanks for entering", Timestamp(1000, 0))

    @Test fun `loads own notices newest first with unread count`() {
        val repository = FakeNotificationRepository(initial = listOf(lose, win, win.copy(id = "private", recipientId = "other")))
        val control = NotificationController(repository)
        assertEquals(listOf(win, lose), control.state.value.notifications)
        assertEquals(2, control.state.value.unreadCount)
        assertFalse(control.state.value.isLoading)
    }

    @Test fun `empty inbox completes loading and new notices arrive live`() {
        val repository = FakeNotificationRepository()
        val control = NotificationController(repository)
        assertTrue(control.state.value.notifications.isEmpty())
        assertFalse(control.state.value.loadFailed)
        assertFalse(control.state.value.isLoading)
        repository.changeElsewhere(win)
        assertEquals(listOf(win), control.state.value.notifications)
    }

    @Test fun `opening win and lose marks them read and back returns to list`() {
        val control = NotificationController(FakeNotificationRepository(initial = listOf(win, lose)))
        control.onOpen(win.id)
        assertEquals(NotificationType.WIN, control.state.value.selected?.type)
        assertTrue(control.state.value.selected!!.read)
        assertEquals(1, control.state.value.unreadCount)
        control.onBackToInbox()
        assertNull(control.state.value.selectedId)
        control.onOpen(lose.id)
        assertEquals(NotificationType.LOSE, control.state.value.selected?.type)
        assertEquals(0, control.state.value.unreadCount)
    }

    @Test fun `opening a read notice does not send another write`() {
        val base = FakeNotificationRepository(initial = listOf(win.copy(read = true)))
        var calls = 0
        val repository = object : NotificationRepository by base {
            override suspend fun markAsRead(id: String) { calls++ }
        }
        val control = NotificationController(repository)
        control.onOpen(win.id)
        assertEquals(0, calls)
        assertEquals(win.id, control.state.value.selectedId)
    }

    @Test fun `unknown ID is not selected or written`() {
        val control = NotificationController(FakeNotificationRepository(initial = listOf(win)))
        control.onOpen("someone-elses-notice")
        assertEquals(NotificationError.NOT_FOUND, control.state.value.error)
        assertNull(control.state.value.selectedId)
        assertEquals(1, control.state.value.unreadCount)
    }

    @Test fun `failed read acknowledgement keeps details and can be retried`() {
        val repository = FakeNotificationRepository(initial = listOf(win)).apply { failMarkAsRead = true }
        val control = NotificationController(repository)
        control.onOpen(win.id)
        assertEquals(win, control.state.value.selected)
        assertEquals(NotificationError.MARK_READ_FAILED, control.state.value.error)
        assertTrue(control.state.value.pendingReadIds.isEmpty())
        repository.failMarkAsRead = false
        control.onRetryMarkRead()
        assertTrue(control.state.value.selected!!.read)
        assertNull(control.state.value.error)
    }

    @Test fun `pending acknowledgement blocks duplicates but not back navigation`() {
        val base = FakeNotificationRepository(initial = listOf(win))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repository = object : NotificationRepository by base {
            override suspend fun markAsRead(id: String) { calls++; gate.await(); base.markAsRead(id) }
        }
        val control = NotificationController(repository)
        control.onOpen(win.id)
        control.onOpen(win.id)
        control.onRetryMarkRead()
        assertEquals(1, calls)
        assertEquals(setOf(win.id), control.state.value.pendingReadIds)
        assertFalse(control.state.value.selected!!.read)
        control.onBackToInbox()
        assertNull(control.state.value.selectedId)
        gate.complete(Unit)
        assertTrue(control.state.value.pendingReadIds.isEmpty())
        assertEquals(0, control.state.value.unreadCount)
    }

    @Test fun `failed acknowledgement of an old selection does not show on another notice`() {
        val base = FakeNotificationRepository(initial = listOf(win, lose.copy(read = true)))
        val gate = CompletableDeferred<Unit>()
        val repository = object : NotificationRepository by base {
            override suspend fun markAsRead(id: String) { gate.await(); throw IOException("Offline") }
        }
        val control = NotificationController(repository)
        control.onOpen(win.id)
        control.onOpen(lose.id)
        gate.complete(Unit)
        assertEquals(lose.id, control.state.value.selectedId)
        assertNull(control.state.value.error)
    }

    @Test fun `load failure is retryable and cannot open stale notices`() {
        val base = FakeNotificationRepository(initial = listOf(win))
        var failing = true
        val repository = object : NotificationRepository by base {
            override fun myNotifications(): Flow<List<Notification>> = flow {
                if (failing) throw IOException("Read refused")
                base.myNotifications().collect { emit(it) }
            }
        }
        val control = NotificationController(repository)
        assertTrue(control.state.value.loadFailed)
        assertFalse(control.state.value.isLoading)
        control.onOpen(win.id)
        assertNull(control.state.value.selectedId)
        failing = false
        control.onRetry()
        assertFalse(control.state.value.loadFailed)
        assertEquals(listOf(win), control.state.value.notifications)
    }
}
