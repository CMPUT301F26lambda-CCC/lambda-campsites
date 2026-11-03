/* Feature owner: Lalith Valluru. Purpose: US 02.09.01 authorized contact access.
 * AI use: OpenAI GPT-6 through Codex assisted with repository and mapping. Human review pending. */
package com.lambda.campsite.model.repository

import com.google.firebase.functions.FirebaseFunctions
import com.lambda.campsite.model.WaitingListContact
import kotlinx.coroutines.tasks.await

/** Loads minimal contacts for current registrations; the server checks owner access on every call. */
fun interface WaitingListContactRepository {
    /** Loads [entrantIds] in [lotteryId], omitting removed registrations; throws on denied/failed reads. */
    suspend fun contacts(lotteryId: String, entrantIds: List<String>): Map<String, WaitingListContact>
}

/**
 * Uses the authenticated callable, never direct reads of another user's private profile.
 * @param functions configured Firebase Functions instance (local emulator in tests).
 */
class CallableWaitingListContactRepository(private val functions: FirebaseFunctions) : WaitingListContactRepository {
    override suspend fun contacts(lotteryId: String, entrantIds: List<String>): Map<String, WaitingListContact> {
        val contacts = mutableMapOf<String, WaitingListContact>()
        entrantIds.distinct().chunked(100).forEach { batch ->
            val result = functions.getHttpsCallable("owner_waiting_list_contacts")
                .call(mapOf("lotteryId" to lotteryId, "entrantIds" to batch)).await().data as? Map<*, *>
                ?: error("Invalid waiting-list contact response")
            val rows = result["contacts"] as? List<*> ?: error("Missing waiting-list contacts")
            rows.forEach { value ->
                val row = value as? Map<*, *> ?: error("Invalid contact")
                val id = row["entrantId"] as? String ?: error("Missing entrant ID")
                check(id in batch) { "Unexpected entrant ID" }
                contacts[id] = WaitingListContact(id, row["name"] as? String ?: "",
                    row["email"] as? String, row["phone"] as? String)
            }
        }
        return contacts
    }
}
