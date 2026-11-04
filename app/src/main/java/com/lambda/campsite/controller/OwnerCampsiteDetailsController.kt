// Issue #150: GPT-6 (OpenAI), through Codex, assisted me with this follow-up.
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with ViewModel setup, StateFlow combining campsite and availability flows,
 * error handling, syntax and KDoc. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.QRCode
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.ImageRepository
import com.lambda.campsite.model.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the owner's campsite details screen (US 02.01.04).
 *
 * @property campsite the campsite details, or `null` while loading or if not found.
 * @property availability the campsite's seasonal calendar and booked nights.
 * @property isLoading `true` while the campsite is being retrieved from Firestore.
 * @property error load error message, if retrieval failed.
 */
data class OwnerCampsiteDetailsUiState(
    val campsite: Campsite? = null,
    val availability: Availability? = null,
    val isLoading: Boolean = true,
    val error: OwnerDetailsError? = null,
    val lotteries: List<Lottery> = emptyList(),
    val photoUrl: String? = null,
    val qrBitmap: Bitmap? = null,
)

/** Why owner campsite details cannot be shown. */
enum class OwnerDetailsError { NOT_FOUND, LOAD_FAILED }

/**
 * Controller for viewing all details of a campsite for its owner (US 02.01.04).
 *
 * It listens to [CampsiteRepository.campsite] and [CampsiteRepository.availability] for live updates
 * to ratings, calendar availability, and details.
 *
 * @param campsiteId document ID of the campsite to display.
 * @param repository where campsite and availability data are loaded from.
 */
class OwnerCampsiteDetailsController(
    private val campsiteId: String,
    private val repository: CampsiteRepository,
    private val lotteryRepository: LotteryRepository? = null,
    private val imageRepository: ImageRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerCampsiteDetailsUiState())

    /** The current UI state of the campsite details screen. */
    val state: StateFlow<OwnerCampsiteDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.campsite(campsiteId),
                repository.availability(campsiteId),
                lotteryRepository?.lotteriesFor(campsiteId)?.catch { emit(emptyList()) } ?: flowOf(emptyList()),
            ) { camp, avail, lotteries ->
                OwnerCampsiteDetailsUiState(
                    campsite = camp,
                    availability = avail,
                    isLoading = false,
                    error = if (camp == null) OwnerDetailsError.NOT_FOUND else null,
                    lotteries = lotteries,
                )
            }.catch {
                _state.update { it.copy(isLoading = false, error = OwnerDetailsError.LOAD_FAILED) }
            }.collect { newState ->
                val previous = _state.value
                _state.value = newState.copy(
                    photoUrl = if (previous.campsite?.imageId == newState.campsite?.imageId) previous.photoUrl else null,
                    qrBitmap = if (previous.campsite?.id == newState.campsite?.id) previous.qrBitmap else null,
                )
                newState.campsite?.let { campsite ->
                    if (_state.value.qrBitmap == null) viewModelScope.launch {
                        try {
                            val qr = withContext(Dispatchers.Default) { QRCode.generate(campsite, 256) }
                            if (_state.value.campsite?.id == campsite.id) _state.update { it.copy(qrBitmap = qr) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) { /* The full QR screen can still be opened. */ }
                    }
                    campsite.imageId?.takeIf { _state.value.photoUrl == null }?.let { imageId ->
                        viewModelScope.launch {
                            try {
                                val url = imageRepository?.downloadUrl(imageId)
                                if (_state.value.campsite?.imageId == imageId) _state.update { it.copy(photoUrl = url) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) { /* The illustration remains visible offline. */ }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Creates a [ViewModelProvider.Factory] supplying [campsiteId] to [OwnerCampsiteDetailsController]. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerCampsiteDetailsController(campsiteId, application.container.campsiteRepository,
                    application.container.lotteryRepository, application.container.imageRepository)
            }
        }
    }
}
