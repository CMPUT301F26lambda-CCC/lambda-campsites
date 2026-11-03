/*
 * Last updated: 2026-11-03 (Flow 12).
 * Outstanding issues: Shared-project verification remains pending (#129).
 * Feature owner: Lalith Valluru. Purpose: Flow 12 / US 02.09.01 owner waiting-list state.
 * AI use: OpenAI GPT-6 (OpenAI), through Codex, assisted Lalith with ownership guards,
 * listener cancellation, selection and KDoc. Human review pending.
 * Owner-only contact projections are fetched through an authenticated callable.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import com.lambda.campsite.model.Role
import com.lambda.campsite.model.WaitingListContact
import com.lambda.campsite.model.repository.WaitingListContactRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A visible failure; an empty successful list is never an error. */
enum class OwnerWaitingListError { LOAD_LOTTERIES, LOAD_ENTRIES, ACCESS_DENIED, LOTTERY_UNAVAILABLE }

/**
 * Flow 12 presentation state. Counts come from the successfully loaded entries.
 * @property lotteries selectable lotteries owned by the current active profile.
 * @property selectedLotteryId currently selected lottery, or null before selection/removal.
 * @property entries registrations in joinedAt order, then entrant ID to break ties.
 * @property loadingLotteries waiting for owner/profile and lottery data.
 * @property loadingEntries waiting for the selected list; stale rows are cleared first.
 * @property selectedEntrantId entry displayed in the details sheet, or null.
 * @property error the failed operation or access restriction, or null.
 * @property contacts minimal name/email/phone projections authorized for this selected lottery.
 * @property loadingContacts contact request is pending; existing entry IDs remain accessible.
 * @property contactsFailed contact read failed; retry is offered without claiming an empty list.
 */
data class OwnerWaitingListUiState(
    val lotteries: List<Lottery> = emptyList(),
    val selectedLotteryId: String? = null,
    val entries: List<LotteryEntry> = emptyList(),
    val loadingLotteries: Boolean = true,
    val loadingEntries: Boolean = false,
    val selectedEntrantId: String? = null,
    val error: OwnerWaitingListError? = null,
    val contacts: Map<String, WaitingListContact> = emptyMap(),
    val loadingContacts: Boolean = false,
    val contactsFailed: Boolean = false,
) {
    /** Selected owner lottery, or null if it disappeared or is no longer owned. */
    val selectedLottery: Lottery? get() = lotteries.firstOrNull { it.id == selectedLotteryId }
    /** Current sheet record; live removal of an entry automatically closes its sheet. */
    val selectedEntrant: LotteryEntry? get() = entries.firstOrNull { it.entrantId == selectedEntrantId }
}

/**
 * Read-only owner waiting-list controller (US 02.09.01, Flow #129).
 * The backend enforces ownership too. Private profile documents are never loaded by the app.
 * @param lotteries existing lottery query/listener repository.
 * @param profiles observes the current identity/roles to clear access when those change.
 * @param initialLotteryId optional destination from a future owner lottery-details route.
 * @param contactRepository authorized contact reader; defaults to an empty test implementation.
 */
