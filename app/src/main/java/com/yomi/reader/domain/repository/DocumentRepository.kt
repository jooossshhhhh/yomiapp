package com.yomi.reader.domain.repository

import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getAllDocuments(): Flow<List<DocumentItem>>
    fun getDocumentsByCategory(category: DocumentCategory): Flow<List<DocumentItem>>
    fun getFavoriteDocuments(): Flow<List<DocumentItem>>
    fun getRecentDocuments(): Flow<List<DocumentItem>>
    fun searchDocuments(query: String): Flow<List<DocumentItem>>
    suspend fun getDocumentById(id: Long): DocumentItem?
    suspend fun getDocumentByUri(uriString: String): DocumentItem?
    suspend fun importDocument(uriString: String, title: String, type: DocumentType, sizeBytes: Long, parentPath: String = ""): DocumentItem
    suspend fun toggleFavorite(id: Long)
    suspend fun toggleFavorites(ids: Set<Long>)
    suspend fun recordOpened(id: Long)
    suspend fun deleteDocument(id: Long)
    suspend fun deleteDocuments(ids: Set<Long>)
    suspend fun updateDocumentFolder(id: Long, folderName: String)
    suspend fun updateDocumentsFolder(ids: Set<Long>, folderName: String)
    suspend fun renameDocument(id: Long, newTitle: String)
    suspend fun scanStorageForDocuments(): Int
    suspend fun syncDriveFolder(treeUri: android.net.Uri): Int
    suspend fun syncDriveLinkFolder(folderUrlOrId: String, parentPath: String = "", accessToken: String? = null): Result<String>
    suspend fun createDriveSubfolder(treeUri: android.net.Uri, parentPath: String, folderName: String): Result<DriveFolderItem>
    suspend fun uploadPdfToDriveFolder(treeUri: android.net.Uri, parentPath: String, sourcePdfUri: android.net.Uri, desiredFileName: String): Result<DriveFileItem>
}
