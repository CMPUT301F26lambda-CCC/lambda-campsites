/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in NotificationFields. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Image

/**
 * How an [Image] is stored in its Firestore document, `images/{id}`.
 *
 * The ID is the document's ID, so it isn't stored as a field. The field name
 * must match `firestore.rules`.
 */
object ImageFields {

    const val OWNER_ID = "ownerId"

    /** The document fields for [image]. */
    fun toMap(image: Image): Map<String, Any?> = mapOf(OWNER_ID to image.ownerId)

    /** The image stored in the document with ID [id] and these [fields]. */
    fun fromMap(id: String, fields: Map<String, Any?>): Image =
        Image(id = id, ownerId = fields[OWNER_ID] as? String ?: "")
}
