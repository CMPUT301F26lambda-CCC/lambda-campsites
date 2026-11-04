/*
 * Purpose: entrant join/leave state for Flow 7 (US 01.01.06, 01.02.01, 01.02.02).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03 (#149).
 * Outstanding issues: manual accessibility checks and team review remain for #124.
 * AI use: OpenAI GPT-6 through Codex helped write this controller and its KDoc.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Timestamp
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.WaitingListException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Failures the view maps to localized strings, without exposing exception messages. */
enum class LotteryError {
    LOAD_FAILED, NO_SUCH_LOTTERY, REGISTRATION_CLOSED, FULL,
    ALREADY_JOINED, NOT_JOINED, SAVE_FAILED,
}

/** A server-acknowledged action the view may announce to the entrant. */
enum class LotteryNotice { JOINED, LEFT }

/** Why the selected lottery cannot accept a new registration right now. */
enum class LotteryJoinBlock { UPCOMING, CLOSED, FULL, DRAWN, ENDED, UNAVAILABLE }

/**
 * State for the lotteries offered by one campsite.
 * @property lotteries live lotteries containing the selected campsite.
 * @property entries the current entrant's entries, restricted to displayed lotteries.
 * @property isLoading whether the initial load or retry is waiting for both data sources.
 * @property pendingLotteryId the lottery with an in-flight write, or null when idle.
 * @property error a load or action failure, or null.
 * @property notice the last acknowledged action, cleared with [LotteryController.onNoticeShown].
 * @property observedAt clock snapshot refreshed at registration boundaries and on resume.
 */
data class LotteryUiState(
    val lotteries: List<Lottery> = emptyList(),
    val entries: List<LotteryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val pendingLotteryId: String? = null,
    val error: LotteryError? = null,
    val notice: LotteryNotice? = null,
    val observedAt: Timestamp = Timestamp.now(),
) {
    /** The visible reason Join is disabled, independent of transient pending/loading state. */
    fun joinBlock(id: String): LotteryJoinBlock? {
        val lottery = lotteries.firstOrNull { it.id == id } ?: return LotteryJoinBlock.UNAVAILABLE
        return when {
            lottery.status == LotteryStatus.DRAWN -> LotteryJoinBlock.DRAWN
            lottery.status == LotteryStatus.ENDED -> LotteryJoinBlock.ENDED
            observedAt < lottery.registrationOpens -> LotteryJoinBlock.UPCOMING
            !lottery.isRegistrationOpen(observedAt) -> LotteryJoinBlock.CLOSED
            !lottery.hasSpace() -> LotteryJoinBlock.FULL
            else -> null
        }
    }

    /** Enables Join only for a nonmember with an eligible lottery and no pending/failed load. */
    fun canJoin(id: String): Boolean = !isLoading && pendingLotteryId == null && error != LotteryError.LOAD_FAILED &&
        entries.none { it.lotteryId == id } && joinBlock(id) == null

    /** A waiting entrant can withdraw while registration is open, even when capacity is full. */
    fun canWithdraw(id: String): Boolean = !isLoading && pendingLotteryId == null && error != LotteryError.LOAD_FAILED &&
        entries.any { it.lotteryId == id && it.status == EntryStatus.WAITING } &&
        lotteries.any { it.id == id && it.isRegistrationOpen(observedAt) }
}

/**
 * Observes campsite lotteries and handles joining/leaving through [repository].
 * Local model checks give immediate feedback; repository transactions remain authoritative.
 * No draw or owner-list logic belongs to this entrant controller.
 * @param campsiteId campsite whose lottery actions are being displayed.
 * @param repository live lottery data and atomic join/leave operations.
 * @param now clock for local registration checks, replaceable in tests.
 */
