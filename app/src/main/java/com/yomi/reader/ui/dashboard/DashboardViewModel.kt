package com.yomi.reader.ui.dashboard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentSortOrder
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import com.yomi.reader.domain.repository.DocumentRepository
import com.yomi.reader.ui.dashboard.components.CustomFolderStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DashboardViewModel(
    private val repository: DocumentRepository,
    private val preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow(DocumentCategory.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isScanning = MutableStateFlow(false)
    private val _isDriveSyncing = MutableStateFlow(false)
    private val _currentFolderPath = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(DocumentSortOrder.DATE_DESC)
    private val _fallbackGridView = MutableStateFlow(false)
    private val _isGridView: StateFlow<Boolean> = preferencesManager?.defaultGridView ?: _fallbackGridView
    private val _isFavoritesOnly = MutableStateFlow(false)

    private val _debouncedSearchQuery = _searchQuery.debounce { query ->
        if (query.isBlank()) 0L else 200L
    }

    private val _documents = combine(_selectedCategory, _debouncedSearchQuery) { category, query ->
        Pair(category, query)
    }.flatMapLatest { (category, query) ->
        if (query.isNotBlank()) {
            repository.searchDocuments(query.trim())
        } else {
            repository.getDocumentsByCategory(category)
        }
    }

    private val _recents = repository.getRecentDocuments()
    private val _allDocuments = repository.getDocumentsByCategory(DocumentCategory.ALL)
    private val _folders = preferencesManager?.driveFolders ?: MutableStateFlow(emptyList())
    private val _driveFiles = preferencesManager?.driveFiles ?: MutableStateFlow(emptyList())
    private val _driveLocalCopies = preferencesManager?.driveLocalCopies ?: MutableStateFlow(emptyMap())
    private val _rootFolderUri = preferencesManager?.connectedDriveFolderUri ?: MutableStateFlow(null)
    private val _rootFolderName = preferencesManager?.connectedDriveFolderName ?: MutableStateFlow(null)

    private val _driveDataState = combine(_folders, _driveFiles, _driveLocalCopies, _rootFolderUri, _rootFolderName) { folders, files, localCopies, rootUri, rootName ->
        DriveDataBundle(folders, files, localCopies, rootUri, rootName)
    }

    private val _syncState = combine(_isScanning, _isDriveSyncing) { scanning, driveSyncing ->
        Pair(scanning, driveSyncing)
    }

    private val _selectedCustomFolder = MutableStateFlow<String?>(null)
    private val _customFolders = preferencesManager?.customFolders ?: MutableStateFlow(setOf("Work", "Study", "Personal", "Archive"))
    private val _customFolderStyles = preferencesManager?.customFolderStyles ?: MutableStateFlow(emptyMap<String, CustomFolderStyle>())

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(_documents, _allDocuments, _recents, _selectedCategory) { docs, allDocs, recents, cat ->
            Tuple4(docs, allDocs, recents, cat)
        },
        combine(_searchQuery, _currentFolderPath, _sortOrder, _isGridView) { query, path, sort, grid ->
            Tuple4(query, path, sort, grid)
        },
        combine(_isFavoritesOnly, _driveDataState) { favs, driveData ->
            Pair(favs, driveData)
        },
        combine(_syncState, _customFolders, _customFolderStyles, _selectedCustomFolder) { sync, cFolders, cStyles, selFolder ->
            Tuple4(sync, cFolders, cStyles, selFolder)
        }
    ) { (rawDocs, allDocs, recents, category), (query, currentPath, sortOrder, isGrid), (isFavsOnly, driveData), (sync, cFolders, cStyles, selFolder) ->
        val (scanning, driveSyncing) = sync
        val (allFolders, allDriveFiles, driveLocalCopies, rootUri, rootName) = driveData
        val isDriveLinkMode = rootUri?.startsWith("http", ignoreCase = true) == true

        // 1. Current subfolders for active folder path when in Google Drive category
        val currentSubfolders = if (category == DocumentCategory.GOOGLE_DRIVE && query.isEmpty()) {
            allFolders.filter { it.parentPath == currentPath }
        } else {
            emptyList()
        }

        // 1b. Read-safe Drive files for active folder path when in Google Drive category
        val filteredDriveFiles = if (category == DocumentCategory.GOOGLE_DRIVE) {
            val trimmedQuery = query.trim()
            allDriveFiles.filter { file ->
                val matchesPath = if (currentPath.isEmpty() && trimmedQuery.isNotEmpty()) {
                    true
                } else {
                    file.parentPath == currentPath
                }
                val matchesQuery = trimmedQuery.isEmpty() || file.name.contains(trimmedQuery, ignoreCase = true)
                matchesPath && matchesQuery
            }
        } else {
            emptyList()
        }
        val sortedDriveFiles = when (sortOrder) {
            DocumentSortOrder.DATE_DESC -> filteredDriveFiles.sortedByDescending { it.lastModified }
            DocumentSortOrder.DATE_ASC -> filteredDriveFiles.sortedBy { it.lastModified }
            DocumentSortOrder.NAME_ASC -> filteredDriveFiles.sortedBy { it.name.lowercase() }
            DocumentSortOrder.NAME_DESC -> filteredDriveFiles.sortedByDescending { it.name.lowercase() }
            DocumentSortOrder.SIZE_DESC -> filteredDriveFiles.sortedByDescending { it.sizeBytes }
            DocumentSortOrder.SIZE_ASC -> filteredDriveFiles.sortedBy { it.sizeBytes }
        }

        // 2. Filter documents by folder when in custom folder (Drive folder reads strictly from driveFiles)
        var filteredDocs = if (category == DocumentCategory.GOOGLE_DRIVE) {
            emptyList()
        } else if (selFolder != null && category == DocumentCategory.ALL) {
            rawDocs.filter { it.parentPath == selFolder }
        } else {
            rawDocs
        }

        // 3. Filter by favorites if toggled
        if (isFavsOnly) {
            filteredDocs = filteredDocs.filter { it.isFavorite }
        }

        // 4. Sort documents
        val sortedDocs = when (sortOrder) {
            DocumentSortOrder.DATE_DESC -> filteredDocs.sortedByDescending { it.lastModified }
            DocumentSortOrder.DATE_ASC -> filteredDocs.sortedBy { it.lastModified }
            DocumentSortOrder.NAME_ASC -> filteredDocs.sortedBy { it.title.lowercase() }
            DocumentSortOrder.NAME_DESC -> filteredDocs.sortedByDescending { it.title.lowercase() }
            DocumentSortOrder.SIZE_DESC -> filteredDocs.sortedByDescending { it.sizeBytes }
            DocumentSortOrder.SIZE_ASC -> filteredDocs.sortedBy { it.sizeBytes }
        }

        val folderDisplayName = when {
            currentPath.isNotEmpty() -> currentPath.substringAfterLast('/')
            !rootName.isNullOrEmpty() -> rootName
            else -> "Google Drive"
        }

        var pdfCount = 0
        var wordCount = 0
        var excelCount = 0
        var pptCount = 0
        var starredCount = 0
        var totalBytes = 0L

        for (doc in rawDocs) {
            totalBytes += doc.sizeBytes
            if (doc.isFavorite) starredCount++
            when (doc.type) {
                DocumentType.PDF -> pdfCount++
                DocumentType.WORD -> wordCount++
                DocumentType.EXCEL -> excelCount++
                DocumentType.POWERPOINT -> pptCount++
                else -> {}
            }
        }
        val totalFormattedSize = DashboardUiState.formatTotalBytes(totalBytes)

        DashboardUiState(
            documents = sortedDocs,
            allDocuments = allDocs,
            recentDocuments = recents,
            selectedCategory = category,
            searchQuery = query,
            isLoading = false,
            isScanning = scanning,
            isDriveSyncing = driveSyncing,
            isDriveLinkMode = isDriveLinkMode,
            currentFolderPath = currentPath,
            currentFolderDisplayName = folderDisplayName,
            currentSubfolders = currentSubfolders,
            driveFiles = sortedDriveFiles,
            driveLocalCopies = driveLocalCopies,
            sortOrder = sortOrder,
            isGridView = isGrid,
            isFavoritesOnly = isFavsOnly,
            customFolders = cFolders,
            customFolderStyles = cStyles,
            selectedCustomFolder = selFolder,
            totalDocumentCount = rawDocs.size,
            totalFormattedSize = totalFormattedSize,
            pdfCount = pdfCount,
            wordCount = wordCount,
            excelCount = excelCount,
            pptCount = pptCount,
            starredCount = starredCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    private fun extractParentFolder(uriString: String): String {
        return runCatching {
            val decoded = Uri.decode(uriString)
            val parts = decoded.split('/')
            if (parts.size >= 2) {
                val folder = parts[parts.size - 2]
                if (folder.contains(':')) folder.substringAfter(':') else folder
            } else ""
        }.getOrDefault("")
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelect(category: DocumentCategory) {
        _selectedCategory.value = category
        _currentFolderPath.value = "" // Reset folder navigation to root when changing category
    }

    fun navigateToFolder(path: String) {
        _currentFolderPath.value = path
        if (uiState.value.isDriveLinkMode && path.isNotBlank()) {
            val subFolder = _folders.value.firstOrNull { it.path == path }
            val subUrl = subFolder?.uriString
            val alreadyLoaded = _folders.value.any { it.parentPath == path } ||
                _driveFiles.value.any { it.parentPath == path }
            if (!subUrl.isNullOrBlank() && subUrl.startsWith("http", ignoreCase = true) && !alreadyLoaded) {
                viewModelScope.launch {
                    _isDriveSyncing.value = true
                    try {
                        repository.syncDriveLinkFolder(subUrl, parentPath = path)
                    } finally {
                        _isDriveSyncing.value = false
                    }
                }
            }
        }
    }

    fun navigateUpFolder() {
        val current = _currentFolderPath.value
        val lastSlash = current.lastIndexOf('/')
        _currentFolderPath.value = if (lastSlash != -1) current.substring(0, lastSlash) else ""
    }

    fun resetToRootFolder() {
        _currentFolderPath.value = ""
    }

    fun setSortOrder(order: DocumentSortOrder) {
        _sortOrder.value = order
    }

    fun toggleViewMode() {
        val next = !_isGridView.value
        if (preferencesManager != null) {
            preferencesManager.setDefaultGridView(next)
        } else {
            _fallbackGridView.value = next
        }
    }

    fun toggleFavoritesOnly() {
        _isFavoritesOnly.value = !_isFavoritesOnly.value
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _isFavoritesOnly.value = enabled
    }

    fun resetToRootHome() {
        _isFavoritesOnly.value = false
        _selectedCategory.value = DocumentCategory.ALL
        _selectedCustomFolder.value = null
        _searchQuery.value = ""
        _currentFolderPath.value = ""
    }

    fun toggleFavorite(document: DocumentItem) {
        viewModelScope.launch {
            repository.toggleFavorite(document.id)
        }
    }

    fun recordOpened(document: DocumentItem) {
        viewModelScope.launch {
            repository.recordOpened(document.id)
        }
    }

    fun deleteDocument(document: DocumentItem) {
        viewModelScope.launch {
            repository.deleteDocument(document.id)
        }
    }

    fun renameDocument(documentId: Long, newTitle: String) {
        viewModelScope.launch {
            val titleWithExt = if (newTitle.endsWith(".pdf", ignoreCase = true)) newTitle else "$newTitle.pdf"
            repository.renameDocument(documentId, titleWithExt)
        }
    }

    fun deleteDocuments(ids: Set<Long>) {
        viewModelScope.launch {
            repository.deleteDocuments(ids)
        }
    }

    fun toggleFavorites(ids: Set<Long>) {
        viewModelScope.launch {
            repository.toggleFavorites(ids)
        }
    }

    fun moveDocumentsToFolder(ids: Set<Long>, folderName: String) {
        viewModelScope.launch {
            repository.updateDocumentsFolder(ids, folderName)
        }
    }

    fun removeDocumentsFromFolder(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateDocumentsFolder(ids, "")
        }
    }

    fun selectCustomFolder(folderName: String?) {
        _selectedCustomFolder.value = folderName
    }

    fun createCustomFolder(name: String, colorKey: String? = null, iconKey: String? = null) {
        preferencesManager?.addCustomFolder(name, colorKey, iconKey)
    }

    fun updateCustomFolderStyle(name: String, colorKey: String, iconKey: String) {
        preferencesManager?.updateCustomFolderStyle(name, colorKey, iconKey)
    }

    fun removeCustomFolder(name: String) {
        preferencesManager?.removeCustomFolder(name)
        if (_selectedCustomFolder.value == name) {
            _selectedCustomFolder.value = null
        }
        viewModelScope.launch {
            val docsInFolder = repository.getDocumentsByCategory(DocumentCategory.ALL)
                .first()
                .filter { it.parentPath == name }
                .map { it.id }
                .toSet()
            if (docsInFolder.isNotEmpty()) {
                repository.updateDocumentsFolder(docsInFolder, "")
            }
        }
    }

    fun renameCustomFolder(oldName: String, newName: String) {
        val cleanNew = newName.trim()
        if (cleanNew.isBlank() || cleanNew == oldName) return
        preferencesManager?.renameCustomFolder(oldName, cleanNew)
        if (_selectedCustomFolder.value == oldName) {
            _selectedCustomFolder.value = cleanNew
        }
        viewModelScope.launch {
            val docsInFolder = repository.getDocumentsByCategory(DocumentCategory.ALL)
                .first()
                .filter { it.parentPath == oldName }
                .map { it.id }
                .toSet()
            if (docsInFolder.isNotEmpty()) {
                repository.updateDocumentsFolder(docsInFolder, cleanNew)
            }
        }
    }

    fun moveDocumentToFolder(document: DocumentItem, folderName: String) {
        viewModelScope.launch {
            repository.updateDocumentFolder(document.id, folderName)
        }
    }

    private var hasTriggeredInitialScan = false

    fun checkAndTriggerInitialScan(force: Boolean = false) {
        if (!hasTriggeredInitialScan || force) {
            hasTriggeredInitialScan = true
            triggerScan()
        }
    }

    fun triggerScan() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanStorageForDocuments()
            } catch (_: Exception) {
                // Keep UI responsive even if storage scan hits a transient provider error.
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun syncDriveFolder(treeUri: Uri, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            _isDriveSyncing.value = true
            try {
                val syncedCount = repository.syncDriveFolder(treeUri)
                onComplete?.invoke(syncedCount)
            } catch (_: Exception) {
                onComplete?.invoke(0)
            } finally {
                _isDriveSyncing.value = false
            }
        }
    }

    fun openDriveFolderByLink(
        linkInput: String,
        accessToken: String? = null,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            _isDriveSyncing.value = true
            try {
                val res = repository.syncDriveLinkFolder(
                    folderUrlOrId = linkInput,
                    parentPath = "",
                    accessToken = accessToken
                )
                if (res.isSuccess) {
                    _currentFolderPath.value = ""
                    _selectedCustomFolder.value = null
                    _selectedCategory.value = DocumentCategory.GOOGLE_DRIVE
                }
                onResult(res)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isDriveSyncing.value = false
            }
        }
    }

    fun refreshConnectedDriveFolder(
        accessToken: String? = null,
        onComplete: ((Result<String>) -> Unit)? = null
    ) {
        val connectedUri = _rootFolderUri.value ?: return
        val currentPath = _currentFolderPath.value
        if (connectedUri.startsWith("http", ignoreCase = true)) {
            val activeUrl = if (currentPath.isNotBlank()) {
                _folders.value.firstOrNull { it.path == currentPath }?.uriString ?: connectedUri
            } else {
                connectedUri
            }
            viewModelScope.launch {
                _isDriveSyncing.value = true
                try {
                    val res = repository.syncDriveLinkFolder(
                        folderUrlOrId = activeUrl,
                        parentPath = currentPath,
                        accessToken = accessToken
                    )
                    onComplete?.invoke(res)
                } catch (e: Exception) {
                    onComplete?.invoke(Result.failure(e))
                } finally {
                    _isDriveSyncing.value = false
                }
            }
        } else {
            viewModelScope.launch {
                _isDriveSyncing.value = true
                try {
                    val count = repository.syncDriveFolder(Uri.parse(connectedUri))
                    onComplete?.invoke(Result.success("$count file(s) refreshed"))
                } catch (e: Exception) {
                    onComplete?.invoke(Result.failure(e))
                } finally {
                    _isDriveSyncing.value = false
                }
            }
        }
    }

    fun resolveExistingLocalCopy(driveUriString: String): DocumentItem? {
        val localUri = preferencesManager?.driveLocalCopies?.value?.get(driveUriString) ?: return null
        val match = uiState.value.allDocuments.firstOrNull { it.uriString == localUri } ?: run {
            preferencesManager?.removeDriveLocalCopy(driveUriString)
            return null
        }
        val parsed = Uri.parse(localUri)
        if (parsed.scheme == "file") {
            val filePath = parsed.path
            if (filePath.isNullOrBlank() || !java.io.File(filePath).exists()) {
                preferencesManager?.removeDriveLocalCopy(driveUriString)
                return null
            }
        }
        return match
    }

    fun createDriveSubfolder(
        treeUri: Uri,
        parentPath: String,
        folderName: String,
        onResult: (Result<DriveFolderItem>) -> Unit
    ) {
        viewModelScope.launch {
            _isDriveSyncing.value = true
            try {
                val res = repository.createDriveSubfolder(treeUri, parentPath, folderName)
                onResult(res)
            } finally {
                _isDriveSyncing.value = false
            }
        }
    }

    fun uploadPdfToDriveFolder(
        treeUri: Uri,
        parentPath: String,
        sourcePdfUri: Uri,
        desiredFileName: String,
        onResult: (Result<DriveFileItem>) -> Unit
    ) {
        viewModelScope.launch {
            _isDriveSyncing.value = true
            try {
                val res = repository.uploadPdfToDriveFolder(treeUri, parentPath, sourcePdfUri, desiredFileName)
                onResult(res)
            } finally {
                _isDriveSyncing.value = false
            }
        }
    }

    fun importDocuments(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            val contentResolver = context.contentResolver
            for (uri in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                var fileName = "Imported_Document"
                var fileSize = 0L

                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val ext = fileName.substringAfterLast('.', "")
                val type = DocumentType.fromExtension(ext)
                repository.importDocument(
                    uriString = uri.toString(),
                    title = fileName,
                    type = type,
                    sizeBytes = fileSize
                )
            }
        }
    }

    suspend fun importSingleDocument(
        uriString: String,
        title: String,
        type: DocumentType,
        sizeBytes: Long,
        parentPath: String = ""
    ): DocumentItem {
        return repository.importDocument(uriString, title, type, sizeBytes, parentPath)
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private data class DriveDataBundle(
        val folders: List<DriveFolderItem>,
        val files: List<DriveFileItem>,
        val localCopies: Map<String, String>,
        val rootUri: String?,
        val rootName: String?
    )

    companion object {
        fun provideFactory(
            repository: DocumentRepository,
            preferencesManager: PreferencesManager? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(repository, preferencesManager) as T
                }
            }
    }
}
