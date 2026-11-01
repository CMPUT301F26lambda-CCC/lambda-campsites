/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * Notification class in the team's class diagram. Aarush Bhat reviewed and
 * committed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp

/**
 * What a [Notification] is about (US 01.03.01, 01.03.02, 01.05.01).
 *
 * WIN and LOSE come from the lottery draw Cloud Function. CAMPSITE_REMOVED
 * comes from the `cleanUpCampsite` Cloud Function (US 03.01.01).
 */
enum class NotificationType {
    WIN,
    LOSE,
    RESERVATION_CONFIRMED,
    CAMPSITE_REMOVED,
}

/**
 * A message in an entrant's or owner's inbox (US 01.03.01, 01.03.02).
 *
 * Only a Cloud Function creates a notification. The app only reads a
 * person's own notifications and marks them read, so there's no method here
 * for building one from scratch.
 *
 * @property id the notification's document ID.
 * @property recipientId the Firebase Auth user ID of the profile it's for.
 * @property type what the notification is about.
 * @property title a short line for the inbox list.
 * @property body the full message.
 * @property sentAt when the server created it. Notifications are sorted by
 *   this, newest first.
 * @property read whether the recipient has opened it.
 */
data class Notification(
    val id: String,
    val recipientId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val sentAt: Timestamp,
    val read: Boolean = false,
)
