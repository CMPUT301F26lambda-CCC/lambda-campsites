/*
 * Purpose: Checks campsite edit prefilling, validation, and saving.
 * Outstanding issues: None known.
 */
// AI use: For issue #150, GPT-6 (OpenAI), through Codex, assisted me with this follow-up.
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for pre-filled data, editing fields, saving changes
 * while retaining the same campsite ID (US 02.01.03), and offline failure handling.
 * Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.model.repository.CampsiteRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import com.lambda.campsite.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerCampsiteEditController]: editing existing campsite details (US 02.01.03). */
class OwnerCampsiteEditControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val original = Campsite(
        id = "site-original-999",
        ownerId = "owner-1",
        name = "Original Name",
        location = "Original Location",
        description = "Original Description",
        maxOccupancy = 2,
    )

    @Test
    fun `initializes with existing campsite fields prefilled`() {
        val repository = FakeCampsiteRepository(initial = listOf(original))
        val controller = OwnerCampsiteEditController("site-original-999", repository)

        val state = controller.state.value
        assertEquals("Original Name", state.name)
        assertEquals("Original Location", state.location)
        assertEquals("Original Description", state.description)
        assertEquals(2, state.maxOccupancy)
        assertFalse(state.isLoading)
    }

    @Test
    fun `saving keeps the same campsite ID so the QR code still works`() {
        val repository = FakeCampsiteRepository(initial = listOf(original))
        val controller = OwnerCampsiteEditController("site-original-999", repository)

        controller.onNameChange("Updated Name")
        controller.onOccupancyChange(6)

        var savedSuccess = false
        controller.onSave { savedSuccess = true }

        assertTrue(savedSuccess)
        assertEquals(1, repository.saved.size)
        val saved = repository.saved.first()
        assertEquals("site-original-999", saved.id)
        assertEquals("owner-1", saved.ownerId)
        assertEquals("Updated Name", saved.name)
        assertEquals(6, saved.maxOccupancy)
    }

    @Test
    fun `offline save updates saveFailed state`() {
        val repository = FakeCampsiteRepository(initial = listOf(original)).apply { failSaves = true }
        val controller = OwnerCampsiteEditController("site-original-999", repository)

        controller.onNameChange("Another Name")

        var savedSuccess = false
        controller.onSave { savedSuccess = true }

        assertFalse(savedSuccess)
        assertTrue(controller.state.value.saveFailed)
    }

    @Test
    fun `edit load failure stops spinner and retry restores fields`() {
        val base = FakeCampsiteRepository(initial = listOf(original))
        var fail = true
        val repository = object : CampsiteRepository by base {
            override fun campsite(id: String): Flow<Campsite?> = flow {
                if (fail) throw IOException("Offline")
                emitAll(base.campsite(id))
            }
        }
        val controller = OwnerCampsiteEditController(original.id, repository)
        assertTrue(controller.state.value.loadFailed)
        assertFalse(controller.state.value.isLoading)
        fail = false
        controller.retryLoad()
        assertFalse(controller.state.value.loadFailed)
        assertEquals(original.name, controller.state.value.name)
    }

    @Test
    fun `editing persists price and rules with original campsite ID`() {
        val repository = FakeCampsiteRepository(initial = listOf(original))
        val controller = OwnerCampsiteEditController(original.id, repository)
        controller.onPriceChange("42.00")
        controller.onRulesChange("Quiet after 10 pm")
        controller.onSave { }
        val saved = repository.saved.single()
        assertEquals(original.id, saved.id)
        assertEquals(42.0, saved.pricePerNight!!, 0.0)
        assertEquals("Quiet after 10 pm", saved.rules)
    }
}
