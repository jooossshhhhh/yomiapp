package com.yomi.reader.parser.pptx

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFAutoShape
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFPictureShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFSheet
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextParagraph
import org.apache.poi.xslf.usermodel.XSLFTextRun
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorMapping
import org.openxmlformats.schemas.drawingml.x2006.main.CTColorScheme
import org.openxmlformats.schemas.drawingml.x2006.main.CTGroupTransform2D
import org.openxmlformats.schemas.drawingml.x2006.main.CTOfficeStyleSheet
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties
import org.openxmlformats.schemas.drawingml.x2006.main.CTTransform2D
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackground
import org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture
import org.openxmlformats.schemas.presentationml.x2006.main.CTPlaceholder
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape
import java.io.InputStream

private const val EMU_PER_POINT = 12700f

data class SlideBounds(
    val xPt: Float,
    val yPt: Float,
    val widthPt: Float,
    val heightPt: Float
)

data class SlideTextRun(
    val text: String,
    val fontSizePt: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val colorHex: String? = null,
    val fontFamily: String? = null
)

data class SlideParagraph(
    val runs: List<SlideTextRun>,
    val alignment: String = "left", // left, center, right, justify
    val bulletChar: String? = null,
    val indentLevel: Int = 0,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 4f
)

data class SlideTableCell(
    val paragraphs: List<SlideParagraph>,
    val fillColorHex: String? = null,
    val gridSpan: Int = 1,
    val rowSpan: Int = 1,
    val vMerge: Boolean = false,
    val hMerge: Boolean = false,
    val verticalAlign: String = "ctr"
)

sealed class SlideElement {
    data class TextBox(
        val bounds: SlideBounds,
        val paragraphs: List<SlideParagraph>,
        val fillColorHex: String? = null,
        val borderColorHex: String? = null,
        val borderWidthPt: Float = 0f,
        val isRoundedRect: Boolean = false,
        val verticalAnchor: String = "t", // t, ctr, b
        val insetLeftPt: Float = 7.2f,
        val insetRightPt: Float = 7.2f,
        val insetTopPt: Float = 3.6f,
        val insetBottomPt: Float = 3.6f
    ) : SlideElement()

    data class Image(
        val bounds: SlideBounds,
        val imageBytes: ByteArray
    ) : SlideElement() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Image
            return bounds == other.bounds && imageBytes.contentEquals(other.imageBytes)
        }

        override fun hashCode(): Int = 31 * bounds.hashCode() + imageBytes.contentHashCode()
    }

    data class Table(
        val bounds: SlideBounds,
        val colWidthsPt: List<Float>,
        val rows: List<List<SlideTableCell>>
    ) : SlideElement()
}

data class SlidePageModel(
    val slideNumber: Int,
    val backgroundColorHex: String? = null,
    val backgroundImageBytes: ByteArray? = null,
    val elements: List<SlideElement> = emptyList()
)

data class PptxPresentationModel(
    val slideWidthPt: Float = 960f,
    val slideHeightPt: Float = 540f,
    val slides: List<SlidePageModel> = emptyList()
)

// Backward-compatible simple model
data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val textBlocks: List<String>
)

class PptxSlideParser(private val context: Context) {

    private fun bytesToHex(bytes: ByteArray?): String? {
        if (bytes == null || bytes.size < 3) return null
        return "%02X%02X%02X".format(bytes[0], bytes[1], bytes[2])
    }

