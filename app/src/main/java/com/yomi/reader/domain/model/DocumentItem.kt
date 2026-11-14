package com.yomi.reader.domain.model

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DocumentItem(
    val id: Long = 0,
    val uriString: String,
    val title: String,
    val type: DocumentType,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Boolean = false,
    val lastOpened: Long? = null,
    val parentPath: String = "",
    val formattedSize: String = formatSize(sizeBytes),
    val formattedDate: String = formatDate(lastModified),
    val isFromGoogleDrive: Boolean = checkIsDrive(uriString)
) {
    companion object {
        fun formatSize(sizeBytes: Long): String {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val format = DecimalFormat("#,##0.#")
            val group = digitGroups.coerceIn(0, units.size - 1)
            return "${format.format(sizeBytes / Math.pow(1024.0, group.toDouble()))} ${units[group]}"
        }

        fun formatDate(lastModified: Long): String {
            if (lastModified <= 0) return "Unknown date"
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

        fun checkIsDrive(uriString: String): Boolean {
            return uriString.contains("com.google.android.apps.docs") ||
                    uriString.contains("google.drive") ||
                    uriString.contains("drive.google.com")
        }
    }
}
