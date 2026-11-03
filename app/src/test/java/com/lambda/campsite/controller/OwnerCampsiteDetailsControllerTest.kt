/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for loading campsite details, availability, and error handling.
 * Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerCampsiteDetailsController]: viewing details and calendar (US 02.01.04). */
class OwnerCampsiteDetailsControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val camp = Campsite(
        id = "site-101",
        ownerId = "owner-1",
        name = "Aspen Hollow Campsite",
        location = "Hinton – Grande Cache, AB",
        description = "A quiet forest site.",
        maxOccupancy = 8,
    )

    private val availability = Availability(
        seasonStart = LocalDate.of(2027, 5, 1),
        seasonEnd = LocalDate.of(2027, 10, 12),
    )

    @Test
    fun `loads and combines campsite and availability data`() {
        val repository = FakeCampsiteRepository(
            initial = listOf(camp),
            initialCalendars = mapOf("site-101" to availability),
        )
        val controller = OwnerCampsiteDetailsController("site-101", repository)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.campsite)
        assertEquals("Aspen Hollow Campsite", state.campsite?.name)
        assertEquals(8, state.campsite?.maxOccupancy)
        assertEquals(availability, state.availability)
        assertNull(state.error)
    }

    @Test
    fun `missing campsite reports not found error`() {
        val repository = FakeCampsiteRepository(initial = emptyList())
        val controller = OwnerCampsiteDetailsController("unknown-site", repository)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertNull(state.campsite)
        assertNotNull(state.error)
    }
}
