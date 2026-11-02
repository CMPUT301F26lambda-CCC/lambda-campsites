/*
 * Purpose: the model layer's interface for loading and saving this device's
 * profile. Controllers depend on it instead of on Firestore (the repository
 * pattern), so unit tests can swap in FakeProfileRepository.
 *
 * Outstanding issues: None known.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped update the KDoc when the profile moved to Firestore.
 * Sashreek Addanki reviewed it and committed it.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add savePushToken, on November 1, 2026, and say it never
 * creates the profile, on November 2, 2026. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Loads and saves the profile for this device.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [FirestoreProfileRepository], and unit tests use a fake.
 */
interface ProfileRepository {

    /**
     * The profile for this device. Sends nothing until it has loaded, then a new
     * value every time the profile changes.
     */
    fun currentProfile(): Flow<Profile>

    /**
     * Saves [profile], replacing the stored one.
     *
     * @throws Exception if the profile can't be saved, for example when the phone is offline.
     */
    suspend fun save(profile: Profile)

    /**
     * Saves [token] as this device's push token, changing only that field, so
     * it can't undo a profile change made at the same moment (US 01.10.01).
     * `sendPush` sends this device's pushes to it. It never creates the
     * profile, so it fails if the profile doesn't exist yet.
     *
     * @throws Exception if the token can't be saved, for example when the
     *   profile doesn't exist yet or the phone is offline.
     */
    suspend fun savePushToken(token: String)
}
