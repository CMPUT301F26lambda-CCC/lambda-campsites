/*
 * AI use: GLM 5.3 Flash (Z.ai) helped write this file in November 2026, through
 * OpenCode, at Raghav Sethi's request. It helped with the ViewModel and
 * StateFlow setup, the four-step wizard state, date validation through
 * [Lottery.validateDates], error handling, syntax and KDoc. Raghav Sethi
 * reviewed, tested, and committed it.
 * #169 follow-up, 2026-11-04: GLM 5.3 Flash (Z.ai), through OpenCode, helped
 * add the edit locks from US 02.04.06: the campsites and registration dates
 * lock once entrants joined, the capacity floor against the live wait list,
 * and the fresh re-read of the stored lottery on save.
 */
// Follow-up #172: Lalith Valluru, 2026-11-04. OpenAI GPT-6 (OpenAI), through Codex,
// assisted with edit guards, race handling and merge preservation. Human review pending.
// Outstanding issues: manual TalkBack review and rules deployment remain pending.
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.google.firebase.Timestamp
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEditRestriction
import com.lambda.campsite.model.repository.LotteryEditException
import com.lambda.campsite.model.LotteryStatus
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.ProfileRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Firestore timestamp for midnight at the start of [date] in this device's timezone. */
private fun startOfDay(date: LocalDate): Timestamp =
    Timestamp(java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))

/** Midnight immediately after [date], so the selected final day remains open. */
private fun endOfDay(date: LocalDate): Timestamp = startOfDay(date.plusDays(1))

/** Why the wizard refused to move on or to create the lottery. */
enum class NewLotteryError {
    /** Step 1: no campsite is selected (US 02.04.02). */
    NO_CAMPSITES_SELECTED,

    /** Step 1: a sixth campsite was picked. A lottery covers at most five. */
    TOO_MANY_CAMPSITES,

    /** Step 2: a registration period or booking period date is missing (US 02.04.03, 02.04.04). */
    MISSING_DATES,

    /** Step 2: the dates don't fit together, checked on the [Lottery] model. */
    INVALID_DATES,

    /** Step 3: the lottery name is empty (US 02.04.05). */
    MISSING_NAME,

    /**
     * The lottery is being edited and the waiting-list capacity can't go below
     * the entrants already on it (US 02.04.06, 02.04.07).
     */
    CAPACITY_BELOW_JOINED,

    /**
     * The draw picked the winners while the edit was open, so the lottery
     * can't be edited any more (US 02.04.06); only Cloud Functions change the
     * status (see docs/architecture.md).
     */
    NOT_DRAWABLE,

    /** The lottery couldn't be saved, for example because the phone is offline. */
    SAVE_FAILED,

    /** Registration changed during editing; review the refreshed locked fields before saving. */
    LOCKED_FIELDS_CHANGED,
}

/**
 * State for the "New lottery" wizard (US 02.04.01, 02.04.02, 02.04.03,
 * 02.04.04, 02.04.05, 02.04.07).
 *
 * Date-only values, like the registration period, are `LocalDate`s in the
 * form. They become timestamps at that day's start, in this device's
 * timezone, when the lottery is created.
 *
 * @property step which wizard step is shown: 1 campsites, 2 dates, 3
 *   settings, 4 review.
 * @property campsites the owner's campsites to choose from (US 02.04.02).
 * @property selectedCampsiteIds the campsites picked for this lottery, in the
 *   order they were picked.
 * @property registrationOpens when entrants can start joining (US 02.04.03).
 * @property registrationCloses when joining stops and the draw runs (US 02.04.03).
 * @property stayStart the first night a winner can book (US 02.04.04).
 * @property stayEnd the last day a winner can check out (US 02.04.04).
 * @property name what entrants see in lists (US 02.04.05).
 * @property numberOfWinners how many entrants the draw picks (US 02.04.05).
 * @property limitWaitingList `false` lets the waiting list take everyone (US 02.04.07).
 * @property capacity the waiting-list limit, used only when [limitWaitingList] is `true`.
 * @property error why the last action was refused, or `null` if it was allowed.
 * @property isLoadingCampsites `true` while the owner's campsites are still loading.
 * @property isSaving `true` while the lottery is being saved to Firestore.
 * @property saveFailed `true` if saving the lottery failed.
 */
