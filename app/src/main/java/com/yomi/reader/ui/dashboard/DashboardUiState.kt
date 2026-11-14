package com.yomi.reader.ui.dashboard

import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentSortOrder
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import com.yomi.reader.ui.dashboard.components.CustomFolderStyle
import java.text.DecimalFormat

data class DashboardUiState(
    val documents: List<DocumentItem> = emptyList(),
    val allDocuments: List<DocumentItem> = emptyList(),
    val recentDocuments: List<DocumentItem> = emptyList(),
    val selectedCategory: DocumentCategory = DocumentCategory.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val isDriveSyncing: Boolean = false,
    val isDriveLinkMode: Boolean = false,
    val currentFolderPath: String = "", // Current folder relative path, empty = root
    val currentFolderDisplayName: String = "", // e.g. "PRDS_Capstone_Project" or "Word_Files(Present)"
    val currentSubfolders: List<DriveFolderItem> = emptyList(), // Subfolders inside currentFolderPath
    val driveFiles: List<DriveFileItem> = emptyList(), // Read-safe Drive files inside currentFolderPath
    val driveLocalCopies: Map<String, String> = emptyMap(), // driveUri -> saved local Yomi URI
    val sortOrder: DocumentSortOrder = DocumentSortOrder.DATE_DESC,
    val isGridView: Boolean = false,
    val isFavoritesOnly: Boolean = false,
    val customFolders: Set<String> = emptySet(),
    val customFolderStyles: Map<String, CustomFolderStyle> = emptyMap(),
    val selectedCustomFolder: String? = null,
    val totalDocumentCount: Int = 0,
    val totalFormattedSize: String = "0 B",
    val pdfCount: Int = 0,
    val wordCount: Int = 0,
    val excelCount: Int = 0,
    val pptCount: Int = 0,
    val starredCount: Int = 0
) {
    val isEmpty: Boolean
        get() = if (selectedCategory == DocumentCategory.GOOGLE_DRIVE) {
            !isLoading && driveFiles.isEmpty() && currentSubfolders.isEmpty()
        } else {
            !isLoading && documents.isEmpty() && currentSubfolders.isEmpty()
        }

    companion object {
        fun formatTotalBytes(totalBytes: Long): String {
            if (totalBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(totalBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val format = DecimalFormat("#,##0.#")
            val group = digitGroups.coerceIn(0, units.size - 1)
            return "${format.format(totalBytes / Math.pow(1024.0, group.toDouble()))} ${units[group]}"
        }
    }
}