    private fun buildThemeColorMap(slide: XSLFSlide): Map<String, String> {
        val map = mutableMapOf(
            "dk1" to "000000",
            "lt1" to "FFFFFF",
            "dk2" to "1F497D",
            "lt2" to "EEECE1",
            "accent1" to "4F81BD",
            "accent2" to "C0504D",
            "accent3" to "9BBB59",
            "accent4" to "8064A2",
            "accent5" to "4BACC6",
            "accent6" to "F79646",
            "hlink" to "0000FF",
            "folHlink" to "800080"
        )

        runCatching {
            val master = slide.slideMaster
            val clrScheme: CTColorScheme? = master?.theme?.xmlObject?.themeElements?.clrScheme
            if (clrScheme != null) {
                fun readColorChoice(srgb: ByteArray?, sysLastClr: ByteArray?): String? {
                    return bytesToHex(srgb) ?: bytesToHex(sysLastClr)
                }
                clrScheme.dk1?.let { c ->
                    readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["dk1"] = it }
                }
                clrScheme.lt1?.let { c ->
                    readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["lt1"] = it }
                }
                clrScheme.dk2?.let { c ->
                    readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["dk2"] = it }
                }
                clrScheme.lt2?.let { c ->
                    readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["lt2"] = it }
                }
                clrScheme.accent1?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent1"] = it } }
                clrScheme.accent2?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent2"] = it } }
                clrScheme.accent3?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent3"] = it } }
                clrScheme.accent4?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent4"] = it } }
                clrScheme.accent5?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent5"] = it } }
                clrScheme.accent6?.let { c -> readColorChoice(c.srgbClr?.`val`, c.sysClr?.lastClr)?.let { map["accent6"] = it } }
            }

            // Apply slideMaster clrMap aliases (tx1 -> dk1, bg1 -> lt1, etc.)
            val ctMaster = master?.xmlObject as? org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMaster
            val clrMap: CTColorMapping? = ctMaster?.clrMap
            map["tx1"] = map[clrMap?.tx1?.toString() ?: "dk1"] ?: map["dk1"]!!
            map["bg1"] = map[clrMap?.bg1?.toString() ?: "lt1"] ?: map["lt1"]!!
            map["tx2"] = map[clrMap?.tx2?.toString() ?: "dk2"] ?: map["dk2"]!!
            map["bg2"] = map[clrMap?.bg2?.toString() ?: "lt2"] ?: map["lt2"]!!
        }

        return map
    }

    private fun resolveSolidFillHex(solidFill: CTSolidColorFillProperties?, themeMap: Map<String, String>): String? {
        if (solidFill == null) return null
        solidFill.srgbClr?.`val`?.let { bytes ->
            return bytesToHex(bytes)
        }
        solidFill.sysClr?.lastClr?.let { bytes ->
            return bytesToHex(bytes)
        }
        val schemeName = solidFill.schemeClr?.`val`?.toString()
        if (!schemeName.isNullOrBlank()) {
            return themeMap[schemeName]
        }
        return null
    }

    private fun readTransformBounds(xfrm: CTTransform2D?): SlideBounds? {
        if (xfrm == null) return null
        val off = xfrm.off ?: return null
        val ext = xfrm.ext ?: return null
        val x = (off.x?.toString()?.toFloatOrNull() ?: 0f) / EMU_PER_POINT
        val y = (off.y?.toString()?.toFloatOrNull() ?: 0f) / EMU_PER_POINT
        val w = (ext.cx.toFloat()) / EMU_PER_POINT
        val h = (ext.cy.toFloat()) / EMU_PER_POINT
        if (w <= 0f || h <= 0f) return null
        return SlideBounds(x, y, w, h)
    }

    private fun findPlaceholderShape(sheet: XSLFSheet?, ph: CTPlaceholder?): CTShape? {
        if (sheet == null || ph == null) return null
        val targetType = ph.type?.toString()
        val targetIdx = if (ph.isSetIdx) ph.idx else -1L

        val shapes = sheet.shapes ?: return null
        // First try exact idx match
        if (targetIdx >= 0) {
            for (s in shapes) {
                val ct = s.xmlObject as? CTShape ?: continue
                val cPh = ct.nvSpPr?.nvPr?.ph ?: continue
                if (cPh.isSetIdx && cPh.idx == targetIdx) return ct
            }
        }
        // Fallback: match by placeholder type (title, ctrTitle, body, subTitle)
        if (!targetType.isNullOrBlank()) {
            for (s in shapes) {
                val ct = s.xmlObject as? CTShape ?: continue
                val cPh = ct.nvSpPr?.nvPr?.ph ?: continue
                if (cPh.type?.toString() == targetType) return ct
            }
        }
        return null
    }

    private fun resolveShapeBounds(shape: XSLFShape, slide: XSLFSlide): SlideBounds? {
        val xml = shape.xmlObject
        when (xml) {
            is CTShape -> {
                readTransformBounds(xml.spPr?.xfrm)?.let { return it }
                val ph = xml.nvSpPr?.nvPr?.ph
                findPlaceholderShape(slide.slideLayout, ph)?.let { layoutCt ->
                    readTransformBounds(layoutCt.spPr?.xfrm)?.let { return it }
                }
                findPlaceholderShape(slide.slideMaster, ph)?.let { masterCt ->
                    readTransformBounds(masterCt.spPr?.xfrm)?.let { return it }
                }
            }
            is CTPicture -> {
                readTransformBounds(xml.spPr?.xfrm)?.let { return it }
            }
            is CTGraphicalObjectFrame -> {
                readTransformBounds(xml.xfrm)?.let { return it }
            }
        }
        return null
    }

    private fun readEmbedImageBytes(sheet: XSLFSheet?, rId: String?): ByteArray? {
        if (sheet == null || rId.isNullOrBlank()) return null
        return runCatching {
            val part = sheet.packagePart.getRelatedPart(sheet.packagePart.getRelationship(rId))
            part.inputStream.use { it.readBytes() }
        }.getOrNull()
    }

    private fun resolveSlideBackground(
        slide: XSLFSlide,
        themeMap: Map<String, String>
    ): Pair<String?, ByteArray?> {
        fun extractFromBg(bg: CTBackground?, ownerSheet: XSLFSheet?): Pair<String?, ByteArray?>? {
            if (bg == null) return null
            val bgPr = bg.bgPr
            if (bgPr != null) {
                val color = resolveSolidFillHex(bgPr.solidFill, themeMap)
                val embedId = bgPr.blipFill?.blip?.embed
                val imgBytes = readEmbedImageBytes(ownerSheet, embedId)
                if (color != null || imgBytes != null) {
                    return color to imgBytes
                }
            }
            val bgRef = bg.bgRef
            if (bgRef != null) {
                val color = bgRef.srgbClr?.`val`?.let { bytesToHex(it) }
                    ?: bgRef.schemeClr?.`val`?.toString()?.let { themeMap[it] }
                if (color != null) return color to null
            }
            return null
        }

        val ctSlide = slide.xmlObject as? org.openxmlformats.schemas.presentationml.x2006.main.CTSlide
        val ctLayout = slide.slideLayout?.xmlObject as? org.openxmlformats.schemas.presentationml.x2006.main.CTSlideLayout
        val ctMaster = slide.slideMaster?.xmlObject as? org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMaster
        extractFromBg(ctSlide?.cSld?.bg, slide)?.let { return it }
        extractFromBg(ctLayout?.cSld?.bg, slide.slideLayout)?.let { return it }
        extractFromBg(ctMaster?.cSld?.bg, slide.slideMaster)?.let { return it }
        return null to null
    }

    private fun parseParagraphs(
        paragraphs: List<XSLFTextParagraph>,
        defaultFontSizePt: Float,
        themeMap: Map<String, String>,
        defaultTextColorHex: String?
    ): List<SlideParagraph> {
        val result = mutableListOf<SlideParagraph>()
        var autoNumCounter = 1

        for (p in paragraphs) {
            val ctP = p.xmlObject
            val pPr = ctP.pPr
            val align = when (pPr?.algn?.toString()) {
                "ctr" -> "center"
                "r" -> "right"
                "just", "dist" -> "justify"
                else -> "left"
            }

            val indentLvl = if (pPr?.isSetLvl == true) pPr.lvl else 0
            val bulletChar: String? = when {
                pPr?.isSetBuNone == true -> null
                pPr?.isSetBuChar == true -> pPr.buChar?.`char` ?: "•"
                pPr?.isSetBuAutoNum == true -> "${autoNumCounter++}."
                else -> null
            }

            val spaceAfterPt = runCatching {
                val pts = pPr?.spcAft?.spcPts?.`val`
                if (pts != null) pts / 100f else 4f
            }.getOrDefault(4f)

            val spaceBeforePt = runCatching {
                val pts = pPr?.spcBef?.spcPts?.`val`
                if (pts != null) pts / 100f else 0f
            }.getOrDefault(0f)

            val pDefSz = runCatching {
                pPr?.defRPr?.sz?.let { it / 100f }
            }.getOrNull()

            val runs = mutableListOf<SlideTextRun>()
            for (r: XSLFTextRun in p.textRuns) {
                val txt = r.rawText ?: ""
                if (txt.isEmpty()) continue

                val rPr = runCatching {
                    val xml = r.xmlObject
                    when (xml) {
                        is org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun -> xml.rPr
                        is org.openxmlformats.schemas.drawingml.x2006.main.CTTextLineBreak -> xml.rPr
                        is org.openxmlformats.schemas.drawingml.x2006.main.CTTextField -> xml.rPr
                        else -> null
                    }
                }.getOrNull()

                val szPt = (rPr?.sz?.let { it / 100f }) ?: pDefSz ?: defaultFontSizePt
                val isBold = rPr?.b == true || r.isBold
                val isItalic = rPr?.i == true || r.isItalic
                val isUnderline = (rPr?.u != null && rPr.u != org.openxmlformats.schemas.drawingml.x2006.main.STTextUnderlineType.NONE) || r.isUnderlined
                val isStrike = (rPr?.strike != null && rPr.strike != org.openxmlformats.schemas.drawingml.x2006.main.STTextStrikeType.NO_STRIKE) || r.isStrikethrough
                val fontColor = resolveSolidFillHex(rPr?.solidFill, themeMap) ?: defaultTextColorHex
                val fontFamily = rPr?.latin?.typeface

                runs.add(
                    SlideTextRun(
                        text = txt,
                        fontSizePt = szPt,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        isStrikeThrough = isStrike,
                        colorHex = fontColor,
                        fontFamily = fontFamily
                    )
                )
            }

            if (runs.isNotEmpty()) {
                result.add(
                    SlideParagraph(
                        runs = runs,
                        alignment = align,
                        bulletChar = bulletChar,
                        indentLevel = indentLvl,
                        spaceBeforePt = spaceBeforePt,
                        spaceAfterPt = spaceAfterPt
                    )
                )
            }
        }
        return result
    }

    private fun collectSlideShapes(
        shapes: List<XSLFShape>,
        slide: XSLFSlide,
        themeMap: Map<String, String>,
        skipPlaceholders: Boolean,
        out: MutableList<SlideElement>,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f
    ) {
        for (shape in shapes) {
            if (shape is XSLFGroupShape) {
                val grpXml = shape.xmlObject as? org.openxmlformats.schemas.presentationml.x2006.main.CTGroupShape
                val xfrm: CTGroupTransform2D? = grpXml?.grpSpPr?.xfrm
                val grpOffX = (xfrm?.off?.x?.toString()?.toFloatOrNull() ?: 0f) / EMU_PER_POINT
                val grpOffY = (xfrm?.off?.y?.toString()?.toFloatOrNull() ?: 0f) / EMU_PER_POINT
                val grpExtW = (xfrm?.ext?.cx?.toFloat() ?: 1f) / EMU_PER_POINT
                val grpExtH = (xfrm?.ext?.cy?.toFloat() ?: 1f) / EMU_PER_POINT
                val chOffX = (xfrm?.chOff?.x?.toString()?.toFloatOrNull() ?: grpOffX) / EMU_PER_POINT
                val chOffY = (xfrm?.chOff?.y?.toString()?.toFloatOrNull() ?: grpOffY) / EMU_PER_POINT
                val chExtW = ((xfrm?.chExt?.cx?.toFloat() ?: 0f) / EMU_PER_POINT).takeIf { it > 0f } ?: grpExtW
                val chExtH = ((xfrm?.chExt?.cy?.toFloat() ?: 0f) / EMU_PER_POINT).takeIf { it > 0f } ?: grpExtH

                val subScaleX = scaleX * (grpExtW / chExtW)
                val subScaleY = scaleY * (grpExtH / chExtH)
                val subOffX = offsetX + (grpOffX - chOffX * (grpExtW / chExtW)) * scaleX
                val subOffY = offsetY + (grpOffY - chOffY * (grpExtH / chExtH)) * scaleY

                collectSlideShapes(shape.shapes, slide, themeMap, skipPlaceholders, out, subOffX, subOffY, subScaleX, subScaleY)
                continue
            }

            if (skipPlaceholders) {
                val ctShape = shape.xmlObject as? CTShape
                if (ctShape?.nvSpPr?.nvPr?.isSetPh == true) continue
                val ctPic = shape.xmlObject as? CTPicture
                if (ctPic?.nvPicPr?.nvPr?.isSetPh == true) continue
            }

            val rawBounds = resolveShapeBounds(shape, slide) ?: continue
            val bounds = SlideBounds(
                xPt = offsetX + rawBounds.xPt * scaleX,
                yPt = offsetY + rawBounds.yPt * scaleY,
                widthPt = rawBounds.widthPt * scaleX,
                heightPt = rawBounds.heightPt * scaleY
            )

            when (shape) {
                is XSLFPictureShape -> {
                    val bytes = runCatching { shape.pictureData?.data }.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        out.add(SlideElement.Image(bounds = bounds, imageBytes = bytes))
                    }
                }

                is XSLFTable -> {
                    val ctFrame = shape.xmlObject as? CTGraphicalObjectFrame
                    val tblXml = runCatching {
                        shape.ctTable
                    }.getOrNull()

                    val totalGridW = tblXml?.tblGrid?.gridColList?.sumOf { it.w.toString().toDoubleOrNull() ?: 0.0 }?.toFloat()?.div(EMU_PER_POINT) ?: bounds.widthPt
                    val colWidths = tblXml?.tblGrid?.gridColList?.map { col ->
                        val raw = col.w?.toString()?.toFloatOrNull()?.div(EMU_PER_POINT) ?: (bounds.widthPt / shape.numberOfColumns.coerceAtLeast(1))
                        if (totalGridW > 0f) raw * (bounds.widthPt / totalGridW) else raw
                    } ?: List(shape.numberOfColumns.coerceAtLeast(1)) { bounds.widthPt / shape.numberOfColumns.coerceAtLeast(1) }

                    val rows = shape.rows.map { row ->
                        row.cells.map { cell ->
                            val ctCell = cell.xmlObject as? org.openxmlformats.schemas.drawingml.x2006.main.CTTableCell
                            val fillHex = resolveSolidFillHex(ctCell?.tcPr?.solidFill, themeMap)
                            val paras = parseParagraphs(cell.textParagraphs, 11f, themeMap, "111827")
                            SlideTableCell(
                                paragraphs = paras,
                                fillColorHex = fillHex,
                                gridSpan = cell.gridSpan.coerceAtLeast(1),
                                rowSpan = cell.rowSpan.coerceAtLeast(1),
                                vMerge = cell.isMerged && ctCell?.isSetVMerge == true,
                                hMerge = cell.isMerged && ctCell?.isSetHMerge == true
                            )
                        }
                    }

                    out.add(SlideElement.Table(bounds = bounds, colWidthsPt = colWidths, rows = rows))
                }

                is XSLFTextShape -> {
                    val ctShape = shape.xmlObject as? CTShape
                    val spPr = ctShape?.spPr
                    val fillHex = resolveSolidFillHex(spPr?.solidFill, themeMap)
                    val borderHex = resolveSolidFillHex(spPr?.ln?.solidFill, themeMap)
                    val borderW = (spPr?.ln?.w?.toFloat() ?: 0f) / EMU_PER_POINT

                    val bodyPr = ctShape?.txBody?.bodyPr
                    val vAnchor = bodyPr?.anchor?.toString() ?: "t"
                    val insL = if (bodyPr?.isSetLIns == true) (bodyPr.lIns?.toString()?.toFloatOrNull() ?: 91440f) / EMU_PER_POINT else 7.2f
                    val insR = if (bodyPr?.isSetRIns == true) (bodyPr.rIns?.toString()?.toFloatOrNull() ?: 91440f) / EMU_PER_POINT else 7.2f
                    val insT = if (bodyPr?.isSetTIns == true) (bodyPr.tIns?.toString()?.toFloatOrNull() ?: 45720f) / EMU_PER_POINT else 3.6f
                    val insB = if (bodyPr?.isSetBIns == true) (bodyPr.bIns?.toString()?.toFloatOrNull() ?: 45720f) / EMU_PER_POINT else 3.6f

                    val phType = ctShape?.nvSpPr?.nvPr?.ph?.type?.toString()
                    val defaultFontPt = when (phType) {
                        "title", "ctrTitle" -> 32f
                        "subTitle" -> 20f
                        else -> 16f
                    }
                    val defaultTextColor = if (fillHex != null && isDarkColor(fillHex)) "FFFFFF" else themeMap["tx1"] ?: "111827"

                    val paragraphs = parseParagraphs(shape.textParagraphs, defaultFontPt, themeMap, defaultTextColor)
                    val prst = spPr?.prstGeom?.prst?.toString()
                    val isRounded = prst == "roundRect" || prst == "ellipse"

                    // Add shape even if no text when it has a visible fill or border (decorative shapes/cards)
                    if (paragraphs.isNotEmpty() || fillHex != null || (borderHex != null && borderW > 0f)) {
                        out.add(
                            SlideElement.TextBox(
                                bounds = bounds,
                                paragraphs = paragraphs,
                                fillColorHex = fillHex,
                                borderColorHex = borderHex,
                                borderWidthPt = borderW,
                                isRoundedRect = isRounded,
                                verticalAnchor = vAnchor,
                                insetLeftPt = insL,
                                insetRightPt = insR,
                                insetTopPt = insT,
                                insetBottomPt = insB
                            )
                        )
                    }
                }
            }
        }
    }

    private fun isDarkColor(hex: String): Boolean {
        return runCatching {
            val r = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            val luma = 0.299 * r + 0.587 * g + 0.114 * b
            luma < 128.0
        }.getOrDefault(false)
    }

    suspend fun parsePresentationModel(uri: Uri): PptxPresentationModel = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open presentation at $uri")

        inputStream.use { stream ->
            val pptx = XMLSlideShow(stream)
            val sldSz = pptx.ctPresentation?.sldSz
            val slideW = if (sldSz != null && sldSz.cx > 0) sldSz.cx.toFloat() / EMU_PER_POINT else 960f
            val slideH = if (sldSz != null && sldSz.cy > 0) sldSz.cy.toFloat() / EMU_PER_POINT else 540f

            val slideModels = pptx.slides.mapIndexed { index, slide ->
                val themeMap = buildThemeColorMap(slide)
                val (bgColor, bgImg) = resolveSlideBackground(slide, themeMap)
                val elements = mutableListOf<SlideElement>()

                // Render non-placeholder background decorations from master & layout first
                runCatching {
                    collectSlideShapes(slide.slideMaster.shapes, slide, themeMap, skipPlaceholders = true, out = elements)
                }
                runCatching {
                    collectSlideShapes(slide.slideLayout.shapes, slide, themeMap, skipPlaceholders = true, out = elements)
                }
                // Render actual slide shapes on top
                collectSlideShapes(slide.shapes, slide, themeMap, skipPlaceholders = false, out = elements)

                SlidePageModel(
                    slideNumber = index + 1,
                    backgroundColorHex = bgColor,
                    backgroundImageBytes = bgImg,
                    elements = elements
                )
            }

            PptxPresentationModel(
                slideWidthPt = slideW,
                slideHeightPt = slideH,
                slides = slideModels
            )
        }
    }

    suspend fun parsePresentation(uri: Uri): List<SlideItem> = withContext(Dispatchers.IO) {
        val model = parsePresentationModel(uri)
        model.slides.map { page ->
            val texts = page.elements.filterIsInstance<SlideElement.TextBox>()
                .flatMap { tb -> tb.paragraphs.map { p -> p.runs.joinToString("") { it.text } } }
                .filter { it.isNotBlank() }
            SlideItem(
                slideNumber = page.slideNumber,
                title = texts.firstOrNull() ?: "",
                textBlocks = texts.drop(1)
            )
        }
    }
}
