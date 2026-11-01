/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the Lottery class and the
 * LotteryStatus enumeration in the team's class diagram. It helped with the
 * Kotlin code, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId

/** Where a [Lottery] is in its life (US 02.04.03, 02.05.01). */
enum class LotteryStatus {

    /** Registration hasn't opened yet. It's never stored: [Lottery.statusAt] works it out from the clock. */
    UPCOMING,

    /** Not drawn yet. The app saves every new lottery with this status. */
    OPEN,

    /** The draw has picked the winners. Only the `drawLotteries` Cloud Function sets it. */
    DRAWN,

    /** Cancelled, like when its campsite is removed (US 03.01.01). Only a Cloud Function sets it. */
    ENDED,
}

/**
 * A lottery an owner runs to give out stays at their campsites
 * (US 02.04.01 to 02.04.07).
 *
 * Entrants join its waiting list while registration is open. When
 * registration closes, the `drawLotteries` Cloud Function picks
 * [numberOfWinners] entrants at random, and each winner can book nights
 * between [stayStart] and [stayEnd].
 *
 * Like [Campsite], a lottery never changes in place. Make an edited copy with
 * `copy()` and save it through the repository.
 *
 * The class diagram's `owner: Profile` and `campsites: List<Campsite>` are
 * [ownerId] and [campsiteIds] here, the way Firestore stores them. Its
 * `entries` list is the `entries` subcollection, which the repository loads
 * on its own, so a lottery only keeps [entryCount].
 *
 * @property id the lottery's document ID.
 * @property ownerId the Firebase Auth user ID of the owner who runs it.
 * @property name what entrants see in lists, like "Moose Lake, July 2027".
 * @property campsiteIds the campsites a winner can book. At least one, all
 *   owned by [ownerId] (US 02.04.02).
 * @property registrationOpens when entrants can start joining (US 02.04.03).
 * @property registrationCloses when joining stops and the draw runs
 *   (US 02.04.03, 02.05.01).
 * @property stayStart the first night a winner can book (US 02.04.04).
 * @property stayEnd the last day a winner can check out, like
 *   [Availability.seasonEnd] (US 02.04.04).
 * @property numberOfWinners how many entrants the draw picks (US 02.04.05).
 * @property capacity the most entrants the waiting list takes, or `null` for
 *   no limit (US 02.04.07).
 * @property status whether it's been drawn. The app saves
 *   [LotteryStatus.OPEN], and only Cloud Functions change it.
 * @property entryCount how many entrants are on the waiting list. Joining and
 *   leaving change it in the same transaction as the entry.
 */
data class Lottery(
    val id: String,
    val ownerId: String,
    val name: String,
    val campsiteIds: List<String>,
    val registrationOpens: Timestamp,
    val registrationCloses: Timestamp,
    val stayStart: LocalDate,
    val stayEnd: LocalDate,
    val numberOfWinners: Int = 1,
    val capacity: Int? = null,
    val status: LotteryStatus = LotteryStatus.OPEN,
    val entryCount: Int = 0,
) {

    /**
     * Returns `true` if entrants can join or leave at [now]: from
     * [registrationOpens] up to, but not including, [registrationCloses],
     * and only while the lottery is [LotteryStatus.OPEN] (US 01.02.01, 01.02.02).
     */
    fun isRegistrationOpen(now: Timestamp): Boolean =
        status == LotteryStatus.OPEN && now >= registrationOpens && now < registrationCloses

    /** Returns `true` if the waiting list can take one more entrant (US 02.04.07). */
    fun hasSpace(): Boolean = capacity == null || entryCount < capacity

    /**
     * Returns `true` if the dates fit together (US 02.04.03, 02.04.04).
     * Registration has to close after it opens, the stay has to end after it
     * starts, and registration has to close by midnight at the start of
     * [stayStart] in [zone], so winners hear before their stay begins. The
     * owner's form checks this before saving.
     */
    fun validateDates(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val stayBegins = Timestamp(stayStart.atStartOfDay(zone).toInstant())
        return registrationCloses > registrationOpens &&
            stayEnd.isAfter(stayStart) &&
            registrationCloses <= stayBegins
    }

    /** The status to show at [now]: [LotteryStatus.UPCOMING] before registration opens, otherwise [status]. */
    fun statusAt(now: Timestamp): LotteryStatus =
        if (status == LotteryStatus.OPEN && now < registrationOpens) LotteryStatus.UPCOMING else status
}
