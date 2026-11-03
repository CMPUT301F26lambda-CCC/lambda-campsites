/* Feature owner: Lalith Valluru. Purpose: minimal owner-only waiting-list contact.
 * AI use: OpenAI GPT-6 through Codex assisted with this model. Human review pending. */
package com.lambda.campsite.model

/**
 * Contact projection authorized by the server for a particular lottery's entrant (US 02.09.01).
 * It deliberately excludes roles, device tokens, policy and moderation fields.
 * @property entrantId registration identity.
 * @property name entrant's current display name, or empty if unavailable.
 * @property email current contact email, or null if not provided.
 * @property phone current contact phone, or null if not provided.
 */
data class WaitingListContact(val entrantId: String, val name: String, val email: String?, val phone: String?)
