/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add failLoads when the profile moved to Firestore. Sashreek
 * Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [ProfileRepository] for unit tests. It keeps the profile in memory, records
 * every save, and can be told to fail like an offline phone would.
 *
 * @param initial the profile to start with.
 */
class FakeProfileRepository(initial: Profile) : ProfileRepository {

    private val stored = MutableStateFlow(initial)

    /** Every profile passed to [save], in order. */
    val saved = mutableListOf<Profile>()

    /** When `true`, [save] throws as if the phone were offline. */
    var failSaves = false

    /** When `true`, [currentProfile] fails as if Firestore refused to send the profile. */
    var failLoads = false

    override fun currentProfile(): Flow<Profile> = flow {
        if (failLoads) throw IOException("Can't load")
        emitAll(stored)
    }

    override suspend fun save(profile: Profile) {
        if (failSaves) throw IOException("Offline")
        saved += profile
        stored.value = profile
    }

    /** Changes the stored profile without a save, like an admin editing it elsewhere. */
    fun changeElsewhere(profile: Profile) {
        stored.value = profile
    }
}
