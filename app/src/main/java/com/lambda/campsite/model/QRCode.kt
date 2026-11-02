/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026,
 * following the QRCode class in the team's class diagram. It helped with the standard
 * QR code matrix encoding, Bitmap generation, URI decoding, and KDoc. Raghav Sethi
 * reviewed and committed it.
 */
package com.lambda.campsite.model

import android.graphics.Bitmap
import android.graphics.Color

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
        generateForUri("$URI_PREFIX${campsite.id}", sizePx)

    /**
     * Generates a scannable QR code [Bitmap] for an arbitrary campsite ID.
     */
    fun generate(campsiteId: String, sizePx: Int = 512): Bitmap =
        generateForUri("$URI_PREFIX$campsiteId", sizePx)

    /**
     * Generates a QR code bitmap for [uri].
     */
    fun generateForUri(uri: String, sizePx: Int = 512): Bitmap {
        val matrix = generateMatrix(uri)
        val moduleCount = matrix.size
        val quietZone = 4
        val totalModules = moduleCount + quietZone * 2
        val scale = (sizePx / totalModules).coerceAtLeast(1)
        val imageSize = totalModules * scale

        val bitmap = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(imageSize * imageSize)

        for (y in 0 until imageSize) {
            val moduleY = y / scale - quietZone
            for (x in 0 until imageSize) {
                val moduleX = x / scale - quietZone
                val isBlack = if (moduleY in 0 until moduleCount && moduleX in 0 until moduleCount) {
                    matrix[moduleY][moduleX]
                } else {
                    false
                }
                pixels[y * imageSize + x] = if (isBlack) Color.BLACK else Color.WHITE
            }
        }

        bitmap.setPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)
        return bitmap
    }

    /**
     * Generates a standard 2D boolean module matrix for [payload] using Version 3 QR Code (29x29)
     * with Error Correction Level M.
     */
    internal fun generateMatrix(payload: String): Array<BooleanArray> {
        val size = 29
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun setFunctionModule(r: Int, c: Int, black: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                matrix[r][c] = black
                reserved[r][c] = true
            }
        }

        // 1. Finder patterns
        fun drawFinder(topRow: Int, leftCol: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val row = topRow + r
                    val col = leftCol + c
                    if (row in 0 until size && col in 0 until size) {
                        val isBlack = (r in 0..6 && (c == 0 || c == 6)) ||
                                (c in 0..6 && (r == 0 || r == 6)) ||
                                (r in 2..4 && c in 2..4)
                        setFunctionModule(row, col, isBlack)
                    }
                }
            }
        }
        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // 2. Alignment pattern at (22, 22) for Version 3
        val alignR = 22
        val alignC = 22
        for (r in -2..2) {
            for (c in -2..2) {
                val isBlack = (r == -2 || r == 2 || c == -2 || c == 2 || (r == 0 && c == 0))
                setFunctionModule(alignR + r, alignC + c, isBlack)
            }
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val isBlack = (i % 2 == 0)
            setFunctionModule(6, i, isBlack)
            setFunctionModule(i, 6, isBlack)
        }

        // 4. Dark module
        setFunctionModule(size - 8, 8, true)

        // 5. Reserve format information areas
        for (i in 0..8) {
            setFunctionModule(8, i, false)
            setFunctionModule(i, 8, false)
        }
        for (i in 0..7) {
            setFunctionModule(8, size - 1 - i, false)
            setFunctionModule(size - 1 - i, 8, false)
        }

        // 6. Data Encoding (Byte mode, Version 3, EC Level M -> 44 data codewords, 26 EC codewords)
        val dataCodewords = 44
        val ecCodewords = 26
        val rawBytes = payload.toByteArray(Charsets.ISO_8859_1)

        val bits = ArrayList<Boolean>()
        fun addBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add(((value ushr i) and 1) == 1)
            }
        }

        // Mode indicator for 8-bit byte mode is 0100
        addBits(4, 4)
        // Character count indicator (8 bits for Version 1-9)
        addBits(rawBytes.size.coerceAtMost(dataCodewords - 2), 8)
        // Data bytes
        for (i in 0 until rawBytes.size.coerceAtMost(dataCodewords - 2)) {
            addBits(rawBytes[i].toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeroes)
        val maxDataBits = dataCodewords * 8
        val terminatorBits = (maxDataBits - bits.size).coerceIn(0, 4)
        addBits(0, terminatorBits)
        // Pad to byte boundary
        while (bits.size % 8 != 0 && bits.size < maxDataBits) {
            bits.add(false)
        }
        // Pad bytes (0xEC, 0x11)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < maxDataBits) {
            addBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        // Convert data bits to codewords
        val data = IntArray(dataCodewords)
        for (i in 0 until dataCodewords) {
            var byteVal = 0
            for (b in 0 until 8) {
                if (bits[i * 8 + b]) byteVal = byteVal or (1 shl (7 - b))
            }
            data[i] = byteVal
        }

        // Reed-Solomon computation for 26 error correction codewords
        val ec = calculateReedSolomon(data, ecCodewords)

        // Combined codewords
        val allCodewords = IntArray(dataCodewords + ecCodewords)
        System.arraycopy(data, 0, allCodewords, 0, dataCodewords)
        System.arraycopy(ec, 0, allCodewords, dataCodewords, ecCodewords)

        // Flatten all codewords into bits
        val allBits = BooleanArray(allCodewords.size * 8)
        for (i in allCodewords.indices) {
            val v = allCodewords[i]
            for (b in 0 until 8) {
                allBits[i * 8 + b] = ((v ushr (7 - b)) and 1) == 1
            }
        }

        // 7. Place data bits into matrix in 2-column zig-zag right to left
        var bitIndex = 0
        var goingUp = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column
            val rows = if (goingUp) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (c in 0..1) {
                    val currCol = col - c
                    if (!reserved[row][currCol]) {
                        val bit = if (bitIndex < allBits.size) allBits[bitIndex++] else false
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        val mask = (row + currCol) % 2 == 0
                        matrix[row][currCol] = bit xor mask
                    }
                }
            }
            col -= 2
            goingUp = !goingUp
        }

        // 8. Place Format Information: EC Level M (00) + Mask 0 (000) -> 101010000010010
        val formatBits = intArrayOf(1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0)
        for (i in 0..5) matrix[8][i] = formatBits[i] == 1
        matrix[8][7] = formatBits[6] == 1
        matrix[8][8] = formatBits[7] == 1
        matrix[7][8] = formatBits[8] == 1
        for (i in 0..5) matrix[5 - i][8] = formatBits[9 + i] == 1

        for (i in 0..7) matrix[size - 1 - i][8] = formatBits[i] == 1
        for (i in 0..6) matrix[8][size - 7 + i] = formatBits[8 + i] == 1

        return matrix
    }

    /** Reed-Solomon generator polynomial computation in GF(256). */
    private fun calculateReedSolomon(data: IntArray, ecCount: Int): IntArray {
        // GF(256) tables with primitive polynomial 0x11D (285)
        val exp = IntArray(512)
        val log = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 285
        }

        fun gfMul(a: Int, b: Int): Int =
            if (a == 0 || b == 0) 0 else exp[(log[a] + log[b]) % 255]

        // Build generator polynomial: prod_{i=0}^{ecCount-1} (x - alpha^i)
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gen[j]
                nextGen[j + 1] = nextGen[j + 1] xor gfMul(gen[j], exp[i])
            }
            gen = nextGen
        }

        // Synthetic division
        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = b xor remainder[0]
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1)
            remainder[ecCount - 1] = 0
            for (j in 0 until ecCount) {
                remainder[j] = remainder[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return remainder
    }
}
