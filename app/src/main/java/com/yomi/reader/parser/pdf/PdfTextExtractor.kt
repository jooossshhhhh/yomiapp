package com.yomi.reader.parser.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

data class PdfSearchResult(
    val pageNumber: Int,
    val snippet: String
)

object PdfTextExtractor {

    suspend fun searchInPdf(context: Context, uri: Uri, query: String, totalPages: Int): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val fullText = extractText(context, uri)
        if (fullText.startsWith("Unable") || fullText.startsWith("Text extraction") || fullText.startsWith("No extractable")) {
            return@withContext emptyList()
        }

        val lines = fullText.lines().filter { it.isNotBlank() }
        val results = mutableListOf<PdfSearchResult>()
        val linesPerPage = (lines.size.toFloat() / totalPages.coerceAtLeast(1).toFloat()).coerceAtLeast(1f)

        for ((index, line) in lines.withIndex()) {
            if (line.contains(query, ignoreCase = true)) {
                val approxPage = ((index / linesPerPage).toInt() + 1).coerceIn(1, totalPages)
                val trimmed = line.trim()
                val matchIdx = trimmed.indexOf(query, ignoreCase = true)
                val start = (matchIdx - 20).coerceAtLeast(0)
                val end = (matchIdx + query.length + 35).coerceAtMost(trimmed.length)
                val snippet = (if (start > 0) "..." else "") + trimmed.substring(start, end) + (if (end < trimmed.length) "..." else "")
                results.add(PdfSearchResult(pageNumber = approxPage, snippet = snippet))
                if (results.size >= 50) break
            }
        }
        results
    }

    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                extractTextFromStream(stream)
            } ?: "Unable to read document stream."
        } catch (e: Exception) {
            "Text extraction error: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    private fun extractTextFromStream(stream: InputStream): String {
        val bytes = stream.readBytes()
        val textBuilder = StringBuilder()

        val streamTag = "stream".toByteArray(Charsets.US_ASCII)
        val endstreamTag = "endstream".toByteArray(Charsets.US_ASCII)

        var idx = 0
        while (idx < bytes.size) {
            val streamStart = indexOf(bytes, streamTag, idx)
            if (streamStart == -1) break

            var contentStart = streamStart + 6
            while (contentStart < bytes.size && (bytes[contentStart] == '\r'.code.toByte() || bytes[contentStart] == '\n'.code.toByte())) {
                contentStart++
            }

            val streamEnd = indexOf(bytes, endstreamTag, contentStart)
            if (streamEnd == -1) break

            var contentEnd = streamEnd
            while (contentEnd > contentStart && (bytes[contentEnd - 1] == '\r'.code.toByte() || bytes[contentEnd - 1] == '\n'.code.toByte())) {
                contentEnd--
            }

            val length = contentEnd - contentStart
            if (length > 0) {
                val streamBytes = bytes.copyOfRange(contentStart, contentEnd)
                val decompressed = tryDecompress(streamBytes) ?: streamBytes
                val contentString = String(decompressed, Charsets.ISO_8859_1)

                if (contentString.contains("BT") && contentString.contains("ET")) {
                    parseTextFromContentStream(contentString, textBuilder)
                }
            }

            idx = streamEnd + 9
        }

        val result = textBuilder.toString().trim()
        return if (result.length >= 10) {
            result
        } else {
            "No extractable text found in this PDF.\n\nNote: Documents consisting of scanned photos or flattened vector drawings do not contain encoded text streams."
        }
    }

    private fun tryDecompress(bytes: ByteArray): ByteArray? {
        return try {
            val bais = ByteArrayInputStream(bytes)
            val inflater = InflaterInputStream(bais)
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var read: Int
            while (inflater.read(buffer).also { read = it } != -1) {
                baos.write(buffer, 0, read)
            }
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTextFromContentStream(streamContent: String, output: StringBuilder) {
        val btIdx = streamContent.indexOf("BT")
        if (btIdx == -1) return

        val blocks = streamContent.split("BT")
        for (block in blocks) {
            val etIdx = block.indexOf("ET")
            val textBlock = if (etIdx != -1) block.substring(0, etIdx) else block

            // Extract string literals in (...)
            var i = 0
            while (i < textBlock.length) {
                val openParen = textBlock.indexOf('(', i)
                if (openParen == -1) break

                var closeParen = -1
                var depth = 1
                var j = openParen + 1
                while (j < textBlock.length) {
                    val c = textBlock[j]
                    if (c == '\\') {
                        j += 2
                        continue
                    } else if (c == '(') {
                        depth++
                    } else if (c == ')') {
                        depth--
                        if (depth == 0) {
                            closeParen = j
                            break
                        }
                    }
                    j++
                }

                if (closeParen != -1) {
                    val rawText = textBlock.substring(openParen + 1, closeParen)
                    val cleaned = unescapePdfString(rawText)
                    if (cleaned.isNotBlank()) {
                        output.append(cleaned).append(" ")
                    }
                    i = closeParen + 1
                } else {
                    break
                }
            }

            // Check for line endings in PDF stream: T*, ', "
            if (textBlock.contains("T*") || textBlock.contains("'") || textBlock.contains("TD") || textBlock.contains("Td")) {
                output.append("\n")
            }
        }
    }

    private fun unescapePdfString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (input[i + 1]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '(' -> sb.append('(')
                    ')' -> sb.append(')')
                    else -> sb.append(input[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex >= source.size || target.isEmpty()) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}