class LotteryController(
    private val campsiteId: String,
    private val repository: LotteryRepository,
    private val now: () -> Timestamp = { Timestamp.now() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(LotteryUiState(observedAt = now()))
    private var observation: Job? = null
    private var clockRefresh: Job? = null

    /** Read-only state for the Compose screen. */
    val state: StateFlow<LotteryUiState> = mutableState.asStateFlow()

    init { observe() }

    private fun observe() {
        observation?.cancel()
        mutableState.update { it.copy(isLoading = true, error = null, notice = null) }
        observation = viewModelScope.launch {
            combine(repository.lotteriesFor(campsiteId), repository.myEntries()) { lotteries, entries ->
                lotteries to entries.filter { entry -> lotteries.any { it.id == entry.lotteryId } }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(isLoading = false, error = LotteryError.LOAD_FAILED) }
            }.collect { (lotteries, entries) ->
                mutableState.update { it.copy(lotteries = lotteries, entries = entries, isLoading = false) }
                onRefreshTime()
            }
        }
    }

    /** Restarts failed listeners; never silently repeats a join/leave write. */
    fun onRetry() { if (state.value.pendingLotteryId == null) observe() }

    /** Refreshes eligibility on resume and schedules the next opening/closing boundary. */
    fun onRefreshTime() {
        clockRefresh?.cancel()
        val currentTime = now()
        mutableState.update { it.copy(observedAt = currentTime) }
        val boundary = state.value.lotteries.flatMap { listOf(it.registrationOpens, it.registrationCloses) }
            .filter { it > currentTime }.minOrNull() ?: return
        val milliseconds = ((boundary.seconds - currentTime.seconds) * 1000 +
            (boundary.nanoseconds - currentTime.nanoseconds) / 1_000_000).coerceAtLeast(1)
        clockRefresh = viewModelScope.launch { delay(milliseconds); onRefreshTime() }
    }

    /** Joins [lotteryId] if loaded, not already joined, open and not full. */
    fun onJoin(lotteryId: String) = act(lotteryId, joining = true)

    /** Leaves [lotteryId] if joined and registration remains open. */
    fun onLeave(lotteryId: String) = act(lotteryId, joining = false)

    private fun act(lotteryId: String, joining: Boolean) {
        onRefreshTime()
        val current = state.value
        if (current.isLoading || current.pendingLotteryId != null || current.error == LotteryError.LOAD_FAILED) return
        val lottery = current.lotteries.firstOrNull { it.id == lotteryId }
        val joined = current.entries.any { it.lotteryId == lotteryId }
        val refusal = when {
            lottery == null -> LotteryError.NO_SUCH_LOTTERY
            joining && joined -> LotteryError.ALREADY_JOINED
            !joining && !joined -> LotteryError.NOT_JOINED
            !lottery.isRegistrationOpen(now()) -> LotteryError.REGISTRATION_CLOSED
            joining && !lottery.hasSpace() -> LotteryError.FULL
            else -> null
        }
        if (refusal != null) {
            mutableState.update { it.copy(error = refusal, notice = null) }
            return
        }
        // Set before launching so repeated taps cannot start concurrent writes.
        mutableState.update { it.copy(pendingLotteryId = lotteryId, error = null, notice = null) }
        viewModelScope.launch {
            try {
                if (joining) repository.join(lotteryId) else repository.leave(lotteryId)
                // Membership comes from the listener, never an optimistic local edit.
                mutableState.update { it.copy(notice = if (joining) LotteryNotice.JOINED else LotteryNotice.LEFT) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: WaitingListException) {
                val error = when (failure.reason) {
                    WaitingListException.Reason.NO_SUCH_LOTTERY -> LotteryError.NO_SUCH_LOTTERY
                    WaitingListException.Reason.REGISTRATION_CLOSED -> LotteryError.REGISTRATION_CLOSED
                    WaitingListException.Reason.FULL -> LotteryError.FULL
                    WaitingListException.Reason.ALREADY_JOINED -> LotteryError.ALREADY_JOINED
                    WaitingListException.Reason.NOT_JOINED -> LotteryError.NOT_JOINED
                }
                mutableState.update { it.copy(error = error) }
            } catch (failure: Exception) {
                mutableState.update { it.copy(error = LotteryError.SAVE_FAILED) }
            } finally {
                mutableState.update { it.copy(pendingLotteryId = null) }
            }
        }
    }

    /** Clears an action error; listener failures remain until an explicit retry. */
    fun onErrorShown() {
        mutableState.update { if (it.error == LotteryError.LOAD_FAILED) it else it.copy(error = null) }
    }

    /** Clears a success announcement after the screen consumes it. */
    fun onNoticeShown() { mutableState.update { it.copy(notice = null) } }

    companion object {
        /** Creates the controller using the shared app repository for [campsiteId]. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                LotteryController(campsiteId, app.container.lotteryRepository)
            }
        }
    }
}
