package com.yomi.reader.util.conversion

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Normalizes `.xlsx` worksheets on a temporary copy right before LibreOfficeKit loads them so
 * every sheet scales cleanly onto 1 standard A4 page (portrait or landscape based on sheet
 * proportions) without manual page-break splits or distorted giant FullSheetPreview canvases.
 */
object XlsxPreProcessor {

    private val CELL_REF_REGEX = Regex("""<c\b[^>]*\br="([A-Z]+)(\d+)"""")
    private val DIMENSION_REGEX = Regex("""<dimension\b[^>]*\bref="(?:[A-Z]+\d+:)?([A-Z]+)(\d+)"""")
    private val COL_DEF_REGEX = Regex("""<col\b[^>]*\bmin="(\d+)"[^>]*\bmax="(\d+)"(?:[^>]*\bwidth="([0-9.]+)")?""")
    private val ROW_BREAKS_REGEX = Regex("""<rowBreaks\b[^>]*>.*?</rowBreaks>|<rowBreaks\b[^/>]*/>""", RegexOption.DOT_MATCHES_ALL)
    private val COL_BREAKS_REGEX = Regex("""<colBreaks\b[^>]*>.*?</colBreaks>|<colBreaks\b[^/>]*/>""", RegexOption.DOT_MATCHES_ALL)
    private val PAGE_SETUP_REGEX = Regex("""<pageSetup\b[^>]*>.*?</pageSetup>|<pageSetup\b[^/>]*/>""", RegexOption.DOT_MATCHES_ALL)
    private val PAGE_MARGINS_REGEX = Regex("""<pageMargins\b[^>]*/>|<pageMargins\b[^>]*>.*?</pageMargins>""", RegexOption.DOT_MATCHES_ALL)

    fun normalizeForPdfExport(inputXlsx: File, outputXlsx: File): Boolean {
        return runCatching {
            val buffer = ByteArrayOutputStream()
            ZipInputStream(FileInputStream(inputXlsx)).use { zis ->
                ZipOutputStream(buffer).use { zos ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val bytes = zis.readBytes()
                        val processedBytes = if (isWorksheetXml(name)) {
                            normalizeWorksheetXml(bytes)
                        } else {
                            bytes
                        }
                        zos.putNextEntry(ZipEntry(name))
                        zos.write(processedBytes)
                        zos.closeEntry()
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            outputXlsx.parentFile?.mkdirs()
            FileOutputStream(outputXlsx).use { it.write(buffer.toByteArray()) }
            true
        }.getOrDefault(false)
    }

    private fun isWorksheetXml(entryName: String): Boolean {
        val lower = entryName.lowercase()
        return lower.startsWith("xl/worksheets/sheet") && lower.endsWith(".xml")
    }

    internal fun normalizeWorksheetXml(xmlBytes: ByteArray): ByteArray {
        val original = String(xmlBytes, Charsets.UTF_8)
        var xml = original

        val orientation = detectBestOrientation(xml)

        // 1. Ensure <sheetPr> enables fitToPage="1"
        xml = ensureFitToPageInSheetPr(xml)

        // 2. Remove manual page breaks that would split a sheet onto multiple pages
        xml = ROW_BREAKS_REGEX.replace(xml, "")
        xml = COL_BREAKS_REGEX.replace(xml, "")

        // 3. Apply compact margins + standard A4 1x1 scaling in OOXML <worksheet> schema order
        val marginsTag = """<pageMargins left="0.3" right="0.3" top="0.4" bottom="0.4" header="0.2" footer="0.2"/>"""
        val setupTag = """<pageSetup paperSize="9" orientation="$orientation" fitToWidth="1" fitToHeight="1"/>"""

        xml = if (PAGE_MARGINS_REGEX.containsMatchIn(xml)) {
            PAGE_MARGINS_REGEX.replaceFirst(xml, marginsTag)
        } else {
            insertBeforeFirstTag(
                xml,
                listOf("<pageSetup", "<headerFooter", "<drawing", "<legacyDrawing", "<tableParts", "<extLst", "</worksheet>"),
                marginsTag
            )
        }

        xml = if (PAGE_SETUP_REGEX.containsMatchIn(xml)) {
            PAGE_SETUP_REGEX.replaceFirst(xml, setupTag)
        } else {
            insertBeforeFirstTag(
                xml,
                listOf("<headerFooter", "<drawing", "<legacyDrawing", "<tableParts", "<extLst", "</worksheet>"),
                setupTag
            )
        }

        return if (xml == original) xmlBytes else xml.toByteArray(Charsets.UTF_8)
    }

    internal fun detectBestOrientation(xml: String): String {
        var maxCol = 1
        var maxRow = 1

        DIMENSION_REGEX.find(xml)?.let { match ->
            maxCol = maxOf(maxCol, colLettersToIndex(match.groupValues[1]))
            maxRow = maxOf(maxRow, match.groupValues[2].toIntOrNull() ?: 1)
        }
        for (match in CELL_REF_REGEX.findAll(xml)) {
            maxCol = maxOf(maxCol, colLettersToIndex(match.groupValues[1]))
            maxRow = maxOf(maxRow, match.groupValues[2].toIntOrNull() ?: 1)
        }

        // Compute rough character-width units across used columns
        var widthScore = 0.0
        val explicitWidths = HashMap<Int, Double>()
        for (match in COL_DEF_REGEX.findAll(xml)) {
            val min = match.groupValues[1].toIntOrNull() ?: continue
            val max = (match.groupValues[2].toIntOrNull() ?: min).coerceAtMost(maxCol)
            val width = match.groupValues[3].toDoubleOrNull() ?: 10.0
            for (c in min..max) {
                explicitWidths[c] = width
            }
        }
        for (c in 1..maxCol) {
            widthScore += explicitWidths[c] ?: 10.0
        }

        // Standard A4 portrait printable ratio ~7.3" x 10.8"; ~8.4 char-widths ~ 1 inch, ~4.5 rows ~ 1 inch
        val approxInchesW = widthScore / 8.5
        val approxInchesH = maxRow / 4.2
        return if (maxCol >= 7 || approxInchesW > approxInchesH * 0.92) {
            "landscape"
        } else {
            "portrait"
        }
    }

    private fun colLettersToIndex(letters: String): Int {
        var col = 0
        for (ch in letters.uppercase()) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            }
        }
        return col.coerceAtLeast(1)
    }

    private fun ensureFitToPageInSheetPr(xml: String): String {
        val openSheetPrRegex = Regex("""<sheetPr\b([^>]*)>""")
        val selfCloseMatch = Regex("""<sheetPr\b[^>]*/>""").find(xml)
        if (selfCloseMatch != null) {
            val openTag = selfCloseMatch.value.removeSuffix("/>") + ">"
            return xml.replaceRange(
                selfCloseMatch.range,
                """$openTag<pageSetUpPr fitToPage="1"/></sheetPr>"""
            )
        }

        val openMatch = openSheetPrRegex.find(xml)
        if (openMatch != null) {
            val closeIdx = xml.indexOf("</sheetPr>", openMatch.range.last + 1)
            if (closeIdx >= 0) {
                val inner = xml.substring(openMatch.range.last + 1, closeIdx)
                val pageSetUpPrRegex = Regex("""<pageSetUpPr\b([^/>]*)/>""")
                val existingPr = pageSetUpPrRegex.find(inner)
                val newInner = if (existingPr != null) {
                    val attrs = existingPr.groupValues[1]
                    val updatedPr = if (attrs.contains("fitToPage=")) {
                        """<pageSetUpPr${attrs.replace(Regex("""fitToPage="[^"]*""""), """fitToPage="1"""")}/>"""
                    } else {
                        """<pageSetUpPr$attrs fitToPage="1"/>"""
                    }
                    inner.replaceRange(existingPr.range, updatedPr)
                } else {
                    """$inner<pageSetUpPr fitToPage="1"/>"""
                }
                return xml.substring(0, openMatch.range.last + 1) + newInner + xml.substring(closeIdx)
            }
        }

        return insertBeforeFirstTag(
            xml,
            listOf("<dimension", "<sheetViews", "<sheetFormatPr", "<cols", "<sheetData"),
            """<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>"""
        )
    }

    private fun insertBeforeFirstTag(xml: String, candidatePrefixes: List<String>, snippet: String): String {
        for (prefix in candidatePrefixes) {
            val idx = xml.indexOf(prefix)
            if (idx >= 0) {
                return xml.substring(0, idx) + snippet + xml.substring(idx)
            }
        }
        return xml
    }
}

