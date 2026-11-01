/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * this file in November 2026, following the pattern in NotificationFields. It
 * helped with the Kotlin code, syntax and KDoc. Sashreek Addanki reviewed it.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.lambda.campsite.model.EntryStatus
import com.lambda.campsite.model.LotteryEntry

/**
 * How a [LotteryEntry] is stored in its Firestore document,
 * `lotteries/{lotteryId}/entries/{entrantId}`.
 *
 * The lottery and entrant IDs come from the document's path. [ENTRANT_ID] is
 * a copy of the entrant's, so one collection group query on `entries` finds
 * every waiting list a person is on. The field names must match
 * `firestore.rules`.
 */
object LotteryEntryFields {

    const val ENTRANT_ID = "entrantId"
    const val JOINED_AT = "joinedAt"
    const val STATUS = "status"
    const val RESERVATION_ID = "reservationId"

    /**
     * The fields the app writes when [entrantId] joins a waiting list. The
     * server fills in [JOINED_AT], so a phone with the wrong clock can't move
     * ahead in the queue.
     */
    fun newEntryFields(entrantId: String): Map<String, Any> = mapOf(
        ENTRANT_ID to entrantId,
        JOINED_AT to FieldValue.serverTimestamp(),
        STATUS to EntryStatus.WAITING.name,
    )

    /**
     * The document fields for [entry], the way the draw and booking leave
     * them. The app itself only writes [newEntryFields]. Tests use this to set
     * up entries.
     */
    fun toMap(entry: LotteryEntry): Map<String, Any?> = mapOf(
        ENTRANT_ID to entry.entrantId,
        JOINED_AT to entry.joinedAt,
        STATUS to entry.status.name,
        RESERVATION_ID to entry.reservationId,
    )

    /**
     * The entry stored at `lotteries/{lotteryId}/entries/{entrantId}` with
     * these [fields], or `null` if its [STATUS] is missing or one this version
     * of the app doesn't know. A later version might add a status, and an
     * older app shouldn't show that entry as something else, like a win.
     */
    fun fromMap(lotteryId: String, entrantId: String, fields: Map<String, Any?>): LotteryEntry? {
        val status = (fields[STATUS] as? String)?.let(::statusOrNull) ?: return null
        return LotteryEntry(
            lotteryId = lotteryId,
            entrantId = entrantId,
            joinedAt = fields[JOINED_AT] as? Timestamp ?: Timestamp(0, 0),
            status = status,
            reservationId = fields[RESERVATION_ID] as? String,
        )
    }

    private fun statusOrNull(name: String): EntryStatus? =
        EntryStatus.entries.firstOrNull { it.name == name }
}
