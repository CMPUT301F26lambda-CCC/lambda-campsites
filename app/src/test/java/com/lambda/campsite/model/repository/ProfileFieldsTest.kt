/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in October 2026. It helped with the test cases and the Kotlin
 * syntax. Sashreek Addanki reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [ProfileFields], how a profile is stored in Firestore. */
class ProfileFieldsTest {

    private val profile = Profile(
        deviceId = "device-1",
        name = "Jordan Lee",
        email = "jordan@example.com",
        phone = "780-555-0100",
        roles = setOf(Role.ENTRANT, Role.OWNER),
        notificationsOn = false,
        policyVersionAgreed = 2,
        ownerBanned = false,
        removed = false,
        pushToken = "token-1",
    )

    @Test
    fun `a profile reads back the same after it's stored`() {
        val fields = ProfileFields.toMap(profile)

        assertEquals(profile, ProfileFields.fromMap("device-1", fields))
    }

    @Test
    fun `roles are stored by name`() {
        val fields = ProfileFields.toMap(profile)

        assertEquals(listOf("ENTRANT", "OWNER"), fields[ProfileFields.ROLES])
    }

    @Test
    fun `missing fields read as the profile defaults`() {
        val read = ProfileFields.fromMap("device-1", emptyMap())

        assertEquals(Profile(deviceId = "device-1", name = "", email = ""), read)
    }

    @Test
    fun `role names the app doesn't know are skipped`() {
        val read = ProfileFields.fromMap("device-1", mapOf(ProfileFields.ROLES to listOf("OWNER", "SUPERUSER")))

        assertEquals(setOf(Role.ENTRANT, Role.OWNER), read.roles)
    }

    @Test
    fun `roles always include entrant`() {
        val read = ProfileFields.fromMap("device-1", mapOf(ProfileFields.ROLES to listOf("OWNER")))

        assertEquals(setOf(Role.ENTRANT, Role.OWNER), read.roles)
    }

    @Test
    fun `a policy version stored as a Long reads back as an Int`() {
        val read = ProfileFields.fromMap("device-1", mapOf(ProfileFields.POLICY_VERSION_AGREED to 3L))

        assertEquals(3, read.policyVersionAgreed)
    }
}
