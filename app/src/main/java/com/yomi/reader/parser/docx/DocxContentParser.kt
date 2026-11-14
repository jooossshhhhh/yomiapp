package com.yomi.reader.parser.docx

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.Borders
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.VerticalAlign
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class DocxTabStop(
    val posPt: Float,
    val align: String = "left"
)

data class DocxRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontSizeSp: Float? = null,
    val fontFamily: String? = null,
    val colorHex: String? = null,
    val highlightColorHex: String? = null,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false
)

data class DocxTableCell(
    val paragraphs: List<DocxElement.Paragraph>,
    val plainText: String,
    val backgroundHex: String? = null,
    val gridSpan: Int = 1,
    val vMergeContinue: Boolean = false,
    val verticalAlign: String = "top",
    val widthPt: Float? = null,
    val hasTopBorder: Boolean = true,
    val hasBottomBorder: Boolean = true,
    val hasLeftBorder: Boolean = true,
    val hasRightBorder: Boolean = true,
    val topBorderColorHex: String? = null,
    val bottomBorderColorHex: String? = null,
    val leftBorderColorHex: String? = null,
    val rightBorderColorHex: String? = null
)

sealed class DocxElement(open val id: String) {
    data class Paragraph(
        override val id: String = UUID.randomUUID().toString(),
        var text: String,
        val runs: List<DocxRun> = emptyList(),
        var isHeading: Boolean = false,
        var headingLevel: Int = 0,
        var isBold: Boolean = false,
        var isItalic: Boolean = false,
        var isUnderline: Boolean = false,
        var isBullet: Boolean = false,
        var isNumbered: Boolean = false,
        var listPrefix: String? = null,
        var alignment: TextAlign = TextAlign.Start,
        var indentStartDp: Int = 0,
        var indentEndDp: Int = 0,
        var firstLineIndentDp: Int = 0,
        var hangingIndentDp: Int = 0,
        var spacingBeforePt: Float = 0f,
        var spacingAfterDp: Int = 6,
        var lineSpacingMultiplier: Float = 1.15f,
        var fontSizeSp: Float? = null,
        var fontFamily: String? = null,
        var colorHex: String? = null,
        var backgroundFillHex: String? = null,
        var hasBottomBorder: Boolean = false,
        var hasPageBreakBefore: Boolean = false,
        val hasSoftPageBreakHint: Boolean = false,
        val hasPageBreakAfter: Boolean = false,
        val keepNext: Boolean = false,
        val isEmptyLine: Boolean = false,
        val tabStopsPt: List<DocxTabStop> = emptyList()
    ) : DocxElement(id)

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val imageBytes: ByteArray,
        val extension: String,
        val fileName: String? = null,
        val widthDp: Int = 0,
        val heightDp: Int = 0,
        val widthPt: Float = 0f,
        val heightPt: Float = 0f,
        val alignment: TextAlign = TextAlign.Center
    ) : DocxElement(id) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Image
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        val rows: List<List<String>>,
        val richRows: List<List<DocxTableCell>> = emptyList(),
        val columnWidthsPt: List<Float> = emptyList(),
        val isBorderless: Boolean = false
    ) : DocxElement(id)
}

data class DocxDocumentModel(
    val elements: List<DocxElement>,
    val headerElements: List<DocxElement> = emptyList(),
    val footerElements: List<DocxElement> = emptyList(),
    val paperName: String = "Letter (8.5\" × 11\")",
    val pageWidthDp: Int = 612,
    val pageHeightDp: Int = 792,
    val marginLeftPt: Float = 72f,
    val marginRightPt: Float = 72f,
    val marginTopPt: Float = 72f,
    val marginBottomPt: Float = 72f,
    val paddingHorizontalDp: Int = 24,
    val paddingVerticalDp: Int = 32,
    val totalImages: Int = 0,
    val totalTables: Int = 0,
    val totalParagraphs: Int = 0,
    val embeddedFontPaths: Map<String, Map<Int, String>> = emptyMap()
)

class DocxContentParser(private val context: Context) {

    private fun mapAlignment(alignment: ParagraphAlignment?): TextAlign {
        return when (alignment) {
            ParagraphAlignment.CENTER -> TextAlign.Center
            ParagraphAlignment.RIGHT -> TextAlign.End
            ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> TextAlign.Justify
            else -> TextAlign.Start
        }
    }

    private fun normalizeHexColor(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().removePrefix("#").uppercase()
        if (cleaned == "AUTO" || cleaned == "NONE" || cleaned == "TRANSPARENT") return null
        return if (cleaned.length == 6 && cleaned.all { it in '0'..'9' || it in 'A'..'F' }) {
            cleaned
        } else null
    }

    private fun mapHighlightColor(name: String?): String? {
        return when (name?.lowercase()) {
            "yellow" -> "FFFF00"
            "green" -> "00FF00"
            "cyan" -> "00FFFF"
            "magenta" -> "FF00FF"
            "blue" -> "0000FF"
            "red" -> "FF0000"
            "darkblue" -> "000080"
            "darkcyan" -> "008080"
            "darkgreen" -> "008000"
            "darkmagenta" -> "800080"
            "darkred" -> "800000"
            "darkyellow" -> "808000"
            "darkgray" -> "808080"
            "lightgray" -> "D3D3D3"
            "black" -> "000000"
            else -> null
        }
    }

    private data class StyleRunProps(
        val rStyleId: String? = null,
        val isBold: Boolean? = null,
        val isItalic: Boolean? = null,
        val isUnderline: Boolean? = null,
        val isStrike: Boolean? = null,
        val fontSizeSp: Float? = null,
        val fontFamily: String? = null,
        val colorHex: String? = null
    )

    private data class StyleParaProps(
        val alignment: TextAlign? = null,
        val spacingBeforePt: Float? = null,
        val spacingAfterDp: Int? = null,
        val lineSpacingMultiplier: Float? = null,
        val lineSpacingExactPt: Float? = null,
        val indentLeftDp: Int? = null,
        val indentRightDp: Int? = null,
        val firstLineIndentDp: Int? = null,
        val hangingIndentDp: Int? = null,
        val keepNext: Boolean? = null,
        val tabStops: List<DocxTabStop> = emptyList()
    )

    private data class TableBorderSpec(
        val visible: Boolean,
        val colorHex: String? = null
    )

    private data class TableBordersSet(
        val top: TableBorderSpec? = null,
        val bottom: TableBorderSpec? = null,
        val left: TableBorderSpec? = null,
        val right: TableBorderSpec? = null,
        val insideH: TableBorderSpec? = null,
        val insideV: TableBorderSpec? = null
    )

    private data class TableStyleInfo(
        val styleId: String,
        val basedOn: String? = null,
        val borders: TableBordersSet = TableBordersSet()
    )

    private data class DocDefaultsContext(
        val runProps: StyleRunProps = StyleRunProps(),
        val paraProps: StyleParaProps = StyleParaProps(),
        val majorFont: String? = null,
        val minorFont: String? = null,
        val defaultParagraphStyleId: String = "Normal",
        val tableStyles: Map<String, TableStyleInfo> = emptyMap()
    )

    private fun parseToggleAttr(cursor: org.apache.xmlbeans.XmlCursor): Boolean {
        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val raw = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.trim()?.lowercase()
        return raw == null || raw == "1" || raw == "true" || raw == "on"
    }

    private fun resolveThemeFont(themeAttr: String?, majorFont: String?, minorFont: String?): String? {
        if (themeAttr.isNullOrBlank()) return null
        val lower = themeAttr.lowercase()
        return when {
            lower.startsWith("major") -> majorFont ?: "Calibri"
            lower.startsWith("minor") -> minorFont ?: "Calibri"
            else -> null
        }
    }

    private fun parseTblBordersXml(bordersObj: org.apache.xmlbeans.XmlObject?): TableBordersSet {
        if (bordersObj == null) return TableBordersSet()
        var top: TableBorderSpec? = null
        var bottom: TableBorderSpec? = null
        var left: TableBorderSpec? = null
        var right: TableBorderSpec? = null
        var insideH: TableBorderSpec? = null
        var insideV: TableBorderSpec? = null

        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        runCatching {
            val cursor = bordersObj.newCursor()
            if (cursor.toFirstChild()) {
                do {
                    val edgeName = cursor.name?.localPart ?: continue
                    val valAttr = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.lowercase()
                    val colorHex = normalizeHexColor(cursor.getAttributeText(javax.xml.namespace.QName(wNs, "color")))
                    val isHidden = valAttr == "none" || valAttr == "nil"
                    val spec = TableBorderSpec(visible = !isHidden, colorHex = colorHex)
                    when (edgeName) {
                        "top" -> top = spec
                        "bottom" -> bottom = spec
                        "left", "start" -> left = spec
                        "right", "end" -> right = spec
                        "insideH" -> insideH = spec
                        "insideV" -> insideV = spec
                    }
                } while (cursor.toNextSibling())
            }
            cursor.close()
        }
        return TableBordersSet(top, bottom, left, right, insideH, insideV)
    }

    private fun mergeTableBorders(base: TableBordersSet, override: TableBordersSet): TableBordersSet = TableBordersSet(
        top = override.top ?: base.top,
        bottom = override.bottom ?: base.bottom,
        left = override.left ?: base.left,
        right = override.right ?: base.right,
        insideH = override.insideH ?: base.insideH,
        insideV = override.insideV ?: base.insideV
    )

