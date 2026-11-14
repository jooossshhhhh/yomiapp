package com.yomi.reader.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.yomi.reader.parser.pdf.PdfPageRenderer
import com.yomi.reader.parser.pdf.PdfPasswordRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Local offline PDF creation & page-manipulation utilities used by Yomi Quick Suite.
 */
object PdfToolsHelper {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val MAX_IMAGE_EDGE_PX = 2000
    private const val MAX_SPLIT_RENDER_EDGE_PX = 2200

    /**
     * Converts one or more image URIs into a multi-page A4 PDF stored in Yomi's Converted_PDFs folder.
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputBaseName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Select at least one image."))
        }

        val outputFile = createUniquePdfFile(
            context = context,
            requestedBaseName = outputBaseName.ifBlank { "Images_${System.currentTimeMillis()}" }
        )

        val pdfDocument = PdfDocument()
        try {
            var writtenPages = 0
            imageUris.forEachIndexed { index, uri ->
                val bitmap = decodeNormalizedBitmap(context, uri)
                    ?: throw IllegalStateException("Could not decode selected image #${index + 1}.")

                try {
                    val isLandscape = bitmap.width > bitmap.height
                    val pageWidth = if (isLandscape) A4_HEIGHT else A4_WIDTH
                    val pageHeight = if (isLandscape) A4_WIDTH else A4_HEIGHT
                    val margin = 24f

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, writtenPages + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val availW = pageWidth - margin * 2
                    val availH = pageHeight - margin * 2
                    val scale = min(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val drawX = margin + (availW - drawW) / 2f
                    val drawY = margin + (availH - drawH) / 2f

                    canvas.drawBitmap(bitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), null)
                    pdfDocument.finishPage(page)
                    writtenPages++
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

                val pct = (((index + 1).toFloat() / imageUris.size.toFloat()) * 90f).roundToInt().coerceIn(5, 90)
                onProgress(pct)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            onProgress(100)
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } finally {
            runCatching { pdfDocument.close() }
        }
    }

    /**
     * Reads total page count of a PDF without holding open descriptors.
     */
    suspend fun getPdfPageCount(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val renderer = PdfPageRenderer(context)
            renderer.openSession(uri).use { session ->
                Result.success(session.pageCount)
            }
        } catch (sec: PdfPasswordRequiredException) {
            Result.failure(sec)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses a 1-based page specification like "1-3, 5, 8-10" into validated unique page numbers.
     */
    fun parsePageRanges(input: String, maxPages: Int): Result<List<Int>> {
        if (maxPages <= 0) {
            return Result.failure(IllegalArgumentException("The selected PDF has no pages."))
        }
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter pages or a range (e.g. 1-3, 5)."))
        }

        val orderedUnique = LinkedHashSet<Int>()
        val tokens = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter valid page numbers."))
        }

