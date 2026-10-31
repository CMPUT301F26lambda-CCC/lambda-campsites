/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * It also helped add Firebase Auth when the app was connected to Firebase.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.lambda.campsite.model.repository.InMemoryProfileRepository
import com.lambda.campsite.model.repository.ProfileRepository

/**
 * Builds the app's repositories once and hands them to the controllers.
 *
 * Every controller gets its repositories from here through its `Factory`, never
 * by creating them itself. That's what lets unit tests pass in a fake repository
 * instead.
 *
 * Add each new repository here as a `by lazy` property. The app is connected to
 * Firebase, but the repositories still keep their data in memory until their
 * Firestore versions are written. Switching to Firestore means changing the
 * classes created in this file, and nothing else in the app.
 */
class AppContainer {

    /**
     * Firebase Auth. Each install signs in anonymously, and its user ID is the
     * ID of its profile document (US 01.09.01).
     */
    val auth: FirebaseAuth by lazy { Firebase.auth }

    /** The signed-in profile. Swap for the Firestore version in #96. */
    val profileRepository: ProfileRepository by lazy { InMemoryProfileRepository() }
}
