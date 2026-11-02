/*
 * Purpose: the Firestore version of ReservationRepository. It keeps
 * reservations at `reservations/{id}`, watches a guest's, an owner's or a
 * campsite's reservations as live queries, and books, cancels, rejects and
 * declines stays as transactions.
 *
 * Outstanding issues:
 * - book, cancel, reject and decline aren't written yet. They come in the
 *   next steps of #108, and until then they throw NotImplementedError.
 *   Nothing in the app uses this class yet, so nothing can reach them.
 * - The lists are sorted on the phone instead of in the query. Sorting in
 *   the query would need a composite index for each list, and one person
 *   has few reservations.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * pattern in FirestoreLotteryRepository. It helped with the Kotlin code,
 * the Firestore queries, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.lambda.campsite.model.Reservation
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Keeps reservations in Firestore at `reservations/{id}`.
 * [ReservationFields] decides how they're stored.
 *
 * Every flow waits for sign-in first, and ends with a
 * `FirebaseFirestoreException` if Firestore refuses the read. Lists leave out
 * reservations the app can't read, like one with a status this version of
 * the app doesn't know.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreReservationRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ReservationRepository {

    private val reservations = firestore.collection(COLLECTION)

    override fun myReservations(): Flow<List<Reservation>> =
        auth.signedInUserId().flatMapLatest { uid ->
            reservations.whereEqualTo(ReservationFields.GUEST_ID, uid).liveSnapshots().map { it.toReservations() }
        }

    override fun ownerReservations(): Flow<List<Reservation>> =
        auth.signedInUserId().flatMapLatest { uid ->
            reservations.whereEqualTo(ReservationFields.OWNER_ID, uid).liveSnapshots().map { it.toReservations() }
        }

    /**
     * The query also asks for this device's own reservations as owner.
     * Firestore rules don't filter queries, so a query that could return
     * another owner's reservations is refused, even when the campsite is
     * this device's.
     */
    override fun campsiteReservations(campsiteId: String): Flow<List<Reservation>> =
        auth.signedInUserId().flatMapLatest { uid ->
            reservations
                .whereEqualTo(ReservationFields.CAMPSITE_ID, campsiteId)
                .whereEqualTo(ReservationFields.OWNER_ID, uid)
                .liveSnapshots()
                .map { it.toReservations() }
        }

    override suspend fun book(
        lotteryId: String,
        campsiteId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): String = TODO("#108: the booking transaction")

    override suspend fun cancel(reservationId: String): Unit = TODO("#108: the cancel transaction")

    override suspend fun reject(reservationId: String): Unit = TODO("#108: the reject transaction")

    override suspend fun decline(lotteryId: String): Unit = TODO("#108: the decline transaction")

    /** The readable reservations in this snapshot, earliest check-in first. */
    private fun QuerySnapshot.toReservations(): List<Reservation> =
        documents
            .mapNotNull { ReservationFields.fromMap(it.id, it.data.orEmpty()) }
            .sortedBy { it.checkIn }

    private companion object {
        const val COLLECTION = "reservations"
    }
}
