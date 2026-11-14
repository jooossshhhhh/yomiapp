package com.yomi.reader.util

/**
 * Pure-Kotlin safety and naming rules for the append-only, read-safe Google Drive folder integration:
 * - Only supported document extensions can be browsed/downloaded into Yomi.
 * - Uploading a PDF or creating a subfolder never overwrites or modifies existing Drive items.
 */
object DriveFolderSafetyHelper {

    private val supportedExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")

    fun isSupportedDriveExtension(extension: String): Boolean {
        return extension.trim().lowercase() in supportedExtensions
    }

    fun isSupportedDriveFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "")
        return ext.isNotEmpty() && isSupportedDriveExtension(ext)
    }

    fun targetLocalPdfTitle(driveFileName: String): String {
        val trimmed = driveFileName.trim().ifBlank { "Document.pdf" }
        val ext = trimmed.substringAfterLast('.', "").lowercase()
        return if (ext == "pdf") {
            trimmed
        } else {
            val base = trimmed.substringBeforeLast('.', trimmed).ifBlank { "Document" }
            "$base.pdf"
        }
    }

    /**
     * Produces a PDF filename that does not collide (case-insensitively) with any item in
     * [existingNames], guaranteeing uploads never overwrite or modify existing Drive files.
     */
    fun resolveNonOverwritingPdfName(
        desiredFileName: String,
        existingNames: Collection<String>
    ): String {
        val existingLower = existingNames.map { it.trim().lowercase() }.toSet()
        val raw = desiredFileName.trim().ifBlank { "Document.pdf" }
        val base = raw.substringBeforeLast('.', raw)
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .ifBlank { "Document" }

        val candidate = "$base.pdf"
        if (candidate.lowercase() !in existingLower) {
            return candidate
        }

        var counter = 1
        while (true) {
            val next = "$base ($counter).pdf"
            if (next.lowercase() !in existingLower) {
                return next
            }
            counter++
        }
    }

    /**
     * Validates a new Drive subfolder name without allowing path traversal or overwriting
     * an existing folder/file name.
     */
    fun validateNewSubfolderName(
        rawName: String,
        existingNames: Collection<String>
    ): Result<String> {
        val clean = rawName.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Folder name cannot be empty"))
        }
        if (clean == "." || clean == ".." || clean.contains('/') || clean.contains('\\')) {
            return Result.failure(IllegalArgumentException("Folder name contains invalid characters"))
        }
        val exists = existingNames.any { it.trim().equals(clean, ignoreCase = true) }
        if (exists) {
            return Result.failure(
                IllegalStateException("\"$clean\" already exists. Existing Drive folders are protected.")
            )
        }
        return Result.success(clean)
    }
}

