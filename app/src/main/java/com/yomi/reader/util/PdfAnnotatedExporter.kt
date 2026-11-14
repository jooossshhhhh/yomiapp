package com.yomi.reader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import com.yomi.reader.parser.pdf.PdfPageRenderer
import com.yomi.reader.ui.viewer.annotation.PdfPageAnnotation
import com.yomi.reader.ui.viewer.annotation.StickyNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Flattens page renders with highlights, pen strokes, and pinned sticky notes into a real PDF
 * and saves it to the device's public `Documents/Yomi/` folder via [PublicDocumentsExporter].
 */
object PdfAnnotatedExporter {

    private const val MAX_RENDER_EDGE_PX = 2000

    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceUri: Uri,
        documentTitle: String,
        marksByPage: Map<Int, List<PdfPageAnnotation>>,
        stickyNotes: List<StickyNote>,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempPdf = File(context.cacheDir, "annotated_export_${System.currentTimeMillis()}.pdf")
        val pdfDocument = PdfDocument()
        try {
            val renderer = PdfPageRenderer(context)
            renderer.openSession(sourceUri).use { session ->
                val pageCount = session.pageCount
                if (pageCount <= 0) {
                    return@withContext Result.failure(IllegalStateException("PDF has no pages."))
                }

                for (pageIndex in 0 until pageCount) {
                    val srcPage = session.renderer.openPage(pageIndex)
                    try {
                        val pageW = srcPage.width.coerceAtLeast(1)
                        val pageH = srcPage.height.coerceAtLeast(1)
                        val maxEdge = max(pageW, pageH)
                        val scale = min(2f, MAX_RENDER_EDGE_PX.toFloat() / maxEdge.toFloat()).coerceAtLeast(1f)
                        val bmpW = (pageW * scale).roundToInt().coerceAtLeast(1)
                        val bmpH = (pageH * scale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                            val outPage = pdfDocument.startPage(pageInfo)
                            val canvas = outPage.canvas
                            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), null)

                            val pageMarks = marksByPage[pageIndex].orEmpty()
                            val pageNotes = stickyNotes.filter { it.pageIndex == pageIndex }

                            drawAnnotationsOnCanvas(
                                canvas = canvas,
                                pageWidth = pageW.toFloat(),
                                pageHeight = pageH.toFloat(),
                                marks = pageMarks,
                                notes = pageNotes
                            )

                            pdfDocument.finishPage(outPage)
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    } finally {
                        srcPage.close()
                    }

                    val pct = (((pageIndex + 1).toFloat() / pageCount.toFloat()) * 90f).roundToInt().coerceIn(5, 90)
                    onProgress(pct)
                }
            }

            FileOutputStream(tempPdf).use { out ->
                pdfDocument.writeTo(out)
            }
            onProgress(95)

