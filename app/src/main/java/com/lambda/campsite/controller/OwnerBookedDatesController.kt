/*
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
 * @property availability the live calendar; its booked nights are authoritative.
 * @property reservations the campsite's live reservation details.
 * @property month the month currently shown.
 * @property selectedReservationId details opened from a booked date.
 * @property isLoading whether the initial data is still pending.
 * @property loadFailed whether a repository listener failed.
 */
data class OwnerBookedDatesUiState(
    val campsite: Campsite? = null,
    val availability: Availability? = null,
    val reservations: List<Reservation> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val selectedReservationId: String? = null,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
) {
    /** Nights booked in the displayed month. */
    val bookedThisMonth: Set<LocalDate>
        get() = availability?.bookedNights?.filterTo(mutableSetOf()) { YearMonth.from(it) == month }
            ?: emptySet()

    /** Current details, updated if the reservation changes elsewhere. */
    val selectedReservation: Reservation?
        get() = reservations.firstOrNull { it.id == selectedReservationId && it.status == ReservationStatus.CONFIRMED }
}

/**
 * ViewModel for an owner's campsite calendar (US 02.06.01).
 * It combines the campsite, availability document and reservation listener.
 */
class OwnerBookedDatesController(
    private val campsiteId: String,
    private val campsiteRepository: CampsiteRepository,
    private val reservationRepository: ReservationRepository,
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(OwnerBookedDatesUiState(month = initialMonth))
    /** State read by the calendar screen. */
    val state: StateFlow<OwnerBookedDatesUiState> = mutableState.asStateFlow()
    private var listener: Job? = null

    init { observe() }

    /** Shows the preceding month. */
    fun onPreviousMonth() { mutableState.update { it.copy(month = it.month.minusMonths(1), selectedReservationId = null) } }

    /** Shows the following month. */
    fun onNextMonth() { mutableState.update { it.copy(month = it.month.plusMonths(1), selectedReservationId = null) } }

    /** Opens the confirmed reservation covering [date], if it is booked. */
    fun onBookedDateSelected(date: LocalDate) {
        mutableState.update { current ->
            if (date !in current.bookedThisMonth) return@update current
            val reservation = current.reservations.firstOrNull {
                it.status == ReservationStatus.CONFIRMED &&
                    !date.isBefore(it.checkIn) && date.isBefore(it.checkOut)
            }
            current.copy(selectedReservationId = reservation?.id)
        }
    }

    /** Closes the reservation details. */
    fun onDetailsDismissed() { mutableState.update { it.copy(selectedReservationId = null) } }

    /** Restarts all three listeners after a load failure. */
    fun onRetry() { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            combine(
                campsiteRepository.campsite(campsiteId),
                campsiteRepository.availability(campsiteId),
                reservationRepository.campsiteReservations(campsiteId),
            ) { campsite, availability, reservations -> Triple(campsite, availability, reservations) }
                .catch { mutableState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { (campsite, availability, reservations) ->
                    mutableState.update {
                        it.copy(
                            campsite = campsite,
                            availability = availability,
                            reservations = reservations,
                            isLoading = false,
                            loadFailed = false,
                        )
                    }
                }
        }
    }

    companion object {
        /** Builds the controller for [campsiteId] from the app's repositories. */
        fun createFactory(campsiteId: String): ViewModelProvider.Factory = viewModelFactory {
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
