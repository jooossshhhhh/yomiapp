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

internal object OfflinePptxToPdfConverter {
    private fun buildSlideParagraphSpannable(
        assets: AssetManager,
        p: SlideParagraph,
        baseFontPt: Float,
        defaultTextColor: Int
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        if (!p.bulletChar.isNullOrBlank()) {
            val bStart = sb.length
            sb.append(p.bulletChar).append("  ")
            val bEnd = sb.length
            val refRun = p.runs.firstOrNull()
            val refStyle = when {
                refRun?.isBold == true && refRun.isItalic -> Typeface.BOLD_ITALIC
                refRun?.isBold == true -> Typeface.BOLD
                refRun?.isItalic == true -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val resolvedBullet = OfflinePdfRenderSupport.resolveOfficeTypeface(assets, refRun?.fontFamily, refStyle)
            sb.setSpan(OfflinePdfRenderSupport.OfficeTypefaceSpan(resolvedBullet), bStart, bEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (run in p.runs) {
            val start = sb.length
            sb.append(run.text)
            val end = sb.length
            if (end <= start) continue

            val runStyle = when {
                run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                run.isBold -> Typeface.BOLD
                run.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val resolvedRunTf = OfflinePdfRenderSupport.resolveOfficeTypeface(assets, run.fontFamily, runStyle)
            sb.setSpan(OfflinePdfRenderSupport.OfficeTypefaceSpan(resolvedRunTf), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (run.isUnderline) {
                sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.isStrikeThrough) {
                sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val ratio = (run.fontSizePt / baseFontPt).coerceIn(0.4f, 6f)
            if (kotlin.math.abs(ratio - 1f) > 0.02f) {
                sb.setSpan(RelativeSizeSpan(ratio), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val runColor = OfflinePdfRenderSupport.parseColorSafe(run.colorHex) ?: defaultTextColor
            sb.setSpan(ForegroundColorSpan(runColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return sb
    }

    suspend fun convert(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        val parser = PptxSlideParser(context)
        val model = parser.parsePresentationModel(fileUri)
        onProgress(20)

        val slideW = model.slideWidthPt.toInt().coerceIn(320, 1920)
        val slideH = model.slideHeightPt.toInt().coerceIn(240, 1280)
        val pdfDocument = PdfDocument()

        val baseSizePt = 16f
        val basePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = baseSizePt
            isAntiAlias = true
            typeface = OfflinePdfRenderSupport.resolveOfficeTypeface(context.assets, null, Typeface.NORMAL).typeface
        }

        if (model.slides.isEmpty()) {
            val emptyPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(slideW, slideH, 1).create())
            pdfDocument.finishPage(emptyPage)
        }

        val totalSlides = model.slides.size.coerceAtLeast(1)
        for ((sIdx, slide) in model.slides.withIndex()) {
            onProgress(20 + (((sIdx + 1) * 75f) / totalSlides).toInt().coerceIn(0, 75))
            val pageInfo = PdfDocument.PageInfo.Builder(slideW, slideH, slide.slideNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // 1. Slide background (exact color or background image)
            val bgColorInt = OfflinePdfRenderSupport.parseColorSafe(slide.backgroundColorHex) ?: Color.WHITE
            canvas.drawColor(bgColorInt)

            slide.backgroundImageBytes?.let { bgBytes ->
                runCatching {
                    val bmp = BitmapFactory.decodeByteArray(bgBytes, 0, bgBytes.size)
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, null, RectF(0f, 0f, slideW.toFloat(), slideH.toFloat()), null)
                        bmp.recycle()
                    }
                }
            }

            // 2. Render each slide element at its exact bounds
            for (el in slide.elements) {
                when (el) {
                    is SlideElement.Image -> {
                        runCatching {
                            val bmp = BitmapFactory.decodeByteArray(el.imageBytes, 0, el.imageBytes.size)
                            if (bmp != null) {
                                val rect = RectF(
                                    el.bounds.xPt,
                                    el.bounds.yPt,
                                    el.bounds.xPt + el.bounds.widthPt,
                                    el.bounds.yPt + el.bounds.heightPt
                                )
                                canvas.drawBitmap(bmp, null, rect, null)
                                bmp.recycle()
                            }
                        }
                    }

                    is SlideElement.TextBox -> {
                        val b = el.bounds
                        val rect = RectF(b.xPt, b.yPt, b.xPt + b.widthPt, b.yPt + b.heightPt)
                        val corner = if (el.isRoundedRect) min(b.widthPt, b.heightPt) * 0.12f else 0f

                        OfflinePdfRenderSupport.parseColorSafe(el.fillColorHex)?.let { fillCol ->
                            val fillPaint = Paint().apply { color = fillCol; style = Paint.Style.FILL; isAntiAlias = true }
                            if (corner > 0f) canvas.drawRoundRect(rect, corner, corner, fillPaint)
                            else canvas.drawRect(rect, fillPaint)
                        }

                        if (el.borderWidthPt > 0f) {
                            OfflinePdfRenderSupport.parseColorSafe(el.borderColorHex)?.let { borderCol ->
                                val strokePaint = Paint().apply {
                                    color = borderCol
                                    style = Paint.Style.STROKE
                                    strokeWidth = el.borderWidthPt
                                    isAntiAlias = true
                                }
                                if (corner > 0f) canvas.drawRoundRect(rect, corner, corner, strokePaint)
                                else canvas.drawRect(rect, strokePaint)
                            }
                        }

                        if (el.paragraphs.isNotEmpty()) {
                            val availW = (b.widthPt - el.insetLeftPt - el.insetRightPt).toInt().coerceAtLeast(20)
                            val layouts = el.paragraphs.mapNotNull { p ->
                                val indentPx = p.indentLevel * 16
                                val sp = buildSlideParagraphSpannable(context.assets, p, baseSizePt, Color.BLACK)
                                if (sp.isEmpty()) null else {
                                    val sl = OfflinePdfRenderSupport.buildStaticLayout(
                                        text = sp,
                                        paint = basePaint,
                                        widthPx = (availW - indentPx).coerceAtLeast(16),
                                        alignment = OfflinePdfRenderSupport.mapLayoutAlignmentStr(p.alignment),
                                        lineMultiplier = 1.15f,
                                        isJustify = p.alignment == "justify"
                                    )
                                    Triple(sl, p, indentPx)
                                }
                            }

                            val totalTextH = layouts.sumOf { (sl, p, _) ->
                                (sl.height + p.spaceBeforePt + p.spaceAfterPt).toDouble()
                            }.toFloat()

                            val availH = (b.heightPt - el.insetTopPt - el.insetBottomPt).coerceAtLeast(10f)
                            var drawY = when (el.verticalAnchor) {
                                "ctr" -> b.yPt + el.insetTopPt + ((availH - totalTextH) / 2f).coerceAtLeast(0f)
                                "b" -> b.yPt + b.heightPt - el.insetBottomPt - totalTextH
                                else -> b.yPt + el.insetTopPt
                            }

                            canvas.save()
                            canvas.clipRect(rect)
                            for ((sl, p, indentPx) in layouts) {
                                drawY += p.spaceBeforePt
                                canvas.save()
                                canvas.translate(b.xPt + el.insetLeftPt + indentPx, drawY)
                                sl.draw(canvas)
                                canvas.restore()
                                drawY += sl.height + p.spaceAfterPt
                            }
                            canvas.restore()
                        }
                    }

                    is SlideElement.Table -> {
                        val b = el.bounds
                        val tableBorderPaint = Paint().apply {
                            color = Color.rgb(180, 185, 195)
                            style = Paint.Style.STROKE
                            strokeWidth = 0.7f
                        }

                        var rowY = b.yPt
                        for (row in el.rows) {
                            var rowH = 22f
                            val cellLayouts = mutableListOf<Pair<Int, List<StaticLayout>>>()
                            var colCursor = 0

                            for (cell in row) {
                                if (cell.vMerge || cell.hMerge) {
                                    cellLayouts.add(colCursor to emptyList())
                                    colCursor++
                                    continue
                                }
                                val cellW = (0 until cell.gridSpan).sumOf {
                                    el.colWidthsPt.getOrElse(colCursor + it) { 40f }.toDouble()
                                }.toFloat().coerceAtLeast(20f)

                                val pLayouts = cell.paragraphs.mapNotNull { p ->
                                    val sp = buildSlideParagraphSpannable(context.assets, p, baseSizePt, Color.BLACK)
                                    if (sp.isEmpty()) null else OfflinePdfRenderSupport.buildStaticLayout(sp, basePaint, (cellW - 8f).toInt().coerceAtLeast(14), OfflinePdfRenderSupport.mapLayoutAlignmentStr(p.alignment))
                                }
                                rowH = max(rowH, pLayouts.sumOf { it.height + 2 }.toFloat() + 8f)
                                cellLayouts.add(colCursor to pLayouts)
                                colCursor += cell.gridSpan
                            }

                            colCursor = 0
                            for ((idx, cell) in row.withIndex()) {
                                val cellX = b.xPt + (0 until colCursor).sumOf {
                                    el.colWidthsPt.getOrElse(it) { 40f }.toDouble()
                                }.toFloat()
                                val cellW = (0 until cell.gridSpan).sumOf {
                                    el.colWidthsPt.getOrElse(colCursor + it) { 40f }.toDouble()
                                }.toFloat()

                                OfflinePdfRenderSupport.parseColorSafe(cell.fillColorHex)?.let { bg ->
                                    val bgPaint = Paint().apply { color = bg; style = Paint.Style.FILL }
                                    canvas.drawRect(cellX, rowY, cellX + cellW, rowY + rowH, bgPaint)
                                }
                                canvas.drawRect(cellX, rowY, cellX + cellW, rowY + rowH, tableBorderPaint)

                                var cy = rowY + 4f
                                for (sl in cellLayouts[idx].second) {
                                    canvas.save()
                                    canvas.translate(cellX + 4f, cy)
                                    sl.draw(canvas)
                                    canvas.restore()
                                    cy += sl.height + 2f
                                }
                                colCursor += cell.gridSpan
                            }

                            rowY += rowH
                        }
                    }
                }
            }

            pdfDocument.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }
}
