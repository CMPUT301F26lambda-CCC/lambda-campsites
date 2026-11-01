/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in FakeCampsiteRepository. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model.repository

import com.lambda.campsite.model.Notification
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A [NotificationRepository] for unit tests. It keeps notifications in
 * memory and can be told to fail like an offline phone would.
 *
 * @param myUserId the user ID [myNotifications] treats as this device's.
 * @param initial the notifications to start with.
 */
class FakeNotificationRepository(
    private val myUserId: String = "entrant-1",
    initial: List<Notification> = emptyList(),
) : NotificationRepository {

    private val stored = MutableStateFlow(initial.associateBy { it.id })

    /** When `true`, [markAsRead] throws as if the phone were offline. */
    var failMarkAsRead = false

    override fun myNotifications(): Flow<List<Notification>> =
        stored.map { notifications ->
            notifications.values.filter { it.recipientId == myUserId }.sortedByDescending { it.sentAt }
        }

    override suspend fun markAsRead(id: String) {
        if (failMarkAsRead) throw IOException("Offline")
        stored.update { current ->
            val notification = current[id] ?: return@update current
            current + (id to notification.copy(read = true))
        }
    }

    /** Adds or changes a notification without a save, like a Cloud Function sending one. */
    fun changeElsewhere(notification: Notification) {
        stored.update { it + (notification.id to notification) }
    }
}
