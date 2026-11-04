/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the ViewModel setup,
 * counting the draw's winners and not-selected entrants, error handling,
 * syntax and KDoc. Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.LotteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why loading the lottery results failed. */
enum class LotteryResultsError {
    /** There's no lottery with this ID, or the app can't read it. */
    LOTTERY_NOT_FOUND,

    /** The results couldn't be loaded, for example because the phone is offline. */
    LOAD_FAILED,
}

/**
 * State for the lottery results screen (US 02.05.01).
 *
 * @property lottery the drawn lottery, or `null` while loading and when no
 *   lottery with the screen's ID exists.
 * @property campsiteNames the names of the lottery's campsites, in the order
 *   they were picked, for the summary.
 * @property winnersCount how many entrants the draw picked. Winners who
 *   already booked their stay count too; a winner who declined was still
 *   picked (US 01.04.03), and there's no replacement draw.
 * @property notSelectedCount how many entrants the draw didn't pick.
 * @property isLoading `true` while the results are still loading.
 * @property error why the last load failed, or `null` if loading succeeded.
 */
data class LotteryResultsUiState(
    val lottery: Lottery? = null,
    val campsiteNames: List<String> = emptyList(),
    val winnersCount: Int = 0,
    val notSelectedCount: Int = 0,
    val isLoading: Boolean = true,
    val error: LotteryResultsError? = null,
)

/**
 * Controller for the lottery results screen (US 02.05.01).
 *
 * The `drawLotteries` Cloud Function picks the winners after registration
 * closes, so the screen never triggers the draw. It only shows the entries'
 * statuses the draw set.
 *
 * @param lotteryId the document ID of the drawn lottery to show.
 * @param repository where the lottery and its waiting list load from.
 * @param campsiteRepository where the campsites' names come from.
 */
class LotteryResultsController(
    lotteryId: String,
    private val repository: LotteryRepository,
    campsiteRepository: CampsiteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LotteryResultsUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<LotteryResultsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.lottery(lotteryId),
                repository.waitingList(lotteryId),
                campsiteRepository.myCampsites(),
            ) { lottery, entries, myCampsites ->
                LotteryResultsUiState(
                    lottery = lottery,
                    campsiteNames = lottery?.campsiteIds
                        ?.mapNotNull { id -> myCampsites.firstOrNull { it.id == id }?.name }
                        .orEmpty(),
                    winnersCount = entries.count { it.status == EntryStatus.WON || it.status == EntryStatus.BOOKED },
                    notSelectedCount = entries.count { it.status == EntryStatus.NOT_SELECTED },
                    isLoading = false,
                    error = if (lottery == null) LotteryResultsError.LOTTERY_NOT_FOUND else null,
                )
            }.catch {
                _state.update { it.copy(isLoading = false, error = LotteryResultsError.LOAD_FAILED) }
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
        /** Factory creating [LotteryResultsController] for [lotteryId]. */
        fun createFactory(lotteryId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                LotteryResultsController(
                    lotteryId,
                    application.container.lotteryRepository,
                    application.container.campsiteRepository,
                )
            }
        }
    }
}
