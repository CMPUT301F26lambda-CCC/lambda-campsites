/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the JUnit and
 * Kotlin syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [Profile]: roles and becoming an owner (US 03.07.01, US 03.11.01). */
class ProfileTest {

    private val entrant = Profile(deviceId = "device-1", name = "Jordan Lee", email = "jordan@example.com")

    @Test
    fun `a new profile is only an entrant`() {
        assertEquals(setOf(Role.ENTRANT), entrant.roles)
    }

    @Test
    fun `hasRole is true only for roles the profile holds`() {
        assertTrue(entrant.hasRole(Role.ENTRANT))
        assertFalse(entrant.hasRole(Role.OWNER))
        assertFalse(entrant.hasRole(Role.ADMIN))
    }

    @Test
    fun `becomeOwner adds the owner role and keeps the others`() {
        val owner = entrant.becomeOwner()

        assertEquals(setOf(Role.ENTRANT, Role.OWNER), owner.roles)
    }

    @Test
    fun `becomeOwner fails when an admin banned the profile`() {
        val banned = entrant.copy(ownerBanned = true)

        assertThrows(IllegalStateException::class.java) { banned.becomeOwner() }
    }
}
