/**
 * Purpose: Generates and decodes QR codes for campsites using ZXing (US 01.12.01, US 02.02.01).
 * Outstanding issues: None known. The camera scanner is integrated in Flow 16.
 * Third-party library: ZXing core provides QRCodeWriter and BitMatrix;
 * https://github.com/zxing/zxing.
 */
/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026,
 * following the QRCode class in the team's class diagram. It replaced the fixed-capacity
 * hand-written matrix encoder with ZXing to properly support arbitrary-length Firestore IDs
 * without truncation, added BitMatrix generation, and provided KDoc. Raghav Sethi reviewed,
 * tested, and committed it.
 */
package com.lambda.campsite.model

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates and decodes QR codes for campsites (US 01.12.01, US 02.02.01).
 *
 * Each campsite gets a unique QR code pointing to its URI. Entrants scan it to
 * open the campsite, and owners can view and share it from their details screen.
 */
object QRCode {

    /** The deep link URI prefix for campsite QR codes. */
    const val URI_PREFIX = "lambda-campsite://campsite/"

    /** Alternate short prefix for campsite IDs. */
    const val SHORT_PREFIX = "campsite:"

    /**
     * Returns `true` if [contents] belongs to Lambda Campsite.
     *
     * @param contents the scanned string from a QR code.
     */
    fun isOurs(contents: String): Boolean =
        contents.startsWith(URI_PREFIX) || contents.startsWith(SHORT_PREFIX)

    /**
     * Extracts the campsite ID from [contents] if it belongs to this app,
     * or `null` if it's an unrecognized QR code.
     *
     * @param contents the scanned QR code content string.
     */
    fun decode(contents: String): String? {
        return when {
            contents.startsWith(URI_PREFIX) -> contents.removePrefix(URI_PREFIX).trim().ifEmpty { null }
            contents.startsWith(SHORT_PREFIX) -> contents.removePrefix(SHORT_PREFIX).trim().ifEmpty { null }
            else -> null
        }
    }

    /**
     * Generates a scannable QR code [Bitmap] for [campsite] (US 02.02.01).
     *
     * @param campsite the campsite whose QR code is being generated.
     * @param sizePx the pixel width and height of the returned image. Defaults to 512.
     * @return a high-contrast black-and-white [Bitmap].
     */
    fun generate(campsite: Campsite, sizePx: Int = 512): Bitmap =
        generate(campsite.id, sizePx)

    /**
     * Generates a scannable QR code [Bitmap] for an arbitrary campsite ID.
     */
    fun generate(campsiteId: String, sizePx: Int = 512): Bitmap {
        val uri = "$URI_PREFIX$campsiteId"
        val bitMatrix = encodeBitMatrix(uri, sizePx)
        return bitMatrixToBitmap(bitMatrix)
    }

    /**
     * Encodes [payload] into a ZXing [BitMatrix] with Error Correction Level M.
     * Usable in both Android and JVM unit tests without Android Bitmap dependencies.
     */
    fun encodeBitMatrix(payload: String, sizePx: Int = 512): BitMatrix {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        return QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    /**
     * Converts a ZXing [BitMatrix] into an Android [Bitmap].
     */
    fun bitMatrixToBitmap(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
