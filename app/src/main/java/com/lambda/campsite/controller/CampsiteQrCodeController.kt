/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with ViewModel setup, generating QR bitmaps on Dispatchers.Default,
 * load-error enums and KDoc. Raghav Sethi reviewed, tested, and committed it.
 *
 * The share sheet, the toasts and saving the image used to live here with a
 * `Context`, which could leak the activity. They now belong to
 * CampsiteQrCodeScreen, which owns an Android context anyway.
 */
package com.lambda.campsite.controller

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why the campsite QR screen is empty. The screen turns each one into a message. */
enum class CampsiteQrError {
    /** The campsite couldn't be loaded, for example because Firestore refused the read. */
    LOAD_FAILED,

    /** No campsite has this ID, like when a QR code for a deleted campsite is scanned (US 01.12.01). */
    NOT_FOUND,
}

/**
 * State for the campsite QR code screen (US 02.02.01).
 *
 * @property campsite the campsite whose QR code is displayed.
 * @property qrBitmap the generated QR code bitmap image.
 * @property qrPayload the URI encoded into the QR code.
 * @property isLoading `true` while the campsite or QR bitmap is being generated.
 * @property error why the screen is empty, or `null` when everything is fine.
 */
data class CampsiteQrCodeUiState(
    val campsite: Campsite? = null,
    val qrBitmap: Bitmap? = null,
    val qrPayload: String = "",
    val isLoading: Boolean = true,
    val error: CampsiteQrError? = null,
)

/**
 * Controller for displaying a campsite's QR code (US 02.02.01).
 *
 * It listens to [CampsiteRepository.campsite] and renders the QR matrix bitmap
 * on a background thread. Sharing and saving the image are screen jobs, since
 * they need Android's share sheet and MediaStore.
 *
 * @param campsiteId ID of the campsite to display.
 * @param repository where the campsite is loaded from.
 */
class CampsiteQrCodeController(
    private val campsiteId: String,
    private val repository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CampsiteQrCodeUiState(qrPayload = "${QRCode.URI_PREFIX}$campsiteId"))

    /** The current UI state of the campsite QR screen. */
    val state: StateFlow<CampsiteQrCodeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.campsite(campsiteId)
                .catch { _state.update { it.copy(isLoading = false, error = CampsiteQrError.LOAD_FAILED) } }
                .collect { campsite ->
                    if (campsite != null) {
                        val bitmap = withContext(Dispatchers.Default) {
                            try {
                                QRCode.generate(campsite)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        _state.update {
                            it.copy(
                                campsite = campsite,
                                qrBitmap = bitmap,
                                qrPayload = "${QRCode.URI_PREFIX}${campsite.id}",
                                isLoading = false,
                                error = null,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(isLoading = false, error = CampsiteQrError.NOT_FOUND)
                        }
                    }
                }
        }
    }

    companion object {
        /** Factory creating a [CampsiteQrCodeController] for [campsiteId]. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                CampsiteQrCodeController(campsiteId, application.container.campsiteRepository)
            }
        }
    }
}
