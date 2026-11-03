/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for searching places, pin dragging, coordinate updates
 * and controller factory. Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [MapLocationController]: search and pin position adjustments (US 02.01.02). */
class MapLocationControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state defaults to Elk Island if no coordinates provided`() {
        val controller = MapLocationController()
        val state = controller.state.value

        assertEquals(53.6000, state.latitude, 0.001)
        assertEquals(-112.8667, state.longitude, 0.001)
        assertEquals("Elk Island National Park, AB", state.pinLocationName)
    }

    @Test
    fun `searching query filters suggestions`() {
        val controller = MapLocationController()

        controller.onQueryChange("Jasper")
        val suggestions = controller.state.value.suggestions

        assertEquals(1, suggestions.size)
        assertEquals("Jasper National Park, AB", suggestions.first().name)
    }

    @Test
    fun `selecting place updates pinned coordinates and name`() {
        val controller = MapLocationController()
        val place = PlaceLocation("Banff National Park, AB", 51.4968, -115.9281)

        controller.onPlaceSelected(place)

        val state = controller.state.value
        assertEquals(51.4968, state.latitude, 0.0001)
        assertEquals(-115.9281, state.longitude, 0.0001)
        assertEquals("Banff National Park, AB", state.pinLocationName)
    }

    @Test
    fun `dragging pin updates coordinates by delta`() {
        val controller = MapLocationController(50.0, -110.0, "Test Place")

        controller.onPinDragged(0.05, 0.02)

        val state = controller.state.value
        assertEquals(50.05, state.latitude, 0.0001)
        assertEquals(-109.98, state.longitude, 0.0001)
    }
}
