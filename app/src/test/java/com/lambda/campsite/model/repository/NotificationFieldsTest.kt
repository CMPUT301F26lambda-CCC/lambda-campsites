/*
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following the
 * pattern in CampsiteFieldsTest. Aarush Bhat reviewed, ran and committed
 * them.
 *
 * Claude Opus 5.5 (Anthropic), used through Claude Code, also helped
 * Sashreek Addanki change the tests for missing and unknown types in
 * November 2026. Sashreek Addanki reviewed them.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.lambda.campsite.model.Notification
import com.lambda.campsite.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a notification with a type the app doesn't know reads as null`() {
        val fields = NotificationFields.toMap(notification) + mapOf(NotificationFields.TYPE to "SOMETHING_NEW")

        assertNull(NotificationFields.fromMap("notification-1", fields))
    }

    @Test
    fun `a notification with no type reads as null`() {
        val fields = NotificationFields.toMap(notification) - NotificationFields.TYPE

        assertNull(NotificationFields.fromMap("notification-1", fields))
    }

    @Test
    fun `missing fields other than the type read as the defaults`() {
        val read = NotificationFields.fromMap("notification-1", mapOf(NotificationFields.TYPE to "LOSE"))

        val defaults = Notification(
            id = "notification-1",
            recipientId = "",
            type = NotificationType.LOSE,
            title = "",
            body = "",
            sentAt = Timestamp(0, 0),
        )
        assertEquals(defaults, read)
    }
}