class OwnerWaitingListController(
    private val lotteries: LotteryRepository,
    private val profiles: ProfileRepository,
    initialLotteryId: String? = null,
    private val contactRepository: WaitingListContactRepository = WaitingListContactRepository { _, _ -> emptyMap() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(OwnerWaitingListUiState())
    private var ownerJob: Job? = null
    private var entriesJob: Job? = null
    private var listVersion = 0L
    private var desiredId = initialLotteryId
    private var mayAutoSelect = initialLotteryId == null
    /** Observable UI state; the view never calls Firestore directly. */
    val state: StateFlow<OwnerWaitingListUiState> = mutableState.asStateFlow()

    init { observeOwnerLotteries() }

    private fun cancelEntries() {
        listVersion++
        entriesJob?.cancel()
        entriesJob = null
    }

    private fun observeOwnerLotteries() {
        ownerJob?.cancel()
        cancelEntries()
        mutableState.value = OwnerWaitingListUiState()
        ownerJob = viewModelScope.launch {
            combine(profiles.currentProfile(), lotteries.myLotteries()) { profile, owned ->
                profile to owned.filter { it.ownerId == profile.deviceId }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                cancelEntries()
                mutableState.value = OwnerWaitingListUiState(loadingLotteries = false,
                    error = OwnerWaitingListError.LOAD_LOTTERIES)
            }.collect { (profile, owned) ->
                if (!profile.hasRole(Role.OWNER) || profile.ownerBanned || profile.removed) {
                    cancelEntries()
                    mutableState.value = OwnerWaitingListUiState(loadingLotteries = false,
                        error = OwnerWaitingListError.ACCESS_DENIED)
                    return@collect
                }
                val choices = owned.sortedWith(compareBy<Lottery> { it.name.lowercase() }.thenBy { it.id })
                val current = state.value.selectedLotteryId
                val next = when {
                    desiredId != null -> choices.firstOrNull { it.id == desiredId }?.id
                    mayAutoSelect -> choices.firstOrNull()?.id
                    else -> null
                }
                if (next == null) {
                    cancelEntries()
                    mutableState.value = OwnerWaitingListUiState(lotteries = choices, loadingLotteries = false,
                        error = if (desiredId != null) OwnerWaitingListError.LOTTERY_UNAVAILABLE else null)
                } else {
                    desiredId = next
                    mayAutoSelect = false
                    mutableState.update { it.copy(lotteries = choices, loadingLotteries = false) }
                    if (current != next || entriesJob == null) loadEntries(next)
                }
            }
        }
    }

    /** Selects only a currently owned lottery. Invalid IDs never trigger a waiting-list read. */
    fun onSelectLottery(id: String) {
        if (state.value.loadingLotteries || state.value.error == OwnerWaitingListError.ACCESS_DENIED) return
        if (state.value.lotteries.none { it.id == id }) return
        if (state.value.selectedLotteryId == id && state.value.error != OwnerWaitingListError.LOAD_ENTRIES) return
        desiredId = id
        mayAutoSelect = false
        loadEntries(id)
    }

    private fun loadEntries(id: String) {
        cancelEntries()
        val version = listVersion
        mutableState.update { it.copy(selectedLotteryId = id, entries = emptyList(), selectedEntrantId = null,
            loadingEntries = true, error = null, contacts = emptyMap(), loadingContacts = false, contactsFailed = false) }
        entriesJob = viewModelScope.launch {
            lotteries.waitingList(id).catch { failure ->
                if (failure is CancellationException) throw failure
                if (version == listVersion) mutableState.update { it.copy(entries = emptyList(),
                    selectedEntrantId = null, loadingEntries = false, error = OwnerWaitingListError.LOAD_ENTRIES,
                    contacts = emptyMap(), loadingContacts = false, contactsFailed = false) }
            }.collectLatest { entries ->
                if (version != listVersion || state.value.selectedLotteryId != id) return@collectLatest
                val ordered = entries.filter { it.lotteryId == id }
                    .sortedWith(compareBy<LotteryEntry> { it.joinedAt }.thenBy { it.entrantId })
                mutableState.update { it.copy(entries = ordered, loadingEntries = false, error = null,
                    contacts = it.contacts.filterKeys { key -> ordered.any { row -> row.entrantId == key } },
                    loadingContacts = ordered.isNotEmpty(), contactsFailed = false,
                    selectedEntrantId = it.selectedEntrantId?.takeIf { selected -> ordered.any { row -> row.entrantId == selected } }) }
                try {
                    val contacts = contactRepository.contacts(id, ordered.map { it.entrantId })
                    if (version == listVersion) mutableState.update { it.copy(
                        contacts = contacts.filterKeys { key -> ordered.any { row -> row.entrantId == key } }, loadingContacts = false) }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    if (version == listVersion) mutableState.update { it.copy(contacts = emptyMap(),
                        loadingContacts = false, contactsFailed = true) }
                }
            }
        }
    }

    /** Retries the failed listener only, without writing data or changing the selected lottery. */
    fun onRetry() {
        if (state.value.error == OwnerWaitingListError.LOAD_ENTRIES || state.value.contactsFailed) {
            state.value.selectedLotteryId?.takeIf { id -> state.value.lotteries.any { it.id == id } }?.let(::loadEntries)
        } else observeOwnerLotteries()
    }

    /** Opens the latest visible entry; unavailable or stale entry IDs are ignored. */
    fun onOpenEntrant(id: String) {
        if (state.value.loadingEntries || state.value.error != null) return
        if (state.value.entries.any { it.entrantId == id }) mutableState.update { it.copy(selectedEntrantId = id) }
    }

    /** Dismisses the sheet without changing registrations. */
    fun onDismissEntrant() { mutableState.update { it.copy(selectedEntrantId = null) } }

    companion object {
        /** Creates this controller using the existing application repositories. */
        fun createFactory(initialLotteryId: String? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                OwnerWaitingListController(app.container.lotteryRepository, app.container.profileRepository,
                    initialLotteryId, app.container.waitingListContactRepository)
            }
        }
    }
}
