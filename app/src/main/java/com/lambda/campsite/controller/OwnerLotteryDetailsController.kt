/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the ViewModel setup,
 * combining the lottery, campsite and waiting-list flows, error handling,
 * syntax and KDoc, following the OwnerCampsiteDetailsController shape.
 * Raghav Sethi reviewed, tested, and committed it.
 * Issue #194: DeepSeek V4.1 Flash Free (DeepSeek), through OpenCode, helped load a
 * lottery's campsites by ID so a co-owner sees every covered campsite; human review pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.LotteryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why loading the lottery details failed. */
enum class LotteryDetailsError {
    /** There's no lottery with this ID, or the app can't read it. */
    LOTTERY_NOT_FOUND,

    /** The lottery couldn't be loaded, for example because the phone is offline. */
    LOAD_FAILED,
}

/**
 * State for the owner's lottery details screen (US 02.04.06).
 *
 * @property lottery the lottery, or `null` while loading and when no lottery
 *   with the screen's ID exists.
 * @property campsites the lottery's campsites, so the screen can show names.
 * @property waitingListCount the entrants on the waiting list (US 02.09.01).
 * @property isLoading `true` while the lottery is still loading.
 * @property error why the last load failed, or `null` if loading succeeded.
 */
data class OwnerLotteryDetailsUiState(
    val lottery: Lottery? = null,
    val campsites: List<Campsite> = emptyList(),
    val waitingList: List<LotteryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: LotteryDetailsError? = null,
)

/**
 * Controller for viewing one lottery's details for its owner (US 02.04.06).
 *
 * It listens to the lottery, the campsites in it and its waiting list, and
 * publishes everything in [state]. Only one of the lottery's owners can read
 * the waiting list, so the screen is for them (US 02.04.06, 02.04.08,
 * 02.09.01).
 *
 * @param lotteryId the document ID of the lottery to show.
 * @param lotteryRepository where the lottery and waiting list load from.
 * @param campsiteRepository where each campsite's name comes from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerLotteryDetailsController(
    private val lotteryId: String,
    private val lotteryRepository: LotteryRepository,
    private val campsiteRepository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerLotteryDetailsUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<OwnerLotteryDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                // A lottery covers at most five campsites, so listening to each
                // one by ID is cheap and works for a co-owner's campsite too.
                lotteryRepository.lottery(lotteryId).flatMapLatest { lottery ->
                    val campsiteFlows = lottery?.campsiteIds.orEmpty().map(campsiteRepository::campsite)
                    val campsites = if (campsiteFlows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(campsiteFlows) { found -> found.filterNotNull() }
                    }
                    campsites.map { lottery to it }
                },
                lotteryRepository.waitingList(lotteryId),
            ) { (lottery, campsites), waitingList ->
                OwnerLotteryDetailsUiState(
                    lottery = lottery,
                    campsites = campsites,
                    waitingList = waitingList,
                    isLoading = false,
                    error = if (lottery == null) LotteryDetailsError.LOTTERY_NOT_FOUND else null,
                )
            }.catch {
                _state.update { it.copy(isLoading = false, error = LotteryDetailsError.LOAD_FAILED) }
            }.collect { fresh ->
                _state.value = fresh
            }
        }
    }

    /** Clears the load error once the screen has displayed it. */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        /** Factory creating [OwnerLotteryDetailsController] for [lotteryId]. */
        fun createFactory(lotteryId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerLotteryDetailsController(
                    lotteryId,
                    application.container.lotteryRepository,
                    application.container.campsiteRepository,
                )
            }
        }
    }
}
