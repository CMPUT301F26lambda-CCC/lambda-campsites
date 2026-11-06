/*
 * Purpose: winner-only campsite/date selection, review, booking and decline (Flow 9).
 * Feature owner: Lalith Valluru. Last updated: 2026-11-03.
 * Outstanding issues: manual TalkBack/device review remains pending (#126).
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted with state, validation,
 * repository integration and KDoc; human review pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.*
import com.lambda.campsite.model.repository.*
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** One lottery campsite and its authoritative live calendar; null means unavailable. */
data class WinnerBookingSite(val campsite: Campsite?, val calendar: Availability?)

/** The current step; confirmation is entered only after the repository returns a saved ID. */
enum class WinnerBookingStep { CAMPSITE, DATES, REVIEW, CONFIRMED, DECLINED }

/** Recoverable booking failures, including a race with another winner. */
enum class WinnerBookingError { DATES_TAKEN, INVALID_DATES, GUESTS, NOT_A_WINNER, SAVE_FAILED, RULES_REQUIRED }

/**
 * Winner-only presentation state (US 01.04.01–03, 01.05.01).
 * @property sites all campsite IDs in the lottery, preserving unavailable choices explicitly.
 * @property entry the signed-in entrant's entry, never an owner list or another user's entry.
 * @property savedId committed reservation ID; prevents repeated submissions while its receipt loads.
 * @property receipt actual repository reservation matching [savedId], never a reconstructed receipt.
 * @property today earliest selectable check-in; refreshed when the screen resumes.
 */
data class WinnerBookingUiState(
    val lottery: Lottery? = null,
    val sites: List<WinnerBookingSite> = emptyList(),
    val entry: LotteryEntry? = null,
    val selectedCampsiteId: String? = null,
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val month: YearMonth = YearMonth.now(),
    val today: LocalDate = LocalDate.now(),
    val guests: Int = 1,
    val rulesAgreed: Boolean = false,
    val step: WinnerBookingStep = WinnerBookingStep.CAMPSITE,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val confirmDecline: Boolean = false,
    val error: WinnerBookingError? = null,
    val savedId: String? = null,
    val receipt: Reservation? = null,
) {
    /** Only an unused, drawn win authorizes access to selection or a mutation. */
    val canBook: Boolean get() = entry?.status == EntryStatus.WON && lottery?.status == LotteryStatus.DRAWN &&
        savedId == null && step != WinnerBookingStep.DECLINED
    /** Currently selected live campsite/calendar. */
    val selectedSite: WinnerBookingSite? get() = sites.firstOrNull { it.campsite?.id == selectedCampsiteId }
    /** True only for a free night inside both the lottery period and campsite season. */
    fun nightAvailable(date: LocalDate): Boolean {
        val lot = lottery ?: return false
        val calendar = selectedSite?.calendar ?: return false
        return !date.isBefore(today) && !date.isBefore(lot.stayStart) && date.isBefore(lot.stayEnd) &&
            !date.isBefore(calendar.seasonStart) && date.isBefore(calendar.seasonEnd) && date !in calendar.bookedNights
    }
    /** Checkout can be on a booked night: that night is not part of this stay. */
    fun checkoutAvailable(date: LocalDate): Boolean {
        val start = checkIn ?: return false
        if (!date.isAfter(start)) return false
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(date) }.all(::nightAvailable)
    }
    /** Current range is rechecked on every live calendar emission and before submission. */
    val validRange: Boolean get() = checkOut?.let(::checkoutAvailable) == true
    /** Whether any future free night remains, without allocating an unbounded date list. */
    val hasDates: Boolean get() {
        val lot = lottery ?: return false
        val calendar = selectedSite?.calendar ?: return false
        val start = maxOf(today, lot.stayStart, calendar.seasonStart)
        val end = minOf(lot.stayEnd, calendar.seasonEnd)
        if (!start.isBefore(end)) return false
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }.any(::nightAvailable)
    }
}

