/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the Flow and
 * snapshot listener setup, syntax and KDoc. It also helped move the wait for
 * sign-in into AuthFlows.kt. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.lambda.campsite.model.Profile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await

/**
 * Keeps this device's profile in Firestore at `profiles/{uid}`, where `uid` is
 * the install's Firebase Auth user ID (US 01.09.01). [ProfileFields] decides
 * how the profile is stored.
 *
 * The first time the app opens on a device there's no profile yet, so this
 * creates an entrant profile with an empty name and email. The profile stories
 * fill those in later.
 *
 * This is the one to copy when writing another Firestore repository.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 */
class FirestoreProfileRepository(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore,
) : ProfileRepository {

    private val profiles = firestore.collection(COLLECTION)

    /**
     * Sends the profile once sign-in has finished and the profile has loaded,
     * then again every time it changes, on this phone or anywhere else, like
     * when an admin bans the owner role. Creates the profile first if this
     * device doesn't have one yet.
     *
     * Offline, it sends the copy saved on the phone. On a first launch with no
     * connection, it waits until the phone is back online.
     *
     * Deleting a profile (US 01.08.01) has to sign the user out too, or this
     * creates a new empty profile straight away.
     *
     * The flow ends with a `FirebaseFirestoreException` if Firestore refuses
     * to send the profile.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun currentProfile(): Flow<Profile> =
        auth.signedInUserId().flatMapLatest { uid ->
            // INCLUDE also sends a snapshot when only its source changes, so we
            // hear when the server confirms there's no profile.
            profiles.document(uid).liveSnapshots(MetadataChanges.INCLUDE).mapNotNull { snapshot ->
                when {
                    snapshot.exists() -> ProfileFields.fromMap(uid, snapshot.data.orEmpty())
                    // The phone's copy can be empty just because it hasn't loaded
                    // yet, so only create the profile when the server says so.
                    !snapshot.metadata.isFromCache -> {
                        save(Profile(deviceId = uid, name = "", email = ""))
                        null
                    }
                    else -> null
                }
            }
        }.distinctUntilChanged()

    /**
     * Saves [profile] to `profiles/{deviceId}`, replacing what's there. Returns
     * once the server accepts it. Offline, the phone keeps the change and this
     * waits until the connection comes back.
     *
     * @throws com.google.firebase.firestore.FirebaseFirestoreException if the
     *   server refuses the write, for example when `firestore.rules` doesn't
     *   allow the change.
     */
    override suspend fun save(profile: Profile) {
        profiles.document(profile.deviceId).set(ProfileFields.toMap(profile)).await()
    }

    private companion object {
        const val COLLECTION = "profiles"
    }
}