        for (token in tokens) {
            if (token.contains('-')) {
                val bounds = token.split('-').map { it.trim() }
                if (bounds.size != 2 || bounds[0].isEmpty() || bounds[1].isEmpty()) {
                    return Result.failure(IllegalArgumentException("Invalid range \"$token\". Use format like 1-4."))
                }
                val start = bounds[0].toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid page number \"${bounds[0]}\"."))
                val end = bounds[1].toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid page number \"${bounds[1]}\"."))
                if (start < 1 || end < 1 || start > end) {
                    return Result.failure(IllegalArgumentException("Invalid range \"$token\". Start must be 1..$end."))
                }
                if (end > maxPages) {
                    return Result.failure(IllegalArgumentException("Page $end exceeds total pages ($maxPages)."))
                }
                for (p in start..end) {
                    orderedUnique.add(p)
                }
            } else {
                val pageNum = token.toIntOrNull()
                    ?: return Result.failure(IllegalArgumentException("Invalid page number \"$token\"."))
                if (pageNum < 1 || pageNum > maxPages) {
                    return Result.failure(IllegalArgumentException("Page $pageNum must be between 1 and $maxPages."))
                }
                orderedUnique.add(pageNum)
            }
        }

        return if (orderedUnique.isEmpty()) {
            Result.failure(IllegalArgumentException("No pages selected."))
        } else {
            Result.success(orderedUnique.toList())
        }
    }

    /**
     * Extracts the requested 1-based page numbers from [sourceUri] into a new PDF file.
     */
    suspend fun splitPdfPages(
        context: Context,
        sourceUri: Uri,
        pageNumbersOneBased: List<Int>,
        outputBaseName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pageNumbersOneBased.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages selected to extract."))
        }

        val outputFile = createUniquePdfFile(
            context = context,
            requestedBaseName = outputBaseName.ifBlank { "Split_${System.currentTimeMillis()}" }
        )

        val pdfDocument = PdfDocument()
        try {
            val pageRenderer = PdfPageRenderer(context)
            pageRenderer.openSession(sourceUri).use { session ->
                if (session.pageCount <= 0) {
                    throw IllegalStateException("Source PDF has 0 pages.")
                }

                pageNumbersOneBased.forEachIndexed { outIdx, oneBasedPage ->
                    val pageIndex = oneBasedPage - 1
                    if (pageIndex !in 0 until session.pageCount) {
                        throw IllegalArgumentException("Page $oneBasedPage is out of bounds (1..${session.pageCount}).")
                    }

                    val srcPage = session.renderer.openPage(pageIndex)
                    try {
                        val pageW = srcPage.width.coerceAtLeast(1)
                        val pageH = srcPage.height.coerceAtLeast(1)

                        // Render at crisp 2x scale capped to keep memory bounded
                        val maxEdge = max(pageW, pageH)
                        val scale = min(2f, MAX_SPLIT_RENDER_EDGE_PX.toFloat() / maxEdge.toFloat()).coerceAtLeast(1f)
                        val bmpW = (pageW * scale).roundToInt().coerceAtLeast(1)
                        val bmpH = (pageH * scale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, outIdx + 1).create()
                            val outPage = pdfDocument.startPage(pageInfo)
                            outPage.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), null)
                            pdfDocument.finishPage(outPage)
                        } finally {
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    } finally {
                        srcPage.close()
                    }

                    val pct = (((outIdx + 1).toFloat() / pageNumbersOneBased.size.toFloat()) * 90f).roundToInt().coerceIn(5, 90)
                    onProgress(pct)
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            onProgress(100)
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } finally {
            runCatching { pdfDocument.close() }
        }
    }

    private fun createUniquePdfFile(context: Context, requestedBaseName: String): File {
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Converted_PDFs").apply {
            if (!exists()) mkdirs()
        }
        val cleanBase = requestedBaseName
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .trim()
            .ifBlank { "Yomi_${System.currentTimeMillis()}" }

        var candidate = File(outputDir, "$cleanBase.pdf")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(outputDir, "${cleanBase}_$suffix.pdf")
            suffix++
        }
        return candidate
    }

    internal fun decodeNormalizedBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedViaImageDecoder = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > MAX_IMAGE_EDGE_PX && w > 0 && h > 0) {
                        val ratio = MAX_IMAGE_EDGE_PX.toFloat() / longest.toFloat()
                        decoder.setTargetSize(
                            (w * ratio).roundToInt().coerceAtLeast(1),
                            (h * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            }.getOrNull()
            if (decodedViaImageDecoder != null) {
                return decodedViaImageDecoder
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        val longest = max(rawW, rawH)
        while ((longest / sampleSize) > MAX_IMAGE_EDGE_PX) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val rotationDegrees = readExifRotation(context, uri)
        if (rotationDegrees == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded && !decoded.isRecycled) {
            decoded.recycle()
        }
        return rotated
    }

    private fun readExifRotation(context: Context, uri: Uri): Float {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
    }

    private val supportedImportMimeTypes = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.ms-powerpoint"
    )

    /**
     * Launches Google Drive's built-in file picker (ACTION_GET_CONTENT) so the user can pick files
     * directly back into Yomi without leaving the app or manually sharing from Drive.
     */
    fun buildGoogleDriveFilePickerIntent(): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, supportedImportMimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            setPackage("com.google.android.apps.docs")
        }

    /**
     * System document manager fallback picker when Google Drive app is unavailable.
     */
    fun buildDeviceDocumentPickerIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, supportedImportMimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

    fun extractUrisFromActivityResult(data: Intent?): List<Uri> {
        if (data == null) return emptyList()
        val uris = LinkedHashSet<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        data.data?.let { uris.add(it) }
        return uris.toList()
    }
}

