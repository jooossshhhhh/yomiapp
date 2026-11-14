package com.yomi.reader.util.conversion

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TabStopSpan
import android.text.style.UnderlineSpan
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.style.TextAlign
import com.yomi.reader.parser.docx.DocxContentParser
import com.yomi.reader.parser.docx.DocxElement
import com.yomi.reader.parser.docx.DocxRun
import com.yomi.reader.parser.pptx.PptxSlideParser
import com.yomi.reader.parser.pptx.SlideElement
import com.yomi.reader.parser.pptx.SlideParagraph
import com.yomi.reader.parser.xlsx.SpreadsheetSheet
import com.yomi.reader.parser.xlsx.XlsxSheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

internal object OfflineDocxToPdfConverter {
    suspend fun convert(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        val parser = DocxContentParser(context)
        val model = parser.parseDocumentModel(fileUri)
        onProgress(20)

        val pageWidth = model.pageWidthDp.coerceIn(320, 1400)
        val pageHeight = model.pageHeightDp.coerceIn(400, 1800)
        val marginL = model.marginLeftPt.coerceIn(18f, pageWidth * 0.25f)
        val marginR = model.marginRightPt.coerceIn(18f, pageWidth * 0.25f)
        val marginT = model.marginTopPt.coerceIn(18f, pageHeight * 0.2f)
        val marginB = model.marginBottomPt.coerceIn(18f, pageHeight * 0.2f)
        val contentWidth = (pageWidth - marginL - marginR).coerceAtLeast(100f)

        val baseSizePt = 11f
        val baseTextPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = baseSizePt
            isAntiAlias = true
            typeface = OfflinePdfRenderSupport.resolveOfficeTypeface(context.assets, null, Typeface.NORMAL, model.embeddedFontPaths).typeface
        }

