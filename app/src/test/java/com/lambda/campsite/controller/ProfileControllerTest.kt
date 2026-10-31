/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the JUnit,
 * coroutines and Kotlin syntax. Sashreek Addanki reviewed them, ran them and
 * committed them.
 */
package com.lambda.campsite.controller

import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import com.lambda.campsite.model.repository.FakeProfileRepository
import com.lambda.campsite.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tests for [ProfileController]: switching roles and becoming an owner (US 03.07.01, US 03.11.01). */
class ProfileControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val entrant = Profile(deviceId = "device-1", name = "Jordan Lee", email = "jordan@example.com")
    private val owner = entrant.copy(roles = setOf(Role.ENTRANT, Role.OWNER))

    @Test
    fun `state shows the profile from the repository as an entrant`() {
        val controller = ProfileController(FakeProfileRepository(entrant))

        assertEquals(entrant, controller.state.value.profile)
        assertEquals(Role.ENTRANT, controller.state.value.activeRole)
    }

    @Test
    fun `switching to a role the profile holds changes the active role`() {
        val controller = ProfileController(FakeProfileRepository(owner))

        controller.onSwitchRole(Role.OWNER)

        assertEquals(Role.OWNER, controller.state.value.activeRole)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `switching to a role the profile lacks shows an error and keeps the role`() {
        val controller = ProfileController(FakeProfileRepository(entrant))

        controller.onSwitchRole(Role.ADMIN)

        assertEquals(Role.ENTRANT, controller.state.value.activeRole)
        assertEquals(ProfileError.ROLE_NOT_HELD, controller.state.value.error)
    }

    @Test
    fun `becoming an owner saves the role and switches to owner`() {
        val repository = FakeProfileRepository(entrant)
        val controller = ProfileController(repository)

        controller.onBecomeOwner()

        assertTrue(repository.saved.last().hasRole(Role.OWNER))
        assertEquals(Role.OWNER, controller.state.value.activeRole)
        assertTrue(controller.state.value.profile!!.hasRole(Role.OWNER))
    }

    @Test
    fun `a banned profile can't become an owner`() {
        val repository = FakeProfileRepository(entrant.copy(ownerBanned = true))
        val controller = ProfileController(repository)

        controller.onBecomeOwner()

        assertEquals(ProfileError.OWNER_BANNED, controller.state.value.error)
        assertTrue(repository.saved.isEmpty())
        assertEquals(Role.ENTRANT, controller.state.value.activeRole)
    }

    @Test
    fun `a failed save shows an error and stays an entrant`() {
        val repository = FakeProfileRepository(entrant).apply { failSaves = true }
        val controller = ProfileController(repository)

        controller.onBecomeOwner()

        assertEquals(ProfileError.SAVE_FAILED, controller.state.value.error)
        assertEquals(Role.ENTRANT, controller.state.value.activeRole)
    }

    @Test
    fun `losing the active role goes back to entrant`() {
        val repository = FakeProfileRepository(owner)
        val controller = ProfileController(repository)
        controller.onSwitchRole(Role.OWNER)

        repository.changeElsewhere(owner.copy(roles = setOf(Role.ENTRANT), ownerBanned = true))

        assertEquals(Role.ENTRANT, controller.state.value.activeRole)
    }

    @Test
    fun `onErrorShown clears the error`() {
        val controller = ProfileController(FakeProfileRepository(entrant))
        controller.onSwitchRole(Role.ADMIN)

        controller.onErrorShown()

        assertNull(controller.state.value.error)
    }
}
