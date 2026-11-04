/*
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * AI use: GPT-6 (OpenAI), through Codex, assisted me with QR frame regression tests.
 * Human review is pending.
 */
package com.lambda.campsite.model

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reads the exact payload printed by Flow 3, including padded camera planes. */
class QrFrameDecoderTest {
    private val campsiteId = "Z4qT7vXy2Wk9Lm3AbC8d"
    private val payload = "${QRCode.URI_PREFIX}$campsiteId"

    @Test fun `reads owner QR from a contiguous luminance frame`() {
        val matrix = QRCode.encodeBitMatrix(payload, 256)
        val plane = ByteArray(matrix.width * matrix.height) { index ->
            if (matrix.get(index % matrix.width, index / matrix.width)) 0 else 255.toByte()
        }
        assertEquals(payload, QrFrameDecoder.decode(ByteBuffer.wrap(plane),
            matrix.width, matrix.height, matrix.width, 1))
    }

    @Test fun `ignores camera row padding and pixel stride`() {
        val matrix = QRCode.encodeBitMatrix(payload, 256)
        val pixelStride = 2
        val rowStride = matrix.width * pixelStride + 12
        val plane = ByteArray(rowStride * matrix.height) { 127.toByte() }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            plane[y * rowStride + x * pixelStride] =
                if (matrix.get(x, y)) 0 else 255.toByte()
        }
        assertEquals(payload, QrFrameDecoder.decode(ByteBuffer.wrap(plane),
            matrix.width, matrix.height, rowStride, pixelStride))
    }

    @Test fun `blank or truncated camera frame has no code`() {
        assertNull(QrFrameDecoder.decode(ByteBuffer.wrap(ByteArray(256 * 256) { 255.toByte() }),
            256, 256, 256, 1))
        assertNull(QrFrameDecoder.decode(ByteBuffer.wrap(ByteArray(40)), 256, 256, 256, 1))
    }
}
