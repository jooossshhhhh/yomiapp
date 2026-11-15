package com.yomi.reader.util.conversion

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Normalizes OOXML `.docx` files on a temporary copy right before LibreOfficeKit loads them.
 *
 * Uses a surgical token-preserving XML patcher instead of DOM + TransformerFactory so that
 * LibreOffice-sensitive `<w:document>` namespaces, `<mc:AlternateContent>`, `<w:drawing>`,
 * SmartArt (`<dgm:relIds>`), `<w:pict>`, `<w:object>`, `xml:space="preserve"`, and text runs stay
 * byte-for-byte untouched while fixing LibreOffice Writer export bugs:
 * 1) Hidden table-border leakage (`w:val="none"`, `w:val="nil"`, `w:val="clear"`, `w:sz="0"`, and `tblStyle` leakage),
 * 2) Wide/block floating `<w:tblpPr>` tables detaching/reordering past headings or splitting prematurely with lost borders,
 * 3) Double-spaced pure page-break (`<w:br w:type="page"/>`) & trailing empty break paragraphs overflowing into blank pages,
 * 4) `<asvg:svgBlip>` blips missing a fallback `r:embed` on `<a:blip>`,
 * 5) Exact row/line heights (`w:trHeight w:hRule="exact"`, `w:spacing w:lineRule="exact"`) and fixed `<a:noAutofit/>` textboxes clipping overflowing text/inline images,
 * 6) Anchored `<pic:pic>` composites & standalone/off-page `<wp:anchor>` figures being sliced at page boundaries or culled when `posOffset` extends off-page.
 */
object DocxPreProcessor {

    private val BORDER_SIDES = setOf(
        "top", "left", "bottom", "right", "start", "end",
        "insideH", "insideV", "tl2br", "tr2bl", "between", "bar"
    )
    private val CELL_BORDER_SIDES = listOf("top", "left", "bottom", "right", "insideH", "insideV")

    // OOXML CT_TcPrInner child elements that must appear AFTER <w:tcBorders>
    private val POST_TC_BORDERS_PREFIXES = listOf(
        "<w:shd", "<w:noWrap", "<w:tcMar", "<w:textDirection",
        "<w:tcFitText", "<w:vAlign", "<w:hideMark", "<w:headers", "<w:tcPrChange"
    )