        val borderLinePaint = Paint().apply {
            color = Color.rgb(160, 165, 175)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        var currentY = marginT

        fun calibratedLineMult(raw: Float): Float = raw.coerceIn(0.88f, 4.0f)

        fun tryLayoutTabAlignedLine(
            spannable: SpannableStringBuilder,
            el: DocxElement.Paragraph,
            drawStartX: Float,
            availableW: Float,
            isMarginBand: Boolean
        ): Pair<Float, List<Pair<Float, StaticLayout>>>? {
            if (!el.text.contains('\t') || el.text.contains('\n')) return null
            val effectiveStops = if (el.tabStopsPt.isNotEmpty()) {
                el.tabStopsPt
            } else if (isMarginBand) {
                listOf(
                    com.yomi.reader.parser.docx.DocxTabStop(contentWidth / 2f, "center"),
                    com.yomi.reader.parser.docx.DocxTabStop(contentWidth, "right")
                )
            } else return null

            val alignedStops = effectiveStops.filter { it.align == "right" || it.align == "end" || it.align == "center" }
            if (alignedStops.isEmpty() && !isMarginBand) return null

            val parts = mutableListOf<CharSequence>()
            var last = 0
            for (i in 0 until spannable.length) {
                if (spannable[i] == '\t') {
                    parts.add(spannable.subSequence(last, i))
                    last = i + 1
                }
            }
            parts.add(spannable.subSequence(last, spannable.length))
            if (parts.size < 2 || parts.size > 4) return null

            val mult = calibratedLineMult(el.lineSpacingMultiplier)
            val placements = mutableListOf<Pair<Float, StaticLayout>>()
            var maxH = 0f

            // Segment 0 starts at local 0
            val seg0 = parts[0]
            var rightCursor = 0f
            if (seg0.isNotEmpty()) {
                val sl0 = OfflinePdfRenderSupport.buildStaticLayout(seg0, baseTextPaint, availableW.toInt().coerceAtLeast(20), Layout.Alignment.ALIGN_NORMAL, mult, false)
                if (sl0.lineCount > 1) return null
                val w0 = sl0.getLineWidth(0)
                placements.add(drawStartX to sl0)
                maxH = max(maxH, sl0.height.toFloat())
                rightCursor = w0 + 6f
            }

            for (idx in 1 until parts.size) {
                val seg = parts[idx]
                if (seg.isEmpty()) continue
                val stop = effectiveStops.getOrNull(idx - 1) ?: effectiveStops.lastOrNull() ?: return null
                val sl = OfflinePdfRenderSupport.buildStaticLayout(seg, baseTextPaint, availableW.toInt().coerceAtLeast(20), Layout.Alignment.ALIGN_NORMAL, mult, false)
                if (sl.lineCount > 1) return null
                val segW = sl.getLineWidth(0)
                val stopLocalX = (stop.posPt - (drawStartX - marginL)).coerceIn(0f, availableW)
                val segLocalX = when (stop.align) {
                    "center" -> (stopLocalX - segW / 2f).coerceIn(rightCursor, (availableW - segW).coerceAtLeast(rightCursor))
                    "right", "end" -> (stopLocalX - segW).coerceIn(rightCursor, (availableW - segW).coerceAtLeast(rightCursor))
                    else -> stopLocalX.coerceIn(rightCursor, (availableW - segW).coerceAtLeast(rightCursor))
                }
                if (segLocalX < rightCursor - 2f) return null
                placements.add((drawStartX + segLocalX) to sl)
                maxH = max(maxH, sl.height.toFloat())
                rightCursor = segLocalX + segW + 6f
            }

            return if (placements.isEmpty()) null else (maxH to placements)
        }

        fun renderMarginBandElements(c: Canvas, elements: List<DocxElement>, startY: Float, maxH: Float) {
            var y = startY
            for (el in elements) {
                if (y >= startY + maxH) break
                when (el) {
                    is DocxElement.Paragraph -> {
                        val spannable = OfflinePdfRenderSupport.buildDocxParagraphSpannable(
                            assets = context.assets,
                            element = el,
                            baseTextSizePt = baseSizePt,
                            embeddedFonts = model.embeddedFontPaths
                        )
                        if (spannable.isNotEmpty()) {
                            val tabbed = tryLayoutTabAlignedLine(spannable, el, marginL, contentWidth, isMarginBand = true)
                            if (tabbed != null) {
                                for ((px, sl) in tabbed.second) {
                                    c.save()
                                    c.translate(px, y)
                                    sl.draw(c)
                                    c.restore()
                                }
                                y += tabbed.first + 2f
                            } else {
                                val layout = OfflinePdfRenderSupport.buildStaticLayout(
                                    text = spannable,
                                    paint = baseTextPaint,
                                    widthPx = contentWidth.toInt(),
                                    alignment = OfflinePdfRenderSupport.mapLayoutAlignment(el.alignment),
                                    lineMultiplier = calibratedLineMult(el.lineSpacingMultiplier),
                                    isJustify = el.alignment == TextAlign.Justify
                                )
                                c.save()
                                c.translate(marginL, y)
                                layout.draw(c)
                                c.restore()
                                y += layout.height + 2f
                            }
                        }
                        if (el.hasBottomBorder) {
                            c.drawLine(marginL, y, marginL + contentWidth, y, borderLinePaint)
                            y += 4f
                        }
                    }
                    is DocxElement.Image -> {
                        val bitmap = BitmapFactory.decodeByteArray(el.imageBytes, 0, el.imageBytes.size)
                        if (bitmap != null) {
                            val targetW = if (el.widthPt > 0f) min(el.widthPt, contentWidth) else min(bitmap.width.toFloat(), contentWidth)
                            val aspect = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
                            val targetH = min(if (el.heightPt > 0f) el.heightPt else targetW * aspect, maxH)
                            val drawW = targetH / aspect.coerceAtLeast(0.01f)
                            val drawX = when (el.alignment) {
                                TextAlign.Center -> marginL + (contentWidth - drawW) / 2f
                                TextAlign.End, TextAlign.Right -> marginL + contentWidth - drawW
                                else -> marginL
                            }
                            c.drawBitmap(bitmap, null, RectF(drawX, y, drawX + drawW, y + targetH), null)
                            bitmap.recycle()
                            y += targetH + 4f
                        }
                    }
                    is DocxElement.Table -> {
                        val cols = el.richRows.maxOfOrNull { row -> row.sumOf { it.gridSpan } } ?: 1
                        val rawWidths = if (el.columnWidthsPt.size == cols && el.columnWidthsPt.sum() > 0f) {
                            val sum = el.columnWidthsPt.sum()
                            el.columnWidthsPt.map { it * (contentWidth / sum) }
                        } else {
                            List(cols) { contentWidth / cols }
                        }
                        for (row in el.richRows) {
                            var rowH = 12f
                            val cellLayouts = mutableListOf<Pair<Int, List<Pair<StaticLayout, DocxElement.Paragraph>>>>()
                            var colCursor = 0
                            for (cell in row) {
                                val spanW = (0 until cell.gridSpan).sumOf { rawWidths.getOrElse(colCursor + it) { 0f }.toDouble() }.toFloat().coerceAtLeast(20f)
                                val pLayouts = cell.paragraphs.mapNotNull { p ->
                                    val sp = OfflinePdfRenderSupport.buildDocxParagraphSpannable(
                                        assets = context.assets,
                                        element = p,
                                        baseTextSizePt = baseSizePt,
                                        embeddedFonts = model.embeddedFontPaths
                                    )
                                    if (sp.isEmpty()) null else OfflinePdfRenderSupport.buildStaticLayout(sp, baseTextPaint, (spanW - 6f).toInt(), OfflinePdfRenderSupport.mapLayoutAlignment(p.alignment)) to p
                                }
                                val h = pLayouts.sumOf { it.first.height + 2 } + 4f
                                rowH = max(rowH, h)
                                cellLayouts.add(colCursor to pLayouts)
                                colCursor += cell.gridSpan
                            }
                            if (y + rowH > startY + maxH) break
                            colCursor = 0
                            for ((idx, cell) in row.withIndex()) {
                                val cellX = marginL + (0 until colCursor).sumOf { rawWidths.getOrElse(it) { 0f }.toDouble() }.toFloat()
                                val spanW = (0 until cell.gridSpan).sumOf { rawWidths.getOrElse(colCursor + it) { 0f }.toDouble() }.toFloat()
                                if (!el.isBorderless) {
                                    c.drawRect(cellX, y, cellX + spanW, y + rowH, borderLinePaint)
                                }
                                var cy = y + 2f
                                for ((layout, _) in cellLayouts[idx].second) {
                                    c.save()
                                    c.translate(cellX + 3f, cy)
                                    layout.draw(c)
                                    c.restore()
                                    cy += layout.height + 2f
                                }
                                colCursor += cell.gridSpan
                            }
                            y += rowH
                        }
                    }
                }
            }
        }

        fun decoratePageHeaderAndFooter(c: Canvas) {
            if (model.headerElements.isNotEmpty()) {
                renderMarginBandElements(c, model.headerElements, (marginT * 0.25f).coerceAtLeast(12f), (marginT - 16f).coerceAtLeast(24f))
            }
            if (model.footerElements.isNotEmpty()) {
                renderMarginBandElements(c, model.footerElements, pageHeight - marginB + 8f, (marginB - 12f).coerceAtLeast(20f))
            }
        }

        fun advanceToNewPage() {
            decoratePageHeaderAndFooter(canvas)
            pdfDocument.finishPage(currentPage)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            canvas = currentPage.canvas
            currentY = marginT
        }

        val maxContentY = pageHeight - marginB

        val totalElements = model.elements.size.coerceAtLeast(1)
        for ((elIdx, element) in model.elements.withIndex()) {
            val pct = 20 + (((elIdx + 1) * 75f) / totalElements).toInt()
            onProgress(pct.coerceIn(20, 95))
            when (element) {
                is DocxElement.Paragraph -> {
                    val didHardBreak = element.hasPageBreakBefore && currentY > marginT + 4f
                    val didSoftBreak = !didHardBreak && element.hasSoftPageBreakHint &&
                        currentY > marginT + (maxContentY - marginT) * 0.42f
                    if (didHardBreak || didSoftBreak) {
                        advanceToNewPage()
                    }

                    val baseLeft = if (element.hangingIndentDp > 0) {
                        (element.indentStartDp - element.hangingIndentDp).coerceAtLeast(0)
                    } else {
                        element.indentStartDp
                    }
                    val indentL = baseLeft.toFloat().coerceIn(0f, contentWidth * 0.55f)
                    val indentR = element.indentEndDp.toFloat().coerceIn(0f, (contentWidth - indentL - 60f).coerceAtLeast(0f))
                    val availableW = (contentWidth - indentL - indentR).toInt().coerceAtLeast(60)
                    val drawLeftX = marginL + indentL

                    val spannable = OfflinePdfRenderSupport.buildDocxParagraphSpannable(
                        assets = context.assets,
                        element = element,
                        baseTextSizePt = baseSizePt,
                        embeddedFonts = model.embeddedFontPaths,
                        blockOffsetPt = indentL
                    )
                    if (spannable.isEmpty()) {
                        if (element.hasBottomBorder) {
                            if (currentY + 6f > maxContentY) advanceToNewPage()
                            canvas.drawLine(marginL, currentY + 2f, marginL + contentWidth, currentY + 2f, borderLinePaint)
                            currentY += 8f
                        } else if (!didHardBreak && !(element.hasPageBreakBefore && currentY <= marginT + 4f)) {
                            val linePt = ((element.fontSizeSp ?: baseSizePt) * calibratedLineMult(element.lineSpacingMultiplier))
                            currentY += element.spacingBeforePt + linePt + element.spacingAfterDp.toFloat()
                        }
                        if (element.hasPageBreakAfter && currentY > marginT + 4f) {
                            advanceToNewPage()
                        }
                        continue
                    }

                    currentY += element.spacingBeforePt
                    val spacingAfter = element.spacingAfterDp.toFloat().coerceAtLeast(0f)
                    val lineMult = calibratedLineMult(element.lineSpacingMultiplier)

                    val tabbed = tryLayoutTabAlignedLine(spannable, element, drawLeftX, availableW.toFloat(), isMarginBand = false)
                    if (tabbed != null) {
                        val blockHeight = tabbed.first
                        if (currentY + blockHeight > maxContentY && currentY > marginT + 10f) {
                            advanceToNewPage()
                        }
                        OfflinePdfRenderSupport.parseColorSafe(element.backgroundFillHex)?.let { bgCol ->
                            val bgPaint = Paint().apply { color = bgCol; style = Paint.Style.FILL }
                            canvas.drawRect(drawLeftX, currentY - 2f, marginL + contentWidth - indentR, currentY + blockHeight + 2f, bgPaint)
                        }
                        for ((px, sl) in tabbed.second) {
                            canvas.save()
                            canvas.translate(px, currentY)
                            sl.draw(canvas)
                            canvas.restore()
                        }
                        currentY += blockHeight
                        if (element.hasBottomBorder) {
                            canvas.drawLine(drawLeftX, currentY + 3f, marginL + contentWidth - indentR, currentY + 3f, borderLinePaint)
                            currentY += 6f
                        }
                        currentY += spacingAfter
                        if (element.hasPageBreakAfter && currentY > marginT + 4f) {
                            advanceToNewPage()
                        }
                        continue
                    }

                    var remainingSpannable: CharSequence = spannable
                    var isFirstSlice = true
                    while (remainingSpannable.isNotEmpty()) {
                        val sliceSpannable = if (isFirstSlice) {
                            remainingSpannable
                        } else {
                            // Rebuild without first-line indent for wrapped continuation slice
                            val ssb = SpannableStringBuilder(remainingSpannable)
                            ssb.getSpans(0, ssb.length, LeadingMarginSpan.Standard::class.java).forEach { s ->
                                ssb.removeSpan(s)
                            }
                            if (element.hangingIndentDp > 0 && (element.alignment == TextAlign.Start || element.alignment == TextAlign.Justify)) {
                                ssb.setSpan(LeadingMarginSpan.Standard(element.hangingIndentDp, element.hangingIndentDp), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            ssb
                        }

                        val layout = OfflinePdfRenderSupport.buildStaticLayout(
                            text = sliceSpannable,
                            paint = baseTextPaint,
                            widthPx = availableW,
                            alignment = OfflinePdfRenderSupport.mapLayoutAlignment(element.alignment),
                            lineMultiplier = lineMult,
                            isJustify = element.alignment == TextAlign.Justify
                        )

                        val blockHeight = layout.height.toFloat()
                        if (currentY + blockHeight <= maxContentY) {
                            OfflinePdfRenderSupport.parseColorSafe(element.backgroundFillHex)?.let { bgCol ->
                                val bgPaint = Paint().apply { color = bgCol; style = Paint.Style.FILL }
                                canvas.drawRect(drawLeftX, currentY - 2f, marginL + contentWidth - indentR, currentY + blockHeight + 2f, bgPaint)
                            }
                            canvas.save()
                            canvas.translate(drawLeftX, currentY)
                            layout.draw(canvas)
                            canvas.restore()
                            currentY += blockHeight
                            break
                        }

                        val availH = maxContentY - currentY
                        val shouldKeepWhole = (element.keepNext || element.isHeading || blockHeight <= (maxContentY - marginT) * 0.35f) &&
                            currentY > marginT + 10f
                        if (shouldKeepWhole) {
                            advanceToNewPage()
                            continue
                        }

                        // Find how many whole lines fit in remaining space
                        var fitLines = 0
                        for (lineIdx in 0 until layout.lineCount) {
                            if (layout.getLineBottom(lineIdx) <= availH) {
                                fitLines = lineIdx + 1
                            } else break
                        }

                        if (fitLines <= 0) {
                            if (currentY > marginT + 10f) {
                                advanceToNewPage()
                                continue
                            } else {
                                // At top of empty page and even 1 line is taller than page: draw it
                                fitLines = 1
                            }
                        }

                        val clipBottom = layout.getLineBottom(fitLines - 1).toFloat()
                        OfflinePdfRenderSupport.parseColorSafe(element.backgroundFillHex)?.let { bgCol ->
                            val bgPaint = Paint().apply { color = bgCol; style = Paint.Style.FILL }
                            canvas.drawRect(drawLeftX, currentY - 2f, marginL + contentWidth - indentR, currentY + clipBottom + 2f, bgPaint)
                        }
                        canvas.save()
                        canvas.clipRect(drawLeftX, currentY, drawLeftX + availableW, currentY + clipBottom)
                        canvas.translate(drawLeftX, currentY)
                        layout.draw(canvas)
                        canvas.restore()

                        if (fitLines >= layout.lineCount) {
                            currentY += clipBottom
                            break
                        }

                        val nextOffset = layout.getLineStart(fitLines)
                        remainingSpannable = sliceSpannable.subSequence(nextOffset, sliceSpannable.length)
                        isFirstSlice = false
                        advanceToNewPage()
                    }

                    if (element.hasBottomBorder) {
                        canvas.drawLine(drawLeftX, currentY + 3f, marginL + contentWidth - indentR, currentY + 3f, borderLinePaint)
                        currentY += 6f
                    }

                    currentY += spacingAfter
                    if (element.hasPageBreakAfter && currentY > marginT + 4f) {
                        advanceToNewPage()
                    }
                }

                is DocxElement.Table -> {
                    val rows = element.richRows
                    if (rows.isEmpty()) continue

                    val cols = rows.maxOfOrNull { row -> row.sumOf { it.gridSpan } }?.coerceAtLeast(1) ?: 1
                    val colWidths = if (element.columnWidthsPt.size == cols && element.columnWidthsPt.sum() > 0f) {
                        val totalRaw = element.columnWidthsPt.sum()
                        element.columnWidthsPt.map { (it / totalRaw) * contentWidth }
                    } else {
                        List(cols) { contentWidth / cols.toFloat() }
                    }

                    val cellPadX = 5f
                    val cellPadY = 4f
                    val tableStrokePaint = Paint().apply {
                        color = Color.rgb(75, 85, 99)
                        style = Paint.Style.STROKE
                        strokeWidth = 0.75f
                    }

                    for ((rowIdx, row) in rows.withIndex()) {
                        val nextRow = rows.getOrNull(rowIdx + 1)
                        val nextRowByCol = mutableMapOf<Int, com.yomi.reader.parser.docx.DocxTableCell>()
                        if (nextRow != null) {
                            var nc = 0
                            for (nCell in nextRow) {
                                for (k in 0 until nCell.gridSpan) {
                                    nextRowByCol[nc + k] = nCell
                                }
                                nc += nCell.gridSpan
                            }
                        }
                        var maxRowH = 16f
                        val renderedCells = mutableListOf<Pair<Int, List<Pair<StaticLayout, DocxElement.Paragraph>>>>()
                        var colCursor = 0

                        for (cell in row) {
                            val spanW = (0 until cell.gridSpan).sumOf {
                                colWidths.getOrElse(colCursor + it) { contentWidth / cols }.toDouble()
                            }.toFloat().coerceAtLeast(24f)

                            val innerW = (spanW - cellPadX * 2).toInt().coerceAtLeast(16)
                            val paraLayouts = mutableListOf<Pair<StaticLayout, DocxElement.Paragraph>>()
                            var totalCellTextH = cellPadY * 2

                            for (p in cell.paragraphs) {
                                val sp = OfflinePdfRenderSupport.buildDocxParagraphSpannable(
                                    assets = context.assets,
                                    element = p,
                                    baseTextSizePt = baseSizePt,
                                    embeddedFonts = model.embeddedFontPaths
                                )
                                if (sp.isNotEmpty()) {
                                    val cLayout = OfflinePdfRenderSupport.buildStaticLayout(
                                        text = sp,
                                        paint = baseTextPaint,
                                        widthPx = innerW,
                                        alignment = OfflinePdfRenderSupport.mapLayoutAlignment(p.alignment),
                                        lineMultiplier = p.lineSpacingMultiplier,
                                        isJustify = p.alignment == TextAlign.Justify
                                    )
                                    paraLayouts.add(cLayout to p)
                                    totalCellTextH += cLayout.height + 2f
                                }
                            }

                            renderedCells.add(colCursor to paraLayouts)
                            maxRowH = max(maxRowH, totalCellTextH)
                            colCursor += cell.gridSpan
                        }

                        if (currentY + maxRowH > maxContentY && currentY > marginT + 10f) {
                            advanceToNewPage()
                        }

                        for ((cellIdx, cell) in row.withIndex()) {
                            val startCol = renderedCells[cellIdx].first
                            val cellX = marginL + (0 until startCol).sumOf {
                                colWidths.getOrElse(it) { contentWidth / cols }.toDouble()
                            }.toFloat()
                            val spanW = (0 until cell.gridSpan).sumOf {
                                colWidths.getOrElse(startCol + it) { contentWidth / cols }.toDouble()
                            }.toFloat()

                            // Cell background fill (only if specified in Word XML!)
                            OfflinePdfRenderSupport.parseColorSafe(cell.backgroundHex)?.let { fillCol ->
                                val bgPaint = Paint().apply { color = fillCol; style = Paint.Style.FILL }
                                canvas.drawRect(cellX, currentY, cellX + spanW, currentY + maxRowH, bgPaint)
                            }

                            // Cell borders (only if visible in Word XML, skipping internal vertical-merge seam)
                            if (!element.isBorderless) {
                                val nextCellMerged = (0 until cell.gridSpan).any { nextRowByCol[startCol + it]?.vMergeContinue == true }
                                val edgePaint = Paint(tableStrokePaint)
                                if (cell.hasTopBorder && !cell.vMergeContinue) {
                                    edgePaint.color = OfflinePdfRenderSupport.parseColorSafe(cell.topBorderColorHex) ?: tableStrokePaint.color
                                    canvas.drawLine(cellX, currentY, cellX + spanW, currentY, edgePaint)
                                }
                                if (cell.hasBottomBorder && !nextCellMerged) {
                                    edgePaint.color = OfflinePdfRenderSupport.parseColorSafe(cell.bottomBorderColorHex) ?: tableStrokePaint.color
                                    canvas.drawLine(cellX, currentY + maxRowH, cellX + spanW, currentY + maxRowH, edgePaint)
                                }
                                if (cell.hasLeftBorder) {
                                    edgePaint.color = OfflinePdfRenderSupport.parseColorSafe(cell.leftBorderColorHex) ?: tableStrokePaint.color
                                    canvas.drawLine(cellX, currentY, cellX, currentY + maxRowH, edgePaint)
                                }
                                if (cell.hasRightBorder) {
                                    edgePaint.color = OfflinePdfRenderSupport.parseColorSafe(cell.rightBorderColorHex) ?: tableStrokePaint.color
                                    canvas.drawLine(cellX + spanW, currentY, cellX + spanW, currentY + maxRowH, edgePaint)
                                }
                            }

                            val pLayouts = renderedCells[cellIdx].second
                            val totalInnerH = pLayouts.sumOf { it.first.height + 2 }.toFloat()
                            var cellY = when (cell.verticalAlign) {
                                "center" -> currentY + ((maxRowH - totalInnerH) / 2f).coerceAtLeast(cellPadY)
                                "bottom" -> currentY + (maxRowH - totalInnerH - cellPadY).coerceAtLeast(cellPadY)
                                else -> currentY + cellPadY
                            }

                            for ((layout, _) in pLayouts) {
                                canvas.save()
                                canvas.translate(cellX + cellPadX, cellY)
                                layout.draw(canvas)
                                canvas.restore()
                                cellY += layout.height + 2f
                            }
                        }

                        currentY += maxRowH
                    }
                    currentY += 8f
                }

                is DocxElement.Image -> {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(element.imageBytes, 0, element.imageBytes.size)
                        if (bitmap != null) {
                            val origW = if (element.widthPt > 0f) element.widthPt else bitmap.width.toFloat()
                            val origH = if (element.heightPt > 0f) element.heightPt else bitmap.height.toFloat()
                            val maxAvailH = (maxContentY - marginT) * 0.85f
                            val scale = min(contentWidth / origW.coerceAtLeast(1f), maxAvailH / origH.coerceAtLeast(1f)).coerceAtMost(1f)
                            val drawW = origW * scale
                            val drawH = origH * scale

                            if (currentY + drawH > maxContentY && currentY > marginT + 10f) {
                                advanceToNewPage()
                            }

                            val drawX = when (element.alignment) {
                                TextAlign.Center -> marginL + (contentWidth - drawW) / 2f
                                TextAlign.End, TextAlign.Right -> marginL + contentWidth - drawW
                                else -> marginL
                            }
                            canvas.drawBitmap(bitmap, null, RectF(drawX, currentY, drawX + drawW, currentY + drawH), null)
                            currentY += drawH + 8f
                            bitmap.recycle()
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        decoratePageHeaderAndFooter(canvas)
        pdfDocument.finishPage(currentPage)

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }
}
