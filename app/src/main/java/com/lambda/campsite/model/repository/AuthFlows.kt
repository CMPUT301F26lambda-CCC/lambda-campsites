/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * The signed-in user's ID, sent again if a different user signs in. Sends
 * nothing while nobody is signed in.
 *
 * Firestore repositories wait for this before they read or write.
 * `LambdaApplication` starts the anonymous sign-in when the app opens, and
 * until it finishes the security rules refuse reads, and writes stay queued
 * and are never sent.
 */
fun FirebaseAuth.signedInUserId(): Flow<String> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}.filterNotNull().distinctUntilChanged()