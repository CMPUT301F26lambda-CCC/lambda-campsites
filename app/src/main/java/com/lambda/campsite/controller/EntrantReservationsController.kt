/*
 * Feature owner: Sreeman Reddy, Flow 10 (#127), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with the entrant reservation
 * list state and cancellation actions. Human review is pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.ReservationException
import com.lambda.campsite.model.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two lists in the entrant reservation tab (US 01.06.01 and 01.06.02). */
enum class EntrantReservationTab { ACTIVE, PAST }

/** A cancellation failure the details screen can explain without exposing backend errors. */
enum class EntrantCancellationError { NOT_ALLOWED, NOT_FOUND, LOAD_FAILED }

/**
 * State for Flow 10's entrant reservation list and details route.
 * @property reservations the signed-in entrant's live reservations, with every status.
 * @property today the local date used to classify stays, refreshed on resume.
 * @property confirmingId the reservation awaiting the entrant's confirmation.
 * @property cancellingId the reservation whose cancellation transaction is running.
 * @property cancelledId the most recently cancelled reservation, for one result announcement.
 */
data class EntrantReservationsUiState(
    val reservations: List<Reservation> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val selectedTab: EntrantReservationTab = EntrantReservationTab.ACTIVE,
    val confirmingId: String? = null,
    val cancellingId: String? = null,
    val cancelledId: String? = null,
    val cancelError: EntrantCancellationError? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    /** Confirmed current and future stays, soonest check-in first. */
    val active: List<Reservation>
        get() = reservations.filter { it.isActive(today) }.sortedBy { it.checkIn }

    /** Completed, cancelled and rejected stays, newest outcome first as in Figma. */
    val past: List<Reservation>
        get() = reservations.filterNot { it.isActive(today) }.sortedWith(
            compareByDescending<Reservation> {
                it.cancelledAt?.seconds ?: it.checkOut.toEpochDay() * 86_400
            }.thenByDescending { it.id },
        )

    /** Reservations in the selected tab. */
    val visibleReservations: List<Reservation>
        get() = if (selectedTab == EntrantReservationTab.ACTIVE) active else past

    /** The current copy of a reservation opened through its navigation ID. */
    fun reservation(id: String): Reservation? = reservations.firstOrNull { it.id == id }

    /** Only a confirmed stay that has not started may be cancelled. */
    fun canCancel(reservation: Reservation): Boolean =
        reservation.status == ReservationStatus.CONFIRMED && today.isBefore(reservation.checkIn)
}

/**
 * Entrant reservation actions, using the existing entrant-scoped Firestore repository API.
 * The repository enforces ownership and frees nights in a transaction; this controller
 * only controls presentation state and handles errors (Flow 10, #127).
 */
class EntrantReservationsController(
    private val repository: ReservationRepository,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EntrantReservationsUiState(today = currentDate()))
    /** Live state shared by the tab root and pushed details route. */
    val state: StateFlow<EntrantReservationsUiState> = mutableState.asStateFlow()
    private var listener: Job? = null

    init { observe() }

    /** Refreshes date-based groups after returning to the app. */
    fun onResume() { mutableState.update { it.copy(today = currentDate()) } }

    /** Switches between Active and Past without reloading the feed. */
    fun onTabSelected(tab: EntrantReservationTab) {
        mutableState.update { it.copy(selectedTab = tab) }
    }

    /** Opens confirmation only for an eligible reservation in this entrant's feed. */
    fun onCancelRequested(id: String) {
        mutableState.update { current ->
            val reservation = current.reservation(id)
            if (reservation != null && current.canCancel(reservation) && current.cancellingId == null) {
                current.copy(confirmingId = id, cancelError = null)
            } else current
        }
    }

    /** Keeps the reservation and closes its confirmation. */
    fun onCancelDismissed() {
        mutableState.update { if (it.cancellingId == null) it.copy(confirmingId = null, cancelError = null) else it }
    }

    /** Cancels through the repository after confirmation; a live feed updates both lists. */
    fun onCancelConfirmed() {
        val current = mutableState.value
        val id = current.confirmingId ?: return
        val reservation = current.reservation(id) ?: run {
            mutableState.update { it.copy(confirmingId = null, cancelError = EntrantCancellationError.NOT_FOUND) }
            return
        }
        if (current.cancellingId != null) return
        if (!current.canCancel(reservation)) {
            mutableState.update { it.copy(confirmingId = null,
                cancelError = EntrantCancellationError.NOT_ALLOWED) }
            return
        }
        mutableState.update { it.copy(cancellingId = id, cancelError = null) }
        viewModelScope.launch {
            try {
                repository.cancel(id)
                mutableState.update { it.copy(confirmingId = null, cancellingId = null,
                    cancelError = null, cancelledId = id) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: ReservationException) {
                val error = when (failure.reason) {
                    ReservationException.Reason.CANT_CHANGE -> EntrantCancellationError.NOT_ALLOWED
                    ReservationException.Reason.NO_SUCH_RESERVATION -> EntrantCancellationError.NOT_FOUND
                    else -> EntrantCancellationError.LOAD_FAILED
                }
                mutableState.update { it.copy(cancellingId = null, cancelError = error) }
            } catch (_: Exception) {
                mutableState.update { it.copy(cancellingId = null,
                    cancelError = EntrantCancellationError.LOAD_FAILED) }
            }
        }
    }

    /** Clears the one-time result after its snackbar has been shown. */
    fun onCancelResultShown() { mutableState.update { it.copy(cancelledId = null) } }

    /** Restarts the live feed after a load failure. */
    fun onRetry() = observe()

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            repository.myReservations()
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    mutableState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { reservations ->
                    mutableState.update { it.copy(reservations = reservations,
                        isLoading = false, loadFailed = false) }
                }
        }
    }

    companion object {
        /** Supplies the existing reservation repository from the application container. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                EntrantReservationsController(app.container.reservationRepository)
            }
        }
    }
}
