/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in October 2026. It helped with the Kotlin code, syntax and KDoc.
 * Sashreek Addanki reviewed it and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Profile
import com.lambda.campsite.model.Role

/**
 * How a [Profile] is stored in its Firestore document, `profiles/{deviceId}`.
 *
 * The device ID is the document's ID, so it isn't stored as a field. Enums are
 * stored by name, like `"OWNER"`. When a field is missing or has the wrong
 * type, [fromMap] uses the [Profile] default, so documents saved by older
 * versions of the app still load.
 *
 * It's separate from [FirestoreProfileRepository] so unit tests can check it
 * without Firebase. The field names must match `firestore.rules`.
 */
object ProfileFields {

    const val NAME = "name"
    const val EMAIL = "email"
    const val PHONE = "phone"
    const val ROLES = "roles"
    const val NOTIFICATIONS_ON = "notificationsOn"
    const val POLICY_VERSION_AGREED = "policyVersionAgreed"
    const val OWNER_BANNED = "ownerBanned"
    const val REMOVED = "removed"
    const val PUSH_TOKEN = "pushToken"

    /** The document fields for [profile]. */
    fun toMap(profile: Profile): Map<String, Any?> = mapOf(
        NAME to profile.name,
        EMAIL to profile.email,
        PHONE to profile.phone,
        ROLES to profile.roles.sortedBy { it.ordinal }.map { it.name },
        NOTIFICATIONS_ON to profile.notificationsOn,
        POLICY_VERSION_AGREED to profile.policyVersionAgreed,
        OWNER_BANNED to profile.ownerBanned,
        REMOVED to profile.removed,
        PUSH_TOKEN to profile.pushToken,
    )

    /**
     * The profile stored in the document with ID [deviceId] and these [fields].
     * Role names the app doesn't know are skipped, and the roles always include
     * [Role.ENTRANT].
     */
    fun fromMap(deviceId: String, fields: Map<String, Any?>): Profile {
        val defaults = Profile(deviceId = deviceId, name = "", email = "")
        val roles = (fields[ROLES] as? List<*>)
            ?.mapNotNull { name -> Role.entries.find { it.name == name } }
            ?.toSet()
            ?: defaults.roles
        return Profile(
            deviceId = deviceId,
            name = fields[NAME] as? String ?: defaults.name,
            email = fields[EMAIL] as? String ?: defaults.email,
            phone = fields[PHONE] as? String,
            roles = roles + Role.ENTRANT,
            notificationsOn = fields[NOTIFICATIONS_ON] as? Boolean ?: defaults.notificationsOn,
            // Firestore stores every whole number as a Long.
            policyVersionAgreed = (fields[POLICY_VERSION_AGREED] as? Number)?.toInt(),
            ownerBanned = fields[OWNER_BANNED] as? Boolean ?: defaults.ownerBanned,
            removed = fields[REMOVED] as? Boolean ?: defaults.removed,
            pushToken = fields[PUSH_TOKEN] as? String,
        )
    }
}
