/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the ViewModel and
 * StateFlow setup, the Active/Upcoming/Completed splitting, error handling,
 * syntax and KDoc, following the OwnerCampsiteListController shape.
 * Raghav Sethi reviewed, tested, and committed it.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.google.firebase.Timestamp
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.repository.LotteryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why loading the lottery list failed. */
enum class LotteryListError {
    /** The lottery list couldn't be loaded from Firestore. */
    LOAD_FAILED,
}

/**
 * State for the owner lotteries screen (US 02.04.01).
 *
 * A lottery lands in exactly one of the three buckets, worked out at [now]
 * from [Lottery.statusAt] and the stored status.
 *
 * @property active lotteries in registration: opened but not drawn yet.
 * @property upcoming lotteries whose registration hasn't opened yet.
 * @property completed lotteries that have been drawn or ended.
 * @property isLoading `true` while the lotteries are still loading.
 * @property error why the last load failed, or `null` if loading succeeded.
 */
data class OwnerLotteryListUiState(
    val active: List<Lottery> = emptyList(),
    val upcoming: List<Lottery> = emptyList(),
    val completed: List<Lottery> = emptyList(),
    val isLoading: Boolean = true,
    val error: LotteryListError? = null,
)

/**
 * Controller for the owner's lotteries tab (US 02.04.01). It is an Android
 * [ViewModel] that survives screen rotation.
 *
 * It listens to [repository] for the lotteries this device's profile runs and
 * publishes [state], sorted into the Active, Upcoming and Completed buckets
 * the screen shows.
 *
 * @param repository where lotteries are loaded from.
 * @param now what the controller treats as now. The app uses the real clock;
 *   tests fix it so the buckets don't change as time passes.
 */
class OwnerLotteryListController(
    private val repository: LotteryRepository,
    private val now: () -> Timestamp = { Timestamp.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(OwnerLotteryListUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<OwnerLotteryListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.myLotteries()
                .catch { _state.update { it.copy(isLoading = false, error = LotteryListError.LOAD_FAILED) } }
                .collect { lotteries ->
                    _state.update {
                        it.copy(
                            active = lotteries.filter(::isActive),
                            upcoming = lotteries.filter { l -> l.statusAt(now()) == LotteryStatus.UPCOMING },
                            completed = lotteries.filter { l ->
                                l.status == LotteryStatus.DRAWN || l.status == LotteryStatus.ENDED
                            },
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    /** An active lottery has opened, hasn't closed and hasn't been drawn. */
    private fun isActive(lottery: Lottery): Boolean =
        lottery.status == LotteryStatus.OPEN && lottery.statusAt(now()) != LotteryStatus.UPCOMING

    /** Clears the load error once the screen has displayed it. */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        /** Creates an [OwnerLotteryListController] using the lottery repository from [LambdaApplication.container]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                OwnerLotteryListController(application.container.lotteryRepository)
            }
        }
    }
}
