/*
 * Purpose: text search over published campsite data (US 01.01.01).
 * Outstanding issues: structured filters are a separate feature, Flow 15.
 * AI use: OpenAI GPT-6 through Codex assisted with search logic and KDoc; human review pending.
 */
package com.lambda.campsite.model

import com.lambda.campsite.model.repository.CampsiteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Searches published campsites without introducing a separate stored catalogue.
 * @param repository the source of live, published campsite records.
 */
class CampsiteSearch(private val repository: CampsiteRepository) {
    /** Live results matching [text]; blank searches include all published sites. */
    fun search(text: String): Flow<List<Campsite>> = repository.publishedCampsites().map { sites ->
        sites.filter { it.published && matches(it, text) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    companion object {
        /** Each whitespace-separated search term must occur in the campsite's searchable text. */
        fun matches(campsite: Campsite, text: String): Boolean {
            val searchable = listOf(campsite.name, campsite.location, campsite.description)
                .plus(campsite.amenities).plus(campsite.tags).joinToString(" ")
            return text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                .all { searchable.contains(it, ignoreCase = true) }
        }
    }
}
