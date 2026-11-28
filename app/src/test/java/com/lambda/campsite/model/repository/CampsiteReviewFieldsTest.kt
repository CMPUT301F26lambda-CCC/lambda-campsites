/*
 * Purpose: Checks the Firestore field mapping for written reviews.
 * Outstanding issues: None known.
 * AI use: GPT-6 (OpenAI), through Codex, assisted Sreeman Reddy with these
 * Flow 17 field-mapping tests. Human review is pending.
 */
package com.lambda.campsite.model.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Model-layer tests for the review fields used by US 01.13.02. */
class CampsiteReviewFieldsTest {
    @Test fun `post uses server time and completed reservation reference`() {
        val fields = CampsiteReviewFields.forPost("stay-1", "Clean facilities")
        assertEquals("stay-1", fields[CampsiteReviewFields.RESERVATION_ID])
        assertEquals("Clean facilities", fields[CampsiteReviewFields.TEXT])
        assertTrue(fields[CampsiteReviewFields.POSTED_AT] is FieldValue)
    }

    @Test fun `incomplete review document is ignored`() {
        assertNull(CampsiteReviewFields.fromMap("entrant-1", mapOf(
            "reservationId" to "stay-1", "text" to "Clean facilities")))
        assertNull(CampsiteReviewFields.fromMap("entrant-1", mapOf(
            "reservationId" to "stay-1", "text" to "", "postedAt" to Timestamp(1, 0))))
    }
}
