/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Availability
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for [AvailabilityFields], how a campsite's calendar is stored in Firestore. */
class AvailabilityFieldsTest {

    @Test
    fun `season dates are stored as text`() {
        val fields = AvailabilityFields.seasonFields(date("2027-05-15"), date("2027-09-30"))

        assertEquals(
            mapOf(AvailabilityFields.SEASON_START to "2027-05-15", AvailabilityFields.SEASON_END to "2027-09-30"),
            fields,
        )
    }

    @Test
    fun `a calendar reads back with its season and booked nights`() {
        val fields = mapOf(
            AvailabilityFields.SEASON_START to "2027-05-15",
            AvailabilityFields.SEASON_END to "2027-09-30",
            AvailabilityFields.BOOKED_NIGHTS to listOf("2027-07-10", "2027-07-11"),
        )

        val expected = Availability(
            seasonStart = date("2027-05-15"),
            seasonEnd = date("2027-09-30"),
            bookedNights = setOf(date("2027-07-10"), date("2027-07-11")),
        )
        assertEquals(expected, AvailabilityFields.fromMap(fields))
    }

    @Test
    fun `a calendar with only a season has no booked nights`() {
        val fields = AvailabilityFields.seasonFields(date("2027-05-15"), date("2027-09-30"))

        assertEquals(Availability(date("2027-05-15"), date("2027-09-30")), AvailabilityFields.fromMap(fields))
    }

    @Test
    fun `a missing season date reads as null`() {
        val fields = mapOf(AvailabilityFields.SEASON_START to "2027-05-15")

        assertNull(AvailabilityFields.fromMap(fields))
    }

    @Test
    fun `a season date that isn't a real date reads as null`() {
        val notADay = mapOf(AvailabilityFields.SEASON_START to "2027-05-15", AvailabilityFields.SEASON_END to "2027-02-30")
        val notText = mapOf(AvailabilityFields.SEASON_START to "2027-05-15", AvailabilityFields.SEASON_END to 20270930L)

        assertNull(AvailabilityFields.fromMap(notADay))
        assertNull(AvailabilityFields.fromMap(notText))
    }

    @Test
    fun `booked nights that aren't dates are skipped`() {
        val fields = AvailabilityFields.seasonFields(date("2027-05-15"), date("2027-09-30")) +
                mapOf(AvailabilityFields.BOOKED_NIGHTS to listOf("2027-07-10", "not a date", 5L))

        assertEquals(setOf(date("2027-07-10")), AvailabilityFields.fromMap(fields)?.bookedNights)
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)
}