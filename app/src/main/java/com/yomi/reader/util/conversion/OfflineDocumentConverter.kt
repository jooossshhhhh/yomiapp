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

object OfflineDocumentConverter {

    private const val A4_PORTRAIT_WIDTH = 595
    private const val A4_PORTRAIT_HEIGHT = 842
    private const val A4_LANDSCAPE_WIDTH = 842
    private const val A4_LANDSCAPE_HEIGHT = 595

    suspend fun convertToPdf(
        context: Context,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit = {},
        outputBaseName: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val baseName = outputBaseName?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Converted_PDFs").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(outputDir, "${baseName}_converted.pdf")

        try {
            onProgress(5)
            when (extension) {
                "docx", "doc" -> OfflineDocxToPdfConverter.convert(context, fileUri, targetFile, onProgress)
                "xlsx", "xls" -> OfflineXlsxToPdfConverter.convert(context, fileUri, targetFile, onProgress)
                "pptx", "ppt" -> OfflinePptxToPdfConverter.convert(context, fileUri, targetFile, onProgress)
                "txt" -> convertText(context, fileUri, targetFile, onProgress)
                "jpg", "jpeg", "png", "webp" -> convertImage(context, fileUri, targetFile, onProgress)
                else -> convertText(context, fileUri, targetFile, onProgress)
            }
            onProgress(100)
            Result.success(targetFile)
        } catch (e: Exception) {
            targetFile.delete()
            Result.failure(e)
        }
    }

    private fun convertText(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        val inputStream: InputStream = context.contentResolver.openInputStream(fileUri)
            ?: throw IllegalArgumentException("Cannot open text file at $fileUri")

        val fullText = inputStream.bufferedReader().use { it.readText() }
        onProgress(30)
        val pdfDocument = PdfDocument()
        val pageWidth = OfflinePdfRenderSupport.A4_PORTRAIT_WIDTH
        val pageHeight = OfflinePdfRenderSupport.A4_PORTRAIT_HEIGHT
        val margin = 48f
        val contentWidth = pageWidth - (margin * 2)

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = OfflinePdfRenderSupport.resolveOfficeTypeface(context.assets, "Courier New", Typeface.NORMAL).typeface
            isAntiAlias = true
        }

        val layout = StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, contentWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()

        val totalLines = layout.lineCount
        if (totalLines == 0) {
            val emptyPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
            pdfDocument.finishPage(emptyPage)
        } else {
            val lineHeight = (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(12)
            val linesPerPage = ((pageHeight - margin * 2) / lineHeight).toInt().coerceAtLeast(1)
            val totalPages = (totalLines + linesPerPage - 1) / linesPerPage

            for (p in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                var currentY = margin
                val startLine = p * linesPerPage
                val endLine = min(startLine + linesPerPage, totalLines)

                for (lineIdx in startLine until endLine) {
                    val startOffset = layout.getLineStart(lineIdx)
                    val endOffset = layout.getLineEnd(lineIdx)
                    val lineStr = fullText.substring(startOffset, endOffset).trimEnd('\n', '\r')
                    canvas.drawText(lineStr, margin, currentY + 10f, textPaint)
                    currentY += lineHeight
                }

                pdfDocument.finishPage(page)
            }
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

    // ==========================================
    // 5. Image -> PDF Conversion
    // ==========================================
    private fun convertImage(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        onProgress(25)
        val bitmap = com.yomi.reader.util.PdfToolsHelper.decodeNormalizedBitmap(context, fileUri)
            ?: throw IllegalArgumentException("Failed to decode image bitmap at $fileUri")
        onProgress(60)

        val pdfDocument = PdfDocument()
        try {
            val isLandscape = bitmap.width > bitmap.height
            val pageWidth = if (isLandscape) OfflinePdfRenderSupport.A4_LANDSCAPE_WIDTH else OfflinePdfRenderSupport.A4_PORTRAIT_WIDTH
            val pageHeight = if (isLandscape) OfflinePdfRenderSupport.A4_LANDSCAPE_HEIGHT else OfflinePdfRenderSupport.A4_PORTRAIT_HEIGHT
            val margin = 32f

            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val availW = pageWidth - (margin * 2)
            val availH = pageHeight - (margin * 2)
            val scale = min(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())

            val drawW = bitmap.width * scale
            val drawH = bitmap.height * scale
            val drawX = margin + (availW - drawW) / 2f
            val drawY = margin + (availH - drawH) / 2f

            canvas.drawBitmap(bitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), null)
            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            runCatching { pdfDocument.close() }
        }
    }
}