data class NewLotteryUiState(
    val step: Int = 1,
    val campsites: List<Campsite> = emptyList(),
    val selectedCampsiteIds: List<String> = emptyList(),
    val registrationOpens: LocalDate? = null,
    val registrationCloses: LocalDate? = null,
    val stayStart: LocalDate? = null,
    val stayEnd: LocalDate? = null,
    val name: String = "",
    val numberOfWinners: Int = 20,
    val limitWaitingList: Boolean = true,
    val capacity: Int = 150,
    val error: NewLotteryError? = null,
    val isLoadingCampsites: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** True when this wizard is editing an existing, still-open lottery. */
    val isEditing: Boolean = false,
    /** True until the existing lottery has populated the edit form. */
    val isLoadingLottery: Boolean = false,
    /**
     * How many entrants were already on the edited lottery's waiting list when it
     * loaded, `0` for a new lottery. While it isn't zero, registration is under
     * way: the campsites and registration dates are locked, and the waiting-list
     * capacity can't go below this count (US 02.04.06). Saving checks the stored
     * lottery again, since it can keep growing while the form is open.
     */
    val joinedEntrants: Int = 0,
) {

    /**
     * Returns `true` when the edited lottery's waiting list already took entrants,
     * so the campsites and the registration dates can't change: entrants joined
     * those exact campsites and dates, and moving registration would change who
     * is eligible (US 02.04.06).
     */
    fun hasJoinedEntrants(): Boolean = isEditing && joinedEntrants > 0
}

/**
 * Controller for the four-step "New lottery" wizard (US 02.04.01 to 02.04.07).
 * It is an Android [ViewModel] that survives screen rotation.
 *
 * Step 1 picks the owner's campsites, step 2 sets the registration and booking
 * periods, step 3 names the lottery and caps its waiting list, and step 4
 * reviews everything before saving. `onNext` validates the current step before
 * moving on; [Lottery.validateDates] makes the final call on the dates.
 *
 * @param lotteryRepository where the lottery is saved.
 * @param campsiteRepository where the owner's campsites are loaded from.
 * @param profileRepository where the owner's ID comes from for the new lottery.
 */
