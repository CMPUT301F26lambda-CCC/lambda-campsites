/*
 * Purpose: Holds the waiting-list message draft and observes sent messages.
 * Outstanding issues: Shared Firebase deployment verification remains.
 * Lalith Valluru, November 2026. GPT-6 through Codex assisted with #37 and #38.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.functions.FirebaseFunctionsException
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.repository.SentWaitingListMessage
import com.lambda.campsite.model.repository.WaitingListMessageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** State for composing or viewing sent waiting-list messages. */
data class WaitingListMessagesState(
    val draft: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val sendError: WaitingListSendError? = null,
    val history: List<SentWaitingListMessage>? = null,
    val historyFailed: Boolean = false,
)

enum class WaitingListSendError { EMPTY, TOO_LARGE, FAILED }

/** Coordinates sends and history reads with Firebase. */
class WaitingListMessagesController(private val repository: WaitingListMessageRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(WaitingListMessagesState())
    val state = mutableState.asStateFlow()
    private var requestId = UUID.randomUUID().toString()
    private var historyJob: Job? = null

    fun changeDraft(value: String) {
        if (state.value.sending || value.length > 500 || value == state.value.draft) return
        requestId = UUID.randomUUID().toString()
        mutableState.update { it.copy(draft = value, sent = false, sendError = null) }
    }

    fun send(lotteryId: String) {
        val draft = state.value.draft.trim()
        if (draft.isEmpty() || state.value.sending) return
        mutableState.update { it.copy(sending = true, sendError = null, sent = false) }
        viewModelScope.launch {
            try {
                repository.send(lotteryId, draft, requestId)
                requestId = UUID.randomUUID().toString()
                mutableState.update { it.copy(draft = "", sent = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val reason = when ((error as? FirebaseFunctionsException)?.code) {
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> WaitingListSendError.EMPTY
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> WaitingListSendError.TOO_LARGE
                    else -> WaitingListSendError.FAILED
                }
                mutableState.update { it.copy(sendError = reason) }
            } finally {
                mutableState.update { it.copy(sending = false) }
            }
        }
    }

    fun loadHistory() {
        historyJob?.cancel()
        mutableState.update { it.copy(history = null, historyFailed = false) }
        historyJob = viewModelScope.launch {
            try {
                repository.history().collect { rows ->
                    mutableState.update { it.copy(history = rows) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(history = null, historyFailed = true) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                val container = app.container
                WaitingListMessagesController(
                    WaitingListMessageRepository(container.auth, container.firestore, container.functions),
                )
            }
        }
    }
}
