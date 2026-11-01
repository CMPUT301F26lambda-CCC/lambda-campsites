/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the pattern in
 * FirestoreCampsiteRepository. It helped with the Kotlin code, the Firestore
 * queries, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.lambda.campsite.model.Lottery
import com.lambda.campsite.model.LotteryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.tasks.await

/**
 * Keeps lotteries in Firestore at `lotteries/{id}`, and each one's waiting
 * list at `lotteries/{id}/entries/{entrantId}`. [LotteryFields] and
 * [LotteryEntryFields] decide how they're stored. Joining and leaving are
 * transactions that write the entry and the lottery's `entryCount` together.
 *
 * Every flow waits for sign-in first, and ends with a
 * `FirebaseFirestoreException` if Firestore refuses the read. Lists leave out
 * lotteries and entries the app can't read, like one with a status this
 * version of the app doesn't know.
 *
 * @param auth gives the signed-in user's ID. `LambdaApplication` signs in when
 *   the app starts.
 * @param firestore the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreLotteryRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : LotteryRepository {

    private val lotteries = firestore.collection(COLLECTION)

    /**
     * Offline, it sends the copy saved on the phone. A lottery the phone has
     * never loaded sends nothing until the phone is back online. A lottery
     * the app can't read sends `null`, the same as a missing one.
     */
    override fun lottery(id: String): Flow<Lottery?> =
        auth.signedInUserId().flatMapLatest {
            lotteries.document(id).liveSnapshots(MetadataChanges.INCLUDE).transform { snapshot ->
                when {
                    snapshot.exists() -> emit(LotteryFields.fromMap(id, snapshot.data.orEmpty()))
                    // The phone's copy can be empty just because it hasn't loaded
                    // yet, so only report "no such lottery" when the server says so.
                    !snapshot.metadata.isFromCache -> emit(null)
                }
            }
        }.distinctUntilChanged()

    override fun lotteriesFor(campsiteId: String): Flow<List<Lottery>> =
        auth.signedInUserId().flatMapLatest {
            lotteries.whereArrayContains(LotteryFields.CAMPSITE_IDS, campsiteId).liveSnapshots().map { it.toLotteries() }
        }

    override fun myLotteries(): Flow<List<Lottery>> =
        auth.signedInUserId().flatMapLatest { uid ->
            lotteries.whereEqualTo(LotteryFields.OWNER_ID, uid).liveSnapshots().map { it.toLotteries() }
        }

    override fun newLotteryId(): String = lotteries.document().id

    /**
     * Returns once the server accepts the save. Offline, the phone keeps the
     * change and this waits until the connection comes back.
     *
     * @throws com.google.firebase.firestore.FirebaseFirestoreException if the
     *   server refuses the write, for example when the lottery has been drawn
     *   or includes someone else's campsite.
     */
    override suspend fun save(lottery: Lottery) {
        // A write made before sign-in finishes would never be sent.
        auth.signedInUserId().first()
        // merge() keeps the entry count, which toMap leaves out.
        lotteries.document(lottery.id).set(LotteryFields.toMap(lottery), SetOptions.merge()).await()
    }

    override fun waitingList(lotteryId: String): Flow<List<LotteryEntry>> =
        auth.signedInUserId().flatMapLatest {
            entries(lotteryId).orderBy(LotteryEntryFields.JOINED_AT).liveSnapshots().map { it.toEntries() }
        }

    // A collection group query reads the entries subcollection of every
    // lottery. The rules only allow it when it asks for the user's own
    // entries, and it needs the entrantId index in firestore.indexes.json.
    override fun myEntries(): Flow<List<LotteryEntry>> =
        auth.signedInUserId().flatMapLatest { uid ->
            firestore.collectionGroup(ENTRIES)
                .whereEqualTo(LotteryEntryFields.ENTRANT_ID, uid)
                .liveSnapshots()
                .map { it.toEntries() }
        }

    /**
     * The security rules check the same things again, using the server's
     * clock, and only accept the new entry together with the higher count.
     */
    override suspend fun join(lotteryId: String) {
        val uid = auth.signedInUserId().first()
        val entry = entries(lotteryId).document(uid)
        firestore.runTransaction { transaction ->
            // A transaction has to do all its reads before it writes.
            val lottery = transaction.lottery(lotteryId)
            if (transaction.get(entry).exists()) refuse(WaitingListException.Reason.ALREADY_JOINED)
            if (!lottery.isRegistrationOpen(Timestamp.now())) refuse(WaitingListException.Reason.REGISTRATION_CLOSED)
            if (!lottery.hasSpace()) refuse(WaitingListException.Reason.FULL)
            transaction.set(entry, LotteryEntryFields.newEntryFields(uid))
            transaction.update(lotteries.document(lotteryId), LotteryFields.ENTRY_COUNT, lottery.entryCount + 1)
        }.await()
    }

    override suspend fun leave(lotteryId: String) {
        val uid = auth.signedInUserId().first()
        val entry = entries(lotteryId).document(uid)
        firestore.runTransaction { transaction ->
            val lottery = transaction.lottery(lotteryId)
            if (!transaction.get(entry).exists()) refuse(WaitingListException.Reason.NOT_JOINED)
            if (!lottery.isRegistrationOpen(Timestamp.now())) refuse(WaitingListException.Reason.REGISTRATION_CLOSED)
            transaction.delete(entry)
            transaction.update(lotteries.document(lotteryId), LotteryFields.ENTRY_COUNT, lottery.entryCount - 1)
        }.await()
    }

    /** Reads the lottery with this [id] inside the transaction, or refuses if there isn't one the app can read. */
    private fun Transaction.lottery(id: String): Lottery {
        val fields = get(lotteries.document(id)).data ?: refuse(WaitingListException.Reason.NO_SUCH_LOTTERY)
        return LotteryFields.fromMap(id, fields) ?: refuse(WaitingListException.Reason.NO_SUCH_LOTTERY)
    }

    // Throwing inside runTransaction stops it without writing anything, and
    // await() throws the same exception.
    private fun refuse(reason: WaitingListException.Reason): Nothing = throw WaitingListException(reason)

    private fun entries(lotteryId: String) = lotteries.document(lotteryId).collection(ENTRIES)

    private fun QuerySnapshot.toLotteries(): List<Lottery> =
        documents.mapNotNull { LotteryFields.fromMap(it.id, it.data.orEmpty()) }

    private fun QuerySnapshot.toEntries(): List<LotteryEntry> =
        documents.mapNotNull { document ->
            // An entry's path is lotteries/{lotteryId}/entries/{entrantId}.
            val lotteryId = document.reference.parent.parent?.id ?: return@mapNotNull null
            LotteryEntryFields.fromMap(lotteryId, document.id, document.data.orEmpty())
        }

    private companion object {
        const val COLLECTION = "lotteries"
        const val ENTRIES = "entries"
    }
}
