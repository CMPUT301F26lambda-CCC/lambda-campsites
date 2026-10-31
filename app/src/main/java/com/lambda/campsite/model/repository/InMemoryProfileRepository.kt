/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the profile in memory, so the app runs before Firebase is set up.
 *
 * It starts with a sample profile that holds all three roles, so the team can
 * try every nav bar. Data is lost when the app closes. Once Firebase is ready,
 * [com.lambda.campsite.AppContainer] creates a Firestore version instead.
 *
 * @param initial the profile to start with.
 */
class InMemoryProfileRepository(
    initial: Profile = SAMPLE_PROFILE,
) : ProfileRepository {

    private val stored = MutableStateFlow(initial)

    override fun currentProfile(): Flow<Profile> = stored.asStateFlow()

    override suspend fun save(profile: Profile) {
        stored.value = profile
    }

    companion object {
        /** A sample entrant who also holds the owner and admin roles. */
        val SAMPLE_PROFILE = Profile(
            deviceId = "sample-device",
            name = "Jordan Lee",
            email = "jordan.lee@example.com",
            roles = setOf(Role.ENTRANT, Role.OWNER, Role.ADMIN),
        )
    }
}
