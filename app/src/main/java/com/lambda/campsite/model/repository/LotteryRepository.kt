/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the pattern in CampsiteRepository. It
 * helped with the Kotlin code, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Loads and saves lotteries and their waiting lists.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [FirestoreLotteryRepository], and unit tests use
 * `FakeLotteryRepository`.
 */
interface LotteryRepository {

    /**
     * The lottery with this [id], sent again whenever it changes, like when
     * someone joins (US 01.01.06). Sends `null` if there's no such lottery.
     */
    fun lottery(id: String): Flow<Lottery?>

    /**
     * Every lottery that includes the campsite with this [campsiteId], drawn
     * or not, in no particular order, sent again whenever one changes
     * (US 01.01.06).
     */
    fun lotteriesFor(campsiteId: String): Flow<List<Lottery>>

    /**
     * The lotteries this device's profile runs, in no particular order, sent
     * again whenever one changes (US 02.04.06).
     */
    fun myLotteries(): Flow<List<Lottery>>

    /** A new, unused ID for a lottery being set up (US 02.04.01). */
    fun newLotteryId(): String

    /**
     * Saves [lottery], creating it or replacing its details. The stored
     * entry count stays as it is.
     *
     * @throws Exception if the lottery can't be saved, for example when it
     *   includes a campsite this device's profile doesn't own.
     */
    suspend fun save(lottery: Lottery)

    /**
     * The waiting list of the lottery with this [lotteryId], first to join
     * first, sent again whenever someone joins or leaves (US 02.09.01). Only
     * the lottery's owner can read it.
     */
    fun waitingList(lotteryId: String): Flow<List<LotteryEntry>>

    /**
     * This device's entries on every waiting list it has joined, in no
     * particular order, sent again whenever one changes, like when the draw
     * sets the result.
     */
    fun myEntries(): Flow<List<LotteryEntry>>
}
