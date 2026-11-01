/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add Firebase Auth when the app was connected to Firebase, and
 * helped switch the profile to Firestore, and helped add the campsite and
 * lottery repositories. Sashreek Addanki reviewed the profile, campsite and
 * lottery repositories.
 *
 * The notification repository was added by Aarush Bhat with the assistance
 * of Claude Sonnet 5 (Anthropic), through Claude Code, in November 2026.
 * Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FirestoreCampsiteRepository
import com.lambda.campsite.model.repository.FirestoreLotteryRepository
import com.lambda.campsite.model.repository.FirestoreNotificationRepository
import com.lambda.campsite.model.repository.FirestoreProfileRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.NotificationRepository
import com.lambda.campsite.model.repository.ProfileRepository

/**
 * Builds the app's repositories once and hands them to the controllers.
 *
 * Every controller gets its repositories from here through its `Factory`, never
 * by creating them itself. That's what lets unit tests pass in a fake repository
 * instead.
 *
 * Add each new repository here as a `by lazy` property. A repository can start
 * as an in-memory version with sample data and move to its Firestore version
 * later. That move is a change to this file, and nothing else in the app.
 */
class AppContainer {

    /**
     * Firebase Auth. Each install signs in anonymously, and its user ID is the
     * ID of its profile document (US 01.09.01).
     */
    val auth: FirebaseAuth by lazy { Firebase.auth }

    /** The Firestore database. Tests point it at the emulator before the app starts. */
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }

    /** This device's profile, stored in Firestore. */
    val profileRepository: ProfileRepository by lazy { FirestoreProfileRepository(auth, firestore) }

    /** Every campsite, stored in Firestore. */
    val campsiteRepository: CampsiteRepository by lazy { FirestoreCampsiteRepository(auth, firestore) }

    /** Every lottery and waiting list, stored in Firestore. */
    val lotteryRepository: LotteryRepository by lazy { FirestoreLotteryRepository(auth, firestore) }

    /** This device's notifications, stored in Firestore. */
    val notificationRepository: NotificationRepository by lazy { FirestoreNotificationRepository(auth, firestore) }
}
