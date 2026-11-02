/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for URI matching, campsite ID decoding, QR matrix module
 * dimensions and finder pattern verification. Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [QRCode]: URI matching, decoding campsite ID (US 01.12.01) and matrix generation (US 02.02.01). */
class QRCodeTest {

    @Test
    fun `isOurs returns true for deep link URI prefix`() {
        assertTrue(QRCode.isOurs("lambda-campsite://campsite/aspen-hollow-101"))
        assertTrue(QRCode.isOurs("campsite:aspen-hollow-101"))
    }

    @Test
    fun `isOurs returns false for foreign or web URIs`() {
        assertFalse(QRCode.isOurs("https://example.com/campsite/123"))
        assertFalse(QRCode.isOurs("other-app://campsite/123"))
        assertFalse(QRCode.isOurs(""))
    }

    @Test
    fun `decode extracts campsite ID from lambda URI`() {
        val id = QRCode.decode("lambda-campsite://campsite/aspen-hollow-101")
        assertEquals("aspen-hollow-101", id)
    }

    @Test
    fun `decode extracts campsite ID from short campsite prefix`() {
        val id = QRCode.decode("campsite:cedar-point-202")
        assertEquals("cedar-point-202", id)
    }

    @Test
    fun `decode returns null for foreign URI or empty ID`() {
        assertNull(QRCode.decode("https://google.com"))
        assertNull(QRCode.decode("lambda-campsite://campsite/"))
        assertNull(QRCode.decode("campsite:"))
    }

    @Test
    fun `generateMatrix produces standard 29x29 matrix for Version 3 QR code`() {
        val matrix = QRCode.generateMatrix("lambda-campsite://campsite/test-site")
        assertEquals(29, matrix.size)
        assertEquals(29, matrix[0].size)

        // Verify top-left finder pattern corner at (0, 0)
        assertTrue(matrix[0][0])
        assertTrue(matrix[0][6])
        assertTrue(matrix[6][0])
        assertTrue(matrix[6][6])
    }
}
