package com.yomi.reader.data.local.entity

import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType

data class DocumentEntity(
    val id: Long = 0,
    val uriString: String,
    val title: String,
    val typeName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isFavorite: Boolean = false,
    val lastOpened: Long? = null,
    val parentPath: String = ""
) {
    fun toDomain(): DocumentItem {
        return DocumentItem(
            id = id,
            uriString = uriString,
            title = title,
            type = runCatching { DocumentType.valueOf(typeName) }.getOrDefault(DocumentType.UNKNOWN),
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            isFavorite = isFavorite,
            lastOpened = lastOpened,
            parentPath = parentPath
        )
    }

    companion object {
        fun fromDomain(item: DocumentItem): DocumentEntity {
            return DocumentEntity(
                id = item.id,
                uriString = item.uriString,
                title = item.title,
                typeName = item.type.name,
                sizeBytes = item.sizeBytes,
                lastModified = item.lastModified,
                isFavorite = item.isFavorite,
                lastOpened = item.lastOpened,
                parentPath = item.parentPath
            )
        }
    }
}
