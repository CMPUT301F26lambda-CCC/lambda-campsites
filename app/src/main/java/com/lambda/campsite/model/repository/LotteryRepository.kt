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

    /**
     * Adds this device's profile to the waiting list of the lottery with this
     * [lotteryId] (US 01.02.01). It runs as a transaction on the server's
     * latest copy of the lottery, so if two people take the last spot at the
     * same moment, only one of them gets it.
     *
     * @throws WaitingListException if the lottery doesn't exist, registration
     *   isn't open, the list is full, or this device has already joined.
     * @throws Exception if the server can't be reached. A transaction can't
     *   wait for the connection to come back.
     */
    suspend fun join(lotteryId: String)

    /**
     * Takes this device's profile off the waiting list of the lottery with
     * this [lotteryId], while registration is still open (US 01.02.02).
     *
     * @throws WaitingListException if the lottery doesn't exist, registration
     *   has closed, or this device isn't on the list.
     * @throws Exception if the server can't be reached.
     */
    suspend fun leave(lotteryId: String)
}

/**
 * Thrown when [LotteryRepository.join] or [LotteryRepository.leave] isn't
 * allowed. [reason] says why, so the screen can show the right message.
 */
class WaitingListException(val reason: Reason) : Exception("Can't join or leave the waiting list: $reason") {

    /** Why joining or leaving was refused. */
    enum class Reason {

        /** There's no such lottery, or the app can't read it. */
        NO_SUCH_LOTTERY,

        /** Registration hasn't opened yet, has closed, or the lottery has been drawn. */
        REGISTRATION_CLOSED,

        /** The waiting list has reached its limit (US 02.04.07). */
        FULL,

        /** This device is already on the waiting list. */
        ALREADY_JOINED,

        /** This device isn't on the waiting list, so it can't leave it. */
        NOT_JOINED,
    }
}
