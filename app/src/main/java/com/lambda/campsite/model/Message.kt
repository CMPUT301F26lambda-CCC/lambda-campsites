/*
 * Purpose: the Message model class, in the model layer. A message belongs
 * to one Conversation, stored in that conversation's `messages`
 * subcollection.
 *
 * Outstanding issues: None known.
 *
 * AI use: Coded by Aarush Bhat with the assistance of Claude Sonnet 5
 * (Anthropic), through Claude Code, in November 2026, following
 * docs/architecture.md section 4 and the Message class in the team's class
 * diagram. Aarush Bhat reviewed and committed it.
 */
package com.lambda.campsite.model

import com.google.firebase.Timestamp

/**
 * One message in a [Conversation] (US 02.12.01, 02.12.02).
 *
 * @property id the message's document ID.
 * @property senderId the Firebase Auth user ID of whoever sent it: the
 *   conversation's owner or guest.
 * @property text what they wrote.
 * @property sentAt when the server received it. Messages are sorted by
 *   this, newest first.
 */
data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val sentAt: Timestamp,
)
