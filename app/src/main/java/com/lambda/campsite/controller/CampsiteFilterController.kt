/* Purpose: this controller is connected to the Search screen (SearchScreen.kt).
* The Search screen provides filters based on three criteria: location,
* dates and number of guests (US 01.01.02, 01.01.03). The filters work on
* the search results, so the search text and the filters apply together.
* How the filters work:
* - Location: it shows the campsites whose location contains the text
*   entered in the filter, ignoring capital letters.
* - Dates: there are three cases.
*   1. If the user enters only a check-in date, it displays the campsites
*      that are free on that night, ordered by how many nights in a row are
*      free from that date, up to 7 nights. Shorter stays come first.
*   2. If the user enters only a check-out date, it displays the campsites
*      that are free on the night before, counting back up to 7 days before
*      the check-out date (never before today).
*   3. If the user enters both dates, it displays the campsites that are
*      free for the exact stay first. A campsite that isn't free for the
*      exact stay is shown afterwards if leaving one day earlier is free.
* - Guests: it only shows the campsites whose capacity is equal to or higher
*   than the number of guests.
* This design is made so that the results aren't
* limited to exact matches only.
* Outstanding issues: none known.
*/
/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code in November
 * 2026, assisted Harmanjoth Kaur in refining the ideas and rules about the
 * different type of search combination designed by Harmanjoth Kaur
 * It also helped with kotlin code and syntax, refining KDoc comments.
 * Harmanjoth Kaur reviewed the suggestion,implemented them and committed them.
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
import com.lambda.campsite.model.repository.CampsiteRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Filter state for the entrant search list (US 01.01.02).
 * @property location applied location filter, or blank for any location.
 * @property checkIn applied check-in day, or `null` for any dates.
 * @property checkOut applied check-out day, or `null` for any dates.
 * @property guests applied number of guests, or `null` for any group size.
 * @property results the search results that match every applied filter (US 01.01.03),
 *   best date match first (see [CampsiteFilterController]).
 * @property isLoading waiting for the campsites' calendars after a date filter is applied.
 * @property loadFailed the calendars couldn't load; show a retry action.
 */

data class CampsiteFilterUiState(
    val location: String = "",
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val guests: Int? = null,
    val results: List<Campsite> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
) {
    /** `true` when a check-in day, a check-out day or both are set. */
    val hasDates: Boolean get() = checkIn != null || checkOut != null

    /** `true` when at least one filter is applied, so the screen shows [results]. */
    val hasFilters: Boolean get() = location.isNotBlank() || hasDates || guests != null
}

/**
 * Filters the entrant's search results based on location, dates and number of
 * guests (US 01.01.02). It also updates the results when a filter changes or
 * when a campsite's calendar is updated (US 01.01.03).
 *
 * It uses [SearchController] to get the search results. The screen passes the
 * results through [onSearchResultsChanged], so the search text is still applied.
 * The screen uses [state] to display the filter results, [onApplyFilters] to
 * apply the selected filters and [onClearFilters] to remove all filters and
 * show the original search results again.
 *
 * How the filters work:
 * - Location: Matches campsites whose location contains the entered text,
 *   ignoring uppercase and lowercase letters.
 * - Guests: Matches campsites that can accommodate the selected number of guests.
 * - Dates: Campsites without a season set by the owner are not included.
 * - If a filter is left blank or `null`, it does not filter out any campsites.
 *
 * How date filtering works:
 * - Check-in and check-out: Shows campsites available for the selected dates
 *   first, followed by campsites available if check-out is one day earlier.
 *   The check-in date stays the same.
 * - Only check-in: Checks availability starting from the selected check-in
 *   date, from 1 night up to [MAX_NIGHTS] (7 nights).
 * - Only check-out: Checks availability backwards from the selected check-out
 *   date, but never before today.
 * - Campsites with the same match are kept in the order returned by the search.
 *
 *
 * While the calendars are loading, [CampsiteFilterUiState.isLoading] is `true`.
 * If loading fails, [CampsiteFilterUiState.loadFailed] is `true`, and [onRetry]
 * can be used to try loading the calendars again.
 *
 * [MAX_NIGHTS] sets the maximum number of nights checked when only one date is
 * selected. It is currently set to 7 nights.
 * [Factory] creates the controller using the app's shared campsite repository.
 *
 * @param repository provides the campsite calendars.
 * @param today provides today's date.
 */
