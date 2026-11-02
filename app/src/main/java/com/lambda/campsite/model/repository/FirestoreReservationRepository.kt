/*
 * Purpose: the Firestore version of ReservationRepository. It keeps
 * reservations at `reservations/{id}`, watches a guest's, an owner's or a
 * campsite's reservations as live queries, and books, cancels, rejects and
 * declines stays as transactions.
 *
 * Outstanding issues:
 * - cancel, reject and decline aren't written yet. They come in the next
 *   steps of #108, and until then they throw NotImplementedError. Nothing
 *   in the app uses this class yet, so nothing can reach them.
 * - firestore.rules has no reservation rules yet, so the server refuses
 *   book's writes until #108 adds them.
 * - The lists are sorted on the phone instead of in the query. Sorting in
 *   the query would need a composite index for each list, and one person
 *   has few reservations.
 * - book writes the calendar's whole list of booked nights back, not just
 *   the new ones. The transaction has already read the list, and a season
 *   has at most a few hundred nights.
 *
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped
 * Sashreek Addanki write this file on November 2, 2026, following the
 * pattern in FirestoreLotteryRepository. It helped with the Kotlin code,
 * the Firestore queries, the booking transaction, syntax and KDoc. Sashreek
 * Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.Reservation
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Keeps reservations in Firestore at `reservations/{id}`.
 * [ReservationFields] decides how they're stored. Booking is a transaction
 * that writes the reservation, the campsite's calendar and the winning
 * entry together.
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
    private val lotteries = firestore.collection(LOTTERIES)
    private val campsites = firestore.collection(CAMPSITES)
    private val profiles = firestore.collection(PROFILES)

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

    /**
     * If another winner books one of the same nights while this runs,
     * Firestore runs the transaction again on the new calendar, which then
     * refuses with [ReservationException.Reason.NIGHTS_NOT_FREE].
     */
    override suspend fun book(
        lotteryId: String,
        campsiteId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): String {
        val uid = auth.signedInUserId().first()
        val reservationRef = reservations.document()
        val entryRef = lotteries.document(lotteryId).collection(ENTRIES).document(uid)
        val calendarRef = calendar(campsiteId)
        firestore.runTransaction { transaction ->
            // A transaction has to do all its reads before it writes.
            val entry = transaction.get(entryRef).data?.let { LotteryEntryFields.fromMap(lotteryId, uid, it) }
            val lottery = transaction.get(lotteries.document(lotteryId)).data
                ?.let { LotteryFields.fromMap(lotteryId, it) }
            val campsite = transaction.get(campsites.document(campsiteId)).data
                ?.let { CampsiteFields.fromMap(campsiteId, it) }
            val calendar = transaction.get(calendarRef).data?.let(AvailabilityFields::fromMap)
            val profile = transaction.get(profiles.document(uid)).data.orEmpty()

            if (entry?.status != EntryStatus.WON) refuse(ReservationException.Reason.NOT_A_WINNER)
            if (lottery == null || campsite == null || campsiteId !in lottery.campsiteIds) {
                refuse(ReservationException.Reason.NOT_IN_LOTTERY)
            }
            if (!checkOut.isAfter(checkIn) || checkIn.isBefore(lottery.stayStart) || checkOut.isAfter(lottery.stayEnd)) {
                refuse(ReservationException.Reason.OUTSIDE_STAY)
            }
            if (calendar == null || !calendar.isFree(checkIn, checkOut)) {
                refuse(ReservationException.Reason.NIGHTS_NOT_FREE)
            }
            if (guests !in 1..campsite.maxOccupancy) refuse(ReservationException.Reason.GUESTS_DONT_FIT)

            val reservation = Reservation(
                id = reservationRef.id,
                guestId = uid,
                guestName = ProfileFields.fromMap(uid, profile).name,
                ownerId = campsite.ownerId,
                campsiteId = campsiteId,
                campsiteName = campsite.name,
                lotteryId = lotteryId,
                checkIn = checkIn,
                checkOut = checkOut,
                guests = guests,
                // newReservationFields swaps both times for the server's.
                rulesAgreedAt = Timestamp.now(),
                createdAt = Timestamp.now(),
            )
            val bookedNights = calendar.bookedNights + calendar.nights(checkIn, checkOut)
            transaction.update(calendarRef, AvailabilityFields.BOOKED_NIGHTS, bookedNights.sorted().map { it.toString() })
            transaction.set(reservationRef, ReservationFields.newReservationFields(reservation))
            transaction.update(
                entryRef,
                mapOf(
                    LotteryEntryFields.STATUS to EntryStatus.BOOKED.name,
                    LotteryEntryFields.RESERVATION_ID to reservationRef.id,
                ),
            )
        }.await()
        return reservationRef.id
    }

    override suspend fun cancel(reservationId: String): Unit = TODO("#108: the cancel transaction")

    override suspend fun reject(reservationId: String): Unit = TODO("#108: the reject transaction")

    override suspend fun decline(lotteryId: String): Unit = TODO("#108: the decline transaction")

    /** A campsite's calendar, `campsites/{campsiteId}/availability/calendar`. */
    private fun calendar(campsiteId: String) =
        campsites.document(campsiteId).collection(AVAILABILITY).document(CALENDAR)

    /** The readable reservations in this snapshot, earliest check-in first. */
    private fun QuerySnapshot.toReservations(): List<Reservation> =
        documents
            .mapNotNull { ReservationFields.fromMap(it.id, it.data.orEmpty()) }
            .sortedBy { it.checkIn }

    // Throwing inside runTransaction stops it without writing anything, and
    // await() throws the same exception.
    private fun refuse(reason: ReservationException.Reason): Nothing = throw ReservationException(reason)

    private companion object {
        const val COLLECTION = "reservations"
        const val LOTTERIES = "lotteries"
        const val ENTRIES = "entries"
        const val CAMPSITES = "campsites"
        const val AVAILABILITY = "availability"
        const val CALENDAR = "calendar"
        const val PROFILES = "profiles"
    }
}
