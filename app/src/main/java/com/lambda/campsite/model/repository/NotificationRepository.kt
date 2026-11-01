/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in CampsiteRepository. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Loads this device's notifications and marks them read.
 *
 * Controllers depend on this interface, never on a particular version of it.
 * The app uses [FirestoreNotificationRepository], and unit tests use
 * `FakeNotificationRepository`.
 *
 * Only a Cloud Function creates a notification, so this interface has no
 * method for saving one.
 */
interface NotificationRepository {

    /**
     * This device's notifications, newest first, sent again whenever one
     * changes (US 01.03.01, 01.03.02).
     */
    fun myNotifications(): Flow<List<Notification>>

    /**
     * Marks the notification with this [id] as read.
     *
     * @throws Exception if it can't be marked read, for example when it
     *   belongs to someone else.
     */
    suspend fun markAsRead(id: String)
}
