/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [Availability]: nights, free dates and the season (US 01.04.01, 02.01.02). */
class AvailabilityTest {

    // Booked: the nights of July 10 and 11, so that stay checked out on July 12.
    private val availability = Availability(
        seasonStart = date("2027-05-15"),
        seasonEnd = date("2027-09-30"),
        bookedNights = setOf(date("2027-07-10"), date("2027-07-11")),
    )

    @Test
    fun `a stay covers the nights from check-in up to check-out`() {
        val nights = availability.nights(date("2027-07-02"), date("2027-07-04"))

        assertEquals(listOf(date("2027-07-02"), date("2027-07-03")), nights)
    }

    @Test
    fun `check-out on or before check-in is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            availability.nights(date("2027-07-04"), date("2027-07-04"))
        }
    }

    @Test
    fun `a stay away from every booking is free`() {
        assertTrue(availability.isFree(date("2027-07-02"), date("2027-07-04")))
    }

    @Test
    fun `a stay that overlaps a booked night isn't free`() {
        assertFalse(availability.isFree(date("2027-07-09"), date("2027-07-11")))
    }

    @Test
    fun `a stay that checks out the day a booking starts is free`() {
        assertTrue(availability.isFree(date("2027-07-08"), date("2027-07-10")))
    }

    @Test
    fun `a stay that checks in the day a booking checks out is free`() {
        assertTrue(availability.isFree(date("2027-07-12"), date("2027-07-14")))
    }

    @Test
    fun `a stay that starts before the season opens isn't free`() {
        assertFalse(availability.isFree(date("2027-05-14"), date("2027-05-16")))
    }

    @Test
    fun `a stay can check out on the closing day but not after it`() {
        assertTrue(availability.isFree(date("2027-09-28"), date("2027-09-30")))
        assertFalse(availability.isFree(date("2027-09-29"), date("2027-10-01")))
    }

    @Test
    fun `a season has to close after it opens`() {
        assertTrue(availability.hasValidSeason())
        assertFalse(availability.copy(seasonEnd = date("2027-05-15")).hasValidSeason())
    }

    private fun date(text: String): LocalDate = LocalDate.parse(text)
}