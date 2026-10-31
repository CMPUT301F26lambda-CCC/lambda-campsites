/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the Role enumeration in the team's class
 * diagram. Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model

/** A role a [Profile] can hold. One profile can hold several at once (US 03.11.01). */
enum class Role {

    /** Searches campsites, joins lotteries and books stays. Every profile starts with this role. */
    ENTRANT,

    /** Lists campsites and runs lotteries. Any profile can add it unless an admin banned it. */
    OWNER,

    /** Moderates campsites, profiles, images and logs. Only set by hand in Firestore. */
    ADMIN,
}
