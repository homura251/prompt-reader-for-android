package com.promptreader.android.novelai

import android.graphics.Bitmap
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

object NovelAiStealthDecoder {
    private const val MAGIC = "stealth_pngcomp"

    fun tryDecode(bitmap: Bitmap): JSONObject? {
        // Needs alpha channel. We'll still attempt; if alpha always 255 it won't match.
        val extractor = LsbExtractor(bitmap)
        val magicBytes = extractor.nextBytes(MAGIC.length)
        val magic = runCatching { String(magicBytes, Charsets.UTF_8) }.getOrNull() ?: return null
        if (magic != MAGIC) return null

        val bitLen = extractor.readInt32BE() ?: return null
        val byteLen = bitLen / 8
        if (byteLen <= 0 || byteLen > 50_000_000) return null

        val gzipData = extractor.nextBytes(byteLen)
        val jsonText = gunzipToString(gzipData) ?: return null
        return runCatching { JSONObject(jsonText) }.getOrNull()
    }

    private fun gunzipToString(data: ByteArray): String? {
        return runCatching {
            GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = gis.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    private class LsbExtractor(private val bitmap: Bitmap) {
        private val width = bitmap.width
        private val height = bitmap.height

        private var bits = 0
        private var currentByte = 0
        private var row = 0
        private var col = 0

        private fun nextBit(): Int {
            if (row >= height || col >= width) return 0
            val pixel = bitmap.getPixel(col, row)
            val alpha = (pixel ushr 24) and 0xFF
            val bit = alpha and 1

            bits += 1
            currentByte = (currentByte shl 1) or bit

            row += 1
            if (row == height) {
                row = 0
                col += 1
            }
            return bit
        }

        private fun nextByte(): Int {
            while (bits < 8) {
                nextBit()
                if (row >= height && col >= width) break
            }
            val b = currentByte and 0xFF
            bits = 0
            currentByte = 0
            return b
        }

        fun nextBytes(n: Int): ByteArray {
            val out = ByteArray(n)
            for (i in 0 until n) {
                out[i] = nextByte().toByte()
            }
            return out
        }

        fun readInt32BE(): Int? {
            val b = nextBytes(4)
            if (b.size != 4) return null
            return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
        }
    }
}
