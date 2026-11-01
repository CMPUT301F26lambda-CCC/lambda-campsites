/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Availability
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * How an [Availability] is stored in its Firestore document,
 * `campsites/{campsiteId}/availability/calendar`.
 *
 * Dates are stored as text like `2027-07-02`, because Firestore has no type
 * for a date without a time. Text in this form sorts in date order, so the
 * security rules can compare two dates.
 *
 * It's separate from the repository so unit tests can check it without
 * Firebase. The field names must match `firestore.rules`.
 */
object AvailabilityFields {

    const val SEASON_START = "seasonStart"
    const val SEASON_END = "seasonEnd"
    const val BOOKED_NIGHTS = "bookedNights"

    /**
     * The fields an owner writes to set the season (US 02.01.02). It leaves
     * out [BOOKED_NIGHTS], because only booking a stay changes those.
     */
    fun seasonFields(seasonStart: LocalDate, seasonEnd: LocalDate): Map<String, Any> = mapOf(
        SEASON_START to seasonStart.toString(),
        SEASON_END to seasonEnd.toString(),
    )

    /**
     * The calendar stored in these [fields], or `null` if either season date
     * is missing or isn't a real date. Booked nights that aren't dates are
     * skipped.
     */
    fun fromMap(fields: Map<String, Any?>): Availability? {
        val seasonStart = date(fields[SEASON_START]) ?: return null
        val seasonEnd = date(fields[SEASON_END]) ?: return null
        val bookedNights = (fields[BOOKED_NIGHTS] as? List<*>).orEmpty().mapNotNull(::date).toSet()
        return Availability(seasonStart, seasonEnd, bookedNights)
    }

    /** The date in [value], if it's text like `2027-07-02`. */
    private fun date(value: Any?): LocalDate? =
        try {
            (value as? String)?.let(LocalDate::parse)
        } catch (e: DateTimeParseException) {
            null
        }
}