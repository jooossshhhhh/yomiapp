package com.yomi.reader.parser.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfPasswordRequiredException(message: String = "This document is password protected and encrypted.") : SecurityException(message)

class PdfPageRenderer(private val context: Context) {

    data class PdfSession(
        val renderer: PdfRenderer,
        val pageCount: Int,
        val pfd: ParcelFileDescriptor,
        val tempFile: File?
    ) : AutoCloseable {
        override fun close() {
            try {
                renderer.close()
                pfd.close()
                tempFile?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun openSession(uri: Uri): PdfSession = withContext(Dispatchers.IO) {
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalArgumentException("Could not open file descriptor for $uri")
        } catch (e: Exception) {
            // Fallback for URIs that require copying to cache
            val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw e
            val fallbackPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val fallbackRenderer = try {
                PdfRenderer(fallbackPfd)
            } catch (sec: SecurityException) {
                fallbackPfd.close()
                throw PdfPasswordRequiredException()
            }
            return@withContext PdfSession(
                renderer = fallbackRenderer,
                pageCount = fallbackRenderer.pageCount,
                pfd = fallbackPfd,
                tempFile = tempFile
            )
        }

        val renderer = try {
            PdfRenderer(pfd)
        } catch (sec: SecurityException) {
            pfd.close()
            throw PdfPasswordRequiredException()
        }
        PdfSession(
            renderer = renderer,
            pageCount = renderer.pageCount,
            pfd = pfd,
            tempFile = null
        )
    }

    suspend fun renderPage(session: PdfSession, pageIndex: Int, targetWidth: Int = 1080): Bitmap = withContext(Dispatchers.IO) {
        synchronized(session.renderer) {
            val page = session.renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // Fill with pure white before rendering
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        }
    }
}
