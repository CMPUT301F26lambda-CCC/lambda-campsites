/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for loading campsites, observing repository changes,
 * error handling and coroutines rules. Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Campsite
import com.lambda.campsite.model.repository.FakeCampsiteRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [OwnerCampsiteListController]: observing the owner's campsites (US 02.03.01). */
class OwnerCampsiteListControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val camp1 = Campsite(id = "c1", ownerId = "owner-1", name = "Aspen Hollow", location = "Hinton, AB")
    private val camp2 = Campsite(id = "c2", ownerId = "owner-1", name = "Cedar Point", location = "Jasper, AB")
    private val otherCamp = Campsite(id = "c3", ownerId = "other-owner", name = "Foreign Site", location = "Banff, AB")

    @Test
    fun `state displays campsites belonging to the current owner`() {
        val repository = FakeCampsiteRepository(myUserId = "owner-1", initial = listOf(camp1, camp2, otherCamp))
        val controller = OwnerCampsiteListController(repository)

        val state = controller.state.value
        assertEquals(2, state.campsites.size)
        assertEquals(listOf(camp1, camp2), state.campsites)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `state reflects new campsites added elsewhere`() {
        val repository = FakeCampsiteRepository(myUserId = "owner-1", initial = listOf(camp1))
        val controller = OwnerCampsiteListController(repository)

        assertEquals(1, controller.state.value.campsites.size)

        repository.changeElsewhere(camp2)
        assertEquals(2, controller.state.value.campsites.size)
    }

    @Test
    fun `empty repository results in empty list without error`() {
        val repository = FakeCampsiteRepository(myUserId = "owner-1", initial = emptyList())
        val controller = OwnerCampsiteListController(repository)

        assertTrue(controller.state.value.campsites.isEmpty())
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `onErrorShown resets error state`() {
        val repository = FakeCampsiteRepository(myUserId = "owner-1", initial = listOf(camp1))
        val controller = OwnerCampsiteListController(repository)

        controller.onErrorShown()
        assertNull(controller.state.value.error)
    }
}
