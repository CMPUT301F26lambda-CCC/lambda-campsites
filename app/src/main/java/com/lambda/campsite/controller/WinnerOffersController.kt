/*
 * Purpose: own lottery wins and their booked/declined history for the Flow 9 entry point.
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: general reservation lists remain Flow 10 (#127).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with live loading and KDoc; human review pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** A signed-in user's win and its lottery, which may have been removed. */
data class WinnerOffer(val entry: LotteryEntry, val lottery: Lottery?)

/** Live win history, with separate loading/error/empty states. */
data class WinnerOffersUiState(val offers: List<WinnerOffer> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)

/** Loads only this entrant's wins; it never reads an owner's private waiting list. */
@OptIn(ExperimentalCoroutinesApi::class)
class WinnerOffersController(private val repository: LotteryRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(WinnerOffersUiState())
    /** Live offers consumed by the Reservations tab's booking entry point. */
    val state = mutableState.asStateFlow()
    private var listener: Job? = null
    init { onRetry() }
    /** Restarts the listener after a failure without mutating an entry. */
    fun onRetry() {
        listener?.cancel()
        mutableState.value = WinnerOffersUiState()
        listener = viewModelScope.launch {
            repository.myEntries().flatMapLatest { all ->
                val wins = all.filter { it.status in setOf(EntryStatus.WON, EntryStatus.BOOKED, EntryStatus.DECLINED) }
                    .sortedByDescending { it.joinedAt }
                if (wins.isEmpty()) flowOf(emptyList()) else combine(wins.map { entry ->
                    repository.lottery(entry.lotteryId).map { WinnerOffer(entry, it) }
                }) { it.toList() }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                mutableState.value = WinnerOffersUiState(loading = false, failed = true)
            }.collect { mutableState.value = WinnerOffersUiState(offers = it, loading = false) }
        }
    }
    companion object {
        /** Injects the existing lottery repository. */
        val Factory = viewModelFactory { initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
            WinnerOffersController(app.container.lotteryRepository)
        } }
    }
}
