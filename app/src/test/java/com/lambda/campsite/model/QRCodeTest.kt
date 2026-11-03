/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write these tests in November 2026.
 * It helped with test cases for URI matching, campsite ID decoding, ZXing matrix
 * generation and decoding a real 20-character Firestore ID back out of the
 * matrix. Raghav Sethi reviewed, ran, and committed them.
 */
package com.lambda.campsite.model

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [QRCode]: URI matching, decoding campsite ID (US 01.12.01) and
 * encoding matrices that decode back (US 02.02.01).
 *
 * The matrix tests decode with ZXing's own [MultiFormatReader], the same reader
 * the scanner will use (US 01.12.01), so a test failure means the printed QR
 * code really opens the wrong campsite.
 */
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
    fun `encodeBitMatrix builds a square matrix with finder patterns in the corners`() {
        // A size smaller than the code's natural size makes ZXing use exactly
        // one pixel per module, so modules and pixels line up here.
        val matrix = QRCode.encodeBitMatrix("lambda-campsite://campsite/test-site", sizePx = 0)
        assertEquals(matrix.height, matrix.width)

        // The quiet zone is 2 modules wide, and each 7x7 finder pattern follows
        // it. Its dark corners sit 2 modules in and 6 modules further along.
        assertTrue(matrix.get(2, 2))
        assertTrue(matrix.get(2, 8))
        assertTrue(matrix.get(8, 2))
        assertTrue(matrix.get(8, 8))
    }

    @Test
    fun `a QR code for a real 20 character ID decodes back to the full ID`() {
        // Firestore IDs are 20 characters. With the 27 character URI prefix the
        // payload is longer than a Version 3-M code could hold, so the old
        // encoder silently cut the tail and opened a wrong campsite.
        val campsiteId = "Z4qT7vXy2Wk9Lm3AbC8d"
        val payload = "${QRCode.URI_PREFIX}$campsiteId"

        val decoded = decodeMatrix(QRCode.encodeBitMatrix(payload, 512))

        assertEquals(payload, decoded)
    }

    @Test
    fun `a QR code for the short demo ID decodes back too`() {
        val payload = "${QRCode.URI_PREFIX}test-site"

        val decoded = decodeMatrix(QRCode.encodeBitMatrix(payload, 256))

        assertEquals(payload, decoded)
    }

    /** Turns a [BitMatrix] into pixels and reads them back with ZXing's decoder. */
    private fun decodeMatrix(matrix: BitMatrix): String {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFF
            }
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val result = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.PURE_BARCODE to true),
        )
        return result.text
    }
}