    private fun resolveTableStyleBorders(
        styleId: String?,
        tableStyles: Map<String, TableStyleInfo>
    ): TableBordersSet {
        if (styleId.isNullOrBlank()) return TableBordersSet()
        val chain = mutableListOf<TableStyleInfo>()
        var current: String? = styleId
        var depth = 0
        while (!current.isNullOrBlank() && depth < 6) {
            val info = tableStyles[current] ?: break
            chain.add(info)
            current = info.basedOn
            depth++
        }
        var merged = TableBordersSet()
        for (info in chain.asReversed()) {
            merged = mergeTableBorders(merged, info.borders)
        }
        // Fallback: if style is explicitly named TableGrid/Grid without inline tblBorders parsed, treat grid as visible
        if (merged.top == null && merged.bottom == null && merged.insideH == null &&
            styleId.contains("grid", ignoreCase = true)
        ) {
            val visible = TableBorderSpec(visible = true)
            merged = TableBordersSet(visible, visible, visible, visible, visible, visible)
        }
        return merged
    }

    private fun extractEmbeddedDocxFonts(document: XWPFDocument): Map<String, Map<Int, String>> {
        val result = mutableMapOf<String, MutableMap<Int, String>>()
        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val rNs = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        fun hasValidSfntSignature(bytes: ByteArray): Boolean {
            if (bytes.size < 12) return false
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            val b3 = bytes[3].toInt() and 0xFF
            val isTrueType = b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00
            val isOtto = b0 == 0x4F && b1 == 0x54 && b2 == 0x54 && b3 == 0x4F // OTTO
            val isTrue = b0 == 0x74 && b1 == 0x72 && b2 == 0x75 && b3 == 0x65 // true
            return isTrueType || isOtto || isTrue
        }

        fun deobfuscateFontBytes(raw: ByteArray, fontKey: String?): ByteArray? {
            if (hasValidSfntSignature(raw)) return raw
            val cleanHex = fontKey?.replace(Regex("[^0-9A-Fa-f]"), "") ?: return null
            if (cleanHex.length < 32) return null

            val hexBytes = ByteArray(16) { idx ->
                cleanHex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }

            // Candidate 1: ECMA-376 reversed byte order directly on hex representation
            val reversed = raw.copyOf()
            for (i in 0 until minOf(32, reversed.size)) {
                val keyIdx = 15 - (i % 16)
                reversed[i] = (reversed[i].toInt() xor hexBytes[keyIdx].toInt()).toByte()
            }
            if (hasValidSfntSignature(reversed)) return reversed

            // Candidate 2: Mixed-endian Windows GUID bytes swapped then reversed
            val swapped = hexBytes.copyOf()
            swapped[0] = hexBytes[3]; swapped[1] = hexBytes[2]; swapped[2] = hexBytes[1]; swapped[3] = hexBytes[0]
            swapped[4] = hexBytes[5]; swapped[5] = hexBytes[4]
            swapped[6] = hexBytes[7]; swapped[7] = hexBytes[6]
            val candidate2 = raw.copyOf()
            for (i in 0 until minOf(32, candidate2.size)) {
                val keyIdx = 15 - (i % 16)
                candidate2[i] = (candidate2[i].toInt() xor swapped[keyIdx].toInt()).toByte()
            }
            if (hasValidSfntSignature(candidate2)) return candidate2

            // Candidate 3: Straight forward XOR without reversing
            val candidate3 = raw.copyOf()
            for (i in 0 until minOf(32, candidate3.size)) {
                candidate3[i] = (candidate3[i].toInt() xor hexBytes[i % 16].toInt()).toByte()
            }
            return if (hasValidSfntSignature(candidate3)) candidate3 else null
        }

        runCatching {
            val fontTablePart = document.`package`?.parts?.firstOrNull {
                it.partName?.name?.endsWith("fontTable.xml", ignoreCase = true) == true
            } ?: return result

            val outDir = File(context.cacheDir, "docx_embedded_fonts").apply {
                if (!exists()) mkdirs()
            }

            fontTablePart.inputStream.use { stream ->
                val xml = org.apache.xmlbeans.XmlObject.Factory.parse(stream)
                val cursor = xml.newCursor()
                if (cursor.toFirstChild() && cursor.toFirstChild()) {
                    do {
                        if (cursor.name?.localPart == "font") {
                            val fontName = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "name"))
                                ?.trim()?.lowercase()
                            if (!fontName.isNullOrBlank()) {
                                val fCursor = cursor.newCursor()
                                if (fCursor.toFirstChild()) {
                                    do {
                                        val styleInt = when (fCursor.name?.localPart) {
                                            "embedRegular" -> Typeface.NORMAL
                                            "embedBold" -> Typeface.BOLD
                                            "embedItalic" -> Typeface.ITALIC
                                            "embedBoldItalic" -> Typeface.BOLD_ITALIC
                                            else -> -1
                                        }
                                        if (styleInt >= 0) {
                                            val rId = fCursor.getAttributeText(javax.xml.namespace.QName(rNs, "id"))
                                            val fontKey = fCursor.getAttributeText(javax.xml.namespace.QName(wNs, "fontKey"))
                                            val rel = if (!rId.isNullOrBlank()) {
                                                runCatching { fontTablePart.getRelationship(rId) }.getOrNull()
                                            } else null
                                            val targetUri = rel?.targetURI
                                            val fontPart = if (targetUri != null) {
                                                document.`package`?.parts?.firstOrNull { p ->
                                                    val pName = p.partName?.name ?: ""
                                                    p.partName?.uri == targetUri || pName.endsWith(targetUri.toString().substringAfterLast('/'), ignoreCase = true)
                                                }
                                            } else null
                                            if (fontPart != null) {
                                                val rawBytes = fontPart.inputStream.use { it.readBytes() }
                                                val validBytes = deobfuscateFontBytes(rawBytes, fontKey)
                                                if (validBytes != null) {
                                                    val safeName = fontName.replace(Regex("[^a-z0-9]"), "_")
                                                    val outFile = File(outDir, "${safeName}_${styleInt}.ttf")
                                                    FileOutputStream(outFile).use { it.write(validBytes) }
                                                    result.getOrPut(fontName) { mutableMapOf() }[styleInt] = outFile.absolutePath
                                                }
                                            }
                                        }
                                    } while (fCursor.toNextSibling())
                                }
                                fCursor.close()
                            }
                        }
                    } while (cursor.toNextSibling())
                }
                cursor.close()
            }
        }
        return result
    }

    private fun extractDocDefaultsContext(document: XWPFDocument): DocDefaultsContext {
        var majorFont: String? = null
        var minorFont: String? = null

        runCatching {
            val themePart = document.`package`?.parts?.firstOrNull {
                it.partName?.name?.contains("theme", ignoreCase = true) == true &&
                    it.partName?.name?.endsWith(".xml", ignoreCase = true) == true
            }
            if (themePart != null) {
                themePart.inputStream.use { stream ->
                    val xml = org.apache.xmlbeans.XmlObject.Factory.parse(stream)
                    val cursor = xml.newCursor()
                    while (cursor.hasNextToken()) {
                        val tok = cursor.toNextToken()
                        if (tok == org.apache.xmlbeans.XmlCursor.TokenType.START) {
                            when (cursor.name?.localPart) {
                                "majorFont" -> {
                                    val c2 = cursor.newCursor()
                                    if (c2.toFirstChild()) {
                                        do {
                                            if (c2.name?.localPart == "latin") {
                                                majorFont = c2.getAttributeText(javax.xml.namespace.QName("typeface"))
                                                    ?.takeIf { it.isNotBlank() } ?: majorFont
                                            }
                                        } while (c2.toNextSibling())
                                    }
                                    c2.close()
                                }
                                "minorFont" -> {
                                    val c2 = cursor.newCursor()
                                    if (c2.toFirstChild()) {
                                        do {
                                            if (c2.name?.localPart == "latin") {
                                                minorFont = c2.getAttributeText(javax.xml.namespace.QName("typeface"))
                                                    ?.takeIf { it.isNotBlank() } ?: minorFont
                                            }
                                        } while (c2.toNextSibling())
                                    }
                                    c2.close()
                                }
                            }
                        }
                    }
                    cursor.close()
                }
            }
        }

        var defRun = StyleRunProps()
        var defPara = StyleParaProps()
        var defaultParaStyleId = "Normal"
        val tableStyles = mutableMapOf<String, TableStyleInfo>()
        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

        runCatching {
            val stylesPart = document.`package`?.parts?.firstOrNull {
                it.partName?.name?.endsWith("styles.xml", ignoreCase = true) == true
            }
            if (stylesPart != null) {
                stylesPart.inputStream.use { stream ->
                    val xml = org.apache.xmlbeans.XmlObject.Factory.parse(stream)
                    val cursor = xml.newCursor()
                    if (cursor.toFirstChild()) {
                        // Iterate all children of <w:styles>
                        if (cursor.toFirstChild()) {
                            do {
                                when (cursor.name?.localPart) {
                                    "docDefaults" -> {
                                        val dCursor = cursor.newCursor()
                                        if (dCursor.toFirstChild()) {
                                            do {
                                                when (dCursor.name?.localPart) {
                                                    "rPrDefault" -> {
                                                        val rC = dCursor.newCursor()
                                                        if (rC.toFirstChild()) {
                                                            do {
                                                                if (rC.name?.localPart == "rPr") {
                                                                    defRun = readRPrXml(rC.`object`, majorFont, minorFont)
                                                                }
                                                            } while (rC.toNextSibling())
                                                        }
                                                        rC.close()
                                                    }
                                                    "pPrDefault" -> {
                                                        val pC = dCursor.newCursor()
                                                        if (pC.toFirstChild()) {
                                                            do {
                                                                if (pC.name?.localPart == "pPr") {
                                                                    defPara = readPPrXml(pC.`object`)
                                                                }
                                                            } while (pC.toNextSibling())
                                                        }
                                                        pC.close()
                                                    }
                                                }
                                            } while (dCursor.toNextSibling())
                                        }
                                        dCursor.close()
                                    }
                                    "style" -> {
                                        val sType = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "type"))?.lowercase()
                                        val sId = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "styleId"))
                                        val isDef = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "default")) == "1"
                                        if (sType == "paragraph" && isDef && !sId.isNullOrBlank()) {
                                            defaultParaStyleId = sId
                                        } else if (sType == "table" && !sId.isNullOrBlank()) {
                                            var basedOn: String? = null
                                            var borders = TableBordersSet()
                                            val stC = cursor.newCursor()
                                            if (stC.toFirstChild()) {
                                                do {
                                                    when (stC.name?.localPart) {
                                                        "basedOn" -> basedOn = stC.getAttributeText(javax.xml.namespace.QName(wNs, "val"))
                                                        "tblPr" -> {
                                                            val tpC = stC.newCursor()
                                                            if (tpC.toFirstChild()) {
                                                                do {
                                                                    if (tpC.name?.localPart == "tblBorders") {
                                                                        borders = parseTblBordersXml(tpC.`object`)
                                                                    }
                                                                } while (tpC.toNextSibling())
                                                            }
                                                            tpC.close()
                                                        }
                                                    }
                                                } while (stC.toNextSibling())
                                            }
                                            stC.close()
                                            tableStyles[sId] = TableStyleInfo(sId, basedOn, borders)
                                        }
                                    }
                                }
                            } while (cursor.toNextSibling())
                        }
                    }
                    cursor.close()
                }
            }
        }

        return DocDefaultsContext(defRun, defPara, majorFont, minorFont, defaultParaStyleId, tableStyles)
    }

    private fun readRPrXml(
        rPrObj: org.apache.xmlbeans.XmlObject?,
        majorFont: String? = null,
        minorFont: String? = null
    ): StyleRunProps {
        if (rPrObj == null) return StyleRunProps()
        var rStyleId: String? = null
        var isBold: Boolean? = null
        var isItalic: Boolean? = null
        var isUnderline: Boolean? = null
        var isStrike: Boolean? = null
        var fontSizeSp: Float? = null
        var fontFamily: String? = null
        var colorHex: String? = null

        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        runCatching {
            val cursor = rPrObj.newCursor()
            if (cursor.toFirstChild()) {
                do {
                    when (cursor.name?.localPart) {
                        "rStyle" -> rStyleId = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.takeIf { it.isNotBlank() }
                        "b" -> isBold = parseToggleAttr(cursor)
                        "bCs" -> if (isBold == null) isBold = parseToggleAttr(cursor)
                        "i" -> isItalic = parseToggleAttr(cursor)
                        "iCs" -> if (isItalic == null) isItalic = parseToggleAttr(cursor)
                        "u" -> {
                            val uVal = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))
                            isUnderline = uVal == null || !uVal.equals("none", ignoreCase = true)
                        }
                        "strike", "dstrike" -> isStrike = parseToggleAttr(cursor)
                        "sz", "szCs" -> {
                            val sz = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.toFloatOrNull()
                            if (sz != null && sz > 0f && (fontSizeSp == null || cursor.name?.localPart == "sz")) {
                                fontSizeSp = sz / 2f
                            }
                        }
                        "color" -> {
                            val col = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))
                            normalizeHexColor(col)?.let { colorHex = it }
                        }
                        "rFonts" -> {
                            val ascii = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "ascii"))
                            val hAnsi = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "hAnsi"))
                            val cs = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "cs"))
                            val themeAttr = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "asciiTheme"))
                                ?: cursor.getAttributeText(javax.xml.namespace.QName(wNs, "hAnsiTheme"))
                            val themeResolved = resolveThemeFont(themeAttr, majorFont, minorFont)
                            // Per ECMA-376 §17.3.2.26, asciiTheme/hAnsiTheme overrides ascii when present
                            val ff = themeResolved ?: ascii ?: hAnsi ?: cs
                            if (!ff.isNullOrBlank()) fontFamily = ff
                        }
                    }
                } while (cursor.toNextSibling())
            }
            cursor.close()
        }
        return StyleRunProps(rStyleId, isBold, isItalic, isUnderline, isStrike, fontSizeSp, fontFamily, colorHex)
    }

    private fun readPPrXml(pPrObj: org.apache.xmlbeans.XmlObject?): StyleParaProps {
        if (pPrObj == null) return StyleParaProps()
        var alignment: TextAlign? = null
        var spacingBeforePt: Float? = null
        var spacingAfterDp: Int? = null
        var lineSpacingMult: Float? = null
        var lineSpacingExactPt: Float? = null
        var indentLeftDp: Int? = null
        var indentRightDp: Int? = null
        var firstLineIndentDp: Int? = null
        var hangingIndentDp: Int? = null
        var keepNext: Boolean? = null
        val tabs = mutableListOf<DocxTabStop>()

        val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        runCatching {
            val cursor = pPrObj.newCursor()
            if (cursor.toFirstChild()) {
                do {
                    when (cursor.name?.localPart) {
                        "jc" -> {
                            alignment = when (cursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.lowercase()) {
                                "center" -> TextAlign.Center
                                "right", "end" -> TextAlign.End
                                "both", "distribute" -> TextAlign.Justify
                                "left", "start" -> TextAlign.Start
                                else -> null
                            }
                        }
                        "keepNext" -> keepNext = parseToggleAttr(cursor)
                        "spacing" -> {
                            cursor.getAttributeText(javax.xml.namespace.QName(wNs, "before"))?.toFloatOrNull()?.let {
                                spacingBeforePt = (it / 20f).coerceIn(0f, 72f)
                            } ?: run {
                                if (cursor.getAttributeText(javax.xml.namespace.QName(wNs, "beforeAutospacing")) == "1") {
                                    spacingBeforePt = 10f
                                }
                            }
                            cursor.getAttributeText(javax.xml.namespace.QName(wNs, "after"))?.toFloatOrNull()?.let {
                                spacingAfterDp = (it / 20f).toInt().coerceIn(0, 72)
                            } ?: run {
                                if (cursor.getAttributeText(javax.xml.namespace.QName(wNs, "afterAutospacing")) == "1") {
                                    spacingAfterDp = 10
                                }
                            }
                            val lineVal = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "line"))?.toFloatOrNull()
                            val lineRule = cursor.getAttributeText(javax.xml.namespace.QName(wNs, "lineRule"))?.lowercase()
                            if (lineVal != null && lineVal > 0f) {
                                if (lineRule == "exact" || lineRule == "atleast") {
                                    lineSpacingExactPt = (lineVal / 20f).coerceIn(8f, 64f)
                                } else {
                                    lineSpacingMult = (lineVal / 240f).coerceIn(0.85f, 3.0f)
                                }
                            }
                        }
                        "ind" -> {
                            (cursor.getAttributeText(javax.xml.namespace.QName(wNs, "left"))
                                ?: cursor.getAttributeText(javax.xml.namespace.QName(wNs, "start")))?.toFloatOrNull()?.let {
                                indentLeftDp = (it / 20f).toInt().coerceIn(0, 220)
                            }
                            (cursor.getAttributeText(javax.xml.namespace.QName(wNs, "right"))
                                ?: cursor.getAttributeText(javax.xml.namespace.QName(wNs, "end")))?.toFloatOrNull()?.let {
                                indentRightDp = (it / 20f).toInt().coerceIn(0, 220)
                            }
                            cursor.getAttributeText(javax.xml.namespace.QName(wNs, "firstLine"))?.toFloatOrNull()?.let {
                                firstLineIndentDp = (it / 20f).toInt().coerceIn(0, 120)
                            }
                            cursor.getAttributeText(javax.xml.namespace.QName(wNs, "hanging"))?.toFloatOrNull()?.let {
                                hangingIndentDp = (it / 20f).toInt().coerceIn(0, 120)
                            }
                        }
                        "tabs" -> {
                            val tCursor = cursor.newCursor()
                            if (tCursor.toFirstChild()) {
                                do {
                                    if (tCursor.name?.localPart == "tab") {
                                        val valAttr = tCursor.getAttributeText(javax.xml.namespace.QName(wNs, "val"))?.lowercase() ?: "left"
                                        val posTwips = tCursor.getAttributeText(javax.xml.namespace.QName(wNs, "pos"))?.toFloatOrNull()
                                        if (posTwips != null && posTwips > 0f && valAttr != "clear") {
                                            tabs.add(DocxTabStop(posPt = posTwips / 20f, align = valAttr))
                                        }
                                    }
                                } while (tCursor.toNextSibling())
                            }
                            tCursor.close()
                        }
                    }
                } while (cursor.toNextSibling())
            }
            cursor.close()
        }
        return StyleParaProps(
            alignment = alignment,
            spacingBeforePt = spacingBeforePt,
            spacingAfterDp = spacingAfterDp,
            lineSpacingMultiplier = lineSpacingMult,
            lineSpacingExactPt = lineSpacingExactPt,
            indentLeftDp = indentLeftDp,
            indentRightDp = indentRightDp,
            firstLineIndentDp = firstLineIndentDp,
            hangingIndentDp = hangingIndentDp,
            keepNext = keepNext,
            tabStops = tabs
        )
    }

    private fun mergeRunProps(base: StyleRunProps, override: StyleRunProps): StyleRunProps = StyleRunProps(
        rStyleId = override.rStyleId ?: base.rStyleId,
        isBold = override.isBold ?: base.isBold,
        isItalic = override.isItalic ?: base.isItalic,
        isUnderline = override.isUnderline ?: base.isUnderline,
        isStrike = override.isStrike ?: base.isStrike,
        fontSizeSp = override.fontSizeSp ?: base.fontSizeSp,
        fontFamily = override.fontFamily ?: base.fontFamily,
        colorHex = override.colorHex ?: base.colorHex
    )

    private fun resolveCharacterStyleProps(
        document: XWPFDocument,
        charStyleId: String?,
        defaults: DocDefaultsContext
    ): StyleRunProps {
        if (charStyleId.isNullOrBlank()) return StyleRunProps()
        val chain = mutableListOf<StyleRunProps>()
        var currentId: String? = charStyleId
        var depth = 0
        while (!currentId.isNullOrBlank() && depth < 5) {
            val style = runCatching { document.styles?.getStyle(currentId) }.getOrNull() ?: break
            chain.add(readRPrXml(style.ctStyle?.rPr, defaults.majorFont, defaults.minorFont))
            currentId = style.basisStyleID
            depth++
        }
        var merged = StyleRunProps()
        for (rp in chain.asReversed()) {
            merged = mergeRunProps(merged, rp)
        }
        return merged
    }

    private fun resolveMergedStyleProps(
        document: XWPFDocument,
        styleId: String?,
        defaultFontSize: Float,
        defaults: DocDefaultsContext
    ): Pair<StyleRunProps, StyleParaProps> {
        val chain = mutableListOf<Pair<StyleRunProps, StyleParaProps>>()
        var currentId: String? = styleId ?: defaults.defaultParagraphStyleId
        var depth = 0
        while (!currentId.isNullOrBlank() && depth < 6) {
            val style = runCatching { document.styles?.getStyle(currentId) }.getOrNull() ?: break
            val ct = style.ctStyle
            chain.add(readRPrXml(ct?.rPr, defaults.majorFont, defaults.minorFont) to readPPrXml(ct?.pPr))
            currentId = style.basisStyleID
            depth++
        }

        var mergedRun = mergeRunProps(
            StyleRunProps(fontSizeSp = defaultFontSize, fontFamily = defaults.minorFont),
            defaults.runProps
        )
        var mergedPara = defaults.paraProps

        for ((rProps, pProps) in chain.asReversed()) {
            mergedRun = mergeRunProps(mergedRun, rProps)
            mergedPara = StyleParaProps(
                alignment = pProps.alignment ?: mergedPara.alignment,
                spacingBeforePt = pProps.spacingBeforePt ?: mergedPara.spacingBeforePt,
                spacingAfterDp = pProps.spacingAfterDp ?: mergedPara.spacingAfterDp,
                lineSpacingMultiplier = pProps.lineSpacingMultiplier ?: mergedPara.lineSpacingMultiplier,
                lineSpacingExactPt = pProps.lineSpacingExactPt ?: mergedPara.lineSpacingExactPt,
                indentLeftDp = pProps.indentLeftDp ?: mergedPara.indentLeftDp,
                indentRightDp = pProps.indentRightDp ?: mergedPara.indentRightDp,
                firstLineIndentDp = pProps.firstLineIndentDp ?: mergedPara.firstLineIndentDp,
                hangingIndentDp = pProps.hangingIndentDp ?: mergedPara.hangingIndentDp,
                keepNext = pProps.keepNext ?: mergedPara.keepNext,
                tabStops = if (pProps.tabStops.isNotEmpty()) pProps.tabStops else mergedPara.tabStops
            )
        }
        return mergedRun to mergedPara
    }

    private fun resolveNumberingIndents(
        document: XWPFDocument,
        numId: java.math.BigInteger?,
        ilvl: Int
    ): Pair<Int?, Int?> {
        if (numId == null) return null to null
        return runCatching {
            val numbering = document.numbering ?: return@runCatching null to null
            val num = numbering.getNum(numId) ?: return@runCatching null to null
            val absId = num.ctNum?.abstractNumId?.`val` ?: return@runCatching null to null
            val absNum = numbering.getAbstractNum(absId) ?: return@runCatching null to null
            val lvl = absNum.ctAbstractNum?.lvlList?.firstOrNull { (it.ilvl?.toInt() ?: 0) == ilvl }
                ?: absNum.ctAbstractNum?.lvlList?.getOrNull(ilvl)
                ?: return@runCatching null to null
            val lvlPPr = readPPrXml(lvl.pPr)
            lvlPPr.indentLeftDp to lvlPPr.hangingIndentDp
        }.getOrDefault(null to null)
    }

    private data class ExtractedRunResult(
        val text: String,
        val hasHardPageBreak: Boolean,
        val hasSoftPageBreak: Boolean
    )

    private fun extractRunTextClean(run: XWPFRun): ExtractedRunResult {
        val ctr = run.ctr
        // Skip vanished/hidden text runs
        val isVanish = runCatching { ctr.rPr?.vanishList?.isNotEmpty() == true }.getOrDefault(false)
        if (isVanish) return ExtractedRunResult("", hasHardPageBreak = false, hasSoftPageBreak = false)

        var hasHardPageBreak = false
        var hasSoftPageBreak = false
        val sb = StringBuilder()

        // Use XML cursor to preserve exact inline sequence of text, tabs, and breaks
        val handledWithXml = runCatching {
            val cursor = ctr.newCursor()
            if (cursor.toFirstChild()) {
                do {
                    val localName = cursor.name?.localPart
                    when (localName) {
                        "t" -> sb.append(cursor.textValue ?: "")
                        "tab" -> sb.append("\t")
                        "lastRenderedPageBreak" -> hasSoftPageBreak = true
                        "br", "cr" -> {
                            val typeAttr = cursor.getAttributeText(javax.xml.namespace.QName("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type"))
                            if (typeAttr == "page") {
                                hasHardPageBreak = true
                            } else {
                                sb.append("\n")
                            }
                        }
                    }
                } while (cursor.toNextSibling())
            }
            cursor.close()
            true
        }.getOrDefault(false)

        if (!handledWithXml) {
            val fallback = run.text() ?: ""
            sb.append(fallback)
        }

        return ExtractedRunResult(sb.toString(), hasHardPageBreak, hasSoftPageBreak)
    }

    private fun resolveDefaultFontSize(document: XWPFDocument): Float {
        return runCatching {
            val sz = document.styles?.defaultRunStyle?.fontSizeAsDouble?.toFloat()
            if (sz != null && sz > 0f) sz else 11f
        }.getOrDefault(11f)
    }

    private fun resolveStyleFontSize(document: XWPFDocument, styleId: String?, defaultFontSize: Float): Float {
        if (styleId.isNullOrBlank()) return defaultFontSize
        return runCatching {
            var currentId: String? = styleId
            var depth = 0
            while (currentId != null && depth < 6) {
                val style = document.styles?.getStyle(currentId) ?: break
                val szVal = style.ctStyle?.rPr?.szArray?.firstOrNull()?.`val`?.toString()?.toFloatOrNull()
                if (szVal != null && szVal > 0f) {
                    return@runCatching szVal / 2f
                }
                currentId = style.basisStyleID
                depth++
            }
            defaultFontSize
        }.getOrDefault(defaultFontSize)
    }

    private fun formatNumberedPrefix(numFmt: String?, count: Int, levelText: String?): String {
        val formattedNumber = when (numFmt?.lowercase()) {
            "bullet" -> return "•"
            "lowerletter" -> {
                val idx = ((count - 1).coerceAtLeast(0)) % 26
                ('a' + idx).toString()
            }
            "upperletter" -> {
                val idx = ((count - 1).coerceAtLeast(0)) % 26
                ('A' + idx).toString()
            }
            "lowerroman" -> toRoman(count).lowercase()
            "upperroman" -> toRoman(count)
            else -> count.toString()
        }
        return if (!levelText.isNullOrBlank() && levelText.contains("%")) {
            levelText.replace(Regex("%\\d+"), formattedNumber)
        } else {
            "$formattedNumber."
        }
    }

    private fun toRoman(num: Int): String {
        if (num <= 0 || num > 3999) return num.toString()
        val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var n = num
        val sb = StringBuilder()
        for (i in vals.indices) {
            while (n >= vals[i]) {
                n -= vals[i]
                sb.append(syms[i])
            }
        }
        return sb.toString()
    }

    private fun parseParagraph(
        document: XWPFDocument,
        paragraph: XWPFParagraph,
        defaultFontSize: Float,
        listCounters: MutableMap<String, Int>,
        elementsOut: MutableList<DocxElement>?,
        extractedImageHashes: MutableSet<Int>,
        defaults: DocDefaultsContext = DocDefaultsContext(),
        inTable: Boolean = false,
        onImageAdded: () -> Unit
    ): DocxElement.Paragraph? {
        val directParaProps = readPPrXml(paragraph.ctp?.pPr)
        val (styleRunProps, styleParaProps) = resolveMergedStyleProps(document, paragraph.styleID, defaultFontSize, defaults)
        val alignment = directParaProps.alignment
            ?: styleParaProps.alignment
            ?: mapAlignment(paragraph.alignment)
        val styleFontSize = styleRunProps.fontSizeSp ?: resolveStyleFontSize(document, paragraph.styleID, defaultFontSize)

        // Extract inline pictures inside this paragraph's runs
        if (elementsOut != null) {
            for (run in paragraph.runs) {
                val pictures = runCatching { run.embeddedPictures }.getOrNull()
                if (!pictures.isNullOrEmpty()) {
                    for (pic in pictures) {
                        val pData = pic.pictureData ?: continue
                        val bytes = pData.data ?: continue
                        if (bytes.isEmpty()) continue
                        val hash = bytes.contentHashCode()
                        if (extractedImageHashes.add(hash)) {
                            onImageAdded()
                            val ext = runCatching { pData.suggestFileExtension() }.getOrNull() ?: "png"
                            val (wPt, hPt) = runCatching {
                                val xfrm = pic.ctPicture.spPr?.xfrm?.ext
                                val cx = xfrm?.cx?.toString()?.toFloatOrNull() ?: 0f
                                val cy = xfrm?.cy?.toString()?.toFloatOrNull() ?: 0f
                                (cx / 12700f) to (cy / 12700f)
                            }.getOrDefault(0f to 0f)

                            elementsOut.add(
                                DocxElement.Image(
                                    imageBytes = bytes,
                                    extension = ext,
                                    fileName = pData.fileName,
                                    widthDp = wPt.toInt(),
                                    heightDp = hPt.toInt(),
                                    widthPt = wPt,
                                    heightPt = hPt,
                                    alignment = alignment
                                )
                            )
                        }
                    }
                }
            }
        }

        val runs = mutableListOf<DocxRun>()
        var foundHardPageBreakRun = false
        var foundSoftPageBreakRun = false
        for (run in paragraph.runs) {
            val extracted = extractRunTextClean(run)
            if (extracted.hasHardPageBreak) foundHardPageBreakRun = true
            if (extracted.hasSoftPageBreak) foundSoftPageBreakRun = true
            val runText = extracted.text
            if (runText.isEmpty()) continue

            val runXmlProps = readRPrXml(run.ctr?.rPr, defaults.majorFont, defaults.minorFont)
            val charStyleProps = resolveCharacterStyleProps(document, runXmlProps.rStyleId, defaults)
            val effectiveRunBase = mergeRunProps(styleRunProps, charStyleProps)

            val fontSize = run.fontSizeAsDouble?.toFloat()
                ?: runXmlProps.fontSizeSp
                ?: effectiveRunBase.fontSizeSp
                ?: styleFontSize
            val fontColor = normalizeHexColor(run.color)
                ?: runXmlProps.colorHex
                ?: effectiveRunBase.colorHex
            val highlightName = runCatching {
                run.ctr?.rPr?.highlightList?.firstOrNull()?.`val`?.toString()
            }.getOrNull()
            val highlightHex = mapHighlightColor(highlightName)
            val isBold = runXmlProps.isBold ?: (if (run.isBold) true else effectiveRunBase.isBold ?: false)
            val isItalic = runXmlProps.isItalic ?: (if (run.isItalic) true else effectiveRunBase.isItalic ?: false)
            val isUnderline = runXmlProps.isUnderline
                ?: (if (run.underline != null && run.underline != UnderlinePatterns.NONE) true else effectiveRunBase.isUnderline ?: false)
            val isStrike = runXmlProps.isStrike
                ?: (if (run.isStrikeThrough || run.isDoubleStrikeThrough) true else effectiveRunBase.isStrike ?: false)
            val resolvedFamily = runXmlProps.fontFamily ?: effectiveRunBase.fontFamily ?: run.fontFamily
            val vAlign = runCatching { run.verticalAlignment?.toString()?.lowercase() }.getOrNull()

            runs.add(
                DocxRun(
                    text = runText,
                    isBold = isBold,
                    isItalic = isItalic,
                    isUnderline = isUnderline,
                    isStrikeThrough = isStrike,
                    fontSizeSp = fontSize,
                    fontFamily = resolvedFamily,
                    colorHex = fontColor,
                    highlightColorHex = highlightHex,
                    isSuperscript = vAlign == "superscript",
                    isSubscript = vAlign == "subscript"
                )
            )
        }

        val fullText = runs.joinToString("") { it.text }
        val trimmedText = fullText.trimEnd('\r')
        val ctpXml = runCatching { paragraph.ctp.toString() }.getOrNull() ?: ""
        val hasHardPageBreakBefore = foundHardPageBreakRun ||
                paragraph.isPageBreak ||
                ctpXml.contains("type=\"page\"") ||
                ctpXml.contains("pageBreakBefore") ||
                fullText.contains('\u000C')
        val hasSectBreakAfter = runCatching {
            val sectPr = paragraph.ctp?.pPr?.sectPr
            sectPr != null && !sectPr.type?.`val`?.toString().equals("continuous", ignoreCase = true)
        }.getOrDefault(false)

        val hasBottomBorder = paragraph.borderBottom != null && paragraph.borderBottom != Borders.NONE
        val pFill = runCatching { normalizeHexColor(paragraph.ctp?.pPr?.shd?.fill?.toString()) }.getOrNull()

        // Extract textboxes (<w:txbxContent>) embedded inside DrawingML/mc:Choice (skipping mc:Fallback)
        if (elementsOut != null) {
            extractTextBoxesFromParagraph(
                document = document,
                paragraph = paragraph,
                defaultFontSize = defaultFontSize,
                listCounters = listCounters,
                elementsOut = elementsOut,
                extractedImageHashes = extractedImageHashes,
                defaults = defaults,
                onImageAdded = onImageAdded
            )
        }

        val baseBeforePt = directParaProps.spacingBeforePt
            ?: styleParaProps.spacingBeforePt
            ?: if (paragraph.spacingBefore > 0) (paragraph.spacingBefore / 20f).coerceIn(0f, 72f) else 0f
        val baseAfterDp = directParaProps.spacingAfterDp
            ?: if (inTable) 0 else (styleParaProps.spacingAfterDp
                ?: if (paragraph.spacingAfter >= 0) (paragraph.spacingAfter / 20).coerceIn(0, 72) else 6)
        val effectiveParaFontSize = runs.mapNotNull { it.fontSizeSp }.maxOrNull() ?: styleFontSize
        val baseLineMult = run {
            val exactPt = directParaProps.lineSpacingExactPt ?: styleParaProps.lineSpacingExactPt
            if (exactPt != null) {
                (exactPt / effectiveParaFontSize.coerceAtLeast(6f)).coerceIn(0.85f, 4.0f)
            } else {
                directParaProps.lineSpacingMultiplier
                    ?: if (inTable) 1.0f else (styleParaProps.lineSpacingMultiplier
                        ?: runCatching {
                            val raw = paragraph.spacingBetween
                            if (raw in 0.8..4.0) raw.toFloat() else 1.08f
                        }.getOrDefault(1.08f))
            }
        }
        val keepNext = directParaProps.keepNext ?: styleParaProps.keepNext ?: false

        if (trimmedText.isBlank()) {
            return if (hasHardPageBreakBefore || foundSoftPageBreakRun || hasSectBreakAfter || hasBottomBorder || baseAfterDp > 0 || trimmedText.isEmpty()) {
                DocxElement.Paragraph(
                    text = "",
                    alignment = alignment,
                    spacingBeforePt = baseBeforePt,
                    spacingAfterDp = baseAfterDp,
                    lineSpacingMultiplier = baseLineMult,
                    fontSizeSp = styleFontSize,
                    fontFamily = styleRunProps.fontFamily,
                    hasBottomBorder = hasBottomBorder,
                    backgroundFillHex = pFill,
                    hasPageBreakBefore = hasHardPageBreakBefore,
                    hasSoftPageBreakHint = foundSoftPageBreakRun,
                    hasPageBreakAfter = hasSectBreakAfter,
                    isEmptyLine = true
                )
            } else null
        }

        val style = paragraph.style ?: ""
        val isHeading = style.contains("heading", ignoreCase = true) ||
                style.contains("title", ignoreCase = true)
        val headingLevel = when {
            style.contains("1", ignoreCase = true) -> 1
            style.contains("2", ignoreCase = true) -> 2
            style.contains("3", ignoreCase = true) -> 3
            style.contains("title", ignoreCase = true) -> 1
            isHeading -> 1
            else -> 0
        }

        val numId = paragraph.numID
        val numFmt = runCatching { paragraph.numFmt }.getOrNull()
        val numLevelText = runCatching { paragraph.numLevelText }.getOrNull()
        val ilvl = paragraph.numIlvl?.toInt() ?: 0

        var listPrefix: String? = null
        var isBullet = false
        var isNumbered = false

        if (numId != null) {
            val key = "${numId}_$ilvl"
            val nextCount = (listCounters[key] ?: 0) + 1
            listCounters[key] = nextCount
            val prefix = formatNumberedPrefix(numFmt, nextCount, numLevelText)
            val alreadyPrefixed = trimmedText.trimStart().startsWith(prefix) ||
                    trimmedText.trimStart().startsWith("•") ||
                    trimmedText.trimStart().matches(NUMBERED_LIST_REGEX)
            if (!alreadyPrefixed) {
                listPrefix = prefix
            }
            if (numFmt.equals("bullet", ignoreCase = true) || prefix == "•") {
                isBullet = true
            } else {
                isNumbered = true
            }
        } else {
            val tTrim = trimmedText.trimStart()
            isNumbered = tTrim.matches(NUMBERED_LIST_REGEX)
            isBullet = !isNumbered && (style.contains("list", ignoreCase = true) || tTrim.startsWith("•") || tTrim.startsWith("- "))
        }

        val (numLeftIndent, numHangingIndent) = resolveNumberingIndents(document, numId, ilvl)
        val leftIndent = directParaProps.indentLeftDp
            ?: styleParaProps.indentLeftDp
            ?: numLeftIndent
            ?: if (paragraph.indentationLeft > 0) (paragraph.indentationLeft / 20).coerceIn(0, 220) else 0
        val rightIndent = directParaProps.indentRightDp
            ?: styleParaProps.indentRightDp
            ?: if (paragraph.indentationRight > 0) (paragraph.indentationRight / 20).coerceIn(0, 220) else 0
        val firstLineIndent = directParaProps.firstLineIndentDp
            ?: styleParaProps.firstLineIndentDp
            ?: if (paragraph.indentationFirstLine > 0) (paragraph.indentationFirstLine / 20).coerceIn(0, 120) else 0
        val hangingIndent = directParaProps.hangingIndentDp
            ?: styleParaProps.hangingIndentDp
            ?: numHangingIndent
            ?: if (paragraph.indentationHanging > 0) (paragraph.indentationHanging / 20).coerceIn(0, 120) else 0
        val mergedTabStops = (styleParaProps.tabStops + directParaProps.tabStops)
            .distinctBy { it.posPt }
            .sortedBy { it.posPt }

        return DocxElement.Paragraph(
            text = trimmedText,
            runs = runs,
            isHeading = isHeading,
            headingLevel = headingLevel,
            isBold = runs.any { it.isBold },
            isItalic = runs.any { it.isItalic },
            isUnderline = runs.any { it.isUnderline },
            isBullet = isBullet,
            isNumbered = isNumbered,
            listPrefix = listPrefix,
            alignment = alignment,
            indentStartDp = leftIndent,
            indentEndDp = rightIndent,
            firstLineIndentDp = firstLineIndent,
            hangingIndentDp = hangingIndent,
            spacingBeforePt = baseBeforePt,
            spacingAfterDp = baseAfterDp,
            lineSpacingMultiplier = baseLineMult,
            fontSizeSp = styleFontSize,
            fontFamily = styleRunProps.fontFamily,
            colorHex = styleRunProps.colorHex,
            backgroundFillHex = pFill,
            hasBottomBorder = hasBottomBorder,
            hasPageBreakBefore = hasHardPageBreakBefore,
            hasSoftPageBreakHint = foundSoftPageBreakRun,
            hasPageBreakAfter = hasSectBreakAfter,
            keepNext = keepNext,
            isEmptyLine = false,
            tabStopsPt = mergedTabStops
        )
    }

    private fun extractTextBoxesFromParagraph(
        document: XWPFDocument,
        paragraph: XWPFParagraph,
        defaultFontSize: Float,
        listCounters: MutableMap<String, Int>,
        elementsOut: MutableList<DocxElement>,
        extractedImageHashes: MutableSet<Int>,
        defaults: DocDefaultsContext = DocDefaultsContext(),
        onImageAdded: () -> Unit
    ) {
        val ctp = paragraph.ctp ?: return
        val seenTexts = mutableSetOf<String>()

        fun walkForTxbx(cursor: org.apache.xmlbeans.XmlCursor) {
            val name = cursor.name?.localPart
            if (name == "Fallback") return
            if (name == "txbxContent") {
                val contentCursor = cursor.newCursor()
                if (contentCursor.toFirstChild()) {
                    do {
                        when (contentCursor.name?.localPart) {
                            "p" -> {
                                val subCtp = runCatching {
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP.Factory.parse(contentCursor.newXMLStreamReader())
                                }.getOrNull() ?: continue
                                val subPara = XWPFParagraph(subCtp, document)
                                val parsed = parseParagraph(
                                    document = document,
                                    paragraph = subPara,
                                    defaultFontSize = defaultFontSize,
                                    listCounters = listCounters,
                                    elementsOut = null,
                                    extractedImageHashes = extractedImageHashes,
                                    defaults = defaults,
                                    onImageAdded = onImageAdded
                                )
                                if (parsed != null && parsed.text.isNotBlank() && seenTexts.add(parsed.text)) {
                                    elementsOut.add(parsed)
                                }
                            }
                            "tbl" -> {
                                val subTbl = runCatching {
                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl.Factory.parse(contentCursor.newXMLStreamReader())
                                }.getOrNull() ?: continue
                                val parsedTbl = parseTable(
                                    document = document,
                                    tbl = XWPFTable(subTbl, document),
                                    defaultFontSize = defaultFontSize,
                                    listCounters = listCounters,
                                    extractedImageHashes = extractedImageHashes,
                                    defaults = defaults,
                                    onImageAdded = onImageAdded
                                )
                                if (parsedTbl.rows.isNotEmpty()) {
                                    elementsOut.add(parsedTbl)
                                }
                            }
                        }
                    } while (contentCursor.toNextSibling())
                }
                contentCursor.close()
                return
            }

            if (cursor.toFirstChild()) {
                do {
                    walkForTxbx(cursor)
                } while (cursor.toNextSibling())
                cursor.toParent()
            }
        }

        runCatching {
            val cursor = ctp.newCursor()
            if (cursor.toFirstChild()) {
                do {
                    walkForTxbx(cursor)
                } while (cursor.toNextSibling())
            }
            cursor.close()
        }
    }

    private fun parseTable(
        document: XWPFDocument,
        tbl: XWPFTable,
        defaultFontSize: Float,
        listCounters: MutableMap<String, Int>,
        extractedImageHashes: MutableSet<Int>,
        defaults: DocDefaultsContext = DocDefaultsContext(),
        onImageAdded: () -> Unit
    ): DocxElement.Table {
        val plainRows = mutableListOf<List<String>>()
        val richRows = mutableListOf<List<DocxTableCell>>()

        // Resolve table borders: table style chain -> direct tblPr/tblBorders -> per-cell tcBorders.
        // Hidden layout tables (no style/direct/cell borders, e.g. TableNormal) keep isBorderless = true
        // so their content renders aligned cleanly while borders remain hidden.
        val styleId = runCatching { tbl.styleID }.getOrNull()
        val styleBorders = resolveTableStyleBorders(styleId, defaults.tableStyles)
        val directBorders = runCatching { parseTblBordersXml(tbl.ctTbl?.tblPr?.tblBorders) }.getOrDefault(TableBordersSet())
        val effectiveTblBorders = mergeTableBorders(styleBorders, directBorders)

        val colWidthsPt = runCatching {
            tbl.ctTbl?.tblGrid?.gridColList?.mapNotNull { col ->
                col.w?.toString()?.toFloatOrNull()?.div(20f)
            } ?: emptyList()
        }.getOrDefault(emptyList())

        val rowCount = tbl.rows.size
        var totalColCount = colWidthsPt.size
        if (totalColCount <= 0) {
            totalColCount = tbl.rows.maxOfOrNull { r ->
                r.tableCells.sumOf { c -> runCatching { c.ctTc?.tcPr?.gridSpan?.`val`?.toInt() ?: 1 }.getOrDefault(1).coerceAtLeast(1) }
            } ?: 1
        }
        var anyVisibleCellBorder = false

        for ((rowIdx, row) in tbl.rows.withIndex()) {
            val rowPlain = mutableListOf<String>()
            val rowRich = mutableListOf<DocxTableCell>()
            var colCursor = 0

            for (cell in row.tableCells) {
                val cellParagraphs = mutableListOf<DocxElement.Paragraph>()
                for (p in cell.paragraphs) {
                    val parsed = parseParagraph(
                        document = document,
                        paragraph = p,
                        defaultFontSize = defaultFontSize,
                        listCounters = listCounters,
                        elementsOut = null,
                        extractedImageHashes = extractedImageHashes,
                        defaults = defaults,
                        inTable = true,
                        onImageAdded = onImageAdded
                    )
                    if (parsed != null) {
                        cellParagraphs.add(parsed)
                    }
                }
                val cellText = cellParagraphs.joinToString("\n") { it.text }.trim()
                rowPlain.add(cellText)

                val bgHex = normalizeHexColor(cell.color)
                val gridSpan = runCatching { cell.ctTc?.tcPr?.gridSpan?.`val`?.toInt() ?: 1 }.getOrDefault(1).coerceAtLeast(1)
                val vMergeContinue = runCatching {
                    val vMerge = cell.ctTc?.tcPr?.vMerge
                    vMerge != null && (vMerge.`val` == null || vMerge.`val`.toString() == "continue")
                }.getOrDefault(false)
                val vAlign = runCatching {
                    when (cell.verticalAlignment) {
                        XWPFTableCell.XWPFVertAlign.CENTER -> "center"
                        XWPFTableCell.XWPFVertAlign.BOTTOM -> "bottom"
                        else -> "top"
                    }
                }.getOrDefault("top")

                val cellWidthPt = runCatching {
                    cell.ctTc?.tcPr?.tcW?.w?.toString()?.toFloatOrNull()?.div(20f)
                }.getOrNull()

                val cellDirectBorders = runCatching { parseTblBordersXml(cell.ctTc?.tcPr?.tcBorders) }.getOrDefault(TableBordersSet())
                val topSpec = cellDirectBorders.top ?: if (rowIdx == 0) effectiveTblBorders.top else effectiveTblBorders.insideH
                val bottomSpec = cellDirectBorders.bottom ?: if (rowIdx == rowCount - 1) effectiveTblBorders.bottom else effectiveTblBorders.insideH
                val leftSpec = cellDirectBorders.left ?: if (colCursor == 0) effectiveTblBorders.left else effectiveTblBorders.insideV
                val rightSpec = cellDirectBorders.right ?: if (colCursor + gridSpan >= totalColCount) effectiveTblBorders.right else effectiveTblBorders.insideV

                val hasTop = topSpec?.visible == true
                val hasBottom = bottomSpec?.visible == true
                val hasLeft = leftSpec?.visible == true
                val hasRight = rightSpec?.visible == true
                if (hasTop || hasBottom || hasLeft || hasRight) {
                    anyVisibleCellBorder = true
                }

                rowRich.add(
                    DocxTableCell(
                        paragraphs = cellParagraphs,
                        plainText = cellText,
                        backgroundHex = bgHex,
                        gridSpan = gridSpan,
                        vMergeContinue = vMergeContinue,
                        verticalAlign = vAlign,
                        widthPt = cellWidthPt,
                        hasTopBorder = hasTop,
                        hasBottomBorder = hasBottom,
                        hasLeftBorder = hasLeft,
                        hasRightBorder = hasRight,
                        topBorderColorHex = topSpec?.colorHex,
                        bottomBorderColorHex = bottomSpec?.colorHex,
                        leftBorderColorHex = leftSpec?.colorHex,
                        rightBorderColorHex = rightSpec?.colorHex
                    )
                )
                colCursor += gridSpan
            }

            if (rowRich.isNotEmpty()) {
                plainRows.add(rowPlain)
                richRows.add(rowRich)
            }
        }

        val resolvedColWidthsPt = if (colWidthsPt.size == totalColCount && colWidthsPt.sum() > 0f) {
            colWidthsPt
        } else {
            val firstFullRow = richRows.firstOrNull { row -> row.sumOf { it.gridSpan } == totalColCount && row.all { (it.widthPt ?: 0f) > 0f } }
            if (firstFullRow != null) {
                firstFullRow.flatMap { c ->
                    val perCol = (c.widthPt ?: 60f) / c.gridSpan.coerceAtLeast(1)
                    List(c.gridSpan) { perCol }
                }
            } else colWidthsPt
        }

        return DocxElement.Table(
            rows = plainRows,
            richRows = richRows,
            columnWidthsPt = resolvedColWidthsPt,
            isBorderless = !anyVisibleCellBorder
        )
    }

    suspend fun parseDocumentModel(uri: Uri): DocxDocumentModel = withContext(Dispatchers.IO) {
        val elements = mutableListOf<DocxElement>()
        val headerElements = mutableListOf<DocxElement>()
        val footerElements = mutableListOf<DocxElement>()
        val extractedImageHashes = mutableSetOf<Int>()
        val listCounters = mutableMapOf<String, Int>()
        var totalImages = 0
        var totalTables = 0
        var totalParagraphs = 0

        var paperName = "Letter (8.5\" × 11\")"
        var pageWidthDp = 612
        var pageHeightDp = 792
        var marginLeftPt = 72f
        var marginRightPt = 72f
        var marginTopPt = 72f
        var marginBottomPt = 72f

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open document at $uri")

        val embeddedFonts = mutableMapOf<String, Map<Int, String>>()

        inputStream.use { stream ->
            val document = XWPFDocument(stream)
            val defaults = extractDocDefaultsContext(document)
            embeddedFonts.putAll(extractEmbeddedDocxFonts(document))
            val defaultFontSize = defaults.runProps.fontSizeSp ?: resolveDefaultFontSize(document)

            // 1. Detect Page Size and Margins accurately (in points: 1 pt = 20 twips)
            try {
                val sectPr = document.document.body?.sectPr
                val pgSz = sectPr?.pgSz
                if (pgSz != null) {
                    val wTwips = pgSz.w?.toString()?.toFloatOrNull() ?: 12240f
                    val hTwips = pgSz.h?.toString()?.toFloatOrNull() ?: 15840f
                    pageWidthDp = (wTwips / 20f).toInt().coerceIn(300, 1400)
                    pageHeightDp = (hTwips / 20f).toInt().coerceIn(400, 1800)

                    val ratio = wTwips / hTwips.coerceAtLeast(1f)
                    paperName = when {
                        kotlin.math.abs(ratio - 11906f / 16838f) < 0.06f -> "A4 (210 × 297 mm)"
                        kotlin.math.abs(ratio - 12240f / 15840f) < 0.06f -> "Letter (8.5\" × 11\")"
                        kotlin.math.abs(ratio - 12240f / 20160f) < 0.06f -> "Legal (8.5\" × 14\")"
                        else -> "Custom (${pageWidthDp}pt × ${pageHeightDp}pt)"
                    }
                }

                val pgMar = sectPr?.pgMar
                if (pgMar != null) {
                    marginLeftPt = ((pgMar.left?.toString()?.toFloatOrNull() ?: 1440f) / 20f).coerceIn(18f, 144f)
                    marginRightPt = ((pgMar.right?.toString()?.toFloatOrNull() ?: 1440f) / 20f).coerceIn(18f, 144f)
                    marginTopPt = ((pgMar.top?.toString()?.toFloatOrNull() ?: 1440f) / 20f).coerceIn(18f, 144f)
                    marginBottomPt = ((pgMar.bottom?.toString()?.toFloatOrNull() ?: 1440f) / 20f).coerceIn(18f, 144f)
                }
            } catch (_: Exception) {}

            // 2. Parse only the primary/default header & footer (placed in margin zones, not mixed into body)
            try {
                val defaultHeader = document.headerFooterPolicy?.defaultHeader
                if (defaultHeader != null) {
                    for (elem in defaultHeader.bodyElements) {
                        when (elem) {
                            is XWPFParagraph -> {
                                val p = parseParagraph(document, elem, defaultFontSize, mutableMapOf(), headerElements, extractedImageHashes, defaults) { totalImages++ }
                                if (p != null && (p.text.isNotBlank() || p.hasBottomBorder)) {
                                    headerElements.add(p)
                                }
                            }
                            is XWPFTable -> {
                                val tbl = parseTable(document, elem, defaultFontSize, mutableMapOf(), extractedImageHashes, defaults) { totalImages++ }
                                if (tbl.rows.isNotEmpty()) headerElements.add(tbl)
                            }
                        }
                    }
                }

                val defaultFooter = document.headerFooterPolicy?.defaultFooter
                if (defaultFooter != null) {
                    for (elem in defaultFooter.bodyElements) {
                        when (elem) {
                            is XWPFParagraph -> {
                                val p = parseParagraph(document, elem, defaultFontSize, mutableMapOf(), footerElements, extractedImageHashes, defaults) { totalImages++ }
                                if (p != null && p.text.isNotBlank()) {
                                    footerElements.add(p)
                                }
                            }
                            is XWPFTable -> {
                                val tbl = parseTable(document, elem, defaultFontSize, mutableMapOf(), extractedImageHashes, defaults) { totalImages++ }
                                if (tbl.rows.isNotEmpty()) footerElements.add(tbl)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Parse Body Elements in exact XML order (supporting paragraphs, tables, and <w:sdt> content controls)
            fun handleSdtContent(sdtObj: org.apache.xmlbeans.XmlObject) {
                runCatching {
                    val sCursor = sdtObj.newCursor()
                    if (sCursor.toFirstChild()) {
                        do {
                            if (sCursor.name?.localPart == "sdtContent") {
                                val cCursor = sCursor.newCursor()
                                if (cCursor.toFirstChild()) {
                                    do {
                                        when (cCursor.name?.localPart) {
                                            "p" -> {
                                                val subCtp = runCatching {
                                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP.Factory.parse(cCursor.newXMLStreamReader())
                                                }.getOrNull() ?: continue
                                                val parsed = parseParagraph(
                                                    document = document,
                                                    paragraph = XWPFParagraph(subCtp, document),
                                                    defaultFontSize = defaultFontSize,
                                                    listCounters = listCounters,
                                                    elementsOut = elements,
                                                    extractedImageHashes = extractedImageHashes,
                                                    defaults = defaults,
                                                    onImageAdded = { totalImages++ }
                                                )
                                                if (parsed != null) {
                                                    if (parsed.text.isNotBlank()) totalParagraphs++
                                                    elements.add(parsed)
                                                }
                                            }
                                            "tbl" -> {
                                                val subTbl = runCatching {
                                                    org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl.Factory.parse(cCursor.newXMLStreamReader())
                                                }.getOrNull() ?: continue
                                                val parsedTable = parseTable(
                                                    document = document,
                                                    tbl = XWPFTable(subTbl, document),
                                                    defaultFontSize = defaultFontSize,
                                                    listCounters = listCounters,
                                                    extractedImageHashes = extractedImageHashes,
                                                    defaults = defaults,
                                                    onImageAdded = { totalImages++ }
                                                )
                                                if (parsedTable.rows.isNotEmpty()) {
                                                    totalTables++
                                                    elements.add(parsedTable)
                                                }
                                            }
                                            "sdt" -> handleSdtContent(cCursor.`object`)
                                        }
                                    } while (cCursor.toNextSibling())
                                }
                                cCursor.close()
                            }
                        } while (sCursor.toNextSibling())
                    }
                    sCursor.close()
                }
            }

            val bodyCursor = runCatching { document.document.body?.newCursor() }.getOrNull()
            if (bodyCursor != null) {
                var bodyIdxP = 0
                var bodyIdxT = 0
                val bodyParas = document.paragraphs
                val bodyTbls = document.tables
                if (bodyCursor.toFirstChild()) {
                    do {
                        when (bodyCursor.name?.localPart) {
                            "p" -> {
                                val xPara = bodyParas.getOrNull(bodyIdxP++)
                                    ?: runCatching { XWPFParagraph(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP.Factory.parse(bodyCursor.newXMLStreamReader()), document) }.getOrNull()
                                if (xPara != null) {
                                    val parsed = parseParagraph(
                                        document = document,
                                        paragraph = xPara,
                                        defaultFontSize = defaultFontSize,
                                        listCounters = listCounters,
                                        elementsOut = elements,
                                        extractedImageHashes = extractedImageHashes,
                                        defaults = defaults,
                                        onImageAdded = { totalImages++ }
                                    )
                                    if (parsed != null) {
                                        if (parsed.text.isNotBlank()) totalParagraphs++
                                        elements.add(parsed)
                                    }
                                }
                            }
                            "tbl" -> {
                                val xTbl = bodyTbls.getOrNull(bodyIdxT++)
                                    ?: runCatching { XWPFTable(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl.Factory.parse(bodyCursor.newXMLStreamReader()), document) }.getOrNull()
                                if (xTbl != null) {
                                    val parsedTable = parseTable(
                                        document = document,
                                        tbl = xTbl,
                                        defaultFontSize = defaultFontSize,
                                        listCounters = listCounters,
                                        extractedImageHashes = extractedImageHashes,
                                        defaults = defaults,
                                        onImageAdded = { totalImages++ }
                                    )
                                    if (parsedTable.rows.isNotEmpty()) {
                                        totalTables++
                                        elements.add(parsedTable)
                                    }
                                }
                            }
                            "sdt" -> handleSdtContent(bodyCursor.`object`)
                        }
                    } while (bodyCursor.toNextSibling())
                }
                bodyCursor.close()
            } else {
                for (bodyElement in document.bodyElements) {
                    when (bodyElement) {
                        is XWPFParagraph -> {
                            val parsed = parseParagraph(
                                document = document,
                                paragraph = bodyElement,
                                defaultFontSize = defaultFontSize,
                                listCounters = listCounters,
                                elementsOut = elements,
                                extractedImageHashes = extractedImageHashes,
                                defaults = defaults,
                                onImageAdded = { totalImages++ }
                            )
                            if (parsed != null) {
                                if (parsed.text.isNotBlank()) totalParagraphs++
                                elements.add(parsed)
                            }
                        }

                        is XWPFTable -> {
                            val parsedTable = parseTable(
                                document = document,
                                tbl = bodyElement,
                                defaultFontSize = defaultFontSize,
                                listCounters = listCounters,
                                extractedImageHashes = extractedImageHashes,
                                defaults = defaults,
                                onImageAdded = { totalImages++ }
                            )
                            if (parsedTable.rows.isNotEmpty()) {
                                totalTables++
                                elements.add(parsedTable)
                            }
                        }
                    }
                }
            }
        }

        DocxDocumentModel(
            elements = elements,
            headerElements = headerElements,
            footerElements = footerElements,
            paperName = paperName,
            pageWidthDp = pageWidthDp,
            pageHeightDp = pageHeightDp,
            marginLeftPt = marginLeftPt,
            marginRightPt = marginRightPt,
            marginTopPt = marginTopPt,
            marginBottomPt = marginBottomPt,
            paddingHorizontalDp = ((marginLeftPt + marginRightPt) / 2f).toInt(),
            paddingVerticalDp = ((marginTopPt + marginBottomPt) / 2f).toInt(),
            totalImages = totalImages,
            totalTables = totalTables,
            totalParagraphs = totalParagraphs,
            embeddedFontPaths = embeddedFonts
        )
    }

    suspend fun parseDocument(uri: Uri): List<DocxElement> {
        return parseDocumentModel(uri).elements
    }

    suspend fun saveDocument(
        originalUri: Uri,
        elements: List<DocxElement>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val doc = XWPFDocument()

            for (element in elements) {
                when (element) {
                    is DocxElement.Paragraph -> {
                        val p = doc.createParagraph()
                        p.alignment = when (element.alignment) {
                            TextAlign.Center -> ParagraphAlignment.CENTER
                            TextAlign.End, TextAlign.Right -> ParagraphAlignment.RIGHT
                            TextAlign.Justify -> ParagraphAlignment.BOTH
                            else -> ParagraphAlignment.LEFT
                        }

                        if (element.runs.isNotEmpty()) {
                            for (r in element.runs) {
                                val run = p.createRun()
                                run.setText(r.text)
                                if (r.isBold) run.isBold = true
                                if (r.isItalic) run.isItalic = true
                                if (r.isUnderline) run.underline = UnderlinePatterns.SINGLE
                                if (r.isStrikeThrough) run.isStrikeThrough = true
                                if (r.fontSizeSp != null) run.fontSize = r.fontSizeSp.toInt()
                                if (r.fontFamily != null) run.fontFamily = r.fontFamily
                                if (r.colorHex != null) run.color = r.colorHex.removePrefix("#")
                            }
                        } else {
                            val run = p.createRun()
                            run.setText(element.text)
                            if (element.isHeading) {
                                run.fontSize = 18
                                run.isBold = true
                            } else {
                                if (element.isBold) run.isBold = true
                                if (element.isItalic) run.isItalic = true
                                if (element.isUnderline) run.underline = UnderlinePatterns.SINGLE
                            }
                        }
                    }

                    is DocxElement.Image -> {
                        try {
                            val p = doc.createParagraph()
                            p.alignment = ParagraphAlignment.CENTER
                            val run = p.createRun()
                            val picType = when (element.extension.lowercase()) {
                                "jpg", "jpeg" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG
                                "png" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG
                                "gif" -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_GIF
                                else -> org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG
                            }
                            val stream = ByteArrayInputStream(element.imageBytes)
                            run.addPicture(
                                stream,
                                picType,
                                element.fileName ?: "image.${element.extension}",
                                org.apache.poi.util.Units.toEMU(260.0),
                                org.apache.poi.util.Units.toEMU(200.0)
                            )
                        } catch (_: Exception) {}
                    }

                    is DocxElement.Table -> {
                        if (element.rows.isNotEmpty()) {
                            val numRows = element.rows.size
                            val numCols = element.rows.first().size.coerceAtLeast(1)
                            val table = doc.createTable(numRows, numCols)
                            element.rows.forEachIndexed { rIdx, row ->
                                row.forEachIndexed { cIdx, cellText ->
                                    table.getRow(rIdx)?.getCell(cIdx)?.text = cellText
                                }
                            }
                        }
                    }
                }
            }

            var savedDirectly = false
            try {
                context.contentResolver.openOutputStream(originalUri, "wt")?.use { out ->
                    doc.write(out)
                    savedDirectly = true
                }
            } catch (_: Exception) {
                savedDirectly = false
            }

            if (!savedDirectly) {
                val exportDir = File(context.filesDir, "edited_docs").apply { mkdirs() }
                val exportFile = File(exportDir, "Edited_${System.currentTimeMillis()}.docx")
                FileOutputStream(exportFile).use { out ->
                    doc.write(out)
                }
                doc.close()
                Result.success(exportFile.absolutePath)
            } else {
                doc.close()
                Result.success(originalUri.toString())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveFullDocumentText(
        originalUri: Uri,
        fullText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val lines = fullText.split("\n")
        val elements = mutableListOf<DocxElement>()
        val paragraphBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (paragraphBuffer.isNotEmpty()) {
                    elements.add(DocxElement.Paragraph(text = paragraphBuffer.toString().trim()))
                    paragraphBuffer.clear()
                }
            } else {
                if (trimmed.startsWith("#") || trimmed.startsWith("•") || trimmed.startsWith("- ") || trimmed.matches(NUMBERED_PATTERN_REGEX)) {
                    if (paragraphBuffer.isNotEmpty()) {
                        elements.add(DocxElement.Paragraph(text = paragraphBuffer.toString().trim()))
                        paragraphBuffer.clear()
                    }
                    val isHeading = trimmed.startsWith("#")
                    val isBullet = trimmed.startsWith("•") || trimmed.startsWith("- ")
                    val cleanText = when {
                        isHeading -> trimmed.trimStart('#').trim()
                        isBullet -> trimmed.removePrefix("•").removePrefix("-").trim()
                        else -> trimmed
                    }
                    elements.add(
                        DocxElement.Paragraph(
                            text = cleanText,
                            isHeading = isHeading,
                            isBullet = isBullet
                        )
                    )
                } else {
                    if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(" ")
                    paragraphBuffer.append(line)
                }
            }
        }

        if (paragraphBuffer.isNotEmpty()) {
            elements.add(DocxElement.Paragraph(text = paragraphBuffer.toString().trim()))
        }

        if (elements.isEmpty()) {
            elements.add(DocxElement.Paragraph(text = fullText))
        }

        saveDocument(originalUri, elements)
    }

    fun elementsToFullText(elements: List<DocxElement>): String {
        val sb = StringBuilder()
        for (element in elements) {
            when (element) {
                is DocxElement.Paragraph -> {
                    if (element.isHeading) {
                        sb.append("# ").append(element.text).append("\n\n")
                    } else if (element.isBullet) {
                        sb.append("• ").append(element.text).append("\n\n")
                    } else {
                        sb.append(element.text).append("\n\n")
                    }
                }
                is DocxElement.Image -> {
                    sb.append("[Image: ").append(element.fileName ?: "Picture").append("]\n\n")
                }
                is DocxElement.Table -> {
                    for (row in element.rows) {
                        sb.append(row.joinToString("  |  ")).append("\n")
                    }
                    sb.append("\n")
                }
            }
        }
        return sb.toString().trimEnd()
    }

    companion object {
        private val NUMBERED_LIST_REGEX = Regex("^[0-9]+[.)].*")
        private val NUMBERED_PATTERN_REGEX = Regex("^\\d+\\..*")
    }
}
