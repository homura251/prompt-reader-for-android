package com.promptreader.android.png

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.InflaterInputStream

object PngTextChunkReader {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Reads PNG tEXt/iTXt/zTXt chunks into a keyword->text map. */
    fun readTextChunks(input: InputStream): Map<String, String> {
        val dataIn = DataInputStream(input)
        val sig = ByteArray(8)
        dataIn.readFully(sig)
        if (!sig.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Not a PNG")
        }

        val out = LinkedHashMap<String, String>()

        while (true) {
            val length: Int = try {
                dataIn.readInt()
            } catch (e: EOFException) {
                break
            }
            val typeBytes = ByteArray(4)
            dataIn.readFully(typeBytes)
            val type = String(typeBytes, Charsets.ISO_8859_1)

            val chunkData = ByteArray(length)
            dataIn.readFully(chunkData)

            // CRC
            dataIn.readInt()

            when (type) {
                "tEXt" -> {
                    val (k, v) = parseText(chunkData)
                    if (k.isNotBlank()) out[k] = v
                }
                "zTXt" -> {
                    val (k, v) = parseZtxt(chunkData)
                    if (k.isNotBlank()) out[k] = v
                }
                "iTXt" -> {
                    val (k, v) = parseItxt(chunkData)
                    if (k.isNotBlank()) out[k] = v
                }
                "IEND" -> break
            }
        }

        return out
    }

    private fun parseText(data: ByteArray): Pair<String, String> {
        val idx = data.indexOf(0)
        if (idx <= 0) return "" to ""
        val key = String(data, 0, idx, Charsets.ISO_8859_1)
        val text = String(data, idx + 1, data.size - (idx + 1), Charsets.ISO_8859_1)
        return key to text
    }

    private fun parseZtxt(data: ByteArray): Pair<String, String> {
        val idx = data.indexOf(0)
        if (idx <= 0 || idx + 2 > data.size) return "" to ""
        val key = String(data, 0, idx, Charsets.ISO_8859_1)
        val compressionMethod = data[idx + 1].toInt() and 0xFF
        if (compressionMethod != 0) return key to ""
        val compressed = data.copyOfRange(idx + 2, data.size)
        val textBytes = inflateZlib(compressed)
        val text = String(textBytes, Charsets.UTF_8)
        return key to text
    }

    private fun parseItxt(data: ByteArray): Pair<String, String> {
        // keyword\0 compressionFlag compressionMethod languageTag\0 translatedKeyword\0 text
        var p = 0
        val keywordEnd = data.indexOf(0)
        if (keywordEnd <= 0) return "" to ""
        val key = String(data, 0, keywordEnd, Charsets.ISO_8859_1)
        p = keywordEnd + 1
        if (p + 2 > data.size) return key to ""

        val compressionFlag = data[p].toInt() and 0xFF
        val compressionMethod = data[p + 1].toInt() and 0xFF
        p += 2

        val langEnd = data.indexOf(0, startIndex = p)
        if (langEnd < 0) return key to ""
        p = langEnd + 1

        val transEnd = data.indexOf(0, startIndex = p)
        if (transEnd < 0) return key to ""
        p = transEnd + 1

        if (p > data.size) return key to ""
        val textBytes = data.copyOfRange(p, data.size)

        val decoded = if (compressionFlag == 1 && compressionMethod == 0) {
            inflateZlib(textBytes)
        } else {
            textBytes
        }

        // iTXt is UTF-8 per spec.
        val text = String(decoded, Charsets.UTF_8)
        return key to text
    }

    private fun inflateZlib(compressed: ByteArray): ByteArray {
        InflaterInputStream(ByteArrayInputStream(compressed)).use { inflater ->
            val buffer = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            while (true) {
                val n = inflater.read(buffer)
                if (n <= 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun ByteArray.indexOf(value: Int, startIndex: Int = 0): Int {
        for (i in startIndex until this.size) {
            if ((this[i].toInt() and 0xFF) == value) return i
        }
        return -1
    }
}