class NewLotteryController(
    private val lotteryRepository: LotteryRepository,
    campsiteRepository: CampsiteRepository,
    private val profileRepository: ProfileRepository,
    private val lotteryIdToEdit: String? = null,
) : ViewModel() {

    /** A lottery covers at most five campsites (see docs/architecture.md, section 3). */
    private val maxCampsites = 5

    private val _state = MutableStateFlow(NewLotteryUiState())

    /** The current UI state. The screen reads it with `collectAsStateWithLifecycle()`. */
    val state: StateFlow<NewLotteryUiState> = _state.asStateFlow()

    private var editingLottery: Lottery? = null

    init {
        viewModelScope.launch {
            campsiteRepository.myCampsites()
                .catch { _state.update { it.copy(isLoadingCampsites = false) } }
                .collect { campsites ->
                    _state.update { it.copy(campsites = campsites, isLoadingCampsites = false) }
                }
        }
        lotteryIdToEdit?.let { lotteryId ->
            _state.update { it.copy(isEditing = true, isLoadingLottery = true) }
            viewModelScope.launch {
                lotteryRepository.lottery(lotteryId).firstOrNull()?.let { lottery ->
                    editingLottery = lottery
                    _state.update {
                        it.copy(
                            selectedCampsiteIds = lottery.campsiteIds,
                            registrationOpens = lottery.registrationOpens.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                            // Stored as the start of the following day: present the day the owner chose.
                            registrationCloses = lottery.registrationCloses.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1),
                            stayStart = lottery.stayStart,
                            stayEnd = lottery.stayEnd,
                            name = lottery.name,
                            numberOfWinners = lottery.numberOfWinners,
                            limitWaitingList = lottery.capacity != null,
                            capacity = lottery.capacity ?: it.capacity,
                            joinedEntrants = lottery.entryCount,
                            isLoadingLottery = false,
                        )
                    }
                } ?: _state.update { it.copy(isLoadingLottery = false, saveFailed = true) }
            }
        }
    }

    /**
     * Selects or unselects the campsite with this [campsiteId] (US 02.04.02).
     * Refused once five campsites are already selected, since Firestore's
     * rules stop a lottery from covering more. On an edit whose waiting list
     * already took entrants, the campsites are locked (US 02.04.06), so the
     * change is refused; the screen shows them locked with the reason.
     */
    fun onCampsiteToggled(campsiteId: String) {
        _state.update { current ->
            when {
                current.hasJoinedEntrants() -> current
                campsiteId in current.selectedCampsiteIds ->
                    current.copy(selectedCampsiteIds = current.selectedCampsiteIds - campsiteId, error = null)
                current.selectedCampsiteIds.size >= maxCampsites ->
                    current.copy(error = NewLotteryError.TOO_MANY_CAMPSITES)
                else ->
                    current.copy(selectedCampsiteIds = current.selectedCampsiteIds + campsiteId, error = null)
            }
        }
    }

    /**
     * Moves to the next step, checking the current one first. Step 1 refuses
     * without a campsite (US 02.04.02), step 2 refuses an incomplete or
     * impossible date set (US 02.04.03, 02.04.04), step 3 refuses an empty
     * name (US 02.04.05).
     */
    fun onNext() {
        val current = _state.value
        val error = when (current.step) {
            1 -> if (current.selectedCampsiteIds.isEmpty()) NewLotteryError.NO_CAMPSITES_SELECTED else null
            2 -> dateError(current)
            3 -> if (current.name.isBlank()) NewLotteryError.MISSING_NAME else null
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(step = it.step + 1, error = null) }
    }

    /** Moves back one step, for the Back button. Step 1's Back leaves the screen. */
    fun onBack() {
        if (_state.value.step > 1) _state.update { it.copy(step = it.step - 1, error = null) }
    }

    /**
     * Jumps to [step], for the Edit buttons on the review screen. Only earlier
     * steps are reachable this way.
     */
    fun onStepSelected(step: Int) {
        if (step < _state.value.step) _state.update { it.copy(step = step, error = null) }
    }

    /**
     * Sets when registration opens (US 02.04.03). Ignored while the edited
     * lottery's registration dates are locked (US 02.04.06).
     */
    fun onRegistrationOpensChange(date: LocalDate?) {
        if (_state.value.hasJoinedEntrants()) return
        _state.update { it.copy(registrationOpens = date) }
    }

    /**
     * Sets when registration closes (US 02.04.03). Ignored while the edited
     * lottery's registration dates are locked (US 02.04.06).
     */
    fun onRegistrationClosesChange(date: LocalDate?) {
        if (_state.value.hasJoinedEntrants()) return
        _state.update { it.copy(registrationCloses = date) }
    }

    /** Sets the first stay night, the lottery's check-in (US 02.04.04). */
    fun onStayStartChange(date: LocalDate?) {
        _state.update { it.copy(stayStart = date) }
    }

    /** Sets the last stay day, the lottery's check-out (US 02.04.04). */
    fun onStayEndChange(date: LocalDate?) {
        _state.update { it.copy(stayEnd = date) }
    }

    /** Sets the lottery's name (US 02.04.05). */
    fun onNameChange(name: String) {
        _state.update {
            it.copy(
                name = name,
                error = if (it.error == NewLotteryError.MISSING_NAME && name.isNotBlank()) null else it.error,
            )
        }
    }

    /** Sets how many entrants the draw picks (US 02.04.05). */
    fun onWinnersChange(numberOfWinners: Int) {
        _state.update { it.copy(numberOfWinners = numberOfWinners.coerceIn(1, 100)) }
    }

    /** Turns the waiting-list limit on or off. Off means the list takes everyone (US 02.04.07). */
    fun onLimitWaitingListChange(limited: Boolean) {
        _state.update { it.copy(limitWaitingList = limited) }
    }

    /**
     * Sets the waiting-list capacity, used while the limit is on (US 02.04.07).
     * On an edit, it can't go below the entrants already joined (US 02.04.06);
     * raising it for a bigger list stays allowed.
     */
    fun onCapacityChange(capacity: Int) {
        val minimum = maxOf(1, _state.value.joinedEntrants)
        _state.update { it.copy(capacity = capacity.coerceIn(minimum, 10000)) }
    }

    /**
     * Saves the lottery as [LotteryStatus.OPEN] (US 02.04.01). The draw checks
     * the dates one last time on the [Lottery] model, since the timezone can
     * make a date set that looked fine on the form impossible. Refuses to run
     * while a save is in flight, so a quick double tap creates one lottery.
     *
     * On an edit (US 02.04.06), the stored lottery is read back first and
     * decides what's locked: conflicting campsite/registration changes are refused
     * once anyone has joined, and the waiting-list capacity has to still
     * cover everyone already on it, because entrants can join while the wizard
     * sits open.
     *
     * @param onSuccess called with the saved lottery once Firestore accepts it.
     */
    fun onCreateLottery(onSuccess: (Lottery) -> Unit) {
        val current = _state.value
        if (current.isSaving) return

        if (
            current.registrationOpens == null ||
            current.registrationCloses == null ||
            current.stayStart == null ||
            current.stayEnd == null
        ) {
            _state.update { it.copy(error = NewLotteryError.MISSING_DATES) }
            return
        }
        if (current.name.isBlank()) {
            _state.update { it.copy(error = NewLotteryError.MISSING_NAME) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveFailed = false) }
            try {
                val ownerId = editingLottery?.ownerId ?: profileRepository.currentProfile().firstOrNull()?.deviceId
                if (ownerId == null) {
                    // The profile a lottery needs, like this device's, should
                    // always exist. If it doesn't, the save would be refused.
                    _state.update { it.copy(isSaving = false, saveFailed = true) }
                    return@launch
                }

                // US 02.04.06: entrants can join while the wizard sits open, so
                // the stored waiting list, not the one loaded into the form,
                // decides what's locked and what the capacity has to cover.
                val stored = editingLottery
                    ?.let { lotteryRepository.lottery(it.id).firstOrNull() }
                    ?: editingLottery
                val joined = stored?.entryCount ?: 0

                if (stored != null && stored.status != LotteryStatus.OPEN) {
                    refuseEdit(LotteryEditRestriction.NOT_OPEN, stored)
                    return@launch
                }

                if (current.limitWaitingList && current.capacity < joined) {
                    refuseEdit(LotteryEditRestriction.CAPACITY_BELOW_JOINED, stored)
                    return@launch
                }

                val lottery = Lottery(
                    id = editingLottery?.id ?: lotteryRepository.newLotteryId(),
                    ownerId = ownerId,
                    name = current.name.trim(),
                    // Keep the proposal intact so a newly locked edit is explicitly refused.
                    campsiteIds = current.selectedCampsiteIds,
                    registrationOpens = if (stored != null && current.registrationOpens == registrationOpenDate(stored)) stored.registrationOpens else startOfDay(current.registrationOpens!!),
                    registrationCloses = if (stored != null && current.registrationCloses == registrationCloseDate(stored)) stored.registrationCloses else endOfDay(current.registrationCloses!!),
                    stayStart = current.stayStart!!,
                    stayEnd = current.stayEnd!!,
                    numberOfWinners = current.numberOfWinners,
                    capacity = if (current.limitWaitingList) current.capacity else null,
                )
                val restriction = stored?.let(lottery::editRestriction)
                if (restriction != null) {
                    refuseEdit(restriction, stored)
                    return@launch
                }
                if (!lottery.validateDates()) {
                    _state.update { it.copy(isSaving = false, error = NewLotteryError.INVALID_DATES) }
                    return@launch
                }
                lotteryRepository.save(lottery)
                _state.update { it.copy(isSaving = false, saveFailed = false, error = null) }
                onSuccess(lottery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val refusal = generateSequence(e as Throwable?) { it.cause }
                    .filterIsInstance<LotteryEditException>().firstOrNull()
                if (refusal != null) {
                    val latest = editingLottery?.let {
                        try { lotteryRepository.lottery(it.id).firstOrNull() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    }
                    refuseEdit(refusal.restriction, latest)
                } else _state.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    private fun registrationOpenDate(lottery: Lottery): LocalDate =
        lottery.registrationOpens.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

    private fun registrationCloseDate(lottery: Lottery): LocalDate =
        lottery.registrationCloses.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1)

    /** Refresh locked fields after a refused edit; the user must review and save again. */
    private fun refuseEdit(reason: LotteryEditRestriction, latest: Lottery?) {
        if (latest != null) editingLottery = latest
        _state.update { current -> current.copy(
            isSaving = false, saveFailed = false,
            joinedEntrants = latest?.entryCount ?: current.joinedEntrants,
            selectedCampsiteIds = if (reason == LotteryEditRestriction.LOCKED_FIELDS_CHANGED && latest != null) latest.campsiteIds else current.selectedCampsiteIds,
            registrationOpens = if (reason == LotteryEditRestriction.LOCKED_FIELDS_CHANGED && latest != null) registrationOpenDate(latest) else current.registrationOpens,
            registrationCloses = if (reason == LotteryEditRestriction.LOCKED_FIELDS_CHANGED && latest != null) registrationCloseDate(latest) else current.registrationCloses,
            error = when (reason) {
                LotteryEditRestriction.NOT_OPEN -> NewLotteryError.NOT_DRAWABLE
                LotteryEditRestriction.LOCKED_FIELDS_CHANGED -> NewLotteryError.LOCKED_FIELDS_CHANGED
                LotteryEditRestriction.CAPACITY_BELOW_JOINED -> NewLotteryError.CAPACITY_BELOW_JOINED
            }) }
    }

    /** Why step 2 can't continue, or `null` if its dates allow it. */
    private fun dateError(state: NewLotteryUiState): NewLotteryError? {
        val dates = listOf(state.registrationOpens, state.registrationCloses, state.stayStart, state.stayEnd)
        return if (dates.any { it == null }) NewLotteryError.MISSING_DATES else null
    }

    /** Clears the error once the screen has shown it. */
    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        /** Creates a [NewLotteryController] with the app's repositories from the AppContainer. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                NewLotteryController(
                    lotteryRepository = application.container.lotteryRepository,
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                )
            }
        }

        /** Creates a wizard pre-filled from [lotteryId] for owner editing. */
        fun createFactory(lotteryId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LambdaApplication
                NewLotteryController(
                    lotteryRepository = application.container.lotteryRepository,
                    campsiteRepository = application.container.campsiteRepository,
                    profileRepository = application.container.profileRepository,
                    lotteryIdToEdit = lotteryId,
                )
            }
        }

        /** Converts an instant to a Firestore timestamp. */
        private fun java.time.Instant.toTimestamp() = Timestamp(java.util.Date.from(this))
    }
}
