package com.yomi.reader.domain.model

data class DriveFolderItem(
    val name: String,
    val path: String,          // Relative path from root, e.g. "Word_Files(Present)"
    val parentPath: String = "", // Parent path, e.g. "" for root subfolders
    val fileCount: Int = 0,
    val uriString: String? = null
)

data class DriveFileItem(
    val name: String,
    val uriString: String,
    val parentPath: String = "",
    val type: DocumentType,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L
)

data class SavedDriveLink(
    val folderId: String,
    val url: String,
    val name: String,
    val lastOpenedAt: Long = 0L
)
