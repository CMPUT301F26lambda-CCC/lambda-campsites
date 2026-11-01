/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in CampsiteFields. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType

/**
 * How a [Notification] is stored in its Firestore document,
 * `notifications/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. Only a Cloud
 * Function ever writes [TYPE], [TITLE], [BODY] and [SENT_AT]; the app only
 * ever changes [READ], through [readField]. The field names must match
 * `firestore.rules`.
 */
object NotificationFields {

    const val RECIPIENT_ID = "recipientId"
    const val TYPE = "type"
    const val TITLE = "title"
    const val BODY = "body"
    const val SENT_AT = "sentAt"
    const val READ = "read"

    /** The document fields for [notification]. */
    fun toMap(notification: Notification): Map<String, Any?> = mapOf(
        RECIPIENT_ID to notification.recipientId,
        TYPE to notification.type.name,
        TITLE to notification.title,
        BODY to notification.body,
        SENT_AT to notification.sentAt,
        READ to notification.read,
    )

    /** The one field the app ever writes on its own: whether it's been read. */
    fun readField(read: Boolean): Map<String, Any> = mapOf(READ to read)

    /**
     * The notification stored in the document with ID [id] and these
     * [fields]. A missing or unrecognized [TYPE] reads as [NotificationType.WIN],
     * which can't happen in practice since only a Cloud Function sets it.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Notification {
        val defaults = Notification(
            id = id,
            recipientId = "",
            type = NotificationType.WIN,
            title = "",
            body = "",
            sentAt = Timestamp(0, 0),
        )
        return Notification(
            id = id,
            recipientId = fields[RECIPIENT_ID] as? String ?: defaults.recipientId,
            type = (fields[TYPE] as? String)?.let(::typeOrNull) ?: defaults.type,
            title = fields[TITLE] as? String ?: defaults.title,
            body = fields[BODY] as? String ?: defaults.body,
            sentAt = fields[SENT_AT] as? Timestamp ?: defaults.sentAt,
            read = fields[READ] as? Boolean ?: defaults.read,
        )
    }

    private fun typeOrNull(name: String): NotificationType? =
        NotificationType.entries.firstOrNull { it.name == name }
}
