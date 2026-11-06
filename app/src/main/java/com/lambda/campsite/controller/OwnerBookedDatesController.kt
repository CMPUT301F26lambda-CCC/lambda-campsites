/*
 * Purpose: Maintains the selected owner campsite calendar and its live booked-date state for Flow 11.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, helped implement the live owner
 * booking calendar state and reservation lookup for Flow 11 (US 02.06.01).
 * Human review is pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.Reservation
import com.lambda.campsite.model.ReservationStatus
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.ReservationRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for one campsite's booked-date calendar (US 02.06.01).
 *
 * @property campsite the campsite the owner selected.
 * @property campsites the owner's current choices for the Figma campsite field.
 * @property selectedCampsiteId the choice whose calendar is shown.
 * @property availability the live calendar; its booked nights are authoritative.
 * @property reservations the campsite's live reservation details.
 * @property month the month currently shown.
 * @property selectedReservationId details opened from a booked date.
 * @property selectedDate the booked night tapped in the calendar.
 * @property isLoading whether the initial data is still pending.
 * @property loadFailed whether a repository listener failed.
 */
data class OwnerBookedDatesUiState(
    val campsite: Campsite? = null,
    val campsites: List<Campsite> = emptyList(),
    val selectedCampsiteId: String? = null,
    val availability: Availability? = null,
    val reservations: List<Reservation> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val selectedReservationId: String? = null,
    val selectedDate: LocalDate? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    /** Nights booked in the displayed month. */
    val bookedThisMonth: Set<LocalDate>
        get() = availability?.bookedNights?.filterTo(mutableSetOf()) { YearMonth.from(it) == month }
            ?: emptySet()

    /** An unbooked night inside the configured season; check-out day is not an open night. */
    fun isAvailableNight(date: LocalDate): Boolean = availability?.let { calendar ->
        calendar.hasValidSeason() && calendar.isFree(date, date.plusDays(1))
    } ?: false

    /** Current details, updated if the reservation changes elsewhere. */
    val selectedReservation: Reservation?
        get() = reservations.firstOrNull { it.id == selectedReservationId && it.status == ReservationStatus.CONFIRMED }
}

/**
 * ViewModel for an owner's campsite calendar (US 02.06.01).
 * It combines the campsite, availability document and reservation listener.
 */
class OwnerBookedDatesController(
    private val initialCampsiteId: String?,
    private val campsiteRepository: CampsiteRepository,
    private val reservationRepository: ReservationRepository,
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(OwnerBookedDatesUiState(month = initialMonth))
    /** State read by the calendar screen. */
    val state: StateFlow<OwnerBookedDatesUiState> = mutableState.asStateFlow()
    private var ownerListener: Job? = null
    private var calendarListener: Job? = null

    init { observe() }

    /** Shows the preceding month. */
    fun onPreviousMonth() { mutableState.update { it.copy(month = it.month.minusMonths(1), selectedReservationId = null, selectedDate = null) } }

    /** Shows the following month. */
    fun onNextMonth() { mutableState.update { it.copy(month = it.month.plusMonths(1), selectedReservationId = null, selectedDate = null) } }

    /** Changes to another campsite the current owner owns. */
    fun onCampsiteSelected(id: String) {
        val current = mutableState.value
        if (id == current.selectedCampsiteId || current.campsites.none { it.id == id }) return
        mutableState.update {
            it.copy(selectedCampsiteId = id, campsite = current.campsites.first { camp -> camp.id == id },
                availability = null, reservations = emptyList(), selectedReservationId = null,
                selectedDate = null, isLoading = true, loadFailed = false)
        }
        observeSelected(id)
    }

    /** Opens the confirmed reservation covering [date], if it is booked. */
    fun onBookedDateSelected(date: LocalDate) {
        mutableState.update { current ->
            if (date !in current.bookedThisMonth) return@update current
            val reservation = current.reservations.firstOrNull {
                it.status == ReservationStatus.CONFIRMED &&
                    !date.isBefore(it.checkIn) && date.isBefore(it.checkOut)
            }
            current.copy(selectedReservationId = reservation?.id, selectedDate = date.takeIf { reservation != null })
        }
    }

    /** Closes the reservation details. */
    fun onDetailsDismissed() { mutableState.update { it.copy(selectedReservationId = null, selectedDate = null) } }

    /** Restarts the owner-campsite and selected-calendar listeners after a failure. */
    fun onRetry() { observe() }

    private fun observe() {
        ownerListener?.cancel()
        calendarListener?.cancel()
        val preferredId = mutableState.value.selectedCampsiteId ?: initialCampsiteId
        mutableState.update { it.copy(selectedCampsiteId = null, campsite = null, availability = null,
            reservations = emptyList(), isLoading = true, loadFailed = false) }
        ownerListener = viewModelScope.launch {
            campsiteRepository.myCampsites()
                .catch { mutableState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { owned ->
                    val selectedId = mutableState.value.selectedCampsiteId?.takeIf { id -> owned.any { it.id == id } }
                        ?: preferredId?.takeIf { id -> owned.any { it.id == id } }
                        ?: owned.firstOrNull()?.id
                    val selected = owned.firstOrNull { it.id == selectedId }
                    val changed = selectedId != mutableState.value.selectedCampsiteId
                    mutableState.update { it.copy(campsites = owned, selectedCampsiteId = selectedId,
                        campsite = selected, availability = if (changed) null else it.availability,
                        reservations = if (changed) emptyList() else it.reservations,
                        isLoading = if (selectedId == null) false else if (changed) true else it.isLoading,
                        selectedReservationId = if (changed) null else it.selectedReservationId,
                        selectedDate = if (changed) null else it.selectedDate) }
                    if (changed) observeSelected(selectedId)
                }
        }
    }

    private fun observeSelected(id: String?) {
        calendarListener?.cancel()
        if (id == null) {
            mutableState.update { it.copy(availability = null, reservations = emptyList(), isLoading = false) }
            return
        }
        calendarListener = viewModelScope.launch {
            combine(campsiteRepository.availability(id), reservationRepository.campsiteReservations(id)) {
                availability, reservations -> availability to reservations
            }.catch { mutableState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { (availability, reservations) ->
                    mutableState.update { current ->
                        if (current.selectedCampsiteId != id) current else current.copy(
                            availability = availability, reservations = reservations,
                            isLoading = false, loadFailed = false)
                    }
                }
        }
    }

    companion object {
        /** Builds the controller for [campsiteId], or the first owned campsite when null. */
        fun createFactory(campsiteId: String? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                OwnerBookedDatesController(
                    campsiteId,
                    app.container.campsiteRepository,
                    app.container.reservationRepository,
                )
            }
        }
    }
}
