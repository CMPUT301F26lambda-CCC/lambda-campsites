/*
 * Purpose: live text-search state for Flow 6 (US 01.01.01, 01.01.04).
 * Outstanding issues: Flow 15 will add location/date/guest filter controls.
 * AI use: OpenAI GPT-6 through Codex assisted with state/listener logic and KDoc; human review pending.
 */
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lambda.campsite.LambdaApplication
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteSearch
import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search presentation state.
 * @property query user-entered search text, retained across rotation by the controller.
 * @property results published matching sites, ordered by name.
 * @property isLoading waiting for the first repository result.
 * @property loadFailed listener failed; show a retry action, not an empty-results message.
 */
data class SearchUiState(
    val query: String = "", val results: List<Campsite> = emptyList(),
    val isLoading: Boolean = true, val loadFailed: Boolean = false,
)

/**
 * Observes one published catalogue and searches its cached values as the user types.
 * @param repository existing campsite data source; no new Firestore query is made per keystroke.
 */
class SearchController(repository: CampsiteRepository) : ViewModel() {
    private val search = CampsiteSearch(repository)
    private var sites = emptyList<Campsite>()
    private var listener: Job? = null
    private val mutableState = MutableStateFlow(SearchUiState())
    /** State collected by SearchScreen. */
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    init { observe() }

    private fun observe() {
        listener?.cancel()
        mutableState.update { it.copy(isLoading = true, loadFailed = false) }
        listener = viewModelScope.launch {
            search.search("").catch { failure ->
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(isLoading = false, loadFailed = true) }
            }.collect { loaded ->
                sites = loaded
                mutableState.update { it.copy(results = matching(it.query), isLoading = false, loadFailed = false) }
            }
        }
    }

    private fun matching(query: String) = sites.filter { CampsiteSearch.matches(it, query) }

    /** Searches the live catalogue for [query] without discarding the original records. */
    fun onSearch(query: String) {
        mutableState.update { it.copy(query = query, results = matching(query)) }
    }

    /** Reattaches the catalogue listener after a load error, preserving the search query. */
    fun onRetry() = observe()

    companion object {
        /** Uses the same shared repository as the owner's campsite screens. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LambdaApplication
                SearchController(app.container.campsiteRepository)
            }
        }
    }
}
