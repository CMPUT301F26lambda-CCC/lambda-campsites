/*
 * Purpose: Flow 8 inbox state, notice selection and read acknowledgements (US 01.03.01/.02).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: legacy notices may have no source lottery ID (#125).
 * AI use: OpenAI GPT-6 through Codex assisted with state, error handling and KDoc; human review pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.repository.NotificationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A failed read acknowledgement or an unavailable notification. */
enum class NotificationError { MARK_READ_FAILED, NOT_FOUND }

/**
 * Everything shown in the inbox and the selected notice.
 * @property notifications live records for this profile, newest first.
 * @property isLoading waiting for the initial result or retry.
 * @property loadFailed a failed listener, distinct from an empty inbox.
 * @property selectedId notice being read; null means the inbox list.
 * @property pendingReadIds acknowledgements awaiting server confirmation; navigation stays enabled.
 * @property error failure from opening/acknowledging a notice, or null.
 */
data class NotificationUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val selectedId: String? = null,
    val pendingReadIds: Set<String> = emptySet(),
    val error: NotificationError? = null,
) {
    /** Selected live record, or null when unselected or removed. */
    val selected: Notification? get() = notifications.firstOrNull { it.id == selectedId }
    /** Count for a non-color-only unread summary. */
    val unreadCount: Int get() = notifications.count { !it.read }
}

/**
 * Receives in-app notices even when push notifications are disabled.
 * Only existing own-profile repository records can be opened or marked read.
 * @param repository live notices and read acknowledgements; never creates a notification.
 */
class NotificationController(private val repository: NotificationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationUiState())
    private var listener: Job? = null
    /** State read by the Compose inbox. */
    val state: StateFlow<NotificationUiState> = mutableState.asStateFlow()
    init { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            repository.myNotifications().catch { failure ->
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(isLoading = false, loadFailed = true) }
            }.collect { notices ->
                val ordered = notices.sortedWith(compareByDescending<Notification> { it.sentAt }.thenBy { it.id })
                mutableState.update { it.copy(notifications = ordered, isLoading = false, loadFailed = false) }
            }
        }
    }

    /** Retries the live data listener without re-sending any read acknowledgement. */
    fun onRetry() = observe()

    /** Opens [id] and requests a read acknowledgement without blocking navigation. */
    fun onOpen(id: String) {
        if (state.value.isLoading || state.value.loadFailed) return
        val notification = state.value.notifications.firstOrNull { it.id == id }
        if (notification == null) {
            mutableState.update { it.copy(error = NotificationError.NOT_FOUND) }
            return
        }
        mutableState.update { it.copy(selectedId = id, error = null) }
        if (!notification.read) markRead(id)
    }

    private fun markRead(id: String) {
        if (id in state.value.pendingReadIds) return
        mutableState.update { it.copy(pendingReadIds = it.pendingReadIds + id, error = null) }
        viewModelScope.launch {
            try {
                repository.markAsRead(id)
                // The live listener owns the read flag; never claim a successful local-only write.
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                mutableState.update {
                    if (it.selectedId == id) it.copy(error = NotificationError.MARK_READ_FAILED) else it
                }
            } finally {
                mutableState.update { it.copy(pendingReadIds = it.pendingReadIds - id) }
            }
        }
    }

    /** Retries acknowledging only the selected notice, if it still exists and is unread. */
    fun onRetryMarkRead() {
        val notification = state.value.selected ?: return
        if (!notification.read) markRead(notification.id)
    }

    /** Returns to the list; any in-flight acknowledgement can finish without blocking Back. */
    fun onBackToInbox() { mutableState.update { it.copy(selectedId = null, error = null) } }

    /** Clears an action error after it has been presented. */
    fun onErrorShown() { mutableState.update { it.copy(error = null) } }

    companion object {
        /** Creates the controller with the existing shared notification repository. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                NotificationController(app.container.notificationRepository)
            }
        }
    }
}
