/*
 * Purpose: Loads and groups owner reservations and tracks selection for Flow 11.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, helped implement the owner reservation
 * state, date-based groups, selection and repository listener for Flow 11
 * (US 02.07.01 to 02.07.03). Human review is pending.
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
import com.lambda.campsite.model.repository.ReservationRepository
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three owner reservation lists from US 02.07.01 to 02.07.03. */
enum class OwnerReservationTab { ACTIVE, PAST, CANCELLED }

/**
 * State for the owner's reservations screen.
 *
 * @property reservations every reservation at the owner's campsites.
 * @property today the device's current date, refreshed when the screen resumes.
 * @property selectedTab the list the owner is viewing.
 * @property selectedReservationId the reservation whose details are open, if any.
 * @property isLoading whether the first repository result is still pending.
 * @property loadFailed whether the live listener failed.
 */
data class OwnerReservationsUiState(
    val reservations: List<Reservation> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val selectedTab: OwnerReservationTab = OwnerReservationTab.ACTIVE,
    val selectedReservationId: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    /** Reservations still ahead or under way, in check-in order. */
    val active: List<Reservation>
        get() = reservations.filter { it.isActive(today) }.sortedBy { it.checkIn }

    /** Confirmed stays whose check-out has passed, newest first. */
    val past: List<Reservation>
        get() = reservations.filter { it.isCompleted(today) }.sortedByDescending { it.checkOut }

    /** Cancelled and owner-rejected stays, as grouped in the Figma Cancelled tab. */
    val cancelled: List<Reservation>
        get() = reservations.filter { it.status == ReservationStatus.CANCELLED || it.status == ReservationStatus.REJECTED }
            .sortedByDescending { it.cancelledAt?.seconds ?: 0L }

    /** The list for [selectedTab]. */
    val visibleReservations: List<Reservation>
        get() = when (selectedTab) {
            OwnerReservationTab.ACTIVE -> active
            OwnerReservationTab.PAST -> past
            OwnerReservationTab.CANCELLED -> cancelled
        }

    /** Current details, updated when the live repository result changes. */
    val selectedReservation: Reservation?
        get() = reservations.firstOrNull { it.id == selectedReservationId }
}

/**
 * ViewModel for the owner's active, past and cancelled reservations (US 02.07.01 to 02.07.03).
 * It listens to [repository] and keeps date and selection rules out of Compose.
 */
class OwnerReservationsController(
    private val repository: ReservationRepository,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OwnerReservationsUiState(today = currentDate()))
    /** State read by the owner reservations screen. */
    val state: StateFlow<OwnerReservationsUiState> = mutableState.asStateFlow()
    private var listener: Job? = null

    init { observe() }

    /** Updates the date when returning to the screen, so stays move to Past after check-out. */
    fun onResume() { mutableState.update { it.copy(today = currentDate()) } }

    /** Shows one of the three lists. */
    fun onTabSelected(tab: OwnerReservationTab) {
        mutableState.update { it.copy(selectedTab = tab, selectedReservationId = null) }
    }

    /** Opens details for a reservation in the current list. */
    fun onReservationSelected(id: String) {
        mutableState.update { current ->
            if (current.visibleReservations.any { it.id == id }) current.copy(selectedReservationId = id)
            else current
        }
    }

    /** Closes the reservation details. */
    fun onDetailsDismissed() { mutableState.update { it.copy(selectedReservationId = null) } }

    /** Restarts the repository listener after a load failure. */
    fun onRetry() { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            repository.ownerReservations()
                .catch { mutableState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { all ->
                    mutableState.update {
                        it.copy(reservations = all, isLoading = false, loadFailed = false)
                    }
                }
        }
    }

    companion object {
        /** Builds the controller with the app's reservation repository. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                OwnerReservationsController(app.container.reservationRepository)
            }
        }
    }
}
