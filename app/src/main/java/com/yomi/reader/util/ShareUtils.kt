package com.yomi.reader.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import java.io.File

object ShareUtils {

    internal fun resolveShareableUri(context: Context, rawUri: Uri): Uri {
        if (rawUri.scheme.equals("file", ignoreCase = true)) {
            val path = rawUri.path?.takeIf { it.isNotBlank() } ?: return rawUri
            val file = File(path)
            if (file.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        }
        return rawUri
    }

    fun shareDocument(context: Context, document: DocumentItem) {
        try {
            val uri = resolveShareableUri(context, Uri.parse(document.uriString))
            val mimeType = when (document.type) {
                DocumentType.PDF -> "application/pdf"
                DocumentType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                DocumentType.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                DocumentType.POWERPOINT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                else -> "*/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.title)
                putExtra(Intent.EXTRA_TEXT, "Shared from Yomi: ${document.title}")
                clipData = ClipData.newRawUri(document.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share \"${document.title}\" via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocuments(context: Context, documents: List<DocumentItem>) {
        if (documents.isEmpty()) return
        if (documents.size == 1) {
            shareDocument(context, documents.first())
            return
        }
        try {
            val uris = ArrayList<Uri>()
            var multiClip: ClipData? = null
            documents.forEach { doc ->
                val resolved = resolveShareableUri(context, Uri.parse(doc.uriString))
                uris.add(resolved)
                if (multiClip == null) {
                    multiClip = ClipData.newRawUri(doc.title, resolved)
                } else {
                    multiClip?.addItem(ClipData.Item(resolved))
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "Sharing ${documents.size} documents from Yomi")
                clipData = multiClip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${documents.size} documents via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share files: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextContent(context: Context, title: String, content: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share text from \"$title\" via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareBitmap(context: Context, bitmap: android.graphics.Bitmap, title: String) {
        try {
            val cachePath = java.io.File(context.cacheDir, "page_snapshots")
            cachePath.mkdirs()
            val file = java.io.File(cachePath, "${title.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.png")
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Page snapshot from Yomi: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Page Snapshot via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share page image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
