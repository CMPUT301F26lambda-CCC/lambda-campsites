/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with ViewModel setup, generating QR bitmaps on Dispatchers.Default,
 * system share intent dispatch, image saving and KDoc. Raghav Sethi reviewed,
 * tested, and committed it.
 */
package com.lambda.campsite.controller

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.model.repository.CampsiteRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the campsite QR code screen (US 02.02.01).
 *
 * @property campsite the campsite whose QR code is displayed.
 * @property qrBitmap the generated QR code bitmap image.
 * @property qrPayload the URI encoded into the QR code.
 * @property isLoading `true` while the campsite or QR bitmap is being generated.
 * @property error load error message, if retrieval failed.
 */
data class CampsiteQrCodeUiState(
    val campsite: Campsite? = null,
    val qrBitmap: Bitmap? = null,
    val qrPayload: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Controller for displaying, sharing and saving a campsite's QR code (US 02.02.01).
 *
 * It listens to [CampsiteRepository.campsite], renders the QR matrix bitmap on a background thread,
 * and handles Android Share and Save Image intents.
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
                .catch { _state.update { it.copy(isLoading = false, error = "Failed to load campsite.") } }
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
                        _state.update { it.copy(isLoading = false, error = "Campsite not found.") }
                    }
                }
        }
    }

    /**
     * Dispatches an Android Share sheet with the campsite deep link and name.
     */
    fun onShare(context: Context) {
        val campsite = _state.value.campsite ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, campsite.name)
            putExtra(
                Intent.EXTRA_TEXT,
                "Scan to view ${campsite.name} in Lambda Campsite:\n${_state.value.qrPayload}",
            )
        }
        val chooser = Intent.createChooser(shareIntent, "Share Campsite QR Code")
        context.startActivity(chooser)
    }

    /**
     * Saves the QR code image bitmap to the app's cache directory and notifies the user.
     */
    fun onSaveImage(context: Context) {
        val bitmap = _state.value.qrBitmap ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "campsite_${campsiteId}_qr.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "QR code image saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
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
