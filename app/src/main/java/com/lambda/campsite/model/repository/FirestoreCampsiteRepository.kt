/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the Firestore
 * queries, syntax and KDoc. It also helped add the availability calendar in
 * November 2026. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.lambda.campsite.model.Availability
import com.lambda.campsite.model.Campsite
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.tasks.await

/**
 * Keeps campsites in Firestore at `campsites/{id}`, and each one's calendar at
 * `campsites/{id}/availability/calendar`. [CampsiteFields] and
 * [AvailabilityFields] decide how they're stored.
 *
 * Every flow waits for sign-in first, and ends with a
 * `FirebaseFirestoreException` if Firestore refuses the read.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreCampsiteRepository(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore,
) : CampsiteRepository {

    private val campsites = firestore.collection(COLLECTION)

    // The security rules refuse a query that could return a campsite the user
    // can't read, so this one has to ask for published campsites only.
    override fun publishedCampsites(): Flow<List<Campsite>> =
        auth.signedInUserId().flatMapLatest {
            campsites.whereEqualTo(CampsiteFields.PUBLISHED, true).liveSnapshots().map { it.toCampsites() }
        }

    override fun myCampsites(): Flow<List<Campsite>> =
        auth.signedInUserId().flatMapLatest { uid ->
            campsites.whereEqualTo(CampsiteFields.OWNER_ID, uid).liveSnapshots().map { it.toCampsites() }
        }

    /**
     * Offline, it sends the copy saved on the phone. A campsite the phone has
     * never loaded sends nothing until the phone is back online.
     */
    override fun campsite(id: String): Flow<Campsite?> =
        auth.signedInUserId().flatMapLatest {
            campsites.document(id).liveSnapshots(MetadataChanges.INCLUDE).transform { snapshot ->
                when {
                    snapshot.exists() -> emit(CampsiteFields.fromMap(id, snapshot.data.orEmpty()))
                    // The phone's copy can be empty just because it hasn't loaded
                    // yet, so only report "no such campsite" when the server says so.
                    !snapshot.metadata.isFromCache -> emit(null)
                }
            }
        }.distinctUntilChanged()

    override fun newCampsiteId(): String = campsites.document().id

    /**
     * Returns once the server accepts the save. Offline, the phone keeps the
     * change and this waits until the connection comes back.
     *
     * @throws com.google.firebase.firestore.FirebaseFirestoreException if the
     *   server refuses the write, for example when this device's profile isn't
     *   an owner, or the campsite belongs to someone else.
     */
    override suspend fun save(campsite: Campsite) {
        // A write made before sign-in finishes would never be sent.
        auth.signedInUserId().first()
        // merge() keeps the fields toMap leaves out, which are the rating totals.
        campsites.document(campsite.id).set(CampsiteFields.toMap(campsite), SetOptions.merge()).await()
    }

    /**
     * Offline, it sends the copy saved on the phone, the same way [campsite]
     * does.
     */
    override fun availability(campsiteId: String): Flow<Availability?> =
        auth.signedInUserId().flatMapLatest {
            calendar(campsiteId).liveSnapshots(MetadataChanges.INCLUDE).transform { snapshot ->
                when {
                    snapshot.exists() -> emit(AvailabilityFields.fromMap(snapshot.data.orEmpty()))
                    !snapshot.metadata.isFromCache -> emit(null)
                }
            }
        }.distinctUntilChanged()

    /**
     * Returns once the server accepts the save. Offline, the phone keeps the
     * change and this waits until the connection comes back.
     *
     * @throws IllegalArgumentException if [seasonEnd] isn't after [seasonStart].
     * @throws com.google.firebase.firestore.FirebaseFirestoreException if the
     *   server refuses the write, for example when the campsite belongs to
     *   someone else or hasn't been saved yet.
     */
    override suspend fun setSeason(campsiteId: String, seasonStart: LocalDate, seasonEnd: LocalDate) {
        // Checked here too, so a bad season fails at once instead of waiting
        // for the server to refuse it.
        require(seasonEnd.isAfter(seasonStart)) { "The season has to close after it opens." }
        auth.signedInUserId().first()
        // merge() keeps the booked nights, which seasonFields leaves out.
        calendar(campsiteId).set(AvailabilityFields.seasonFields(seasonStart, seasonEnd), SetOptions.merge()).await()
    }

    private fun calendar(campsiteId: String): DocumentReference =
        campsites.document(campsiteId).collection(AVAILABILITY).document(CALENDAR)

    private fun QuerySnapshot.toCampsites(): List<Campsite> =
        documents.map { CampsiteFields.fromMap(it.id, it.data.orEmpty()) }

    private companion object {
        const val COLLECTION = "campsites"
        const val AVAILABILITY = "availability"
        const val CALENDAR = "calendar"
    }
}
