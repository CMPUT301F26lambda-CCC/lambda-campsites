/*
 * Purpose: how a Message is stored in its Firestore document,
 * `conversations/{reservationId}/messages/{id}`: the field names, and
 * turning a Message into a map and back. It's part of the model layer,
 * kept apart from the repository so unit tests can check it without
 * Firebase.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in ReservationFields. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.Message

/**
 * How a [Message] is stored in its Firestore document,
 * `conversations/{reservationId}/messages/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. The field
 * names must match `firestore.rules`.
 */
object MessageFields {

    const val SENDER_ID = "senderId"
    const val TEXT = "text"
    const val SENT_AT = "sentAt"

    /** The document fields for [message]. Sending writes [newMessageFields] instead. */
    fun toMap(message: Message): Map<String, Any?> = mapOf(
        SENDER_ID to message.senderId,
        TEXT to message.text,
        SENT_AT to message.sentAt,
    )

    /**
     * The fields a new [message] is sent with. The server fills in
     * [SENT_AT], so a phone with the wrong clock can't change the order
     * messages arrive in.
     */
    fun newMessageFields(message: Message): Map<String, Any?> =
        toMap(message) + mapOf(SENT_AT to FieldValue.serverTimestamp())

    /**
     * The message stored in the document with ID [id] and these [fields].
     * A missing [SENDER_ID] or [TEXT] reads as empty, and a missing
     * [SENT_AT] reads as time zero, which is also how it reads until the
     * server has set it.
     */
    fun fromMap(id: String, fields: Map<String, Any?>): Message = Message(
        id = id,
        senderId = fields[SENDER_ID] as? String ?: "",
        text = fields[TEXT] as? String ?: "",
        sentAt = fields[SENT_AT] as? Timestamp ?: Timestamp(0, 0),
    )
}