class CampsiteFilterController(
    private val repository: CampsiteRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private var searchResults = emptyList<Campsite>()
    private var calendars = emptyMap<String, Availability?>()
    private var calendarListener: Job? = null
    private var watchedIds = emptyList<String>()
    private val mutableState = MutableStateFlow(CampsiteFilterUiState())

    val state: StateFlow<CampsiteFilterUiState> = mutableState.asStateFlow()

    /** Takes the latest search results, so the filters apply to what the entrant searched for. */
    fun onSearchResultsChanged(results: List<Campsite>) {
        searchResults = results
        mutableState.update { it.withResults() }
        // A campsite added or removed needs its calendar watched or dropped.
        if (state.value.hasDates && results.map { it.id } != watchedIds) watchCalendars()
    }

    /**
     * Applies the entrant's filters from the Filters sheet (US 01.01.02) and
     * updates the list straight away (US 01.01.03). Each blank or `null`
     * filter matches every campsite.
     */
    fun onApplyFilters(location: String, checkIn: LocalDate?, checkOut: LocalDate?, guests: Int?) {
        val hadDates = state.value.hasDates
        mutableState.update {
            it.copy(location = location.trim(), checkIn = checkIn, checkOut = checkOut, guests = guests).withResults()
        }
        if (hadDates || state.value.hasDates) watchCalendars()
    }
    fun onClearFilters() = onApplyFilters("", null, null, null)

    fun onRetry() = watchCalendars()
    private fun watchCalendars() {
        calendarListener?.cancel()
        calendarListener = null
        watchedIds = searchResults.map { it.id }
        if (!state.value.hasDates || watchedIds.isEmpty()) {
            calendars = emptyMap()
            mutableState.update { it.copy(isLoading = false, loadFailed = false).withResults() }
            return
        }
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        val calendarFlows = watchedIds.map { id -> repository.availability(id).map { id to it } }
        calendarListener = viewModelScope.launch {
            combine(calendarFlows) { pairs -> pairs.toMap() }.catch { failure ->
                if (failure is CancellationException) throw failure
                watchedIds = emptyList()
                mutableState.update { it.copy(isLoading = false, loadFailed = true) }
            }.collect { loaded ->
                calendars = loaded
                mutableState.update { it.copy(isLoading = false, loadFailed = false).withResults() }
            }
        }
    }
    private fun CampsiteFilterUiState.withResults(): CampsiteFilterUiState {
        val ranked = searchResults.mapNotNull { campsite -> rank(campsite, this)?.let { campsite to it } }
        return copy(results = ranked.sortedBy { it.second }.map { it.first })
    }

    private fun rank(campsite: Campsite, filters: CampsiteFilterUiState): Int? {
        val locationMatches = filters.location.isBlank() ||
                campsite.location.contains(filters.location, ignoreCase = true)
        val guestsFit = filters.guests == null || campsite.maxOccupancy >= filters.guests
        if (!locationMatches || !guestsFit) return null
        val calendar = calendars[campsite.id]
        val checkIn = filters.checkIn
        val checkOut = filters.checkOut
        return when {
            checkIn == null && checkOut == null -> 0
            checkIn != null && checkOut != null -> bothDatesRank(calendar, checkIn, checkOut)
            checkIn != null -> nightsFrom(calendar, checkIn)
            else -> nightsUntil(calendar, checkOut!!)
        }
    }


    private fun bothDatesRank(calendar: Availability?, checkIn: LocalDate, checkOut: LocalDate): Int? = when {
        isFree(calendar, checkIn, checkOut) -> 0
        isFree(calendar, checkIn, checkOut.minusDays(1)) -> 1
        else -> null
    }

    private fun nightsFrom(calendar: Availability?, checkIn: LocalDate): Int? =
        (1..MAX_NIGHTS).lastOrNull { nights -> isFree(calendar, checkIn, checkIn.plusDays(nights.toLong())) }

    private fun nightsUntil(calendar: Availability?, checkOut: LocalDate): Int? {
        var nights = 0
        while (nights < MAX_NIGHTS) {
            val start = checkOut.minusDays(nights + 1L)
            if (start.isBefore(today()) || !isFree(calendar, start, checkOut)) break
            nights++
        }
        return nights.takeIf { it > 0 }
    }

    private fun isFree(calendar: Availability?, checkIn: LocalDate, checkOut: LocalDate): Boolean =
        checkOut.isAfter(checkIn) && calendar?.isFree(checkIn, checkOut) == true

    companion object {
        const val MAX_NIGHTS = 7
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                CampsiteFilterController(app.container.campsiteRepository)
            }
        }
    }
}