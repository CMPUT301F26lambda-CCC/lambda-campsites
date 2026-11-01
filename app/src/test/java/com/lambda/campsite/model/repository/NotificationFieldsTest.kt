/*
 * AI use: Claude Opus 5.5 (Anthropic), used through Claude Code, helped write
 * these tests in November 2026, following the pattern in
 * CampsiteFieldsTest. It helped with the test cases and the Kotlin syntax.
 * Aarush Bhat reviewed them, ran them and committed them.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for [NotificationFields], how a notification is stored in Firestore. */
class NotificationFieldsTest {

    private val notification = Notification(
        id = "notification-1",
        recipientId = "entrant-1",
        type = NotificationType.WIN,
        title = "You won the lottery",
        body = "You won the lottery for Moose Lake Campsite. Accept or decline in the app.",
        sentAt = Timestamp(1_700_000_000, 0),
        read = false,
    )

    @Test
    fun `a notification reads back the same after it's stored`() {
        val fields = NotificationFields.toMap(notification)

        assertEquals(notification, NotificationFields.fromMap("notification-1", fields))
    }

    @Test
    fun `marking read only writes the read field`() {
        val fields = NotificationFields.readField(true)

        assertEquals(mapOf(NotificationFields.READ to true), fields)
    }

    @Test
    fun `a notification type that isn't recognized reads as the default`() {
        val fields = NotificationFields.toMap(notification) + mapOf(NotificationFields.TYPE to "SOMETHING_NEW")

        assertEquals(NotificationType.WIN, NotificationFields.fromMap("notification-1", fields).type)
    }

    @Test
    fun `missing fields read as the defaults`() {
        val read = NotificationFields.fromMap("notification-1", emptyMap())

        val defaults = Notification(
            id = "notification-1",
            recipientId = "",
            type = NotificationType.WIN,
            title = "",
            body = "",
            sentAt = Timestamp(0, 0),
        )
        assertEquals(defaults, read)
    }
}
