/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the pattern in
 * FakeCampsiteRepository. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [LotteryRepository] for unit tests. It keeps lotteries and waiting-list
 * entries in memory, records every save, and can be told to fail like an
 * offline phone would.
 *
 * @param myUserId the user ID [myLotteries] and [myEntries] treat as this
 *   device's.
 * @param initial the lotteries to start with.
 * @param initialEntries the waiting-list entries to start with, in any
 *   lottery.
 */
class FakeLotteryRepository(
    private val myUserId: String = "user-1",
    initial: List<Lottery> = emptyList(),
    initialEntries: List<LotteryEntry> = emptyList(),
) : LotteryRepository {

    private val stored = MutableStateFlow(initial.associateBy { it.id })
    private val entries = MutableStateFlow(initialEntries)
    private var nextId = 1

    /** Every lottery passed to [save], in order. */
    val saved = mutableListOf<Lottery>()

    /** When `true`, [save] throws as if the phone were offline. */
    var failSaves = false

    override fun lottery(id: String): Flow<Lottery?> = stored.map { it[id] }

    override fun lotteriesFor(campsiteId: String): Flow<List<Lottery>> =
        stored.map { lotteries -> lotteries.values.filter { campsiteId in it.campsiteIds } }

    override fun myLotteries(): Flow<List<Lottery>> =
        stored.map { lotteries -> lotteries.values.filter { it.ownerId == myUserId } }

    override fun newLotteryId(): String = "lottery-${nextId++}"

    override suspend fun save(lottery: Lottery) {
        if (failSaves) throw IOException("Offline")
        saved += lottery
        // Like the Firestore version, saving keeps the stored entry count.
        stored.update { current ->
            val entryCount = current[lottery.id]?.entryCount ?: 0
            current + (lottery.id to lottery.copy(entryCount = entryCount))
        }
    }

    override fun waitingList(lotteryId: String): Flow<List<LotteryEntry>> =
        entries.map { all -> all.filter { it.lotteryId == lotteryId }.sortedBy { it.joinedAt } }

    override fun myEntries(): Flow<List<LotteryEntry>> =
        entries.map { all -> all.filter { it.entrantId == myUserId } }

    /** Changes or adds a lottery without a save, like the draw marking it DRAWN. */
    fun changeElsewhere(lottery: Lottery) {
        stored.update { it + (lottery.id to lottery) }
    }

    /**
     * Changes or adds a waiting-list entry without a save, like another
     * entrant joining or the draw setting a result.
     */
    fun changeEntryElsewhere(entry: LotteryEntry) {
        entries.update { all ->
            all.filterNot { it.lotteryId == entry.lotteryId && it.entrantId == entry.entrantId } + entry
        }
    }
}
