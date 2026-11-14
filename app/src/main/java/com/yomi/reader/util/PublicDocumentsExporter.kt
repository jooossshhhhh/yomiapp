package com.yomi.reader.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.yomi.reader.data.local.DownloadDestinationType
import com.yomi.reader.data.local.DownloadedFileRecord
import com.yomi.reader.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Exports generated, converted, or annotated files into the public `Documents/Yomi` directory
 * via Scoped Storage MediaStore (zero extra storage permissions required on Android 10+).
 * Files saved here remain accessible in the device's Files app and survive app uninstall.
 */
object PublicDocumentsExporter {

    private const val PUBLIC_SUBFOLDER = "Yomi"

    data class DeviceDownloadResult(
        val fileName: String,
        val destinationLabel: String,
        val targetUriOrPath: String,
        val sizeBytes: Long
    )

    suspend fun downloadFileToDevice(
        context: Context,
        sourceFile: File,
        preferencesManager: PreferencesManager,
        displayName: String = sourceFile.name,
        sourceUriString: String = Uri.fromFile(sourceFile).toString()
    ): Result<DeviceDownloadResult> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Source file does not exist or is empty."))
        }
        downloadStreamToDestination(
            context = context,
            openInputStream = { FileInputStream(sourceFile) },
            fileSize = sourceFile.length(),
            displayName = displayName,
            sourceUriString = sourceUriString,
            preferencesManager = preferencesManager
        )
    }

    suspend fun downloadDocumentUriToDevice(
        context: Context,
        sourceUri: Uri,
        displayName: String,
        preferencesManager: PreferencesManager,
        sizeHintBytes: Long = 0L
    ): Result<DeviceDownloadResult> = withContext(Dispatchers.IO) {
        if (sourceUri.scheme == "file") {
            val file = File(sourceUri.path ?: "")
            return@withContext downloadFileToDevice(
                context = context,
                sourceFile = file,
                preferencesManager = preferencesManager,
                displayName = displayName,
                sourceUriString = sourceUri.toString()
            )
        }
        downloadStreamToDestination(
            context = context,
            openInputStream = { context.contentResolver.openInputStream(sourceUri) },
            fileSize = sizeHintBytes,
            displayName = displayName,
            sourceUriString = sourceUri.toString(),
            preferencesManager = preferencesManager
        )
    }

    private fun downloadStreamToDestination(
        context: Context,
        openInputStream: () -> InputStream?,
        fileSize: Long,
        displayName: String,
        sourceUriString: String,
        preferencesManager: PreferencesManager
    ): Result<DeviceDownloadResult> {
        val sanitizedName = sanitizeFileName(displayName)
        val mimeType = inferMimeType(sanitizedName)
        val destType = preferencesManager.downloadDestinationType.value
        val customTreeUri = preferencesManager.downloadCustomTreeUri.value
        val customLabel = preferencesManager.downloadCustomFolderName.value

        if (destType == DownloadDestinationType.CUSTOM_FOLDER && !customTreeUri.isNullOrBlank()) {
            val customAttempt = runCatching {
                val treeUri = Uri.parse(customTreeUri)
                val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw IllegalStateException("Selected download folder is unavailable")
                if (!folderDoc.canWrite()) {
                    throw IllegalStateException("Write permission expired for selected download folder")
                }
                val existingNames = folderDoc.listFiles().mapNotNull { it.name }.toSet()
                val uniqueName = uniqueDisplayName(sanitizedName, existingNames)
                val created = folderDoc.createFile(mimeType, uniqueName)
                    ?: throw IllegalStateException("Could not create \"$uniqueName\" in selected folder")

                var copiedBytes = 0L
                try {
                    val out = context.contentResolver.openOutputStream(created.uri)
                        ?: throw IllegalStateException("Could not open destination output stream")
                    out.use { output ->
                        val input = openInputStream()
                            ?: throw IllegalStateException("Could not read source document")
                        input.use { stream ->
                            copiedBytes = stream.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    runCatching { created.delete() }
                    throw e
                }

                val label = customLabel?.takeIf { it.isNotBlank() } ?: "Custom Folder"
                val finalSize = if (copiedBytes > 0L) copiedBytes else fileSize
                preferencesManager.recordDownloadedFile(
                    DownloadedFileRecord(
                        id = UUID.randomUUID().toString(),
                        title = uniqueName,
                        destinationLabel = label,
                        targetUriOrPath = created.uri.toString(),
                        sourceUriString = sourceUriString,
                        sizeBytes = finalSize,
                        downloadedAt = System.currentTimeMillis()
                    )
                )
                DeviceDownloadResult(
                    fileName = uniqueName,
                    destinationLabel = label,
                    targetUriOrPath = created.uri.toString(),
                    sizeBytes = finalSize
                )
            }
            if (customAttempt.isSuccess) return customAttempt
            return Result.failure(customAttempt.exceptionOrNull() ?: IllegalStateException("Custom folder download failed"))
        }

        val publicBaseDir = if (destType == DownloadDestinationType.DOWNLOADS_YOMI) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_DOCUMENTS
        }
        val destLabel = "$publicBaseDir/$PUBLIC_SUBFOLDER"

        return runCatching {
            val (savedTarget, copiedBytes) = exportStreamToPublicDir(
                context = context,
                openInputStream = openInputStream,
                publicBaseDir = publicBaseDir,
                sanitizedName = sanitizedName,
                mimeType = mimeType
            )
            val finalSize = if (copiedBytes > 0L) copiedBytes else fileSize
            preferencesManager.recordDownloadedFile(
                DownloadedFileRecord(
                    id = UUID.randomUUID().toString(),
                    title = sanitizedName,
                    destinationLabel = destLabel,
                    targetUriOrPath = savedTarget,
                    sourceUriString = sourceUriString,
                    sizeBytes = finalSize,
                    downloadedAt = System.currentTimeMillis()
                )
            )
            DeviceDownloadResult(
                fileName = sanitizedName,
                destinationLabel = destLabel,
                targetUriOrPath = savedTarget,
                sizeBytes = finalSize
            )
        }
    }

    private fun exportStreamToPublicDir(
        context: Context,
        openInputStream: () -> InputStream?,
        publicBaseDir: String,
        sanitizedName: String,
        mimeType: String
    ): Pair<String, Long> {
        var copiedBytes = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val relativePath = "$publicBaseDir/$PUBLIC_SUBFOLDER"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("MediaStore could not create $relativePath/$sanitizedName")

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    val input = openInputStream()
                        ?: throw IllegalStateException("Could not read source document")
                    input.use { stream ->
                        copiedBytes = stream.copyTo(out)
                    }
                } ?: throw IllegalStateException("Failed to open output stream for $itemUri")

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw e
            }

            return itemUri.toString() to copiedBytes
        } else {
            @Suppress("DEPRECATION")
            val docsRoot = Environment.getExternalStoragePublicDirectory(publicBaseDir)
            val targetDir = File(docsRoot, PUBLIC_SUBFOLDER).apply {
                if (!exists()) mkdirs()
            }
            val destFile = resolveUniqueLegacyFile(targetDir, sanitizedName)
            val input = openInputStream()
                ?: throw IllegalStateException("Could not read source document")
            input.use { stream ->
                FileOutputStream(destFile).use { out ->
                    copiedBytes = stream.copyTo(out)
                }
            }
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(destFile.absolutePath),
                arrayOf(mimeType),
                null
            )
            return Uri.fromFile(destFile).toString() to copiedBytes
        }
    }

    internal fun uniqueDisplayName(desiredName: String, existingNames: Set<String>): String {
        if (desiredName !in existingNames) return desiredName
        val base = desiredName.substringBeforeLast('.')
        val ext = desiredName.substringAfterLast('.', "")
        var idx = 1
        while (true) {
            val candidate = if (ext.isNotEmpty() && base != desiredName) "${base}_$idx.$ext" else "${desiredName}_$idx"
            if (candidate !in existingNames) return candidate
            idx++
        }
    }

    suspend fun exportFileToDocumentsYomi(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name,
        mimeType: String = inferMimeType(displayName)
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Source file does not exist or is empty."))
        }

        val sanitizedName = sanitizeFileName(displayName)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_SUBFOLDER"

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val itemUri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("MediaStore could not create $relativePath/$sanitizedName"))

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Failed to open output stream for $itemUri")

                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, publishValues, null, null)
                } catch (e: Exception) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw e
                }

                Result.success("$relativePath/$sanitizedName")
            } else {
                @Suppress("DEPRECATION")
                val docsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val targetDir = File(docsRoot, PUBLIC_SUBFOLDER).apply {
                    if (!exists()) mkdirs()
                }
                val destFile = resolveUniqueLegacyFile(targetDir, sanitizedName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { out ->
                        input.copyTo(out)
                    }
                }
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(destFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                Result.success("${Environment.DIRECTORY_DOCUMENTS}/$PUBLIC_SUBFOLDER/${destFile.name}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "Yomi_${System.currentTimeMillis()}.pdf" }
    }

    internal fun inferMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    private fun resolveUniqueLegacyFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }
}

