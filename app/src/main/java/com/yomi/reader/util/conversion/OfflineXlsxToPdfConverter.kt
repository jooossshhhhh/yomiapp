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

internal object OfflineXlsxToPdfConverter {

    internal data class SinglePageFit(
        val pageWidth: Int,
        val pageHeight: Int,
        val scale: Float,
        val isLandscape: Boolean
    )

    internal fun computeSinglePageFit(
        contentWidthPt: Float,
        contentHeightPt: Float,
        preferLandscape: Boolean,
        marginPt: Float = 24f
    ): SinglePageFit {
        val safeW = contentWidthPt.coerceAtLeast(1f)
        val safeH = contentHeightPt.coerceAtLeast(1f)

        val landAvailW = (OfflinePdfRenderSupport.A4_LANDSCAPE_WIDTH - marginPt * 2f).coerceAtLeast(1f)
        val landAvailH = (OfflinePdfRenderSupport.A4_LANDSCAPE_HEIGHT - marginPt * 2f).coerceAtLeast(1f)
        val landScale = min(landAvailW / safeW, landAvailH / safeH).coerceAtMost(1f)

        val portAvailW = (OfflinePdfRenderSupport.A4_PORTRAIT_WIDTH - marginPt * 2f).coerceAtLeast(1f)
        val portAvailH = (OfflinePdfRenderSupport.A4_PORTRAIT_HEIGHT - marginPt * 2f).coerceAtLeast(1f)
        val portScale = min(portAvailW / safeW, portAvailH / safeH).coerceAtMost(1f)

        val useLandscape = when {
            landScale > portScale + 0.01f -> true
            portScale > landScale + 0.01f -> false
            else -> preferLandscape || safeW > safeH
        }

        return if (useLandscape) {
            SinglePageFit(
                pageWidth = OfflinePdfRenderSupport.A4_LANDSCAPE_WIDTH,
                pageHeight = OfflinePdfRenderSupport.A4_LANDSCAPE_HEIGHT,
                scale = landScale,
                isLandscape = true
            )
        } else {
            SinglePageFit(
                pageWidth = OfflinePdfRenderSupport.A4_PORTRAIT_WIDTH,
                pageHeight = OfflinePdfRenderSupport.A4_PORTRAIT_HEIGHT,
                scale = portScale,
                isLandscape = false
            )
        }
    }

