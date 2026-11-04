/**
 * Purpose: Central dependency injection container creating and providing repositories.
 * Last updated: 2026-11-03 (Flow 12).
 * Outstanding issues: The conversation repository remains to be added when that feature lands.
 */
/*
 * Purpose: builds the app's repositories once and hands them to the
 * controllers. It's the one place that decides which version of each
 * repository the app uses, a simple form of dependency injection.
 *
 * Outstanding issues (Flow 12): Contact callable deployment and shared-project verification
 * remain pending (#129).
 *
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
 *
 * The image repository and the Storage property were also added by Aarush
 * Bhat with the assistance of Claude Sonnet 5 (Anthropic), through Claude
 * Code, in November 2026. Aarush Bhat reviewed and committed them.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki add the reservation repository, on November 2, 2026.
 * Sashreek Addanki reviewed it.
 */
// Flow 12 feature owner: Lalith Valluru. OpenAI GPT-6 through Codex assisted
// with the contact repository binding. Human review pending.
// Issue #151: GPT-6 (OpenAI), through Codex, assisted me with repository grouping.
package com.lambda.campsite

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import com.lambda.campsite.model.repository.CampsiteRepository
import com.lambda.campsite.model.repository.FirestoreCampsiteRepository
import com.lambda.campsite.model.repository.FirestoreImageRepository
import com.lambda.campsite.model.repository.FirestoreLotteryRepository
import com.lambda.campsite.model.repository.FirestoreNotificationRepository
import com.lambda.campsite.model.repository.FirestoreProfileRepository
import com.lambda.campsite.model.repository.FirestoreReservationRepository
import com.lambda.campsite.model.repository.ImageRepository
import com.lambda.campsite.model.repository.LotteryRepository
import com.lambda.campsite.model.repository.NotificationRepository
import com.lambda.campsite.model.repository.ProfileRepository
import com.lambda.campsite.model.repository.ReservationRepository
import com.lambda.campsite.model.repository.CallableWaitingListContactRepository
import com.lambda.campsite.model.repository.WaitingListContactRepository

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

    /** Where photo files are kept. Tests point it at the emulator before the app starts. */
    val storage: FirebaseStorage by lazy { Firebase.storage }

    /** Cloud Functions for the authenticated owner contact projection. */
    val functions: FirebaseFunctions by lazy { Firebase.functions }

    /** This device's profile, stored in Firestore. */
    val profileRepository: ProfileRepository by lazy { FirestoreProfileRepository(auth, firestore) }

    /** Every campsite, stored in Firestore. */
    val campsiteRepository: CampsiteRepository by lazy { FirestoreCampsiteRepository(auth, firestore) }

    /** Every lottery and waiting list, stored in Firestore. */
    val lotteryRepository: LotteryRepository by lazy { FirestoreLotteryRepository(auth, firestore) }

    /** Minimal owner-authorized contacts for current waiting-list entries. */
    val waitingListContactRepository: WaitingListContactRepository by lazy {
        CallableWaitingListContactRepository(functions)
    }

    /**
     * Every reservation, stored in Firestore. Booking also updates the
     * campsite's calendar and the winning lottery entry.
     */
    val reservationRepository: ReservationRepository by lazy { FirestoreReservationRepository(auth, firestore) }

    /** This device's notifications, stored in Firestore. */
    val notificationRepository: NotificationRepository by lazy { FirestoreNotificationRepository(auth, firestore) }

    /** Campsite photos, stored in Storage and Firestore. */
    val imageRepository: ImageRepository by lazy { FirestoreImageRepository(auth, firestore, storage) }
}
