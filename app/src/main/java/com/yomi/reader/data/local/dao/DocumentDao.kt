package com.yomi.reader.data.local.dao

import com.yomi.reader.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

interface DocumentDao {
    fun getAllDocuments(): Flow<List<DocumentEntity>>
    fun getDocumentsByType(typeName: String): Flow<List<DocumentEntity>>
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>
    fun getRecentDocuments(): Flow<List<DocumentEntity>>
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>
    suspend fun getDocumentById(id: Long): DocumentEntity?
    suspend fun getDocumentByUri(uriString: String): DocumentEntity?
    suspend fun insertOrUpdate(document: DocumentEntity): Long
    suspend fun insertAll(documents: List<DocumentEntity>): List<Long>
    suspend fun toggleFavorite(id: Long)
    suspend fun updateLastOpened(id: Long, timestamp: Long)
    suspend fun delete(document: DocumentEntity)
    suspend fun deleteById(id: Long)
    suspend fun deleteByIds(ids: Set<Long>)
    suspend fun toggleFavoritesByIds(ids: Set<Long>)
    suspend fun updateDocumentFolder(id: Long, folderName: String)
    suspend fun updateDocumentFolderByIds(ids: Set<Long>, folderName: String)
    suspend fun getAllUris(): Set<String>
    suspend fun deleteNonPdfDocuments()
    suspend fun renameDocument(id: Long, newTitle: String)
}