    private val BORDER_CONTAINER_NAMES = setOf("w:tblBorders", "w:tcBorders", "w:pBdr")
    private val BORDER_CHILD_NAMES = BORDER_SIDES.map { "w:$it" }.toSet()
    private val ATTR_REGEX = Regex("""([A-Za-z_][A-Za-z0-9_.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

    fun normalizeForPdfExport(inputDocx: File, outputDocx: File): Boolean {
        return runCatching {
            outputDocx.parentFile?.mkdirs()
            val seen = HashSet<String>()
            ZipFile(inputDocx).use { zip ->
                ZipOutputStream(FileOutputStream(outputDocx).buffered()).use { zos ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (entry.isDirectory || !seen.add(name)) continue
                        zos.putNextEntry(ZipEntry(name))
                        if (isWordContentXml(name)) {
                            val bytes = zip.getInputStream(entry).use { it.readBytes() }
                            zos.write(normalizeWordXml(bytes))
                        } else {
                            zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
            true
        }.getOrElse {
            outputDocx.delete()
            false
        }
    }

    private fun isWordContentXml(entryName: String): Boolean {
        val lower = entryName.lowercase()
        return lower == "word/document.xml" ||
            lower == "word/styles.xml" ||
            lower == "word/footnotes.xml" ||
            lower == "word/endnotes.xml" ||
            (lower.startsWith("word/header") && lower.endsWith(".xml")) ||
            (lower.startsWith("word/footer") && lower.endsWith(".xml"))
    }

    internal fun normalizeWordXml(xmlBytes: ByteArray): ByteArray {
        val xml = String(xmlBytes, Charsets.UTF_8)

        // Step 1: Normalize all hidden border child elements inside w:tblBorders, w:tcBorders, w:pBdr
        // (whether standalone, in styles, in tblPrEx, or inside nested tables).
        val afterBorderChildren = normalizeHiddenBorderContainers(xml)

        // Step 2: Enforce table-level hidden sides onto every direct cell's <w:tcBorders> so
        // tblStyle / tblStylePr borders cannot leak through in LibreOffice Writer.
        val afterCellEnforcement = enforceHiddenTableCellBorders(afterBorderChildren)

        // Step 3: Convert wide / centered floating <w:tblpPr> tables into normal flow tables so
        // LibreOffice Writer cannot float them out of order past subsequent captions/tables or split
        // them prematurely inside floating frames.
        val afterFloatingTables = normalizeFloatingTables(afterCellEnforcement)

        // Step 4: Promote <asvg:svgBlip r:embed="..."/> fallback when <a:blip> lacks r:embed/r:link.
        val afterSvgFallback = normalizeSvgBlipFallback(afterFloatingTables)

        // Step 5: Relax hard exact row/line/textbox clipping constraints BEFORE empty-break clamping
        // so overflowing text and inline images are not cut off while collapsed spacers stay 1pt.
        val afterClipNormalization = normalizeExactClippingConstraints(afterSvgFallback)

        // Step 6: Stabilize anchored figures (inline standalone / composite <pic:pic> anchors and
        // clamp off-page posOffsets) so diagrams are neither sliced at page bottoms nor culled.
        val afterAnchorNormalization = normalizeAnchoredDrawingsAndFigures(afterClipNormalization)

        // Step 7: Fold standalone <w:br w:type="page"/> body paragraphs into <w:pageBreakBefore/>
        // (or 1pt exact line height) and clamp immediately preceding empty break-adjacent lines so
        // near-full pages never overflow into phantom blank spillover pages.
        val finalXml = normalizeBreakAndTrailingEmptyParagraphs(afterAnchorNormalization)

        if (finalXml == xml) {
            return xmlBytes
        }
        return finalXml.toByteArray(Charsets.UTF_8)
    }

    private fun normalizeHiddenBorderContainers(xml: String): String {
        val out = StringBuilder(xml.length + 256)
        var cursor = 0
        var inTargetContainer = false
        var activeContainerName = ""

        val len = xml.length
        while (cursor < len) {
            val lt = xml.indexOf('<', cursor)
            if (lt < 0) {
                out.append(xml, cursor, len)
                break
            }
            if (lt > cursor) {
                out.append(xml, cursor, lt)
            }

            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) {
                out.append(xml, lt, len)
                break
            }
            val rawTag = xml.substring(lt, tagEnd + 1)

            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                out.append(rawTag)
                cursor = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!inTargetContainer) {
                if (!isClosing && !isSelfClosing && tagName in BORDER_CONTAINER_NAMES) {
                    inTargetContainer = true
                    activeContainerName = tagName
                }
                out.append(rawTag)
            } else {
                if (isClosing && tagName == activeContainerName) {
                    inTargetContainer = false
                    activeContainerName = ""
                    out.append(rawTag)
                } else if (!isClosing && tagName in BORDER_CHILD_NAMES && isHiddenBorderTag(rawTag)) {
                    out.append(buildNilBorderTag(tagName, isSelfClosing))
                    // If the original hidden border was non-self-closing <w:top ...></w:top>,
                    // skip until its matching closing tag.
                    if (!isSelfClosing) {
                        val closeToken = "</$tagName>"
                        val closeIdx = xml.indexOf(closeToken, tagEnd + 1)
                        if (closeIdx >= 0) {
                            cursor = closeIdx + closeToken.length
                            continue
                        }
                    }
                } else {
                    out.append(rawTag)
                }
            }

            cursor = tagEnd + 1
        }
        return out.toString()
    }

    private data class TableFrame(
        val ownerDepth: Int,
        var tblPrStart: Int = -1,
        var inTblPr: Boolean = false,
        var inTblBorders: Boolean = false,
        val hiddenSides: LinkedHashSet<String> = linkedSetOf()
    )

    private data class CellFrame(
        val ownerDepth: Int,
        val tcOpenTagEnd: Int,
        var tcPrStart: Int = -1,
        var tcPrEnd: Int = -1,
        var hasTcBorders: Boolean = false,
        var tcBordersStart: Int = -1,
        var tcBordersEnd: Int = -1,
        val presentCellSides: HashSet<String> = hashSetOf()
    )

    private enum class CellOpType {
        INSERT_NEW_TCPR,
        EXPAND_SELF_CLOSING_TCPR,
        INSERT_NEW_TCBORDERS,
        EXPAND_SELF_CLOSING_TCBORDERS,
        APPEND_MISSING_SIDES
    }

    private data class CellEdit(
        val start: Int,
        val end: Int,
        val replacement: String
    )

    private fun enforceHiddenTableCellBorders(xml: String): String {
        val edits = mutableListOf<CellEdit>()
        val tableStack = ArrayDeque<TableFrame>()
        val cellStack = ArrayDeque<CellFrame>()
        var xmlElementDepth = 0
        var tblPrDepth = -1
        var tblBordersDepth = -1
        var tcPrDepth = -1
        var tcBordersDepth = -1

        var i = 0
        val len = xml.length
        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) break

            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = xmlElementDepth + 1
                if (tagName == "w:tbl" && !isSelfClosing) {
                    tableStack.addLast(TableFrame(ownerDepth = thisDepth))
                } else if (tagName == "w:tblPr" && !isSelfClosing && tableStack.isNotEmpty() && tableStack.last().ownerDepth == thisDepth - 1) {
                    val top = tableStack.last()
                    if (top.tblPrStart < 0) {
                        top.tblPrStart = lt
                        top.inTblPr = true
                        tblPrDepth = thisDepth
                    }
                } else if (tagName == "w:tblBorders" && !isSelfClosing && tableStack.lastOrNull()?.inTblPr == true && tblPrDepth == thisDepth - 1) {
                    tableStack.last().inTblBorders = true
                    tblBordersDepth = thisDepth
                } else if (tableStack.lastOrNull()?.inTblBorders == true && tblBordersDepth == thisDepth - 1 && tagName in BORDER_CHILD_NAMES) {
                    val side = tagName.removePrefix("w:")
                    if (side in CELL_BORDER_SIDES && isHiddenBorderTag(rawTag)) {
                        tableStack.last().hiddenSides.add(side)
                    } else if (side == "start" && isHiddenBorderTag(rawTag)) {
                        tableStack.last().hiddenSides.add("left")
                    } else if (side == "end" && isHiddenBorderTag(rawTag)) {
                        tableStack.last().hiddenSides.add("right")
                    }
                } else if (tagName == "w:tc" && !isSelfClosing && tableStack.isNotEmpty()) {
                    cellStack.addLast(CellFrame(ownerDepth = thisDepth, tcOpenTagEnd = tagEnd + 1))
                } else if (tagName == "w:tcPr" && cellStack.isNotEmpty() && cellStack.last().ownerDepth == thisDepth - 1) {
                    val cell = cellStack.last()
                    if (cell.tcPrStart < 0) {
                        cell.tcPrStart = lt
                        if (isSelfClosing) {
                            cell.tcPrEnd = tagEnd + 1
                        } else {
                            tcPrDepth = thisDepth
                        }
                    }
                } else if (tagName == "w:tcBorders" && cellStack.isNotEmpty() && tcPrDepth == thisDepth - 1) {
                    val cell = cellStack.last()
                    if (!cell.hasTcBorders) {
                        cell.hasTcBorders = true
                        cell.tcBordersStart = lt
                        if (isSelfClosing) {
                            cell.tcBordersEnd = tagEnd + 1
                        } else {
                            tcBordersDepth = thisDepth
                        }
                    }
                } else if (cellStack.isNotEmpty() && tcBordersDepth == thisDepth - 1 && tagName in BORDER_CHILD_NAMES) {
                    val side = tagName.removePrefix("w:")
                    cellStack.last().presentCellSides.add(side)
                    if (side == "start") cellStack.last().presentCellSides.add("left")
                    if (side == "end") cellStack.last().presentCellSides.add("right")
                }

                if (!isSelfClosing) {
                    xmlElementDepth = thisDepth
                }
            } else {
                val closingDepth = xmlElementDepth
                if (tagName == "w:tblBorders" && tblBordersDepth == closingDepth && tableStack.isNotEmpty()) {
                    tableStack.last().inTblBorders = false
                    tblBordersDepth = -1
                } else if (tagName == "w:tblPr" && tblPrDepth == closingDepth && tableStack.isNotEmpty()) {
                    tableStack.last().inTblPr = false
                    tblPrDepth = -1
                } else if (tagName == "w:tcBorders" && tcBordersDepth == closingDepth && cellStack.isNotEmpty()) {
                    cellStack.last().tcBordersEnd = tagEnd + 1
                    tcBordersDepth = -1
                } else if (tagName == "w:tcPr" && tcPrDepth == closingDepth && cellStack.isNotEmpty()) {
                    cellStack.last().tcPrEnd = tagEnd + 1
                    tcPrDepth = -1
                } else if (tagName == "w:tc" && cellStack.lastOrNull()?.ownerDepth == closingDepth) {
                    val cell = cellStack.removeLast()
                    val hiddenSides = tableStack.lastOrNull()?.hiddenSides.orEmpty()
                    if (hiddenSides.isNotEmpty()) {
                        computeCellBorderEdit(xml, cell, hiddenSides)?.let { edits.add(it) }
                    }
                } else if (tagName == "w:tbl" && tableStack.isNotEmpty()) {
                    tableStack.removeLast()
                }
                if (xmlElementDepth > 0) {
                    xmlElementDepth--
                }
            }

            i = tagEnd + 1
        }

        return applyEdits(xml, edits)
    }

    private fun computeCellBorderEdit(
        xml: String,
        cell: CellFrame,
        hiddenSides: Set<String>
    ): CellEdit? {
        val missingSides = hiddenSides.filter { it !in cell.presentCellSides }
        if (missingSides.isEmpty()) return null

        val nilChildren = buildString {
            for (side in missingSides) {
                append("<w:").append(side).append(""" w:val="nil"/>""")
            }
        }

        return when {
            cell.tcPrStart < 0 -> {
                // No <w:tcPr> exists inside <w:tc>
                CellEdit(
                    start = cell.tcOpenTagEnd,
                    end = cell.tcOpenTagEnd,
                    replacement = "<w:tcPr><w:tcBorders>$nilChildren</w:tcBorders></w:tcPr>"
                )
            }
            xml.substring(cell.tcPrStart, cell.tcPrEnd).endsWith("/>") -> {
                // Self-closing <w:tcPr/>
                val openTag = xml.substring(cell.tcPrStart, cell.tcPrEnd - 2) + ">"
                CellEdit(
                    start = cell.tcPrStart,
                    end = cell.tcPrEnd,
                    replacement = "$openTag<w:tcBorders>$nilChildren</w:tcBorders></w:tcPr>"
                )
            }
            !cell.hasTcBorders -> {
                // Insert <w:tcBorders> in schema-compliant order inside <w:tcPr>...</w:tcPr>
                val insertPos = findTcBordersInsertPosition(xml, cell.tcPrStart, cell.tcPrEnd)
                CellEdit(
                    start = insertPos,
                    end = insertPos,
                    replacement = "<w:tcBorders>$nilChildren</w:tcBorders>"
                )
            }
            xml.substring(cell.tcBordersStart, cell.tcBordersEnd).endsWith("/>") -> {
                // Self-closing <w:tcBorders/>
                val openTag = xml.substring(cell.tcBordersStart, cell.tcBordersEnd - 2) + ">"
                CellEdit(
                    start = cell.tcBordersStart,
                    end = cell.tcBordersEnd,
                    replacement = "$openTag$nilChildren</w:tcBorders>"
                )
            }
            else -> {
                // Append missing hidden sides inside existing </w:tcBorders>
                val closeTagStart = xml.lastIndexOf("</w:tcBorders>", cell.tcBordersEnd - 1)
                if (closeTagStart < cell.tcBordersStart) return null
                CellEdit(
                    start = closeTagStart,
                    end = closeTagStart,
                    replacement = nilChildren
                )
            }
        }
    }

    private fun findTcBordersInsertPosition(xml: String, tcPrStart: Int, tcPrEnd: Int): Int {
        var bestPos = xml.lastIndexOf("</w:tcPr>", tcPrEnd - 1).coerceAtLeast(tcPrStart)
        for (prefix in POST_TC_BORDERS_PREFIXES) {
            val idx = xml.indexOf(prefix, tcPrStart)
            if (idx in tcPrStart until bestPos) {
                val afterChar = xml.getOrNull(idx + prefix.length)
                if (afterChar == '>' || afterChar == '/' || afterChar == ' ' || afterChar == '\t' || afterChar == '\n' || afterChar == '\r') {
                    bestPos = idx
                }
            }
        }
        return bestPos
    }

    private fun extractAttr(rawTag: String, attrName: String): String {
        val localSuffix = ":${attrName.substringAfter(':')}"
        for (match in ATTR_REGEX.findAll(rawTag)) {
            val key = match.groupValues[1]
            if (key == attrName || key.endsWith(localSuffix)) {
                return (match.groupValues[2].ifEmpty { match.groupValues[3] }).trim()
            }
        }
        return ""
    }

    private fun extractAttrExact(rawTag: String, attrName: String): String {
        for (match in ATTR_REGEX.findAll(rawTag)) {
            if (match.groupValues[1] == attrName) {
                return (match.groupValues[2].ifEmpty { match.groupValues[3] }).trim()
            }
        }
        return ""
    }

    private data class FloatingTableFrame(
        val ownerDepth: Int,
        var tblPrStart: Int = -1,
        var tblPrEnd: Int = -1,
        var inTblPr: Boolean = false,
        var inTblGrid: Boolean = false,
        var hasTblpPr: Boolean = false,
        var tblpXSpec: String = "",
        var tblWDxa: Int = 0,
        var tblWPct: Int = 0,
        var gridSumDxa: Int = 0
    )

    private fun normalizeFloatingTables(xml: String): String {
        if (!xml.contains("<w:tblpPr")) return xml

        val edits = mutableListOf<CellEdit>()
        val tableStack = ArrayDeque<FloatingTableFrame>()
        var xmlElementDepth = 0
        var tblPrDepth = -1
        var tblGridDepth = -1

        var i = 0
        val len = xml.length
        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) break

            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = xmlElementDepth + 1
                if (tagName == "w:tbl" && !isSelfClosing) {
                    tableStack.addLast(FloatingTableFrame(ownerDepth = thisDepth))
                } else if (tagName == "w:tblPr" && !isSelfClosing && tableStack.isNotEmpty() && tableStack.last().ownerDepth == thisDepth - 1) {
                    val top = tableStack.last()
                    if (top.tblPrStart < 0) {
                        top.tblPrStart = lt
                        top.inTblPr = true
                        tblPrDepth = thisDepth
                    }
                } else if (tableStack.lastOrNull()?.inTblPr == true && tblPrDepth == thisDepth - 1) {
                    val top = tableStack.last()
                    when (tagName) {
                        "w:tblpPr" -> {
                            top.hasTblpPr = true
                            top.tblpXSpec = extractAttrExact(rawTag, "w:tblpXSpec")
                        }
                        "w:tblW" -> {
                            val wVal = extractAttrExact(rawTag, "w:w").toIntOrNull() ?: 0
                            val wType = extractAttrExact(rawTag, "w:type").lowercase()
                            when (wType) {
                                "dxa", "" -> if (wVal > 0) top.tblWDxa = wVal
                                "pct" -> if (wVal > 0) top.tblWPct = wVal
                            }
                        }
                    }
                } else if (tagName == "w:tblGrid" && !isSelfClosing && tableStack.isNotEmpty() && tableStack.last().ownerDepth == thisDepth - 1) {
                    tableStack.last().inTblGrid = true
                    tblGridDepth = thisDepth
                } else if (tableStack.lastOrNull()?.inTblGrid == true && tblGridDepth == thisDepth - 1 && tagName == "w:gridCol") {
                    val colW = extractAttrExact(rawTag, "w:w").toIntOrNull() ?: 0
                    if (colW > 0) tableStack.last().gridSumDxa += colW
                }

                if (!isSelfClosing) xmlElementDepth = thisDepth
            } else {
                val closingDepth = xmlElementDepth
                if (tagName == "w:tblPr" && tblPrDepth == closingDepth && tableStack.isNotEmpty()) {
                    tableStack.last().tblPrEnd = tagEnd + 1
                    tableStack.last().inTblPr = false
                    tblPrDepth = -1
                } else if (tagName == "w:tblGrid" && tblGridDepth == closingDepth && tableStack.isNotEmpty()) {
                    tableStack.last().inTblGrid = false
                    tblGridDepth = -1
                } else if (tagName == "w:tbl" && tableStack.lastOrNull()?.ownerDepth == closingDepth) {
                    val top = tableStack.removeLast()
                    val effectiveDxa = maxOf(top.tblWDxa, top.gridSumDxa)
                    val shouldInline = top.hasTblpPr &&
                        top.tblPrStart >= 0 &&
                        top.tblPrEnd > top.tblPrStart &&
                        (
                            effectiveDxa >= 5000 ||
                                top.tblWPct >= 2750 ||
                                top.tblpXSpec.equals("center", ignoreCase = true) ||
                                (top.tblWDxa == 0 && top.gridSumDxa == 0 && top.tblWPct == 0)
                            )
                    if (shouldInline) {
                        val rewritten = rewriteFloatingTblPr(
                            xml.substring(top.tblPrStart, top.tblPrEnd),
                            center = top.tblpXSpec.equals("center", ignoreCase = true)
                        )
                        edits.add(CellEdit(top.tblPrStart, top.tblPrEnd, rewritten))
                    }
                }
                if (xmlElementDepth > 0) xmlElementDepth--
            }
            i = tagEnd + 1
        }

        if (edits.isEmpty()) return xml
        return applyEdits(xml, edits)
    }

    private val POST_TBL_JC_TAGS = setOf(
        "w:tblCellSpacing", "w:tblInd", "w:tblBorders", "w:shd",
        "w:tblLayout", "w:tblCellMar", "w:tblLook", "w:tblCaption",
        "w:tblDescription", "w:tblPrChange"
    )

    private fun rewriteFloatingTblPr(tblPrXml: String, center: Boolean): String {
        val sb = StringBuilder(tblPrXml.length)
        var hasJc = false
        var insertedJc = false
        var depth = 0
        var skipUntilTag = ""
        var skipDepth = -1
        var cursor = 0
        val len = tblPrXml.length

        while (cursor < len) {
            val lt = tblPrXml.indexOf('<', cursor)
            if (lt < 0) {
                if (skipUntilTag.isEmpty()) sb.append(tblPrXml, cursor, len)
                break
            }
            if (lt > cursor && skipUntilTag.isEmpty()) {
                sb.append(tblPrXml, cursor, lt)
            }
            val tagEnd = findTagEnd(tblPrXml, lt)
            if (tagEnd < 0) {
                if (skipUntilTag.isEmpty()) sb.append(tblPrXml, lt, len)
                break
            }
            val rawTag = tblPrXml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                if (skipUntilTag.isEmpty()) sb.append(rawTag)
                cursor = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (skipUntilTag.isNotEmpty()) {
                if (!isClosing && !isSelfClosing) depth++
                else if (isClosing) {
                    if (tagName == skipUntilTag && depth == skipDepth) {
                        skipUntilTag = ""
                        skipDepth = -1
                    }
                    depth--
                }
                cursor = tagEnd + 1
                continue
            }

            if (!isClosing && depth == 1 && (tagName == "w:tblpPr" || tagName == "w:tblOverlap")) {
                if (!isSelfClosing) {
                    depth++
                    skipUntilTag = tagName
                    skipDepth = depth
                }
                cursor = tagEnd + 1
                continue
            }

            if (!isClosing && depth == 1 && tagName == "w:jc") {
                hasJc = true
            }

            if (center && !hasJc && !insertedJc &&
                ((!isClosing && depth == 1 && tagName in POST_TBL_JC_TAGS) || (isClosing && depth == 1 && tagName == "w:tblPr"))
            ) {
                sb.append("""<w:jc w:val="center"/>""")
                insertedJc = true
            }

            sb.append(rawTag)
            if (!isClosing && !isSelfClosing) depth++
            else if (isClosing && depth > 0) depth--
            cursor = tagEnd + 1
        }
        return sb.toString()
    }

    private sealed class BodyItem(val start: Int, val end: Int) {
        class Para(
            start: Int,
            end: Int,
            val kind: ParaKind,
            val hasPageBreakBefore: Boolean,
            val hasBookmarks: Boolean,
            val hasProtected: Boolean
        ) : BodyItem(start, end)

        class Other(start: Int, end: Int) : BodyItem(start, end)
    }

    private enum class ParaKind {
        PAGE_BREAK_ONLY,
        EMPTY,
        NORMAL
    }

    private val VISIBLE_PARA_TAGS = setOf(
        "w:drawing", "w:pict", "w:object", "m:oMath", "m:oMathPara",
        "w:sym", "w:fldChar", "w:instrText", "w:fldSimple",
        "w:cr", "w:tab", "w:delText", "w:hyperlink"
    )

    private val PRE_PAGE_BREAK_BEFORE_TAGS = setOf("w:pStyle", "w:keepNext", "w:keepLines")
    private val POST_SPACING_PPR_TAGS = setOf(
        "w:ind", "w:contextualSpacing", "w:mirrorIndents", "w:suppressOverlap",
        "w:jc", "w:textDirection", "w:textAlignment", "w:textboxTightWrap",
        "w:outlineLvl", "w:divId", "w:cnfStyle", "w:rPr", "w:sectPr", "w:pPrChange"
    )

    private fun normalizeBreakAndTrailingEmptyParagraphs(xml: String): String {
        if (!xml.contains("<w:body>") && !xml.contains("<w:body ")) return xml

        val bodyItems = mutableListOf<BodyItem>()
        var xmlElementDepth = 0
        var bodyDepth = -1
        var activeChildStart = -1
        var activeChildTag = ""
        var activeChildDepth = -1

        var i = 0
        val len = xml.length
        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) break

            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = xmlElementDepth + 1
                if (bodyDepth < 0 && tagName == "w:body" && !isSelfClosing) {
                    bodyDepth = thisDepth
                } else if (bodyDepth >= 0 && thisDepth == bodyDepth + 1) {
                    if (isSelfClosing) {
                        if (tagName == "w:p") {
                            bodyItems.add(classifyBodyParagraph(xml, lt, tagEnd + 1))
                        } else {
                            bodyItems.add(BodyItem.Other(lt, tagEnd + 1))
                        }
                    } else {
                        activeChildStart = lt
                        activeChildTag = tagName
                        activeChildDepth = thisDepth
                    }
                }
                if (!isSelfClosing) xmlElementDepth = thisDepth
            } else {
                val closingDepth = xmlElementDepth
                if (bodyDepth >= 0 && closingDepth == activeChildDepth && tagName == activeChildTag) {
                    if (tagName == "w:p") {
                        bodyItems.add(classifyBodyParagraph(xml, activeChildStart, tagEnd + 1))
                    } else {
                        bodyItems.add(BodyItem.Other(activeChildStart, tagEnd + 1))
                    }
                    activeChildStart = -1
                    activeChildTag = ""
                    activeChildDepth = -1
                } else if (bodyDepth == closingDepth && tagName == "w:body") {
                    break
                }
                if (xmlElementDepth > 0) xmlElementDepth--
            }
            i = tagEnd + 1
        }

        if (bodyItems.size < 2) return xml

        val replacements = linkedMapOf<Int, String>()
        for (idx in 0 until bodyItems.size - 1) {
            val curr = bodyItems[idx] as? BodyItem.Para ?: continue
            val next = bodyItems[idx + 1]
            when (curr.kind) {
                ParaKind.PAGE_BREAK_ONLY -> {
                    val nextPara = next as? BodyItem.Para
                    if (!curr.hasBookmarks &&
                        nextPara != null &&
                        !nextPara.hasProtected &&
                        nextPara.kind != ParaKind.PAGE_BREAK_ONLY
                    ) {
                        replacements[idx] = ""
                        if (!nextPara.hasPageBreakBefore) {
                            val nextXml = replacements[idx + 1] ?: xml.substring(nextPara.start, nextPara.end)
                            replacements[idx + 1] = insertPageBreakBefore(nextXml)
                        }
                    } else {
                        val currXml = replacements[idx] ?: xml.substring(curr.start, curr.end)
                        replacements[idx] = collapseEmptyParagraphHeight(currXml)
                    }
                }
                ParaKind.EMPTY -> {
                    val nextBreaksPage = (next as? BodyItem.Para)?.let {
                        it.kind == ParaKind.PAGE_BREAK_ONLY || it.hasPageBreakBefore
                    } == true
                    if (!curr.hasBookmarks && nextBreaksPage) {
                        val currXml = replacements[idx] ?: xml.substring(curr.start, curr.end)
                        replacements[idx] = collapseEmptyParagraphHeight(currXml)
                    }
                }
                ParaKind.NORMAL -> Unit
            }
        }

        // Also handle a PAGE_BREAK_ONLY paragraph if it is the very last item (no bodyItems[idx+1])
        val lastIdx = bodyItems.lastIndex
        val last = bodyItems[lastIdx] as? BodyItem.Para
        if (last != null && last.kind == ParaKind.PAGE_BREAK_ONLY && !replacements.containsKey(lastIdx)) {
            replacements[lastIdx] = collapseEmptyParagraphHeight(xml.substring(last.start, last.end))
        }

        if (replacements.isEmpty()) return xml
        val edits = replacements.entries.map { (itemIdx, text) ->
            val item = bodyItems[itemIdx]
            CellEdit(item.start, item.end, text)
        }
        return applyEdits(xml, edits)
    }

    private fun classifyBodyParagraph(xml: String, pStart: Int, pEnd: Int): BodyItem.Para {
        val raw = xml.substring(pStart, pEnd)
        var hasProtected = false
        var hasVisibleContent = false
        var hasPageBreakBefore = false
        var hasBookmarks = false
        var pageBrCount = 0
        var otherBrCount = 0

        var depth = 0
        var pPrDepth = -1
        var inText = false
        var cursor = 0
        val len = raw.length
        while (cursor < len) {
            val lt = raw.indexOf('<', cursor)
            if (lt < 0) break
            if (inText && lt > cursor && raw.substring(cursor, lt).any { !it.isWhitespace() }) {
                hasVisibleContent = true
            }
            val tagEnd = findTagEnd(raw, lt)
            if (tagEnd < 0) break
            val rawTag = raw.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                cursor = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = depth + 1
                if (tagName == "w:sectPr") hasProtected = true
                if (tagName == "w:bookmarkStart" || tagName == "w:bookmarkEnd") hasBookmarks = true
                if (tagName == "w:pPr" && thisDepth == 2 && !isSelfClosing) {
                    pPrDepth = thisDepth
                } else if (pPrDepth == 2 && thisDepth == 3) {
                    when (tagName) {
                        "w:pageBreakBefore" -> {
                            val v = extractAttrExact(rawTag, "w:val").lowercase()
                            if (v.isEmpty() || v == "1" || v == "true" || v == "on") {
                                hasPageBreakBefore = true
                            }
                        }
                        "w:numPr", "w:pBdr", "w:shd", "w:framePr" -> hasVisibleContent = true
                    }
                } else {
                    if (tagName in VISIBLE_PARA_TAGS) hasVisibleContent = true
                    if (tagName == "w:t" && !isSelfClosing) inText = true
                    if (tagName == "w:br") {
                        val brType = extractAttrExact(rawTag, "w:type").lowercase()
                        if (brType == "page") pageBrCount++ else otherBrCount++
                    }
                }
                if (!isSelfClosing) depth = thisDepth
            } else {
                if (tagName == "w:t") inText = false
                if (tagName == "w:pPr" && depth == pPrDepth) pPrDepth = -1
                if (depth > 0) depth--
            }
            cursor = tagEnd + 1
        }

        val kind = when {
            !hasProtected && !hasVisibleContent && otherBrCount == 0 && pageBrCount == 1 -> ParaKind.PAGE_BREAK_ONLY
            !hasProtected && !hasVisibleContent && otherBrCount == 0 && pageBrCount == 0 && !hasPageBreakBefore -> ParaKind.EMPTY
            else -> ParaKind.NORMAL
        }
        return BodyItem.Para(pStart, pEnd, kind, hasPageBreakBefore, hasBookmarks, hasProtected)
    }

    private fun insertPageBreakBefore(paraXml: String): String {
        val firstTagEnd = findTagEnd(paraXml, 0)
        if (firstTagEnd < 0) return paraXml
        if (paraXml.substring(0, firstTagEnd + 1).endsWith("/>")) {
            return paraXml.substring(0, firstTagEnd - 1) + "><w:pPr><w:pageBreakBefore/></w:pPr></w:p>"
        }
        // Locate direct <w:pPr> child at depth 2
        var depth = 0
        var cursor = 0
        var pPrStart = -1
        var pPrEnd = -1
        var pPrSelfClosing = false
        var insertAfterDirectTag = -1
        while (cursor < paraXml.length) {
            val lt = paraXml.indexOf('<', cursor)
            if (lt < 0) break
            val tagEnd = findTagEnd(paraXml, lt)
            if (tagEnd < 0) break
            val rawTag = paraXml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                cursor = tagEnd + 1
                continue
            }
            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")
            if (!isClosing) {
                val thisDepth = depth + 1
                if (thisDepth == 2 && tagName == "w:pPr") {
                    pPrStart = lt
                    if (isSelfClosing) {
                        pPrEnd = tagEnd + 1
                        pPrSelfClosing = true
                        break
                    }
                } else if (pPrStart >= 0 && thisDepth == 3 && tagName in PRE_PAGE_BREAK_BEFORE_TAGS) {
                    if (isSelfClosing) insertAfterDirectTag = tagEnd + 1
                }
                if (!isSelfClosing) depth = thisDepth
            } else {
                if (pPrStart >= 0 && depth == 3 && tagName in PRE_PAGE_BREAK_BEFORE_TAGS) {
                    insertAfterDirectTag = tagEnd + 1
                } else if (pPrStart >= 0 && depth == 2 && tagName == "w:pPr") {
                    pPrEnd = tagEnd + 1
                    break
                }
                if (depth > 0) depth--
            }
            cursor = tagEnd + 1
        }

        return when {
            pPrStart < 0 -> {
                paraXml.substring(0, firstTagEnd + 1) +
                    "<w:pPr><w:pageBreakBefore/></w:pPr>" +
                    paraXml.substring(firstTagEnd + 1)
            }
            pPrSelfClosing -> {
                val expanded = paraXml.substring(pPrStart, pPrEnd - 2) + "><w:pageBreakBefore/></w:pPr>"
                paraXml.substring(0, pPrStart) + expanded + paraXml.substring(pPrEnd)
            }
            insertAfterDirectTag >= 0 -> {
                paraXml.substring(0, insertAfterDirectTag) + "<w:pageBreakBefore/>" + paraXml.substring(insertAfterDirectTag)
            }
            else -> {
                val pPrOpenEnd = findTagEnd(paraXml, pPrStart)
                paraXml.substring(0, pPrOpenEnd + 1) + "<w:pageBreakBefore/>" + paraXml.substring(pPrOpenEnd + 1)
            }
        }
    }

    private fun collapseEmptyParagraphHeight(paraXml: String): String {
        val compactSpacing = """<w:spacing w:before="0" w:after="0" w:line="20" w:lineRule="exact"/>"""
        val compactRPr = """<w:rPr><w:sz w:val="2"/><w:szCs w:val="2"/></w:rPr>"""
        val firstTagEnd = findTagEnd(paraXml, 0)
        if (firstTagEnd < 0) return paraXml
        if (paraXml.substring(0, firstTagEnd + 1).endsWith("/>")) {
            return paraXml.substring(0, firstTagEnd - 1) + "><w:pPr>$compactSpacing$compactRPr</w:pPr></w:p>"
        }

        // Rebuild paragraph: strip existing w:spacing / w:rPr inside direct w:pPr and inject compact ones,
        // and compact any <w:rPr> inside <w:r> runs.
        val out = StringBuilder(paraXml.length + 128)
        var depth = 0
        var hasDirectPPr = false
        var inDirectPPr = false
        var inRun = false
        var skipTag = ""
        var skipDepth = -1
        var insertedSpacing = false
        var insertedPPrRPr = false
        var cursor = 0
        val len = paraXml.length
        while (cursor < len) {
            val lt = paraXml.indexOf('<', cursor)
            if (lt < 0) {
                if (skipTag.isEmpty()) out.append(paraXml, cursor, len)
                break
            }
            if (lt > cursor && skipTag.isEmpty()) {
                out.append(paraXml, cursor, lt)
            }
            val tagEnd = findTagEnd(paraXml, lt)
            if (tagEnd < 0) {
                if (skipTag.isEmpty()) out.append(paraXml, lt, len)
                break
            }
            val rawTag = paraXml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                if (skipTag.isEmpty()) out.append(rawTag)
                cursor = tagEnd + 1
                continue
            }
            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (skipTag.isNotEmpty()) {
                if (!isClosing && !isSelfClosing) depth++
                else if (isClosing) {
                    if (tagName == skipTag && depth == skipDepth) {
                        skipTag = ""
                        skipDepth = -1
                    }
                    depth--
                }
                cursor = tagEnd + 1
                continue
            }

            if (!isClosing) {
                val thisDepth = depth + 1
                if (thisDepth == 1 && tagName == "w:p") {
                    out.append(rawTag)
                    depth = thisDepth
                    cursor = tagEnd + 1
                    continue
                }
                if (thisDepth == 2 && tagName == "w:pPr") {
                    hasDirectPPr = true
                    if (isSelfClosing) {
                        out.append(rawTag.dropLast(2)).append('>').append(compactSpacing).append(compactRPr).append("</w:pPr>")
                        cursor = tagEnd + 1
                        continue
                    }
                    inDirectPPr = true
                    out.append(rawTag)
                    depth = thisDepth
                    cursor = tagEnd + 1
                    continue
                }
                if (inDirectPPr && thisDepth == 3) {
                    if (tagName == "w:spacing" || tagName == "w:rPr") {
                        if (!isSelfClosing) {
                            depth = thisDepth
                            skipTag = tagName
                            skipDepth = thisDepth
                        }
                        cursor = tagEnd + 1
                        continue
                    }
                    if (!insertedSpacing && tagName in POST_SPACING_PPR_TAGS) {
                        out.append(compactSpacing)
                        insertedSpacing = true
                    }
                    if (!insertedPPrRPr && (tagName == "w:sectPr" || tagName == "w:pPrChange")) {
                        if (!insertedSpacing) {
                            out.append(compactSpacing)
                            insertedSpacing = true
                        }
                        out.append(compactRPr)
                        insertedPPrRPr = true
                    }
                }
                if (!hasDirectPPr && thisDepth == 2) {
                    out.append("<w:pPr>").append(compactSpacing).append(compactRPr).append("</w:pPr>")
                    hasDirectPPr = true
                }
                if (thisDepth == 2 && tagName == "w:r" && !isSelfClosing) {
                    inRun = true
                } else if (inRun && thisDepth == 3 && tagName == "w:rPr") {
                    out.append(compactRPr)
                    if (!isSelfClosing) {
                        depth = thisDepth
                        skipTag = "w:rPr"
                        skipDepth = thisDepth
                    }
                    cursor = tagEnd + 1
                    continue
                }
                out.append(rawTag)
                if (!isSelfClosing) depth = thisDepth
            } else {
                if (inDirectPPr && depth == 2 && tagName == "w:pPr") {
                    if (!insertedSpacing) out.append(compactSpacing)
                    if (!insertedPPrRPr) out.append(compactRPr)
                    inDirectPPr = false
                } else if (!hasDirectPPr && depth == 1 && tagName == "w:p") {
                    out.append("<w:pPr>").append(compactSpacing).append(compactRPr).append("</w:pPr>")
                    hasDirectPPr = true
                } else if (inRun && depth == 2 && tagName == "w:r") {
                    inRun = false
                }
                out.append(rawTag)
                if (depth > 0) depth--
            }
            cursor = tagEnd + 1
        }
        return out.toString()
    }

    private fun normalizeSvgBlipFallback(xml: String): String {
        if (!xml.contains("svgBlip")) return xml
        val regex = Regex("""(<a:blip\b([^>]*)>)([\s\S]*?)</a:blip>""")
        return regex.replace(xml) { match ->
            val openTag = match.groupValues[1]
            val attrs = match.groupValues[2]
            val inner = match.groupValues[3]
            if (attrs.contains("r:embed=") || attrs.contains("r:link=")) {
                match.value
            } else {
                val svgMatch = Regex("""<[^>\s]*:?svgBlip\b[^>]*r:embed\s*=\s*"([^"]+)"""").find(inner)
                if (svgMatch != null) {
                    val rId = svgMatch.groupValues[1]
                    """<a:blip$attrs r:embed="$rId">$inner</a:blip>"""
                } else {
                    match.value
                }
            }
        }
    }

    private fun applyEdits(xml: String, edits: List<CellEdit>): String {
        if (edits.isEmpty()) return xml
        val sorted = edits.sortedBy { it.start }
        val result = StringBuilder(xml.length + sorted.size * 64)
        var pos = 0
        for (edit in sorted) {
            if (edit.start < pos) continue
            result.append(xml, pos, edit.start)
            result.append(edit.replacement)
            pos = edit.end
        }
        result.append(xml, pos, xml.length)
        return result.toString()
    }

    private fun findTagEnd(xml: String, ltIndex: Int): Int {
        if (xml.startsWith("<!--", ltIndex)) {
            val close = xml.indexOf("-->", ltIndex + 4)
            return if (close >= 0) close + 2 else -1
        }
        if (xml.startsWith("<![CDATA[", ltIndex)) {
            val close = xml.indexOf("]]>", ltIndex + 9)
            return if (close >= 0) close + 2 else -1
        }
        var quote: Char = '\u0000'
        var i = ltIndex + 1
        val len = xml.length
        while (i < len) {
            val c = xml[i]
            if (quote != '\u0000') {
                if (c == quote) quote = '\u0000'
            } else {
                if (c == '"' || c == '\'') {
                    quote = c
                } else if (c == '>') {
                    return i
                }
            }
            i++
        }
        return -1
    }

    private fun parseTagName(rawTag: String): String {
        var idx = if (rawTag.startsWith("</")) 2 else 1
        while (idx < rawTag.length) {
            val c = rawTag[idx]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                return rawTag.substring(if (rawTag.startsWith("</")) 2 else 1, idx)
            }
            idx++
        }
        return ""
    }

    private fun isHiddenBorderTag(rawTag: String): Boolean {
        var valAttr = ""
        var szAttr = ""
        var colorAttr = ""
        for (match in ATTR_REGEX.findAll(rawTag)) {
            val key = match.groupValues[1]
            val value = (match.groupValues[2].ifEmpty { match.groupValues[3] }).trim()
            when {
                key == "w:val" || key.endsWith(":val") -> valAttr = value.lowercase()
                key == "w:sz" || key.endsWith(":sz") -> szAttr = value
                key == "w:color" || key.endsWith(":color") -> colorAttr = value.lowercase()
            }
        }
        if (valAttr == "none" || valAttr == "nil" || valAttr == "clear") return true
        if (szAttr == "0") return true
        if (colorAttr == "ffffff" && (szAttr.isEmpty() || szAttr == "0")) return true
        return false
    }

    private fun buildNilBorderTag(tagName: String, isSelfClosing: Boolean): String {
        return """<$tagName w:val="nil"/>"""
    }

    private class WspState(
        val wspDepth: Int,
        var hasTxbx: Boolean = false,
        var bodyPrStart: Int = -1,
        var bodyPrOpenEnd: Int = -1,
        var bodyPrIsSelfClosing: Boolean = false,
        var bodyPrCloseStart: Int = -1,
        var noAutofitStart: Int = -1,
        var noAutofitEnd: Int = -1,
        var pendingNoAutofitStart: Int = -1,
        var hasNormOrSpAutofit: Boolean = false
    )

    private fun normalizeExactClippingConstraints(xml: String): String {
        val edits = mutableListOf<CellEdit>()
        val wspStack = ArrayDeque<WspState>()
        var depth = 0
        var i = 0
        val len = xml.length

        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) break

            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }

            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = depth + 1

                if (tagName == "w:spacing" && extractAttrExact(rawTag, "w:lineRule").equals("exact", ignoreCase = true)) {
                    val lineVal = extractAttrExact(rawTag, "w:line").toIntOrNull()
                    if (lineVal == null || lineVal >= 100) {
                        val replaced = replaceAttrValueExact(rawTag, "w:lineRule", "atLeast")
                        if (replaced != rawTag) {
                            edits.add(CellEdit(lt, tagEnd + 1, replaced))
                        }
                    }
                } else if (tagName == "w:trHeight" && extractAttrExact(rawTag, "w:hRule").equals("exact", ignoreCase = true)) {
                    val replaced = replaceAttrValueExact(rawTag, "w:hRule", "atLeast")
                    if (replaced != rawTag) {
                        edits.add(CellEdit(lt, tagEnd + 1, replaced))
                    }
                } else if (tagName == "v:textbox") {
                    val styleVal = extractAttrExact(rawTag, "style")
                    if (styleVal.isNotEmpty() && !styleVal.contains("mso-fit-shape-to-text", ignoreCase = true)) {
                        val trimmed = styleVal.trimEnd().trimEnd(';')
                        val newStyle = if (trimmed.isEmpty()) "mso-fit-shape-to-text:t" else "$trimmed;mso-fit-shape-to-text:t"
                        val replaced = replaceAttrValueExact(rawTag, "style", newStyle)
                        if (replaced != rawTag) {
                            edits.add(CellEdit(lt, tagEnd + 1, replaced))
                        }
                    }
                }

                if (tagName == "wps:wsp" && !isSelfClosing) {
                    wspStack.addLast(WspState(wspDepth = thisDepth))
                } else if (wspStack.isNotEmpty()) {
                    val top = wspStack.last()
                    if (thisDepth == top.wspDepth + 1) {
                        if (tagName == "wps:txbx") {
                            top.hasTxbx = true
                        } else if (tagName == "wps:bodyPr") {
                            top.bodyPrStart = lt
                            top.bodyPrOpenEnd = tagEnd + 1
                            top.bodyPrIsSelfClosing = isSelfClosing
                            if (isSelfClosing) {
                                top.bodyPrCloseStart = lt
                            }
                        }
                    } else if (
                        top.bodyPrStart >= 0 &&
                        top.bodyPrCloseStart < 0 &&
                        thisDepth == top.wspDepth + 2
                    ) {
                        if (tagName == "a:noAutofit") {
                            if (isSelfClosing) {
                                top.noAutofitStart = lt
                                top.noAutofitEnd = tagEnd + 1
                            } else {
                                top.pendingNoAutofitStart = lt
                            }
                        } else if (tagName == "a:spAutoFit" || tagName == "a:normAutofit") {
                            top.hasNormOrSpAutofit = true
                        }
                    }
                }

                if (!isSelfClosing) depth = thisDepth
            } else {
                if (wspStack.isNotEmpty()) {
                    val top = wspStack.last()
                    if (tagName == "a:noAutofit" && depth == top.wspDepth + 2 && top.pendingNoAutofitStart >= 0) {
                        top.noAutofitStart = top.pendingNoAutofitStart
                        top.noAutofitEnd = tagEnd + 1
                        top.pendingNoAutofitStart = -1
                    } else if (tagName == "wps:bodyPr" && depth == top.wspDepth + 1 && top.bodyPrCloseStart < 0) {
                        top.bodyPrCloseStart = lt
                    } else if (tagName == "wps:wsp" && depth == top.wspDepth) {
                        wspStack.removeLast()
                        if (top.hasTxbx && !top.hasNormOrSpAutofit && top.bodyPrStart >= 0) {
                            if (top.noAutofitStart >= 0 && top.noAutofitEnd > top.noAutofitStart) {
                                edits.add(CellEdit(top.noAutofitStart, top.noAutofitEnd, "<a:spAutoFit/>"))
                            } else if (top.bodyPrIsSelfClosing) {
                                edits.add(
                                    CellEdit(
                                        top.bodyPrOpenEnd - 2,
                                        top.bodyPrOpenEnd,
                                        "><a:spAutoFit/></wps:bodyPr>"
                                    )
                                )
                            } else if (top.bodyPrCloseStart >= top.bodyPrOpenEnd) {
                                val insertAt = findAutofitInsertOffset(xml, top.bodyPrOpenEnd, top.bodyPrCloseStart)
                                edits.add(CellEdit(insertAt, insertAt, "<a:spAutoFit/>"))
                            }
                        }
                    }
                }
                if (depth > 0) depth--
            }

            i = tagEnd + 1
        }

        return applyEdits(xml, edits)
    }

    private fun replaceAttrValueExact(rawTag: String, attrName: String, newValue: String): String {
        val pattern = Regex("""(?<![A-Za-z0-9_.:-])(${Regex.escape(attrName)}\s*=\s*)(?:"[^"]*"|'[^']*')""")
        val match = pattern.find(rawTag) ?: return rawTag
        return rawTag.substring(0, match.range.first) +
            "${match.groupValues[1]}\"$newValue\"" +
            rawTag.substring(match.range.last + 1)
    }

    private fun findAutofitInsertOffset(xml: String, bodyPrInnerStart: Int, bodyPrCloseStart: Int): Int {
        val postAutofitTags = listOf("a:scene3d", "a:sp3d", "a:flatTx", "a:extLst")
        var best = bodyPrCloseStart
        for (tag in postAutofitTags) {
            val pos = findFirstTagStart(xml, tag, bodyPrInnerStart)
            if (pos in bodyPrInnerStart until best) {
                best = pos
            }
        }
        return best
    }

    private data class PageMetrics(
        val pageWidthEmu: Long,
        val pageHeightEmu: Long,
        val marginLeftEmu: Long,
        val marginTopEmu: Long,
        val marginRightEmu: Long,
        val marginBottomEmu: Long
    ) {
        val maxContentWidthEmu: Long get() = maxOf(2_286_000L, pageWidthEmu - marginLeftEmu - marginRightEmu)
        val maxContentHeightEmu: Long get() = maxOf(3_657_600L, pageHeightEmu - marginTopEmu - marginBottomEmu)
    }

    private data class ParsedAnchor(
        val anchorStart: Int,
        val anchorEnd: Int,
        val containerStart: Int,
        val containerEnd: Int,
        val isPurePic: Boolean,
        val wrapType: String,
        val posHRel: String,
        val posHOffset: Long?,
        val posHAlign: String,
        val posVRel: String,
        val posVOffset: Long?,
        val posVOffsetTextStart: Int,
        val posVOffsetTextEnd: Int,
        val cx: Long,
        val cy: Long,
        val relativeHeight: Long,
        val docPrId: String,
        val originalIndex: Int
    )

    private fun parsePageMetrics(xml: String): PageMetrics {
        var pgW = 12240L * 635L
        var pgH = 15840L * 635L
        var marL = 1440L * 635L
        var marT = 1440L * 635L
        var marR = 1440L * 635L
        var marB = 1440L * 635L

        Regex("""<w:pgSz\b[^>]*>""").find(xml)?.value?.let { tag ->
            extractAttrExact(tag, "w:w").toLongOrNull()?.takeIf { it > 1000L }?.let { pgW = it * 635L }
            extractAttrExact(tag, "w:h").toLongOrNull()?.takeIf { it > 1000L }?.let { pgH = it * 635L }
        }
        Regex("""<w:pgMar\b[^>]*>""").find(xml)?.value?.let { tag ->
            extractAttrExact(tag, "w:left").toLongOrNull()?.let { marL = maxOf(0L, it * 635L) }
            extractAttrExact(tag, "w:top").toLongOrNull()?.let { marT = maxOf(0L, it * 635L) }
            extractAttrExact(tag, "w:right").toLongOrNull()?.let { marR = maxOf(0L, it * 635L) }
            extractAttrExact(tag, "w:bottom").toLongOrNull()?.let { marB = maxOf(0L, it * 635L) }
        }
        return PageMetrics(pgW, pgH, marL, marT, marR, marB)
    }

    private fun normalizeAnchoredDrawingsAndFigures(xml: String): String {
        val hasAnchors = xml.contains("<wp:anchor")
        val hasPict = xml.contains("<w:pict")
        if (!hasAnchors && !hasPict) return xml

        val metrics = parsePageMetrics(xml)
        val edits = mutableListOf<CellEdit>()
        var needsWpgNs = false

        // Normalize extreme negative VML margin-top values on legacy <w:pict><v:shape> items.
        if (hasPict) {
            val vmlShapeRegex = Regex("""<v:(?:shape|rect|roundrect|oval|group|line)\b[^>]*style\s*=\s*"([^"]*)"[^>]*>""")
            for (m in vmlShapeRegex.findAll(xml)) {
                val style = m.groupValues[1]
                val clampedStyle = clampNegativeVmlMarginTop(style)
                if (clampedStyle != style) {
                    val replacedTag = replaceAttrValueExact(m.value, "style", clampedStyle)
                    edits.add(CellEdit(m.range.first, m.range.last + 1, replacedTag))
                }
            }
        }

        if (hasAnchors) {
            // Walk top-level flow <w:p> spans (outside w:txbxContent) cleanly using depth tracking.
            val pSpans = collectFlowParagraphSpans(xml)
            for ((pStart, pEnd) in pSpans) {
                val paraSlice = xml.substring(pStart, pEnd)
                if (!paraSlice.contains("<wp:anchor")) continue

                val analysis = analyzeFlowParagraph(paraSlice)
                if (analysis.anchors.isEmpty()) continue

                val anchors = analysis.anchors
                // 1. Composite multi-layer <pic:pic> anchors in a prose-free figure paragraph -> ONE <wp:inline><wpg:wgp>
                if (
                    !analysis.hasProseText &&
                    !analysis.hasOtherMediaOrBreak &&
                    anchors.size >= 2 &&
                    analysis.totalDrawingCount == anchors.size &&
                    anchors.all { it.isPurePic && (it.wrapType == "wrapNone" || it.wrapType == "wrapTopAndBottom") } &&
                    areVerticalCoordsCompatible(anchors)
                ) {
                    val compositeEdit = buildCompositeInlineGroupEdits(
                        xml = xml,
                        pStart = pStart,
                        paraSlice = paraSlice,
                        anchors = anchors,
                        metrics = metrics
                    )
                    if (compositeEdit != null) {
                        edits.addAll(compositeEdit)
                        needsWpgNs = true
                        continue
                    }
                }

                // 2. Single standalone figure <pic:pic> anchor in a prose-free paragraph -> <wp:inline>
                if (
                    !analysis.hasProseText &&
                    !analysis.hasOtherMediaOrBreak &&
                    anchors.size == 1 &&
                    analysis.totalDrawingCount == 1 &&
                    anchors[0].isPurePic
                ) {
                    val a = anchors[0]
                    val offPage = isAnchorOffPage(a, metrics)
                    val shouldInline = a.wrapType == "wrapTopAndBottom" ||
                        (a.wrapType == "wrapNone" && (a.cx >= 1_828_800L || a.cy >= 1_828_800L || offPage)) ||
                        offPage
                    if (shouldInline) {
                        edits.addAll(
                            buildStandaloneInlineEdits(
                                xml = xml,
                                pStart = pStart,
                                paraSlice = paraSlice,
                                anchor = a,
                                metrics = metrics
                            )
                        )
                        continue
                    }
                }

                // 3. Remaining <wp:anchor>s (shapes, connectors, groups, or wrapped pics): clamp off-page posV offsets
                val isSingle = anchors.size == 1
                for (a in anchors) {
                    val v = a.posVOffset ?: continue
                    if (a.posVOffsetTextStart < 0 || a.posVOffsetTextEnd <= a.posVOffsetTextStart) continue
                    val maxBottom = maxOf(0L, metrics.maxContentHeightEmu - maxOf(a.cy, 457_200L))
                    val clampedV: Long = when (a.posVRel) {
                        "page" -> v.coerceIn(0L, maxOf(0L, metrics.pageHeightEmu - maxOf(a.cy, 180_000L)))
                        "margin", "topMargin" -> {
                            val minV = -metrics.marginTopEmu + 50_000L
                            if (v < -metrics.marginTopEmu || (isSingle && v > metrics.maxContentHeightEmu)) {
                                v.coerceIn(minV, maxBottom)
                            } else {
                                v
                            }
                        }
                        "paragraph", "line" -> when {
                            v < -914_400L -> 0L
                            isSingle && v > maxOf(metrics.maxContentHeightEmu, metrics.pageHeightEmu - metrics.marginTopEmu) -> maxBottom
                            else -> v
                        }
                        else -> v
                    }
                    if (clampedV != v) {
                        edits.add(
                            CellEdit(
                                pStart + a.posVOffsetTextStart,
                                pStart + a.posVOffsetTextEnd,
                                clampedV.toString()
                            )
                        )
                    }
                }
            }
        }

        var result = applyEdits(xml, edits)
        if (needsWpgNs) {
            result = ensureDocumentWpgNamespace(result)
        }
        return result
    }

    private fun clampNegativeVmlMarginTop(style: String): String {
        val m = Regex("""margin-top\s*:\s*(-?\d+(?:\.\d+)?)\s*(pt|in|cm|mm|px)?""", RegexOption.IGNORE_CASE).find(style)
            ?: return style
        val num = m.groupValues[1].toDoubleOrNull() ?: return style
        val unit = m.groupValues[2].lowercase()
        val pts = when (unit) {
            "in" -> num * 72.0
            "cm" -> num * 28.3465
            "mm" -> num * 2.83465
            "px" -> num * 0.75
            else -> num
        }
        if (pts >= -90.0) return style
        return style.substring(0, m.range.first) + "margin-top:0pt" + style.substring(m.range.last + 1)
    }

    private fun collectFlowParagraphSpans(xml: String): List<Pair<Int, Int>> {
        val spans = mutableListOf<Pair<Int, Int>>()
        var txbxDepth = 0
        var activeFlowPStart = -1
        var depth = 0
        var flowPDepth = -1
        var i = 0
        val len = xml.length

        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) break
            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }
            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = depth + 1
                if (tagName == "w:txbxContent" && !isSelfClosing) {
                    txbxDepth++
                } else if (tagName == "w:p" && !isSelfClosing && txbxDepth == 0 && activeFlowPStart < 0) {
                    activeFlowPStart = lt
                    flowPDepth = thisDepth
                }
                if (!isSelfClosing) depth = thisDepth
            } else {
                if (tagName == "w:txbxContent" && txbxDepth > 0) {
                    txbxDepth--
                } else if (tagName == "w:p" && depth == flowPDepth && activeFlowPStart >= 0) {
                    spans.add(activeFlowPStart to (tagEnd + 1))
                    activeFlowPStart = -1
                    flowPDepth = -1
                }
                if (depth > 0) depth--
            }
            i = tagEnd + 1
        }
        return spans
    }

    private data class ParagraphAnchorAnalysis(
        val hasProseText: Boolean,
        val hasOtherMediaOrBreak: Boolean,
        val totalDrawingCount: Int,
        val anchors: List<ParsedAnchor>
    )

    private fun analyzeFlowParagraph(paraXml: String): ParagraphAnchorAnalysis {
        var txbxDepth = 0
        var fallbackDepth = 0
        var depth = 0
        var hasProseText = false
        var hasOtherMediaOrBreak = false
        var totalDrawingCount = 0

        val anchors = mutableListOf<ParsedAnchor>()
        var inTextTag = false
        var textStart = -1

        // Track immediate <w:r> and <w:drawing> ranges outside <w:txbxContent>
        var curRunStart = -1
        var curRunDepth = -1
        var curRunOnlyDrawing = true
        var drawingsInCurRun = 0
        var anchorIndicesInCurRun = mutableListOf<Int>()
        var curDrawingStart = -1
        var curDrawingDepth = -1

        // Current <wp:anchor> tracking (top-level inside flow drawing, not inside txbxContent)
        var anchorStart = -1
        var anchorDepth = -1
        var inPosH = false
        var posHRel = "column"
        var posHAlign = ""
        var posHOffset: Long? = null
        var inPosV = false
        var posVRel = "paragraph"
        var posVAlign = ""
        var posVOffset: Long? = null
        var posVTextStart = -1
        var posVTextEnd = -1
        var numTagType = 0 // 1=H offset, 2=V offset, 3=align H, 4=align V
        var numTextStart = -1
        var wrapType = "wrapNone"
        var cx = 0L
        var cy = 0L
        var relHeight = 0L
        var docPrId = "1"
        var isPurePic = false
        var sawPic = false
        var sawNonPic = false
        var curAnchorContainerStart = -1

        var i = 0
        val len = paraXml.length
        while (i < len) {
            val lt = paraXml.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(paraXml, lt)
            if (tagEnd < 0) break
            val rawTag = paraXml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }
            val tagName = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")

            if (!isClosing) {
                val thisDepth = depth + 1
                if (tagName == "w:txbxContent" && !isSelfClosing) {
                    txbxDepth++
                } else if (tagName == "mc:Fallback" && !isSelfClosing) {
                    fallbackDepth++
                } else if (txbxDepth == 0) {
                    if (tagName == "w:t") {
                        if (!isSelfClosing) {
                            inTextTag = true
                            textStart = tagEnd + 1
                        }
                    } else if (tagName == "w:br" || tagName == "w:object" || (tagName == "w:pict" && fallbackDepth == 0)) {
                        hasOtherMediaOrBreak = true
                    } else if (tagName == "w:r" && !isSelfClosing && curRunStart < 0) {
                        curRunStart = lt
                        curRunDepth = thisDepth
                        curRunOnlyDrawing = true
                        drawingsInCurRun = 0
                        anchorIndicesInCurRun.clear()
                    } else if (curRunStart >= 0 && tagName != "w:rPr" && tagName != "w:drawing" &&
                        !tagName.startsWith("mc:") && fallbackDepth == 0 && anchorStart < 0 &&
                        curDrawingStart < 0
                    ) {
                        curRunOnlyDrawing = false
                    }

                    if (tagName == "w:drawing" && !isSelfClosing && curDrawingStart < 0 && fallbackDepth == 0) {
                        totalDrawingCount++
                        if (curRunStart >= 0) drawingsInCurRun++
                        curDrawingStart = lt
                        curDrawingDepth = thisDepth
                    } else if (curDrawingStart >= 0 && tagName == "wp:anchor" && !isSelfClosing && anchorStart < 0) {
                        anchorStart = lt
                        anchorDepth = thisDepth
                        curAnchorContainerStart = curDrawingStart
                        inPosH = false; posHRel = "column"; posHAlign = ""; posHOffset = null
                        inPosV = false; posVRel = "paragraph"; posVAlign = ""; posVOffset = null
                        posVTextStart = -1; posVTextEnd = -1
                        numTagType = 0; wrapType = "wrapNone"; cx = 0L; cy = 0L
                        relHeight = extractAttrExact(rawTag, "relativeHeight").toLongOrNull() ?: 0L
                        docPrId = "1"; sawPic = false; sawNonPic = false
                    } else if (anchorStart >= 0) {
                        if (thisDepth == anchorDepth + 1) {
                            when (tagName) {
                                "wp:positionH" -> {
                                    inPosH = true
                                    posHRel = extractAttrExact(rawTag, "relativeFrom").ifEmpty { "column" }
                                }
                                "wp:positionV" -> {
                                    inPosV = true
                                    posVRel = extractAttrExact(rawTag, "relativeFrom").ifEmpty { "paragraph" }
                                }
                                "wp:extent" -> {
                                    cx = extractAttrExact(rawTag, "cx").toLongOrNull() ?: 0L
                                    cy = extractAttrExact(rawTag, "cy").toLongOrNull() ?: 0L
                                }
                                "wp:wrapNone", "wp:wrapSquare", "wp:wrapTight", "wp:wrapThrough", "wp:wrapTopAndBottom" -> {
                                    wrapType = tagName.substringAfter(':')
                                }
                                "wp:docPr" -> {
                                    extractAttrExact(rawTag, "id").takeIf { it.isNotEmpty() }?.let { docPrId = it }
                                }
                            }
                        } else if ((inPosH || inPosV) && thisDepth == anchorDepth + 2 && !isSelfClosing) {
                            if (tagName == "wp:posOffset") {
                                numTagType = if (inPosH) 1 else 2
                                numTextStart = tagEnd + 1
                            } else if (tagName == "wp:align") {
                                numTagType = if (inPosH) 3 else 4
                                numTextStart = tagEnd + 1
                            }
                        }
                        when {
                            tagName == "pic:pic" -> sawPic = true
                            tagName == "wps:wsp" || tagName == "wpg:wgp" || tagName == "dgm:relIds" || tagName == "c:chart" -> sawNonPic = true
                        }
                    }
                }
                if (!isSelfClosing) depth = thisDepth
            } else {
                if (tagName == "w:txbxContent" && txbxDepth > 0) {
                    txbxDepth--
                } else if (tagName == "mc:Fallback" && fallbackDepth > 0) {
                    fallbackDepth--
                } else if (txbxDepth == 0) {
                    if (tagName == "w:t" && inTextTag) {
                        if (paraXml.substring(textStart, lt).any { !it.isWhitespace() }) {
                            hasProseText = true
                        }
                        inTextTag = false
                    } else if (anchorStart >= 0) {
                        if ((inPosH || inPosV) && depth == anchorDepth + 2 && numTagType != 0 && numTextStart >= 0) {
                            val txt = paraXml.substring(numTextStart, lt).trim()
                            when (numTagType) {
                                1 -> posHOffset = txt.toLongOrNull()
                                2 -> {
                                    posVOffset = txt.toLongOrNull()
                                    posVTextStart = numTextStart
                                    posVTextEnd = lt
                                }
                                3 -> posHAlign = txt
                                4 -> posVAlign = txt
                            }
                            numTagType = 0
                            numTextStart = -1
                        } else if (tagName == "wp:positionH" && depth == anchorDepth + 1) {
                            inPosH = false
                        } else if (tagName == "wp:positionV" && depth == anchorDepth + 1) {
                            inPosV = false
                        } else if (tagName == "wp:anchor" && depth == anchorDepth) {
                            isPurePic = sawPic && !sawNonPic && cx > 0L && cy > 0L
                            anchors.add(
                                ParsedAnchor(
                                    anchorStart = anchorStart,
                                    anchorEnd = tagEnd + 1,
                                    containerStart = curAnchorContainerStart,
                                    containerEnd = tagEnd + 1, // refined when drawing/run closes
                                    isPurePic = isPurePic,
                                    wrapType = wrapType,
                                    posHRel = posHRel,
                                    posHOffset = posHOffset,
                                    posHAlign = posHAlign,
                                    posVRel = posVRel,
                                    posVOffset = posVOffset,
                                    posVOffsetTextStart = posVTextStart,
                                    posVOffsetTextEnd = posVTextEnd,
                                    cx = cx,
                                    cy = cy,
                                    relativeHeight = relHeight,
                                    docPrId = docPrId,
                                    originalIndex = anchors.size
                                )
                            )
                            if (curRunStart >= 0) anchorIndicesInCurRun.add(anchors.lastIndex)
                            anchorStart = -1
                            anchorDepth = -1
                        }
                    }

                    if (curDrawingStart >= 0 && tagName == "w:drawing" && depth == curDrawingDepth) {
                        if (anchors.isNotEmpty() && anchors.last().containerStart == curDrawingStart) {
                            val last = anchors.removeAt(anchors.lastIndex)
                            anchors.add(last.copy(containerEnd = tagEnd + 1))
                        }
                        curDrawingStart = -1
                        curDrawingDepth = -1
                    }
                    if (curRunStart >= 0 && tagName == "w:r" && depth == curRunDepth) {
                        if (curRunOnlyDrawing && drawingsInCurRun == 1 && anchorIndicesInCurRun.size == 1) {
                            val idx = anchorIndicesInCurRun[0]
                            anchors[idx] = anchors[idx].copy(containerStart = curRunStart, containerEnd = tagEnd + 1)
                        }
                        curRunStart = -1
                        curRunDepth = -1
                        anchorIndicesInCurRun.clear()
                    }
                }
                if (depth > 0) depth--
            }
            i = tagEnd + 1
        }
        return ParagraphAnchorAnalysis(hasProseText, hasOtherMediaOrBreak, totalDrawingCount, anchors)
    }

    private fun areVerticalCoordsCompatible(anchors: List<ParsedAnchor>): Boolean {
        val allPara = anchors.all { it.posVRel == "paragraph" || it.posVRel == "line" }
        val allPage = anchors.all { it.posVRel == "margin" || it.posVRel == "page" }
        return allPara || allPage
    }

    private fun isAnchorOffPage(anchor: ParsedAnchor, metrics: PageMetrics): Boolean {
        val v = anchor.posVOffset
        if (v != null) {
            when (anchor.posVRel) {
                "page" -> if (v < 0L || v + anchor.cy > metrics.pageHeightEmu) return true
                "margin", "topMargin" -> if (v < -metrics.marginTopEmu || v + anchor.cy > metrics.pageHeightEmu - metrics.marginTopEmu) return true
                "paragraph", "line" -> if (v < -914_400L || v > metrics.pageHeightEmu - metrics.marginTopEmu) return true
            }
        }
        val h = anchor.posHOffset
        if (h != null) {
            when (anchor.posHRel) {
                "page" -> if (h < 0L || h > metrics.pageWidthEmu - 457_200L) return true
                "margin", "column" -> if (h < -metrics.marginLeftEmu) return true
            }
        }
        return false
    }

    private fun resolveAnchorX(anchor: ParsedAnchor, metrics: PageMetrics): Long {
        if (anchor.posHOffset != null) {
            return when (anchor.posHRel) {
                "page" -> anchor.posHOffset
                else -> metrics.marginLeftEmu + anchor.posHOffset
            }
        }
        return when (anchor.posHAlign.lowercase()) {
            "center" -> (metrics.pageWidthEmu - anchor.cx) / 2L
            "right" -> metrics.pageWidthEmu - metrics.marginRightEmu - anchor.cx
            else -> metrics.marginLeftEmu
        }
    }

    private fun resolveAnchorY(anchor: ParsedAnchor, metrics: PageMetrics): Long {
        val v = anchor.posVOffset ?: 0L
        return when (anchor.posVRel) {
            "margin" -> metrics.marginTopEmu + v
            else -> v
        }
    }

    private fun buildCompositeInlineGroupEdits(
        xml: String,
        pStart: Int,
        paraSlice: String,
        anchors: List<ParsedAnchor>,
        metrics: PageMetrics
    ): List<CellEdit>? {
        val rects = anchors.map { a ->
            val x = resolveAnchorX(a, metrics)
            val y = resolveAnchorY(a, metrics)
            Triple(a, x, y)
        }
        val minX = rects.minOf { it.second }
        val minY = rects.minOf { it.third }
        val maxX = rects.maxOf { it.second + it.first.cx }
        val maxY = rects.maxOf { it.third + it.first.cy }
        val rawW = maxOf(1L, maxX - minX)
        val rawH = maxOf(1L, maxY - minY)

        if (rawW > metrics.pageWidthEmu * 2L || rawH > metrics.pageHeightEmu * 2L) {
            return null
        }

        val scale = minOf(
            1.0,
            metrics.maxContentWidthEmu.toDouble() / rawW.toDouble(),
            metrics.maxContentHeightEmu.toDouble() / rawH.toDouble()
        )
        val outW = maxOf(1L, (rawW * scale).toLong())
        val outH = maxOf(1L, (rawH * scale).toLong())

        val sortedLayers = rects.sortedWith(compareBy<Triple<ParsedAnchor, Long, Long>>({ it.first.relativeHeight }, { it.first.originalIndex }))
        val childrenBuilder = StringBuilder()
        for ((anchor, absX, absY) in sortedLayers) {
            val anchorXml = paraSlice.substring(anchor.anchorStart, anchor.anchorEnd)
            val picStart = findFirstTagStart(anchorXml, "pic:pic")
            val picEnd = if (picStart >= 0) findClosingTagEnd(anchorXml, picStart, "pic:pic") else -1
            if (picStart < 0 || picEnd < 0) return null
            val rawPic = anchorXml.substring(picStart, picEnd)
            val patchedPic = patchPicXfrm(rawPic, absX - minX, absY - minY, anchor.cx, anchor.cy)
            childrenBuilder.append(patchedPic)
        }

        val first = anchors[0]
        val groupInline = buildString {
            append("""<wp:inline distT="0" distB="0" distL="0" distR="0">""")
            append("""<wp:extent cx="$outW" cy="$outH"/>""")
            append("""<wp:effectExtent l="0" t="0" r="0" b="0"/>""")
            append("""<wp:docPr id="${first.docPrId}" name="CompositeFigure"/>""")
            append("""<wp:cNvGraphicFramePr/>""")
            append("""<a:graphic><a:graphicData uri="$WPG_GRAPH_URI">""")
            append("""<wpg:wgp xmlns:wpg="$WPG_NS"><wpg:cNvGrpSpPr/><wpg:grpSpPr><a:xfrm>""")
            append("""<a:off x="0" y="0"/><a:ext cx="$outW" cy="$outH"/>""")
            append("""<a:chOff x="0" y="0"/><a:chExt cx="$rawW" cy="$rawH"/>""")
            append("""</a:xfrm></wpg:grpSpPr>""")
            append(childrenBuilder)
            append("""</wpg:wgp></a:graphicData></a:graphic></wp:inline>""")
        }

        val edits = mutableListOf<CellEdit>()
        edits.add(CellEdit(pStart + first.anchorStart, pStart + first.anchorEnd, groupInline))
        for (idx in 1 until anchors.size) {
            val companion = anchors[idx]
            edits.add(CellEdit(pStart + companion.containerStart, pStart + companion.containerEnd, ""))
        }

        val centerDiff = kotlin.math.abs((minX + rawW / 2.0) - (metrics.pageWidthEmu / 2.0))
        if (centerDiff < metrics.pageWidthEmu * 0.18 || (first.posHOffset ?: 0L) >= 600_000L) {
            buildCenterJcEdit(paraSlice, pStart)?.let { edits.add(it) }
        }
        return edits
    }

    private fun buildStandaloneInlineEdits(
        xml: String,
        pStart: Int,
        paraSlice: String,
        anchor: ParsedAnchor,
        metrics: PageMetrics
    ): List<CellEdit> {
        val edits = mutableListOf<CellEdit>()
        val anchorXml = paraSlice.substring(anchor.anchorStart, anchor.anchorEnd)
        val graphicStart = findFirstTagStart(anchorXml, "a:graphic")
        val graphicEnd = if (graphicStart >= 0) findClosingTagEnd(anchorXml, graphicStart, "a:graphic") else -1
        if (graphicStart < 0 || graphicEnd < 0) return emptyList()

        val scale = minOf(
            1.0,
            metrics.maxContentWidthEmu.toDouble() / maxOf(1L, anchor.cx).toDouble(),
            metrics.maxContentHeightEmu.toDouble() / maxOf(1L, anchor.cy).toDouble()
        )
        val outW = maxOf(1L, (anchor.cx * scale).toLong())
        val outH = maxOf(1L, (anchor.cy * scale).toLong())

        var graphicXml = anchorXml.substring(graphicStart, graphicEnd)
        if (outW != anchor.cx || outH != anchor.cy) {
            graphicXml = patchPicXfrm(graphicXml, null, null, outW, outH)
        }

        val docPrTag = extractTagBlock(anchorXml, "wp:docPr")
            ?: """<wp:docPr id="${anchor.docPrId}" name="Picture"/>"""
        val cNvPrTag = extractTagBlock(anchorXml, "wp:cNvGraphicFramePr") ?: ""

        val inlineXml = buildString {
            append("""<wp:inline distT="0" distB="0" distL="0" distR="0">""")
            append("""<wp:extent cx="$outW" cy="$outH"/>""")
            append("""<wp:effectExtent l="0" t="0" r="0" b="0"/>""")
            append(docPrTag)
            append(cNvPrTag)
            append(graphicXml)
            append("""</wp:inline>""")
        }
        edits.add(CellEdit(pStart + anchor.anchorStart, pStart + anchor.anchorEnd, inlineXml))

        val absX = resolveAnchorX(anchor, metrics)
        val centerDiff = kotlin.math.abs((absX + outW / 2.0) - (metrics.pageWidthEmu / 2.0))
        if (anchor.posHAlign.equals("center", ignoreCase = true) || centerDiff < metrics.pageWidthEmu * 0.18) {
            buildCenterJcEdit(paraSlice, pStart)?.let { edits.add(it) }
        }
        return edits
    }

    private fun patchPicXfrm(xmlSnippet: String, x: Long?, y: Long?, cx: Long, cy: Long): String {
        val spPrStart = findFirstTagStart(xmlSnippet, "pic:spPr")
        if (spPrStart < 0) return xmlSnippet
        val spPrEnd = findClosingTagEnd(xmlSnippet, spPrStart, "pic:spPr")
        if (spPrEnd < 0) return xmlSnippet
        val spPrSlice = xmlSnippet.substring(spPrStart, spPrEnd)
        val xfrmStart = findFirstTagStart(spPrSlice, "a:xfrm")
        if (xfrmStart < 0) return xmlSnippet
        val xfrmEnd = findClosingTagEnd(spPrSlice, xfrmStart, "a:xfrm")
        if (xfrmEnd < 0) return xmlSnippet

        var xfrmSlice = spPrSlice.substring(xfrmStart, xfrmEnd)
        if (x != null && y != null) {
            val offMatch = Regex("""<a:off\b[^/>]*/?>""").find(xfrmSlice)
            if (offMatch != null) {
                xfrmSlice = xfrmSlice.substring(0, offMatch.range.first) +
                    """<a:off x="$x" y="$y"/>""" +
                    xfrmSlice.substring(offMatch.range.last + 1)
            }
        }
        val extMatch = Regex("""<a:ext\b[^/>]*/?>""").find(xfrmSlice)
        if (extMatch != null) {
            xfrmSlice = xfrmSlice.substring(0, extMatch.range.first) +
                """<a:ext cx="$cx" cy="$cy"/>""" +
                xfrmSlice.substring(extMatch.range.last + 1)
        }
        val patchedSpPr = spPrSlice.substring(0, xfrmStart) + xfrmSlice + spPrSlice.substring(xfrmEnd)
        return xmlSnippet.substring(0, spPrStart) + patchedSpPr + xmlSnippet.substring(spPrEnd)
    }

    private fun extractTagBlock(xml: String, targetTag: String): String? {
        val start = findFirstTagStart(xml, targetTag)
        if (start < 0) return null
        val end = findClosingTagEnd(xml, start, targetTag)
        if (end <= start) return null
        return xml.substring(start, end)
    }

    private fun findFirstTagStart(xml: String, targetTag: String, fromIndex: Int = 0): Int {
        var i = fromIndex
        val len = xml.length
        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) return -1
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) return -1
            val rawTag = xml.substring(lt, tagEnd + 1)
            if (!rawTag.startsWith("<!--") && !rawTag.startsWith("<?") && !rawTag.startsWith("<![") &&
                !rawTag.startsWith("</") && parseTagName(rawTag) == targetTag
            ) {
                return lt
            }
            i = tagEnd + 1
        }
        return -1
    }

    private fun findClosingTagEnd(xml: String, openLt: Int, targetTag: String): Int {
        var depth = 0
        var i = openLt
        val len = xml.length
        while (i < len) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) return -1
            val tagEnd = findTagEnd(xml, lt)
            if (tagEnd < 0) return -1
            val rawTag = xml.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }
            val name = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")
            if (name == targetTag) {
                if (!isClosing && !isSelfClosing) depth++
                else if (isSelfClosing && depth == 0) return tagEnd + 1
                else if (isClosing) {
                    depth--
                    if (depth == 0) return tagEnd + 1
                }
            }
            i = tagEnd + 1
        }
        return -1
    }

    private fun buildCenterJcEdit(paraSlice: String, pStart: Int): CellEdit? {
        val openEnd = findTagEnd(paraSlice, 0)
        if (openEnd < 0) return null
        val directPPrEnd = findDirectChildTagSpan(paraSlice, openEnd + 1, "w:pPr")
        if (directPPrEnd == null) {
            return CellEdit(pStart + openEnd + 1, pStart + openEnd + 1, "<w:pPr><w:jc w:val=\"center\"/></w:pPr>")
        }
        val pPrSlice = paraSlice.substring(directPPrEnd.first, directPPrEnd.second)
        if (findFirstTagStart(pPrSlice, "w:jc") >= 0) return null
        if (pPrSlice.endsWith("/>")) {
            val expanded = pPrSlice.dropLast(2) + "><w:jc w:val=\"center\"/></w:pPr>"
            return CellEdit(pStart + directPPrEnd.first, pStart + directPPrEnd.second, expanded)
        }
        val postJcTags = listOf(
            "w:textDirection", "w:textAlignment", "w:textboxTightWrap",
            "w:outlineLvl", "w:divId", "w:cnfStyle", "w:rPr", "w:sectPr", "w:pPrChange"
        )
        val closePos = pPrSlice.lastIndexOf("</w:pPr>")
        if (closePos < 0) return null
        var insertOffset = closePos
        for (tag in postJcTags) {
            val pos = findFirstTagStart(pPrSlice, tag)
            if (pos in 0 until insertOffset) insertOffset = pos
        }
        return CellEdit(
            pStart + directPPrEnd.first + insertOffset,
            pStart + directPPrEnd.first + insertOffset,
            "<w:jc w:val=\"center\"/>"
        )
    }

    private fun findDirectChildTagSpan(parentSlice: String, searchFrom: Int, targetTag: String): Pair<Int, Int>? {
        var depth = 0
        var i = searchFrom
        val len = parentSlice.length
        var foundStart = -1
        while (i < len) {
            val lt = parentSlice.indexOf('<', i)
            if (lt < 0) break
            val tagEnd = findTagEnd(parentSlice, lt)
            if (tagEnd < 0) break
            val rawTag = parentSlice.substring(lt, tagEnd + 1)
            if (rawTag.startsWith("<!--") || rawTag.startsWith("<?") || rawTag.startsWith("<![")) {
                i = tagEnd + 1
                continue
            }
            val name = parseTagName(rawTag)
            val isClosing = rawTag.startsWith("</")
            val isSelfClosing = !isClosing && rawTag.endsWith("/>")
            if (!isClosing) {
                if (depth == 0 && name == targetTag) {
                    if (isSelfClosing) return lt to (tagEnd + 1)
                    foundStart = lt
                }
                if (!isSelfClosing) depth++
            } else {
                if (depth == 1 && name == targetTag && foundStart >= 0) {
                    return foundStart to (tagEnd + 1)
                }
                if (depth > 0) depth--
            }
            i = tagEnd + 1
        }
        return null
    }

    private fun ensureDocumentWpgNamespace(xml: String): String {
        val docIdx = xml.indexOf("<w:document")
        if (docIdx < 0) return xml
        val docEnd = findTagEnd(xml, docIdx)
        if (docEnd < 0) return xml
        val docTag = xml.substring(docIdx, docEnd + 1)
        if (docTag.contains("xmlns:wpg")) return xml
        val insertAt = if (docTag.endsWith("/>")) docEnd - 1 else docEnd
        return xml.substring(0, insertAt) + """ xmlns:wpg="$WPG_NS"""" + xml.substring(insertAt)
    }

    private const val WPG_NS = "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
    private const val WPG_GRAPH_URI = "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
}
