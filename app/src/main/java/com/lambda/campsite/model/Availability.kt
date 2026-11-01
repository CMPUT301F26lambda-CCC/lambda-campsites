/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the Availability class in the team's
 * class diagram. It helped with the Kotlin code, syntax and KDoc. Sashreek
 * Addanki reviewed it.
 */
package com.lambda.campsite.model

import java.time.LocalDate

/**
 * When a campsite is open and which nights are already booked
 * (US 02.01.02, 02.06.01).
 *
 * A stay covers the nights from check-in up to, but not including, check-out.
 * Checking in on July 2 and out on July 4 covers the nights of July 2 and
 * July 3, so another guest can check in on July 4.
 *
 * The owner sets the season. Booking a stay adds its nights to
 * [bookedNights], and cancelling or rejecting it takes them out again.
 *
 * @property seasonStart the first night guests can stay.
 * @property seasonEnd the day the season closes. Guests can check out that
 *   day, so the last night they can stay is the night before.
 * @property bookedNights every night that's already reserved.
 */
data class Availability(
    val seasonStart: LocalDate,
    val seasonEnd: LocalDate,
    val bookedNights: Set<LocalDate> = emptySet(),
) {

    /** Returns `true` if the season closes after it opens. The owner's form checks this before saving. */
    fun hasValidSeason(): Boolean = seasonEnd.isAfter(seasonStart)

    /**
     * The nights a stay from [checkIn] to [checkOut] covers, in order.
     *
     * @throws IllegalArgumentException if [checkOut] isn't after [checkIn].
     */
    fun nights(checkIn: LocalDate, checkOut: LocalDate): List<LocalDate> {
        require(checkOut.isAfter(checkIn)) { "Check-out has to be after check-in." }
        return generateSequence(checkIn) { it.plusDays(1) }.takeWhile { it.isBefore(checkOut) }.toList()
    }

    /**
     * Returns `true` if a stay from [checkIn] to [checkOut] fits inside the
     * season and none of its nights are booked (US 01.04.01).
     *
     * @throws IllegalArgumentException if [checkOut] isn't after [checkIn].
     */
    fun isFree(checkIn: LocalDate, checkOut: LocalDate): Boolean =
        !checkIn.isBefore(seasonStart) &&
                !checkOut.isAfter(seasonEnd) &&
                nights(checkIn, checkOut).none { it in bookedNights }
}