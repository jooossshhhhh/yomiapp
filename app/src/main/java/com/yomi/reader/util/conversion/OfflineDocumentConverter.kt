package com.yomi.reader.util.conversion

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.yomi.reader.util.PdfToolsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.min

object OfflineDocumentConverter {

    private const val A4_PORTRAIT_WIDTH = 595
    private const val A4_PORTRAIT_HEIGHT = 842
    private const val A4_LANDSCAPE_WIDTH = 842
    private const val A4_LANDSCAPE_HEIGHT = 595

    private val LEGACY_FULL_SHEET_EXTENSIONS = setOf("xls", "ods", "csv")

    private val OFFICE_EXTENSIONS = setOf(
        "doc", "docx",
        "xls", "xlsx",
        "ppt", "pptx",
        "rtf", "odt", "ods", "odp", "csv"
    )

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
            when {
                extension in OFFICE_EXTENSIONS -> convertWithLibreOffice(
                    context = context,
                    fileUri = fileUri,
                    extension = extension,
                    outputFile = targetFile,
                    onProgress = onProgress
                )
                extension in setOf("jpg", "jpeg", "png", "webp") -> convertImage(context, fileUri, targetFile, onProgress)
                else -> convertText(context, fileUri, targetFile, onProgress)
            }
            onProgress(100)
            Result.success(targetFile)
        } catch (e: Exception) {
            targetFile.delete()
            Result.failure(e)
        }
    }

    private suspend fun convertWithLibreOffice(
        context: Context,
        fileUri: Uri,
        extension: String,
        outputFile: File,
        onProgress: (Int) -> Unit
    ) {
        val workDir = File(context.cacheDir, "lok_work").apply { mkdirs() }
        val jobId = UUID.randomUUID().toString()
        val tempInput = File(workDir, "$jobId.$extension")
        val normalizedInput = File(workDir, "${jobId}_norm.$extension")
        val tempOutput = File(workDir, "${jobId}_out.pdf")

        try {
            onProgress(15)
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: throw IllegalArgumentException("Cannot open document stream at $fileUri")
            inputStream.use { input ->
                FileOutputStream(tempInput).use { output ->
                    input.copyTo(output)
                }
            }

            onProgress(35)
            val libreOfficeInput = when {
                extension == "docx" && DocxPreProcessor.normalizeForPdfExport(tempInput, normalizedInput) -> normalizedInput
                extension == "xlsx" && XlsxPreProcessor.normalizeForPdfExport(tempInput, normalizedInput) -> normalizedInput
                else -> tempInput
            }
            val filterOptions = if (extension in LEGACY_FULL_SHEET_EXTENSIONS) {
                ",FullSheetPreview=trueFULLSHEETPREVEND"
            } else {
                ""
            }
            val conversionResult = LibreOfficePdfBridge.convertToPdf(
                context = context,
                inputFile = libreOfficeInput,
                outputFile = tempOutput,
                filterOptions = filterOptions
            )
            val generatedPdf = conversionResult.getOrElse { err ->
                throw IllegalStateException(err.message ?: "LibreOfficeKit failed to convert .$extension", err)
            }

            onProgress(90)
            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!generatedPdf.renameTo(outputFile)) {
                generatedPdf.copyTo(outputFile, overwrite = true)
            }
        } finally {
            tempInput.delete()
            normalizedInput.delete()
            tempOutput.delete()
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
        val pageWidth = A4_PORTRAIT_WIDTH
        val pageHeight = A4_PORTRAIT_HEIGHT
        val margin = 48f
        val contentWidth = pageWidth - (margin * 2)

        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.MONOSPACE
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
    // Image -> PDF Conversion
    // ==========================================
    private fun convertImage(
        context: Context,
        fileUri: Uri,
        outputFile: File,
        onProgress: (Int) -> Unit = {}
    ) {
        onProgress(25)
        val bitmap = PdfToolsHelper.decodeNormalizedBitmap(context, fileUri)
            ?: throw IllegalArgumentException("Failed to decode image bitmap at $fileUri")
        onProgress(60)

        val pdfDocument = PdfDocument()
        try {
            val isLandscape = bitmap.width > bitmap.height
            val pageWidth = if (isLandscape) A4_LANDSCAPE_WIDTH else A4_PORTRAIT_WIDTH
            val pageHeight = if (isLandscape) A4_LANDSCAPE_HEIGHT else A4_PORTRAIT_HEIGHT
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
