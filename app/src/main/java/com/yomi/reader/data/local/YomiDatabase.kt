package com.yomi.reader.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yomi.reader.data.local.dao.DocumentDao
import com.yomi.reader.data.local.entity.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class YomiDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    DocumentDao {

    private val changeTrigger = MutableStateFlow(0L)

    private fun notifyChanged() {
        changeTrigger.value = changeTrigger.value + 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DOCUMENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URI TEXT UNIQUE NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_TYPE TEXT NOT NULL,
                $COL_SIZE INTEGER NOT NULL,
                $COL_LAST_MODIFIED INTEGER NOT NULL,
                $COL_IS_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_OPENED INTEGER,
                $COL_PARENT_PATH TEXT DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_documents_uri ON $TABLE_DOCUMENTS($COL_URI)")
        db.execSQL("CREATE INDEX idx_documents_type ON $TABLE_DOCUMENTS($COL_TYPE)")
        db.execSQL("CREATE INDEX idx_documents_fav ON $TABLE_DOCUMENTS($COL_IS_FAVORITE)")
        db.execSQL("CREATE INDEX idx_documents_parent ON $TABLE_DOCUMENTS($COL_PARENT_PATH)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching {
                db.execSQL("ALTER TABLE $TABLE_DOCUMENTS ADD COLUMN $COL_PARENT_PATH TEXT DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_parent ON $TABLE_DOCUMENTS($COL_PARENT_PATH)")
            }
        }
    }

    fun documentDao(): DocumentDao = this

    private fun cursorToEntity(cursor: Cursor): DocumentEntity {
        val parentCol = cursor.getColumnIndex(COL_PARENT_PATH)
        val parent = if (parentCol != -1 && !cursor.isNull(parentCol)) cursor.getString(parentCol) else ""

        return DocumentEntity(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            uriString = cursor.getString(cursor.getColumnIndexOrThrow(COL_URI)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
            typeName = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE)),
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SIZE)),
            lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_MODIFIED)),
            isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_FAVORITE)) == 1,
            lastOpened = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_LAST_OPENED))) null
            else cursor.getLong(cursor.getColumnIndexOrThrow(COL_LAST_OPENED)),
            parentPath = parent
        )
    }

    private fun queryList(selection: String?, selectionArgs: Array<String>?, orderBy: String?): List<DocumentEntity> {
        val list = mutableListOf<DocumentEntity>()
        val db = readableDatabase
        db.query(
            TABLE_DOCUMENTS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToEntity(cursor))
            }
        }
        return list
    }

    override fun getAllDocuments(): Flow<List<DocumentEntity>> {
        return changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryList(null, null, "$COL_LAST_MODIFIED DESC")
            }
        }
    }

    override fun getDocumentsByType(typeName: String): Flow<List<DocumentEntity>> {
        return changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryList("$COL_TYPE = ?", arrayOf(typeName), "$COL_LAST_MODIFIED DESC")
            }
        }
    }

    override fun getFavoriteDocuments(): Flow<List<DocumentEntity>> {
        return changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryList("$COL_IS_FAVORITE = 1", null, "$COL_LAST_MODIFIED DESC")
            }
        }
    }

    override fun getRecentDocuments(): Flow<List<DocumentEntity>> {
        return changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryList("$COL_LAST_OPENED IS NOT NULL", null, "$COL_LAST_OPENED DESC LIMIT 10")
            }
        }
    }

    override fun searchDocuments(query: String): Flow<List<DocumentEntity>> {
        return changeTrigger.map {
            withContext(Dispatchers.IO) {
                queryList("$COL_TITLE LIKE ?", arrayOf("%$query%"), "$COL_LAST_MODIFIED DESC")
            }
        }
    }

    override suspend fun getDocumentById(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        val list = queryList("$COL_ID = ?", arrayOf(id.toString()), null)
        list.firstOrNull()
    }

    override suspend fun getDocumentByUri(uriString: String): DocumentEntity? = withContext(Dispatchers.IO) {
        val list = queryList("$COL_URI = ?", arrayOf(uriString), null)
        list.firstOrNull()
    }

    override suspend fun insertOrUpdate(document: DocumentEntity): Long = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_URI, document.uriString)
            put(COL_TITLE, document.title)
            put(COL_TYPE, document.typeName)
            put(COL_SIZE, document.sizeBytes)
            put(COL_LAST_MODIFIED, document.lastModified)
            put(COL_IS_FAVORITE, if (document.isFavorite) 1 else 0)
            put(COL_LAST_OPENED, document.lastOpened)
            put(COL_PARENT_PATH, document.parentPath)
        }
        val id = db.insertWithOnConflict(TABLE_DOCUMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
        id
    }

    override suspend fun insertAll(documents: List<DocumentEntity>): List<Long> = withContext(Dispatchers.IO) {
        if (documents.isEmpty()) return@withContext emptyList()
        val db = writableDatabase
        val ids = mutableListOf<Long>()
        var insertedCount = 0
        db.beginTransaction()
        try {
            for (doc in documents) {
                val values = ContentValues().apply {
                    put(COL_URI, doc.uriString)
                    put(COL_TITLE, doc.title)
                    put(COL_TYPE, doc.typeName)
                    put(COL_SIZE, doc.sizeBytes)
                    put(COL_LAST_MODIFIED, doc.lastModified)
                    put(COL_IS_FAVORITE, if (doc.isFavorite) 1 else 0)
                    put(COL_LAST_OPENED, doc.lastOpened)
                    put(COL_PARENT_PATH, doc.parentPath)
                }
                val id = db.insertWithOnConflict(TABLE_DOCUMENTS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                ids.add(id)
                if (id != -1L) {
                    insertedCount++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (insertedCount > 0) {
            notifyChanged()
        }
        ids
    }

    override suspend fun toggleFavorite(id: Long): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.execSQL("UPDATE $TABLE_DOCUMENTS SET $COL_IS_FAVORITE = CASE WHEN $COL_IS_FAVORITE = 1 THEN 0 ELSE 1 END WHERE $COL_ID = ?", arrayOf(id))
        notifyChanged()
    }

    override suspend fun updateLastOpened(id: Long, timestamp: Long): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_LAST_OPENED, timestamp)
        }
        db.update(TABLE_DOCUMENTS, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    override suspend fun delete(document: DocumentEntity) {
        deleteById(document.id)
    }

    override suspend fun deleteById(id: Long): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_DOCUMENTS, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    override suspend fun deleteByIds(ids: Set<Long>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            db.delete(TABLE_DOCUMENTS, "$COL_ID IN ($placeholders)", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    override suspend fun toggleFavoritesByIds(ids: Set<Long>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            db.execSQL(
                "UPDATE $TABLE_DOCUMENTS SET $COL_IS_FAVORITE = CASE WHEN $COL_IS_FAVORITE = 1 THEN 0 ELSE 1 END WHERE $COL_ID IN ($placeholders)",
                args
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    override suspend fun updateDocumentFolder(id: Long, folderName: String): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PARENT_PATH, folderName)
        }
        db.update(TABLE_DOCUMENTS, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    override suspend fun updateDocumentFolderByIds(ids: Set<Long>, folderName: String): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_PARENT_PATH, folderName)
            }
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            db.update(TABLE_DOCUMENTS, values, "$COL_ID IN ($placeholders)", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    override suspend fun getAllUris(): Set<String> = withContext(Dispatchers.IO) {
        val set = mutableSetOf<String>()
        val db = readableDatabase
        db.query(TABLE_DOCUMENTS, arrayOf(COL_URI), null, null, null, null, null).use { cursor ->
            val col = cursor.getColumnIndexOrThrow(COL_URI)
            while (cursor.moveToNext()) {
                set.add(cursor.getString(col))
            }
        }
        set
    }

    override suspend fun deleteNonPdfDocuments(): Unit = withContext(Dispatchers.IO) {
        val count = writableDatabase.delete(TABLE_DOCUMENTS, "$COL_TYPE != ?", arrayOf("PDF"))
        if (count > 0) notifyChanged()
    }

    override suspend fun renameDocument(id: Long, newTitle: String): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, newTitle)
        }
        db.update(TABLE_DOCUMENTS, values, "$COL_ID = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    companion object {
        private const val DATABASE_NAME = "yomi_documents.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_DOCUMENTS = "documents"
        private const val COL_ID = "id"
        private const val COL_URI = "uriString"
        private const val COL_TITLE = "title"
        private const val COL_TYPE = "typeName"
        private const val COL_SIZE = "sizeBytes"
        private const val COL_LAST_MODIFIED = "lastModified"
        private const val COL_IS_FAVORITE = "isFavorite"
        private const val COL_LAST_OPENED = "lastOpened"
        private const val COL_PARENT_PATH = "parentPath"

        @Volatile
        private var INSTANCE: YomiDatabase? = null

        fun getInstance(context: Context): YomiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = YomiDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