            val cleanBase = documentTitle
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .ifBlank { "Document" }
            val exportName = "${cleanBase}_annotated.pdf"
            val result = PublicDocumentsExporter.exportFileToDocumentsYomi(
                context = context,
                sourceFile = tempPdf,
                displayName = exportName,
                mimeType = "application/pdf"
            )
            onProgress(100)
            result
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { pdfDocument.close() }
            if (tempPdf.exists()) tempPdf.delete()
        }
    }

    private fun drawAnnotationsOnCanvas(
        canvas: android.graphics.Canvas,
        pageWidth: Float,
        pageHeight: Float,
        marks: List<PdfPageAnnotation>,
        notes: List<StickyNote>
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val strokeScale = (pageWidth / 400f).coerceIn(0.65f, 2.5f)

        // 1. Highlights & Pen Strokes
        for (mark in marks) {
            when (mark) {
                is PdfPageAnnotation.Highlight -> {
                    val baseArgb = mark.highlight.color.toArgb()
                    fillPaint.color = withAlpha(baseArgb, 96)
                    for (rect in mark.highlight.rects) {
                        val r = RectF(
                            rect.left * pageWidth,
                            rect.top * pageHeight,
                            rect.right * pageWidth,
                            rect.bottom * pageHeight
                        )
                        canvas.drawRoundRect(r, 2f, 2f, fillPaint)
                    }
                }
                is PdfPageAnnotation.Stroke -> {
                    val pts = mark.stroke.points
                    if (pts.isEmpty()) continue
                    val baseArgb = mark.stroke.color.toArgb()
                    strokePaint.color = if (mark.stroke.isHighlighter) withAlpha(baseArgb, 102) else baseArgb
                    strokePaint.strokeWidth = (mark.stroke.strokeWidth * strokeScale).coerceAtLeast(1.5f)

                    if (pts.size == 1) {
                        fillPaint.color = strokePaint.color
                        canvas.drawCircle(
                            pts[0].xRatio * pageWidth,
                            pts[0].yRatio * pageHeight,
                            strokePaint.strokeWidth / 2f,
                            fillPaint
                        )
                    } else {
                        val path = Path().apply {
                            moveTo(pts[0].xRatio * pageWidth, pts[0].yRatio * pageHeight)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val curr = pts[i]
                                val midX = ((prev.xRatio + curr.xRatio) * 0.5f) * pageWidth
                                val midY = ((prev.yRatio + curr.yRatio) * 0.5f) * pageHeight
                                quadTo(prev.xRatio * pageWidth, prev.yRatio * pageHeight, midX, midY)
                            }
                            lineTo(pts.last().xRatio * pageWidth, pts.last().yRatio * pageHeight)
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }
            }
        }

        // 2. Sticky Note Anchor Highlights & Numbered Markers
        if (notes.isEmpty()) return
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = (8.5f * strokeScale).coerceIn(7f, 14f)
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#475569")
        }
        val radius = (7f * strokeScale).coerceIn(5.5f, 12f)

        notes.forEachIndexed { idx, note ->
            val noteColor = note.colorValue.toInt()
            if (note.anchorRects.isNotEmpty()) {
                fillPaint.color = withAlpha(noteColor, 110)
                for (rect in note.anchorRects) {
                    canvas.drawRoundRect(
                        RectF(
                            rect.left * pageWidth,
                            rect.top * pageHeight,
                            rect.right * pageWidth,
                            rect.bottom * pageHeight
                        ),
                        2f,
                        2f,
                        fillPaint
                    )
                }
                val first = note.anchorRects.first()
                val bx = (first.left * pageWidth - radius).coerceIn(radius + 4f, pageWidth - radius - 4f)
                val by = (first.top * pageHeight + radius).coerceIn(radius + 4f, pageHeight - radius - 4f)
                fillPaint.color = noteColor
                canvas.drawCircle(bx, by, radius, fillPaint)
                canvas.drawCircle(bx, by, radius, badgeBorderPaint)
                canvas.drawText("${idx + 1}", bx, by + badgeTextPaint.textSize * 0.35f, badgeTextPaint)
            }
        }

        // Compact footnote box at bottom of page when sticky notes exist
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = (7.5f * strokeScale).coerceIn(6.5f, 11f)
        }
        val margin = 14f * strokeScale
        val lineH = labelPaint.textSize * 1.4f
        val boxHeight = lineH * notes.size + 10f
        val top = (pageHeight - boxHeight - margin).coerceAtLeast(margin)
        fillPaint.color = withAlpha(Color.parseColor("#FEF9C3"), 230)
        val footnoteRect = RectF(margin, top, pageWidth - margin, (top + boxHeight).coerceAtMost(pageHeight - 4f))
        canvas.drawRoundRect(footnoteRect, 6f, 6f, fillPaint)
        canvas.drawRoundRect(footnoteRect, 6f, 6f, badgeBorderPaint)

        notes.forEachIndexed { idx, note ->
            val summary = "${idx + 1}. ${note.text.replace('\n', ' ').take(95)}"
            canvas.drawText(summary, margin + 8f, top + 8f + (idx + 1) * labelPaint.textSize + idx * (lineH - labelPaint.textSize), labelPaint)
        }
    }

    private fun withAlpha(argb: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(argb),
            Color.green(argb),
            Color.blue(argb)
        )
    }
}

