/*
 * Purpose: Copies a CameraX luminance plane with its row and pixel strides
 * and decodes a QR payload with ZXing.
 * Outstanding issues: Physical-device Y-plane decoding remains unverified.
 * Feature owner: Sreeman Reddy, Flow 16 (#133), November 2026.
 * Third-party APIs: Android CameraX supplies the Y-plane buffer and strides;
 * ZXing core supplies PlanarYUVLuminanceSource, HybridBinarizer and QRCodeReader.
 * Sources: https://developer.android.com/media/camera/camerax/analyze
 * and https://github.com/zxing/zxing.
 * AI use: GPT-6 (OpenAI), through Codex, helped implement the stride-aware
 * CameraX-to-ZXing decoding in this file. QrFrameDecoderTest covers padded planes.
 * Human review is pending.
 */
package com.lambda.campsite.model

import com.google.zxing.BinaryBitmap
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/** Reads one CameraX Y plane with its actual row and pixel strides (US 01.12.01). */
object QrFrameDecoder {
    /**
     * Returns the QR payload in [plane], or null when no readable QR is present.
     * CameraX supplies the plane's [width], [height] and strides; padding must
     * not be mistaken for image pixels.
     */
    fun decode(plane: ByteBuffer, width: Int, height: Int,
        rowStride: Int, pixelStride: Int): String? {
        if (width < 1 || height < 1 || rowStride < 1 || pixelStride < 1) return null
        val bytes = ByteArray(width * height)
        val source = plane.duplicate()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y.toLong() * rowStride + x.toLong() * pixelStride
                if (index >= source.limit()) return null
                bytes[y * width + x] = source.get(index.toInt())
            }
        }
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
                PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false),
            ))).text
        } catch (_: ReaderException) {
            null
        }
    }
}
