/*
 * Purpose: the match score for the WOW factor (#71). It takes the filtered
 * results from CampsiteFilterController and gives each campsite a score from
 * 0% to 100% based on the applied filters, then sorts the results from the
 * highest score to the lowest. It doesn't filter anything itself.
 * How the score works:
 * - Only applied filters count, and each one has the same weight: 1 filter is
 *   100% of the score, 2 filters are 50% each, 3 filters are about 33.3% each.
 * - Location and dates score 100%, because a campsite that passed those
 *   filters in CampsiteFilterController matches them fully.
 * - Guests score 100% for an exact fit and 10% less for each extra space,
 *   down to 50%.
 * - With no filters applied there is no score, and the order doesn't change.
 * Campsites with the same score keep the order CampsiteFilterController gave.
 * Outstanding issues: nearby campsites and partial date matches aren't scored.
 */
/*
AI use: Claude Opus 5.5 (Anthropic) helped Harmanjoth Kaur in score calculation after the
* filters are applied and revising KDoc.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import com.lambda.campsite.model.Campsite
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scored results the Search screen shows (#71).
 * @property results the filtered campsites, from the highest score to the lowest.
 * @property scores each campsite's score from 0 to 100 by campsite ID, or empty
 *   when no filter is applied.
 */
data class MatchScoreUiState(
    val results: List<Campsite> = emptyList(),
    val scores: Map<String, Int> = emptyMap(),
)

/**
 * Gives each filtered campsite a match score and sorts the results by it
 * (#71). The screen passes in the filtered results and the applied filters
 * with [onFilteredResultsChanged] whenever either changes.
 */
class MatchScoreController : ViewModel() {
    private val mutableState = MutableStateFlow(MatchScoreUiState())

    /** State collected by SearchScreen. */
    val state: StateFlow<MatchScoreUiState> = mutableState.asStateFlow()

    /**
     * Scores [results], the campsites that already passed every filter, and
     * sorts them from the highest score to the lowest.
     *
     * @param results the filtered results, in CampsiteFilterController's order.
     * @param hasLocation `true` when a location filter is applied.
     * @param hasDates `true` when a check-in date, a check-out date or both are applied.
     * @param guests the applied number of guests, or `null` for none.
     */
    fun onFilteredResultsChanged(results: List<Campsite>, hasLocation: Boolean, hasDates: Boolean, guests: Int?) {
        val scored = results.map { campsite -> campsite to score(campsite, hasLocation, hasDates, guests) }
        // sortedByDescending is stable, so equal scores keep the filter's order.
        val sorted = scored.sortedByDescending { it.second ?: 0 }
        mutableState.value = MatchScoreUiState(
            results = sorted.map { it.first },
            scores = sorted.mapNotNull { (campsite, score) -> score?.let { campsite.id to it } }.toMap(),
        )
    }

    private fun score(campsite: Campsite, hasLocation: Boolean, hasDates: Boolean, guests: Int?): Int? {
        val parts = buildList {
            if (hasLocation) add(100.0)
            if (hasDates) add(100.0)
            guests?.let { add(guestsScore(campsite.maxOccupancy, it).toDouble()) }
        }
        return if (parts.isEmpty()) null else parts.average().roundToInt()
    }

    private fun guestsScore(maxOccupancy: Int, guests: Int): Int =
        (100 - GUEST_SCORE_STEP * (maxOccupancy - guests)).coerceAtLeast(MIN_GUEST_SCORE)

    companion object {
        const val GUEST_SCORE_STEP = 10
        const val MIN_GUEST_SCORE = 50
    }
}