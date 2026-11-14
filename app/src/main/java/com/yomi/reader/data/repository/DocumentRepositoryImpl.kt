package com.yomi.reader.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.yomi.reader.data.local.dao.DocumentDao
import com.yomi.reader.data.local.entity.DocumentEntity
import com.yomi.reader.data.remote.GoogleDriveSharedLinkClient
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import com.yomi.reader.domain.model.SavedDriveLink
import com.yomi.reader.domain.repository.DocumentRepository
import com.yomi.reader.util.DriveFolderSafetyHelper
import kotlinx.coroutines.Dispatchers
import androidx.documentfile.provider.DocumentFile
import com.yomi.reader.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val context: Context,
    private val dao: DocumentDao,
    private val preferencesManager: PreferencesManager
) : DocumentRepository {

    override fun getAllDocuments(): Flow<List<DocumentItem>> {
        return dao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDocumentsByCategory(category: DocumentCategory): Flow<List<DocumentItem>> {
        return when (category) {
            DocumentCategory.ALL -> getAllDocuments()
            DocumentCategory.PDF -> dao.getDocumentsByType(DocumentType.PDF.name).map { list -> list.map { it.toDomain() } }
            DocumentCategory.WORD -> dao.getDocumentsByType(DocumentType.WORD.name).map { list -> list.map { it.toDomain() } }
            DocumentCategory.EXCEL -> dao.getDocumentsByType(DocumentType.EXCEL.name).map { list -> list.map { it.toDomain() } }
            DocumentCategory.PRESENTATION -> dao.getDocumentsByType(DocumentType.POWERPOINT.name).map { list -> list.map { it.toDomain() } }
            DocumentCategory.GOOGLE_DRIVE -> getAllDocuments().map { list ->
                list.filter { it.isFromGoogleDrive || preferencesManager.isDriveSyncedUri(it.uriString) }
            }
        }
    }

    override fun getFavoriteDocuments(): Flow<List<DocumentItem>> {
        return dao.getFavoriteDocuments().map { list -> list.map { it.toDomain() } }
    }

    override fun getRecentDocuments(): Flow<List<DocumentItem>> {
        return dao.getRecentDocuments().map { list -> list.map { it.toDomain() } }
    }

    override fun searchDocuments(query: String): Flow<List<DocumentItem>> {
        return dao.searchDocuments(query).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getDocumentById(id: Long): DocumentItem? {
        return dao.getDocumentById(id)?.toDomain()
    }

    override suspend fun getDocumentByUri(uriString: String): DocumentItem? {
        return dao.getDocumentByUri(uriString)?.toDomain()
    }

    override suspend fun importDocument(
        uriString: String,
        title: String,
        type: DocumentType,
        sizeBytes: Long,
        parentPath: String
    ): DocumentItem = withContext(Dispatchers.IO) {
        val existing = dao.getDocumentByUri(uriString)
        val entity = DocumentEntity(
            id = existing?.id ?: 0,
            uriString = uriString,
            title = title,
            typeName = type.name,
            sizeBytes = sizeBytes,
            lastModified = System.currentTimeMillis(),
            isFavorite = existing?.isFavorite ?: false,
            lastOpened = existing?.lastOpened,
            parentPath = if (parentPath.isNotEmpty()) parentPath else (existing?.parentPath ?: "")
        )
        val id = dao.insertOrUpdate(entity)
        entity.copy(id = if (entity.id == 0L) id else entity.id).toDomain()
    }

    override suspend fun toggleFavorite(id: Long) = withContext(Dispatchers.IO) {
        dao.toggleFavorite(id)
    }

    override suspend fun toggleFavorites(ids: Set<Long>) = withContext(Dispatchers.IO) {
        dao.toggleFavoritesByIds(ids)
    }

    override suspend fun recordOpened(id: Long) = withContext(Dispatchers.IO) {
        dao.updateLastOpened(id, System.currentTimeMillis())
    }

    override suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    override suspend fun deleteDocuments(ids: Set<Long>) = withContext(Dispatchers.IO) {
        dao.deleteByIds(ids)
    }

    override suspend fun updateDocumentFolder(id: Long, folderName: String) = withContext(Dispatchers.IO) {
        dao.updateDocumentFolder(id, folderName)
    }

    override suspend fun updateDocumentsFolder(ids: Set<Long>, folderName: String) = withContext(Dispatchers.IO) {
        dao.updateDocumentFolderByIds(ids, folderName)
    }

    override suspend fun renameDocument(id: Long, newTitle: String) = withContext(Dispatchers.IO) {
        dao.renameDocument(id, newTitle)
    }

    override suspend fun scanStorageForDocuments(): Int = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DocumentEntity>()
        val contentResolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        // Purge any non-PDF documents from database (PDF-centered architecture)
        dao.deleteNonPdfDocuments()

        // Filter files matching PDF extension only
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf'"

        runCatching {
            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document_$mediaId"
                    val size = cursor.getLong(sizeCol)
                    val dateModifiedSec = cursor.getLong(dateCol)
                    val dateModifiedMs = if (dateModifiedSec > 0) dateModifiedSec * 1000L else System.currentTimeMillis()

                    val fileUri = ContentUris.withAppendedId(collection, mediaId)
                    val ext = name.substringAfterLast('.', "")
                    val docType = DocumentType.fromExtension(ext)

                    if (docType != DocumentType.UNKNOWN) {
                        discovered.add(
                            DocumentEntity(
                                uriString = fileUri.toString(),
                                title = name,
                                typeName = docType.name,
                                sizeBytes = size,
                                lastModified = dateModifiedMs,
                                isFavorite = false,
                                lastOpened = null
                            )
                        )
                    }
                }
            }
        }

        if (discovered.isNotEmpty()) {
            dao.insertAll(discovered)
        }
        discovered.size
    }

    override suspend fun syncDriveFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        if (treeUri.scheme.equals("http", ignoreCase = true) ||
            treeUri.scheme.equals("https", ignoreCase = true)
        ) {
            syncDriveLinkFolder(folderUrlOrId = treeUri.toString(), parentPath = "")
            return@withContext preferencesManager.driveFiles.value.size
        }
        val treeDoc = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return@withContext 0
        val discoveredFolders = mutableListOf<DriveFolderItem>()
        val discoveredFiles = mutableListOf<DriveFileItem>()
        val folderFileCounts = mutableMapOf<String, Int>()
        val syncedUris = mutableSetOf<String>()

        suspend fun scanFolder(folder: DocumentFile, currentPath: String, depth: Int = 0) {
            if (depth > 6) return
            val files = folder.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    val folderName = file.name ?: continue
                    val subPath = if (currentPath.isEmpty()) folderName else "$currentPath/$folderName"
                    discoveredFolders.add(
                        DriveFolderItem(
                            name = folderName,
                            path = subPath,
                            parentPath = currentPath,
                            uriString = file.uri.toString()
                        )
                    )
                    scanFolder(file, subPath, depth + 1)
                } else {
                    val name = file.name ?: continue
                    val ext = name.substringAfterLast('.', "")
                    if (!DriveFolderSafetyHelper.isSupportedDriveExtension(ext)) continue
                    val docType = DocumentType.fromExtension(ext)
                    if (docType != DocumentType.UNKNOWN) {
                        val uriString = file.uri.toString()
                        syncedUris.add(uriString)

                        var p = currentPath
                        while (p.isNotEmpty()) {
                            folderFileCounts[p] = (folderFileCounts[p] ?: 0) + 1
                            val slashIdx = p.lastIndexOf('/')
                            p = if (slashIdx != -1) p.substring(0, slashIdx) else ""
                        }

                        // Catalog Drive entries separately without touching Room so Drive files
                        // are never edited/deleted directly and only open as local Yomi copies.
                        discoveredFiles.add(
                            DriveFileItem(
                                name = name,
                                uriString = uriString,
                                parentPath = currentPath,
                                type = docType,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        scanFolder(treeDoc, "")

        // Clean up any legacy raw SAF tree entries from earlier sync versions without removing
        // local Yomi copies (file:// URIs) that the user downloaded or converted.
        val legacyTreeDocIds = dao.getAllDocuments().first()
            .filter { entity ->
                !entity.uriString.startsWith("file://") &&
                    entity.uriString.contains("/tree/") &&
                    preferencesManager.isDriveSyncedUri(entity.uriString)
            }
            .map { it.id }
            .toSet()
        if (legacyTreeDocIds.isNotEmpty()) {
            dao.deleteByIds(legacyTreeDocIds)
        }

        val foldersWithCounts = discoveredFolders.map { f ->
            f.copy(fileCount = folderFileCounts[f.path] ?: 0)
        }

        preferencesManager.saveDriveFolders(foldersWithCounts)
        preferencesManager.saveDriveFiles(discoveredFiles)

        if (syncedUris.isNotEmpty()) {
            preferencesManager.addSyncedDriveUris(syncedUris)
        }
        preferencesManager.setLastDriveSyncTime(System.currentTimeMillis())
        discoveredFiles.size
    }

    override suspend fun syncDriveLinkFolder(
        folderUrlOrId: String,
        parentPath: String,
        accessToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val fetched = GoogleDriveSharedLinkClient.fetchFolderContents(
            folderUrlOrId = folderUrlOrId,
            parentPath = parentPath,
            accessToken = accessToken
        ).getOrElse { error ->
            return@withContext Result.failure(error)
        }

        if (parentPath.isBlank()) {
            preferencesManager.setConnectedDriveFolder(fetched.canonicalUrl, fetched.folderName)
            preferencesManager.addRecentDriveLink(
                SavedDriveLink(
                    folderId = fetched.folderId,
                    url = fetched.canonicalUrl,
                    name = fetched.folderName,
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            preferencesManager.saveDriveFolders(fetched.subfolders)
            preferencesManager.saveDriveFiles(fetched.files)
        } else {
            val childPrefix = "$parentPath/"
            val preservedFolders = preferencesManager.driveFolders.value
                .filterNot { it.parentPath == parentPath || it.parentPath.startsWith(childPrefix) }
                .map { item ->
                    if (item.path == parentPath) item.copy(fileCount = fetched.files.size) else item
                }
            val preservedFiles = preferencesManager.driveFiles.value
                .filterNot { it.parentPath == parentPath || it.parentPath.startsWith(childPrefix) }

            preferencesManager.saveDriveFolders(preservedFolders + fetched.subfolders)
            preferencesManager.saveDriveFiles(preservedFiles + fetched.files)
        }

        preferencesManager.setLastDriveSyncTime(System.currentTimeMillis())
        Result.success(fetched.folderName)
    }

    private fun resolveTargetDriveDirectory(treeUri: Uri, parentPath: String): DocumentFile? {
        if (treeUri.scheme.equals("http", ignoreCase = true) ||
            treeUri.scheme.equals("https", ignoreCase = true)
        ) {
            return null
        }
        var current = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return null
        if (parentPath.isBlank()) return current
        val segments = parentPath.split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val child = current.findFile(segment)
            if (child == null || !child.isDirectory) return null
            current = child
        }
        return current
    }

    override suspend fun createDriveSubfolder(
        treeUri: Uri,
        parentPath: String,
        folderName: String
    ): Result<DriveFolderItem> = withContext(Dispatchers.IO) {
        val targetDir = resolveTargetDriveDirectory(treeUri, parentPath)
            ?: return@withContext Result.failure(IllegalStateException("Drive folder could not be accessed"))

        if (!targetDir.canWrite()) {
            return@withContext Result.failure(
                IllegalStateException("This Drive folder is read-only. Open Google Drive app to add folders.")
            )
        }

        val existingNames = targetDir.listFiles().mapNotNull { it.name }
        val validatedName = DriveFolderSafetyHelper.validateNewSubfolderName(folderName, existingNames)
            .getOrElse { return@withContext Result.failure(it) }

        val createdDir = runCatching { targetDir.createDirectory(validatedName) }.getOrNull()
            ?: return@withContext Result.failure(
                IllegalStateException("Could not create folder \"$validatedName\" in this Drive provider.")
            )

        val subPath = if (parentPath.isBlank()) validatedName else "$parentPath/$validatedName"
        syncDriveFolder(treeUri)
        Result.success(
            DriveFolderItem(
                name = createdDir.name ?: validatedName,
                path = subPath,
                parentPath = parentPath,
                fileCount = 0,
                uriString = createdDir.uri.toString()
            )
        )
    }

    override suspend fun uploadPdfToDriveFolder(
        treeUri: Uri,
        parentPath: String,
        sourcePdfUri: Uri,
        desiredFileName: String
    ): Result<DriveFileItem> = withContext(Dispatchers.IO) {
        val cleanExt = desiredFileName.substringAfterLast('.', "").lowercase()
        if (cleanExt != "pdf") {
            return@withContext Result.failure(
                IllegalArgumentException("Only PDF files can be uploaded to the Drive folder.")
            )
        }

        val targetDir = resolveTargetDriveDirectory(treeUri, parentPath)
            ?: return@withContext Result.failure(IllegalStateException("Target Drive folder could not be accessed"))

        if (!targetDir.canWrite()) {
            return@withContext Result.failure(
                IllegalStateException("This Drive folder is read-only. Open Google Drive app to upload files.")
            )
        }

        val existingNames = targetDir.listFiles().mapNotNull { it.name }
        val safeFileName = DriveFolderSafetyHelper.resolveNonOverwritingPdfName(desiredFileName, existingNames)
        val displayTitleWithoutExt = safeFileName.removeSuffix(".pdf")

        // Some SAF providers automatically append .pdf when MIME type is application/pdf;
        // try exact name first, and verify final DocumentFile.
        val createdFile = runCatching {
            targetDir.createFile("application/pdf", displayTitleWithoutExt)
        }.getOrNull() ?: return@withContext Result.failure(
            IllegalStateException("Could not create \"$safeFileName\" in this Drive provider.")
        )

        val copyResult = runCatching {
            context.contentResolver.openInputStream(sourcePdfUri)?.use { input ->
                context.contentResolver.openOutputStream(createdFile.uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Unable to open Drive output stream")
            } ?: throw IllegalStateException("Unable to read source PDF")
        }

        if (copyResult.isFailure) {
            // Remove only the empty partial file we just created if streaming failed; never touch existing files.
            runCatching { createdFile.delete() }
            return@withContext Result.failure(
                copyResult.exceptionOrNull() ?: IllegalStateException("Upload failed")
            )
        }

        syncDriveFolder(treeUri)
        Result.success(
            DriveFileItem(
                name = createdFile.name ?: safeFileName,
                uriString = createdFile.uri.toString(),
                parentPath = parentPath,
                type = DocumentType.PDF,
                sizeBytes = createdFile.length(),
                lastModified = System.currentTimeMillis()
            )
        )
    }
}
