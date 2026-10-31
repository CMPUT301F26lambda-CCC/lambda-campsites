/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite

import com.lambda.campsite.model.repository.InMemoryProfileRepository
import com.lambda.campsite.model.repository.ProfileRepository

/**
 * Builds the app's repositories once and hands them to the controllers.
 *
 * Every controller gets its repositories from here through its `Factory`, never
 * by creating them itself. That's what lets unit tests pass in a fake repository
 * instead.
 *
 * Add each new repository here as a `by lazy` property. Until the Firebase
 * project is set up, the repositories keep their data in memory. Switching to
 * Firestore means changing the classes created in this file, and nothing else
 * in the app.
 */
class AppContainer {

    /** The signed-in profile. Swap for the Firestore version once Firebase is set up. */
    val profileRepository: ProfileRepository by lazy { InMemoryProfileRepository() }
}
