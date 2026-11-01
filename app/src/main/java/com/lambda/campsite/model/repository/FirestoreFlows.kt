/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, the callbackFlow
 * setup, syntax and KDoc. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A snapshot listener on this document, as a [Flow]. It sends the document
 * straight away and again every time it changes, and removes the listener when
 * collection stops.
 *
 * Use this instead of Firestore's own `snapshots()`. When the listener fails,
 * for example because the security rules refuse the read, `snapshots()`
 * cancels its flow, so the error disappears and the screen waits forever.
 * This one ends with the error instead, so a controller can catch it and show
 * a message.
 *
 * @param metadataChanges [MetadataChanges.INCLUDE] also sends a snapshot when
 *   only its metadata changes, like when the server confirms the phone's copy.
 */
fun DocumentReference.liveSnapshots(
    metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE,
): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener(metadataChanges) { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
