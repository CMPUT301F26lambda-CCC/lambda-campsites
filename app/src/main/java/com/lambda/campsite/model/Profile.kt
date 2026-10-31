/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026, following the Profile class in the team's class
 * diagram. It helped with the Kotlin code, syntax and KDoc. Sashreek Addanki
 * reviewed it, ran the tests and committed it.
 */
package com.lambda.campsite.model

/**
 * One person using the app, identified by their device instead of a password.
 *
 * Profiles never change in place. A method that changes something returns an
 * updated copy, and the controller saves that copy through the repository. This
 * suits `StateFlow`, which only sends a new value when it's actually different.
 *
 * The class diagram's `image` attribute arrives with the Image class.
 *
 * @property deviceId the Firebase Auth user ID for this install (US 01.09.01).
 * @property name the person's name.
 * @property email the person's email address.
 * @property phone the person's phone number, or `null` if they didn't give one.
 * @property roles the roles this profile holds. Always includes [Role.ENTRANT].
 * @property notificationsOn whether push notifications are on (US 01.10.01).
 * @property policyVersionAgreed the app policy version the person agreed to, or
 *   `null` if they haven't agreed yet (US 01.11.01).
 * @property ownerBanned set by an admin who removed this profile's owner role
 *   (US 03.07.01). While it's set, the profile can't become an owner again.
 * @property removed set when an admin removes the profile (US 03.02.01).
 * @property pushToken this device's Firebase Cloud Messaging token, or `null`.
 */
data class Profile(
    val deviceId: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val roles: Set<Role> = setOf(Role.ENTRANT),
    val notificationsOn: Boolean = true,
    val policyVersionAgreed: Int? = null,
    val ownerBanned: Boolean = false,
    val removed: Boolean = false,
    val pushToken: String? = null,
) {

    /** Returns `true` if this profile holds [role]. */
    fun hasRole(role: Role): Boolean = role in roles

    /**
     * Returns a copy of this profile with the [Role.OWNER] role added. This is
     * what "List your campsite" does.
     *
     * @throws IllegalStateException if an admin banned this profile from being an owner.
     */
    fun becomeOwner(): Profile {
        check(!ownerBanned) { "An admin removed this profile's owner role." }
        return copy(roles = roles + Role.OWNER)
    }
}