/**
 * Uses the existing transactional booking repository; never writes dates or results directly.
 * An entry changing to BOOKED/DECLINED immediately removes unused booking options.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WinnerBookingController(
    private val lotteryId: String,
    private val lotteries: LotteryRepository,
    private val campsites: CampsiteRepository,
    private val reservations: ReservationRepository,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WinnerBookingUiState(today = currentDate()))
    /** Live state consumed by the booking screen. */
    val state: StateFlow<WinnerBookingUiState> = mutableState.asStateFlow()
    private var listener: Job? = null
    init { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            lotteries.lottery(lotteryId).flatMapLatest { lottery ->
                val siteFlows = lottery?.campsiteIds.orEmpty().map { id ->
                    combine(campsites.campsite(id), campsites.availability(id)) { camp, calendar -> WinnerBookingSite(camp, calendar) }
                }
                val sites = if (siteFlows.isEmpty()) flowOf(emptyList()) else combine(siteFlows) { it.toList() }
                combine(sites, lotteries.myEntries(), reservations.myReservations()) { choices, entries, bookings ->
                    Triple(lottery, choices, entries.firstOrNull { it.lotteryId == lotteryId } to bookings)
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(isLoading = false, loadFailed = true) }
            }.collect { (lottery, sites, personal) ->
                val (entry, bookings) = personal
                mutableState.update { old ->
                    val selected = old.selectedCampsiteId?.takeIf { id -> sites.any { it.campsite?.id == id } }
                        ?: sites.singleOrNull()?.campsite?.id
                    val firstLoad = old.lottery == null
                    val savedId = old.savedId ?: entry?.reservationId?.takeIf { entry.status == EntryStatus.BOOKED }
                    val receipt = bookings.firstOrNull { it.id == savedId && it.lotteryId == lotteryId }
                    old.copy(lottery = lottery, sites = sites, entry = entry, selectedCampsiteId = selected,
                        month = if (firstLoad && lottery != null) YearMonth.from(maxOf(lottery.stayStart, currentDate())) else old.month,
                        step = when {
                            savedId != null -> WinnerBookingStep.CONFIRMED
                            entry?.status == EntryStatus.DECLINED -> WinnerBookingStep.DECLINED
                            firstLoad && selected != null -> WinnerBookingStep.DATES
                            else -> old.step
                        }, savedId = savedId, receipt = receipt, isLoading = false, loadFailed = false)
                }
            }
        }
    }

    /** Retry a failed listener; a previously committed booking is never sent twice. */
    fun onRetry() = observe()
    /** Refresh date-dependent eligibility after returning to the app. */
    fun onResume() { mutableState.update { it.copy(today = currentDate()) } }
    /** Select only one of this lottery's available campsite records; reset the old dates. */
    fun onSelectCampsite(id: String) {
        val old = state.value
        if (!old.canBook || old.saving || old.sites.none { it.campsite?.id == id }) return
        mutableState.update { it.copy(selectedCampsiteId = id, checkIn = null, checkOut = null,
            rulesAgreed = false, guests = 1, error = null) }
    }
    /** Select a free check-in, then a checkout whose intervening nights are all free. */
    fun onDate(date: LocalDate) {
        val old = state.value
        if (!old.canBook || old.saving) return
        when {
            old.checkIn != null && old.checkOut == null && old.checkoutAvailable(date) ->
                mutableState.update { it.copy(checkOut = date, rulesAgreed = false, error = null) }
            old.nightAvailable(date) -> mutableState.update { it.copy(checkIn = date, checkOut = null, rulesAgreed = false, error = null) }
        }
    }
    /** Start editing the check-in, clearing the dependent checkout and rules agreement. */
    fun onEditCheckIn() { if (!state.value.saving) mutableState.update { it.copy(checkIn = null, checkOut = null, rulesAgreed = false) } }
    /** Keep the check-in while choosing a different checkout. */
    fun onEditCheckOut() { if (!state.value.saving) mutableState.update { it.copy(checkOut = null, rulesAgreed = false) } }
    /** Browse months without changing a valid date selection. */
    fun onMonth(delta: Long) { mutableState.update { it.copy(month = it.month.plusMonths(delta)) } }
    /** Move from campsite to dates, or from a still-free range to review. */
    fun onContinue() {
        val old = state.value
        if (!old.canBook || old.saving || old.isLoading || old.loadFailed) return
        if (old.step == WinnerBookingStep.CAMPSITE && old.selectedSite?.campsite != null)
            mutableState.update { it.copy(step = WinnerBookingStep.DATES) }
        else if (old.step == WinnerBookingStep.DATES) mutableState.update {
            if (it.validRange) it.copy(step = WinnerBookingStep.REVIEW, error = null)
            else it.copy(error = WinnerBookingError.INVALID_DATES)
        }
    }
    /** Back through the local wizard, returning false when the route should close. */
    fun onBack(): Boolean {
        if (state.value.saving) return true
        return when (state.value.step) {
            WinnerBookingStep.REVIEW -> { mutableState.update { it.copy(step = WinnerBookingStep.DATES) }; true }
            WinnerBookingStep.DATES -> if (state.value.sites.size > 1) {
                mutableState.update { it.copy(step = WinnerBookingStep.CAMPSITE) }; true
            } else false
            else -> false
        }
    }
    /** Guest count stays inside the selected site's live occupancy limit. */
    fun onGuests(value: Int) { if (!state.value.saving) mutableState.update {
        it.copy(guests = value.coerceIn(1, maxOf(1, it.selectedSite?.campsite?.maxOccupancy ?: 1)))
    } }
    /** Explicit agreement is required before the repository is called. */
    fun onRulesAgreed(value: Boolean) { if (!state.value.saving) mutableState.update { it.copy(rulesAgreed = value) } }
    /** Commits one booking transaction; reports collisions and preserves retryable failures. */
    fun onBook() {
        val old = state.value
        if (old.saving || old.savedId != null || old.isLoading || old.loadFailed) return
        val error = when {
            !old.canBook -> WinnerBookingError.NOT_A_WINNER
            !old.validRange -> WinnerBookingError.INVALID_DATES
            old.guests !in 1..(old.selectedSite?.campsite?.maxOccupancy ?: 0) -> WinnerBookingError.GUESTS
            !old.rulesAgreed -> WinnerBookingError.RULES_REQUIRED
            else -> null
        }
        if (error != null) { mutableState.update { it.copy(error = error) }; return }
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id = reservations.book(lotteryId, requireNotNull(old.selectedCampsiteId),
                    requireNotNull(old.checkIn), requireNotNull(old.checkOut), old.guests)
                mutableState.update { it.copy(savedId = id, step = WinnerBookingStep.CONFIRMED, saving = false) }
                // Restart to read the committed receipt, including server-assigned fields.
                observe()
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { mutableState.update { it.copy(saving = false, error = when {
                failure is ReservationException && failure.reason == ReservationException.Reason.NIGHTS_NOT_FREE -> WinnerBookingError.DATES_TAKEN
                failure is ReservationException && failure.reason == ReservationException.Reason.NOT_A_WINNER -> WinnerBookingError.NOT_A_WINNER
                else -> WinnerBookingError.SAVE_FAILED
            }) } }
        }
    }
    /** Show a confirmation before relinquishing an unused win. */
    fun onRequestDecline() { if (state.value.canBook && !state.value.saving) mutableState.update { it.copy(confirmDecline = true) } }
    /** Dismiss the decline dialog without changing the server. */
    fun onCancelDecline() { if (!state.value.saving) mutableState.update { it.copy(confirmDecline = false) } }
    /** Atomically decline; no dates were held, so other winners' calendars are unchanged. */
    fun onConfirmDecline() {
        if (!state.value.confirmDecline || !state.value.canBook || state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                reservations.decline(lotteryId)
                mutableState.update { it.copy(saving = false, confirmDecline = false, step = WinnerBookingStep.DECLINED) }
            } catch (failure: CancellationException) { throw failure }
            catch (failure: Exception) { mutableState.update { it.copy(saving = false, error = WinnerBookingError.SAVE_FAILED) } }
        }
    }
    companion object {
        /** Injects the shared repositories for one explicit lottery ID, never parsed from notice text. */
        fun createFactory(lotteryId: String): ViewModelProvider.Factory = viewModelFactory { initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
            WinnerBookingController(lotteryId, app.container.lotteryRepository, app.container.campsiteRepository, app.container.reservationRepository)
        } }
    }
}
