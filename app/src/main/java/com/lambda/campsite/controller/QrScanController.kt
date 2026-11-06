/*
 * Purpose: Validates scanned campsite QR payloads and resolves published campsite destinations for Flow 16.
 * Outstanding issues: None known.
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with QR validation and
 * campsite lookup state. Human review is pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Errors displayed while the scanner stays open for another code. */
enum class QrScanError { INVALID_CODE, CAMPSITE_UNAVAILABLE, LOOKUP_FAILED }

/**
 * Scanner presentation state.
 * @property isResolving whether a campsite ID is being checked before navigation.
 * @property destinationId a validated published campsite for one navigation event.
 * @property error the latest failed scan, announced once by the screen.
 */
data class QrScanUiState(
    val isResolving: Boolean = false,
    val destinationId: String? = null,
    val error: QrScanError? = null,
)

/** Validates Flow 3 QR payloads and opens only a published campsite (US 01.12.01). */
class QrScanController(private val repository: CampsiteRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(QrScanUiState())
    /** State observed by the full-screen scanner. */
    val state: StateFlow<QrScanUiState> = mutableState.asStateFlow()
    private var lastPayload: String? = null

    /** Handles a decoded camera payload, ignoring repeated frames of the same QR. */
    fun onCodeSeen(payload: String) {
        if (mutableState.value.isResolving || mutableState.value.destinationId != null ||
            payload == lastPayload) return
        lastPayload = payload
        val id = QRCode.decode(payload)?.takeIf(::isSafeCampsiteId)
        if (id == null) {
            mutableState.update { it.copy(error = QrScanError.INVALID_CODE) }
            return
        }
        mutableState.update { it.copy(isResolving = true, error = null) }
        viewModelScope.launch {
            try {
                val campsite = withTimeout(8_000) { repository.campsite(id).first() }
                mutableState.update { current ->
                    if (campsite?.published == true) current.copy(
                        isResolving = false, destinationId = id)
                    else current.copy(isResolving = false, error = QrScanError.CAMPSITE_UNAVAILABLE)
                }
            } catch (failure: CancellationException) {
                if (failure is TimeoutCancellationException) {
                    mutableState.update { it.copy(isResolving = false, error = QrScanError.LOOKUP_FAILED) }
                } else throw failure
            } catch (_: Exception) {
                mutableState.update { it.copy(isResolving = false, error = QrScanError.LOOKUP_FAILED) }
            }
        }
    }

    /** Allows the same code again after it has left the viewfinder. */
    fun onCodeLost() { lastPayload = null }

    /** Clears a one-time snackbar message without reprocessing the visible code. */
    fun onErrorShown() { mutableState.update { it.copy(error = null) } }

    /** Consumes the destination before the navigation callback changes the stack. */
    fun onDestinationOpened() { mutableState.update { it.copy(destinationId = null) } }

    private fun isSafeCampsiteId(id: String): Boolean =
        id.isNotBlank() && id.toByteArray(Charsets.UTF_8).size <= 1_500 &&
            id.none { it == '/' || it == '?' || it == '#' || it.isISOControl() }

    companion object {
        /** Supplies the app's existing campsite repository. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                QrScanController(app.container.campsiteRepository)
            }
        }
    }
}
