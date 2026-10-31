/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Loads and saves the profile for this device.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [InMemoryProfileRepository] until Firebase is set up, and unit
 * tests use a fake.
 */
interface ProfileRepository {

    /** The profile for this device. Sends a new value every time the profile changes. */
    fun currentProfile(): Flow<Profile>

    /**
     * Saves [profile], replacing the stored one.
     *
     * @throws Exception if the profile can't be saved, for example when the phone is offline.
     */
    suspend fun save(profile: Profile)
}
