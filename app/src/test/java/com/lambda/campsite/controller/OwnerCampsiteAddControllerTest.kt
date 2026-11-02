/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for required field validation, updating form fields,
 * saving with new ID, saving seasons, and offline failure handling. Raghav Sethi
 * reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.CampsiteRequiredField
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerCampsiteAddController]: validation and registering a campsite (US 02.01.01, US 02.01.02). */
class OwnerCampsiteAddControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state has empty fields and occupancy of 1`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository)

        val state = controller.state.value
        assertEquals("", state.name)
        assertEquals("", state.location)
        assertEquals(1, state.maxOccupancy)
        assertTrue(state.errors.isEmpty())
        assertFalse(state.isSaving)
        assertFalse(state.saveFailed)
    }

    @Test
    fun `saving blank campsite populates required field errors and halts save`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository)

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNull(savedCampsite)
        assertTrue(repository.saved.isEmpty())
        val errors = controller.state.value.errors
        assertTrue(CampsiteRequiredField.NAME in errors)
        assertTrue(CampsiteRequiredField.LOCATION in errors)
    }

    @Test
    fun `filling in valid details saves campsite with generated ID and persists season`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository)

        controller.onNameChange("Pine Ridge Campsite")
        controller.onLocationChange("Kananaskis Country, AB")
        controller.onDescriptionChange("Quiet lakeside setting.")
        controller.onOccupancyChange(4)
        controller.onLocationPinSet(51.0, -115.0, "Kananaskis")
        controller.onSeasonChange(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 10, 15))
        controller.onAddTag("lakeside")

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNotNull(savedCampsite)
        assertEquals(1, repository.saved.size)
        val stored = repository.saved.first()
        assertEquals("Pine Ridge Campsite", stored.name)
        assertEquals("Kananaskis Country, AB", stored.location)
        assertEquals(4, stored.maxOccupancy)
        assertEquals(listOf("lakeside"), stored.tags)
        assertFalse(controller.state.value.saveFailed)
    }

    @Test
    fun `offline repository save records failure flag in state`() {
        val repository = FakeCampsiteRepository().apply { failSaves = true }
        val controller = OwnerCampsiteAddController(repository)

        controller.onNameChange("Rocky Mountain Site")
        controller.onLocationChange("Banff, AB")

        var savedCampsite: Campsite? = null
        controller.onSave { savedCampsite = it }

        assertNull(savedCampsite)
        assertTrue(controller.state.value.saveFailed)
        assertFalse(controller.state.value.isSaving)
    }

    @Test
    fun `tag addition and removal update draft state correctly`() {
        val repository = FakeCampsiteRepository()
        val controller = OwnerCampsiteAddController(repository)

        controller.onAddTag("forest")
        controller.onAddTag("lake")
        assertEquals(listOf("forest", "lake"), controller.state.value.tags)

        controller.onRemoveTag("forest")
        assertEquals(listOf("lake"), controller.state.value.tags)
    }
}