    suspend fun convert(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        val parser = XlsxSheetParser(context)
        val sheets = parser.parseSpreadsheet(fileUri)
        onProgress(20)

        val pdfDocument = PdfDocument()
        var pageNumber = 1

        val gridStrokePaint = Paint().apply {
            color = Color.rgb(225, 228, 232)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val explicitBorderPaint = Paint().apply {
            color = Color.rgb(80, 85, 95)
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }

        val nonEmptySheets = sheets.filter { it.richRows.isNotEmpty() }
        val totalSheets = nonEmptySheets.size.coerceAtLeast(1)
        for ((sheetIdx, sheet) in sheets.withIndex()) {
            if (sheet.richRows.isEmpty()) continue
            onProgress(20 + (((sheetIdx + 1) * 75f) / totalSheets).toInt().coerceIn(0, 75))

            val margin = 24f

            // Convert char widths to points (~6.2pt per Excel char width) across the full sheet width
            val naturalWidthsPt = sheet.columnWidthsChars.map { (it * 6.2f).coerceIn(22f, 280f) }
            val totalCols = naturalWidthsPt.size
            val totalRawWidth = naturalWidthsPt.sum().coerceAtLeast(1f)

            // Build quick merged-region lookup for active sheet
            val mergeLookup = HashMap<Pair<Int, Int>, com.yomi.reader.parser.xlsx.MergedCellRegion>()
            for (m in sheet.mergedRegions) {
                for (r in m.firstRow..m.lastRow) {
                    for (c in m.firstCol..m.lastCol) {
                        mergeLookup[r to c] = m
                    }
                }
            }

            val rowHeightsMap = mutableMapOf<Int, Float>()
            val rowLayoutsMap = mutableMapOf<Int, Map<Int, StaticLayout?>>()
            var totalRawHeight = 0f

            // Pre-calculate cell layouts & row heights at natural 1:1 coordinates so text never wraps unevenly
            for (row in sheet.richRows) {
                var maxH = row.heightPt.coerceAtLeast(15f)
                val cellLayouts = mutableMapOf<Int, StaticLayout?>()

                for (colIdx in 0 until totalCols) {
                    val cell = row.cells.getOrNull(colIdx) ?: continue
                    val merge = mergeLookup[row.rowIndex to colIdx]
                    if (merge != null && (merge.firstRow != row.rowIndex || merge.firstCol != colIdx)) {
                        continue
                    }

                    val spanWidth = if (merge != null) {
                        val clampedEnd = min(merge.lastCol, totalCols - 1)
                        (colIdx..clampedEnd).sumOf { c ->
                            naturalWidthsPt.getOrElse(c) { 30f }.toDouble()
                        }.toFloat()
                    } else {
                        naturalWidthsPt[colIdx]
                    }

                    if (cell.value.isNotEmpty()) {
                        val cellStyle = when {
                            cell.isBold && cell.isItalic -> Typeface.BOLD_ITALIC
                            cell.isBold -> Typeface.BOLD
                            cell.isItalic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                        val resolvedCellTf = OfflinePdfRenderSupport.resolveOfficeTypeface(context.assets, cell.fontName, cellStyle)
                        val paint = TextPaint().apply {
                            color = OfflinePdfRenderSupport.parseColorSafe(cell.textColorHex) ?: Color.BLACK
                            textSize = cell.fontSizePt
                            isAntiAlias = true
                            isUnderlineText = cell.isUnderline
                            typeface = resolvedCellTf.typeface
                            isFakeBoldText = resolvedCellTf.needsFakeBold
                            textSkewX = if (resolvedCellTf.needsFakeItalic) -0.25f else 0f
                        }

                        val sl = OfflinePdfRenderSupport.buildStaticLayout(
                            text = cell.value,
                            paint = paint,
                            widthPx = (spanWidth - 6f).toInt().coerceAtLeast(12),
                            alignment = OfflinePdfRenderSupport.mapLayoutAlignmentStr(cell.horizontalAlignment),
                            lineMultiplier = 1.08f
                        )
                        cellLayouts[colIdx] = sl
                        if (merge == null || merge.firstRow == merge.lastRow) {
                            maxH = max(maxH, sl.height + 6f)
                        }
                    }
                }

                rowHeightsMap[row.rowIndex] = maxH
                rowLayoutsMap[row.rowIndex] = cellLayouts
                totalRawHeight += maxH
            }

            val fit = computeSinglePageFit(
                contentWidthPt = totalRawWidth,
                contentHeightPt = totalRawHeight,
                preferLandscape = sheet.isLandscape,
                marginPt = margin
            )

            // Fit the entire sheet cleanly onto a single PDF page without horizontal or vertical slicing
            val pageInfo = PdfDocument.PageInfo.Builder(fit.pageWidth, fit.pageHeight, pageNumber).create()
            val currentPage = pdfDocument.startPage(pageInfo)
            val canvas = currentPage.canvas

            val drawnWidth = totalRawWidth * fit.scale
            val offsetX = ((fit.pageWidth - drawnWidth) / 2f).coerceAtLeast(margin)

            canvas.save()
            canvas.translate(offsetX, margin)
            canvas.scale(fit.scale, fit.scale)

            var currentY = 0f
            for (row in sheet.richRows) {
                val rowH = rowHeightsMap[row.rowIndex] ?: 16f
                val cellLayouts = rowLayoutsMap[row.rowIndex] ?: emptyMap()
                var cellX = 0f

                for (colIdx in 0 until totalCols) {
                    val colW = naturalWidthsPt[colIdx]
                    val cell = row.cells.getOrNull(colIdx)
                    val merge = mergeLookup[row.rowIndex to colIdx]

                    if (merge != null && (merge.firstRow != row.rowIndex || merge.firstCol != colIdx)) {
                        cellX += colW
                        continue
                    }

                    val drawW = if (merge != null) {
                        val clampedEnd = min(merge.lastCol, totalCols - 1)
                        (colIdx..clampedEnd).sumOf { c ->
                            naturalWidthsPt.getOrElse(c) { 30f }.toDouble()
                        }.toFloat()
                    } else {
                        colW
                    }

                    val drawH = if (merge != null && merge.lastRow > merge.firstRow) {
                        (merge.firstRow..merge.lastRow).sumOf { r ->
                            (rowHeightsMap[r] ?: 16f).toDouble()
                        }.toFloat()
                    } else {
                        rowH
                    }

                    if (cell != null) {
                        OfflinePdfRenderSupport.parseColorSafe(cell.backgroundColorHex)?.let { bg ->
                            val bgPaint = Paint().apply { color = bg; style = Paint.Style.FILL }
                            canvas.drawRect(cellX, currentY, cellX + drawW, currentY + drawH, bgPaint)
                        }

                        if (sheet.displayGridlines) {
                            canvas.drawRect(cellX, currentY, cellX + drawW, currentY + drawH, gridStrokePaint)
                        }
                        if (cell.hasTopBorder) canvas.drawLine(cellX, currentY, cellX + drawW, currentY, explicitBorderPaint)
                        if (cell.hasBottomBorder) canvas.drawLine(cellX, currentY + drawH, cellX + drawW, currentY + drawH, explicitBorderPaint)
                        if (cell.hasLeftBorder) canvas.drawLine(cellX, currentY, cellX, currentY + drawH, explicitBorderPaint)
                        if (cell.hasRightBorder) canvas.drawLine(cellX + drawW, currentY, cellX + drawW, currentY + drawH, explicitBorderPaint)

                        val sl = cellLayouts[colIdx]
                        if (sl != null) {
                            val textY = when (cell.verticalAlignment) {
                                "top" -> currentY + 3f
                                "bottom" -> currentY + (drawH - sl.height - 3f).coerceAtLeast(3f)
                                else -> currentY + ((drawH - sl.height) / 2f).coerceAtLeast(2f)
                            }
                            canvas.save()
                            canvas.clipRect(cellX + 1f, currentY + 1f, cellX + drawW - 1f, currentY + drawH - 1f)
                            canvas.translate(cellX + 3f, textY)
                            sl.draw(canvas)
                            canvas.restore()
                        }
                    }

                    cellX += colW
                }

                currentY += rowH
            }

            canvas.restore()
            pdfDocument.finishPage(currentPage)
            pageNumber++
        }

        if (pageNumber == 1) {
            val emptyPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(OfflinePdfRenderSupport.A4_LANDSCAPE_WIDTH, OfflinePdfRenderSupport.A4_LANDSCAPE_HEIGHT, 1).create())
            pdfDocument.finishPage(emptyPage)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

}
