package com.yomi.reader.data.local

import android.content.Context
import android.os.Environment
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.SavedDriveLink
import com.yomi.reader.ui.dashboard.components.CustomFolderStyle
import com.yomi.reader.ui.dashboard.components.FolderAppearanceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class DownloadDestinationType {
    DOCUMENTS_YOMI,
    DOWNLOADS_YOMI,
    CUSTOM_FOLDER
}

data class DownloadedFileRecord(
    val id: String,
    val title: String,
    val destinationLabel: String,
    val targetUriOrPath: String,
    val sourceUriString: String = "",
    val sizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
)

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yomi_app_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _autoScanOnLaunch = MutableStateFlow(prefs.getBoolean("auto_scan_launch", true))
    val autoScanOnLaunch: StateFlow<Boolean> = _autoScanOnLaunch.asStateFlow()

    private val _defaultGridView = MutableStateFlow(prefs.getBoolean("default_grid_view", false))
    val defaultGridView: StateFlow<Boolean> = _defaultGridView.asStateFlow()

    private val _gridColumnCount = MutableStateFlow(
        prefs.getInt("grid_column_count", 2).coerceIn(2, 4)
    )
    val gridColumnCount: StateFlow<Int> = _gridColumnCount.asStateFlow()

    private val _connectedDriveFolderUri = MutableStateFlow(prefs.getString("connected_drive_folder_uri", null))
    val connectedDriveFolderUri: StateFlow<String?> = _connectedDriveFolderUri.asStateFlow()

    private val _connectedDriveFolderName = MutableStateFlow(prefs.getString("connected_drive_folder_name", null))
    val connectedDriveFolderName: StateFlow<String?> = _connectedDriveFolderName.asStateFlow()

    private val _lastDriveSyncTime = MutableStateFlow(prefs.getLong("last_drive_sync_time", 0L))
    val lastDriveSyncTime: StateFlow<Long> = _lastDriveSyncTime.asStateFlow()

    private val _driveAutoSyncEnabled = MutableStateFlow(prefs.getBoolean("drive_auto_sync", true))
    val driveAutoSyncEnabled: StateFlow<Boolean> = _driveAutoSyncEnabled.asStateFlow()

    private val _syncedDriveUris = MutableStateFlow(
        prefs.getStringSet("synced_drive_uris", emptySet()) ?: emptySet()
    )
    private val _customFolders = MutableStateFlow(
        prefs.getStringSet("custom_user_folders", setOf("Work", "Study", "Personal", "Archive"))
            ?: setOf("Work", "Study", "Personal", "Archive")
    )
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _customFolderStyles = MutableStateFlow(loadCustomFolderStyles())
    val customFolderStyles: StateFlow<Map<String, CustomFolderStyle>> = _customFolderStyles.asStateFlow()

    private fun loadCustomFolderStyles(): Map<String, CustomFolderStyle> {
        val raw = prefs.getString("custom_folder_styles_json", null) ?: return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(raw)
            val map = mutableMapOf<String, CustomFolderStyle>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val folderName = keys.next()
                val obj = root.optJSONObject(folderName) ?: continue
                val colorKey = obj.optString("colorKey", "").ifBlank {
                    FolderAppearanceCatalog.defaultColorKeyFor(folderName)
                }
                val iconKey = obj.optString("iconKey", "").ifBlank {
                    FolderAppearanceCatalog.defaultIconKeyFor(folderName)
                }
                map[folderName] = CustomFolderStyle(colorKey = colorKey, iconKey = iconKey)
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun saveCustomFolderStyles(styles: Map<String, CustomFolderStyle>) {
        val root = org.json.JSONObject()
        styles.forEach { (name, style) ->
            root.put(
                name,
                org.json.JSONObject().apply {
                    put("colorKey", style.colorKey)
                    put("iconKey", style.iconKey)
                }
            )
        }
        prefs.edit().putString("custom_folder_styles_json", root.toString()).apply()
        _customFolderStyles.value = styles
    }

    fun addCustomFolder(name: String, colorKey: String? = null, iconKey: String? = null) {
        val clean = name.trim()
        if (clean.isNotBlank()) {
            val updated = _customFolders.value.toMutableSet().apply { add(clean) }
            prefs.edit().putStringSet("custom_user_folders", updated).apply()
            _customFolders.value = updated

            if (colorKey != null || iconKey != null) {
                val updatedStyles = _customFolderStyles.value.toMutableMap().apply {
                    put(
                        clean,
                        CustomFolderStyle(
                            colorKey = colorKey ?: FolderAppearanceCatalog.defaultColorKeyFor(clean),
                            iconKey = iconKey ?: FolderAppearanceCatalog.defaultIconKeyFor(clean)
                        )
                    )
                }
                saveCustomFolderStyles(updatedStyles)
            }
        }
    }

    fun updateCustomFolderStyle(name: String, colorKey: String, iconKey: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val updatedStyles = _customFolderStyles.value.toMutableMap().apply {
            put(clean, CustomFolderStyle(colorKey = colorKey, iconKey = iconKey))
        }
        saveCustomFolderStyles(updatedStyles)
    }

    fun removeCustomFolder(name: String) {
        val updated = _customFolders.value.toMutableSet().apply { remove(name) }
        prefs.edit().putStringSet("custom_user_folders", updated).apply()
        _customFolders.value = updated
        if (_customFolderStyles.value.containsKey(name)) {
            val updatedStyles = _customFolderStyles.value.toMutableMap().apply { remove(name) }
            saveCustomFolderStyles(updatedStyles)
        }
    }

    fun renameCustomFolder(oldName: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank() || clean == oldName) return
        val updated = _customFolders.value.toMutableSet().apply {
            remove(oldName)
            add(clean)
        }
        prefs.edit().putStringSet("custom_user_folders", updated).apply()
        _customFolders.value = updated

        val existingStyle = _customFolderStyles.value[oldName]
        if (existingStyle != null) {
            val updatedStyles = _customFolderStyles.value.toMutableMap().apply {
                remove(oldName)
                put(clean, existingStyle)
            }
            saveCustomFolderStyles(updatedStyles)
        }
    }

    private val _driveFolders = MutableStateFlow(loadDriveFolders())
    val driveFolders: StateFlow<List<com.yomi.reader.domain.model.DriveFolderItem>> = _driveFolders.asStateFlow()

    private fun loadDriveFolders(): List<com.yomi.reader.domain.model.DriveFolderItem> {
        val raw = prefs.getString("synced_drive_folders_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<com.yomi.reader.domain.model.DriveFolderItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    com.yomi.reader.domain.model.DriveFolderItem(
                        name = obj.optString("name"),
                        path = obj.optString("path"),
                        parentPath = obj.optString("parentPath"),
                        fileCount = obj.optInt("fileCount", 0),
                        uriString = obj.optString("uriString").takeIf { it.isNotEmpty() }
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveDriveFolders(folders: List<com.yomi.reader.domain.model.DriveFolderItem>) {
        val array = org.json.JSONArray()
        for (folder in folders) {
            val obj = org.json.JSONObject().apply {
                put("name", folder.name)
                put("path", folder.path)
                put("parentPath", folder.parentPath)
                put("fileCount", folder.fileCount)
                put("uriString", folder.uriString ?: "")
            }
            array.put(obj)
        }
        prefs.edit().putString("synced_drive_folders_json", array.toString()).apply()
        _driveFolders.value = folders
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setAutoScanOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean("auto_scan_launch", enabled).apply()
        _autoScanOnLaunch.value = enabled
        runCatching {
            if (enabled) {
                com.yomi.reader.data.worker.StorageScannerWorker.enqueuePeriodicScan(appContext)
            } else {
                com.yomi.reader.data.worker.StorageScannerWorker.cancelPeriodicScan(appContext)
            }
        }
    }

    fun setDefaultGridView(isGrid: Boolean) {
        prefs.edit().putBoolean("default_grid_view", isGrid).apply()
        _defaultGridView.value = isGrid
    }

    fun setGridColumnCount(columns: Int) {
        val clamped = columns.coerceIn(2, 4)
        prefs.edit().putInt("grid_column_count", clamped).apply()
        _gridColumnCount.value = clamped
    }

    fun setConnectedDriveFolder(uri: String, name: String) {
        prefs.edit()
            .putString("connected_drive_folder_uri", uri)
            .putString("connected_drive_folder_name", name)
            .apply()
        _connectedDriveFolderUri.value = uri
        _connectedDriveFolderName.value = name
    }

    private val _driveFiles = MutableStateFlow(loadDriveFiles())
    val driveFiles: StateFlow<List<DriveFileItem>> = _driveFiles.asStateFlow()

    private fun loadDriveFiles(): List<DriveFileItem> {
        val raw = prefs.getString("synced_drive_files_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<DriveFileItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = runCatching {
                    DocumentType.valueOf(obj.optString("typeName", DocumentType.UNKNOWN.name))
                }.getOrDefault(DocumentType.UNKNOWN)
                list.add(
                    DriveFileItem(
                        name = obj.optString("name"),
                        uriString = obj.optString("uriString"),
                        parentPath = obj.optString("parentPath"),
                        type = type,
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        lastModified = obj.optLong("lastModified", 0L)
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveDriveFiles(files: List<DriveFileItem>) {
        val array = org.json.JSONArray()
        for (file in files) {
            val obj = org.json.JSONObject().apply {
                put("name", file.name)
                put("uriString", file.uriString)
                put("parentPath", file.parentPath)
                put("typeName", file.type.name)
                put("sizeBytes", file.sizeBytes)
                put("lastModified", file.lastModified)
            }
            array.put(obj)
        }
        prefs.edit().putString("synced_drive_files_json", array.toString()).apply()
        _driveFiles.value = files
    }

    private val _driveLocalCopies = MutableStateFlow(loadDriveLocalCopies())
    val driveLocalCopies: StateFlow<Map<String, String>> = _driveLocalCopies.asStateFlow()

    private val _recentDriveLinks = MutableStateFlow(loadRecentDriveLinks())
    val recentDriveLinks: StateFlow<List<SavedDriveLink>> = _recentDriveLinks.asStateFlow()

    private fun loadRecentDriveLinks(): List<SavedDriveLink> {
        val raw = prefs.getString("recent_drive_links_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<SavedDriveLink>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("folderId")
                val url = obj.optString("url")
                val name = obj.optString("name")
                if (id.isNotBlank() && url.isNotBlank()) {
                    list.add(
                        SavedDriveLink(
                            folderId = id,
                            url = url,
                            name = name.ifBlank { "Shared Drive Folder" },
                            lastOpenedAt = obj.optLong("lastOpenedAt", 0L)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun addRecentDriveLink(link: SavedDriveLink) {
        val updated = (_recentDriveLinks.value.filterNot { it.folderId == link.folderId } + link)
            .sortedByDescending { it.lastOpenedAt }
            .take(8)
        saveRecentDriveLinks(updated)
    }

    fun removeRecentDriveLink(folderId: String) {
        val updated = _recentDriveLinks.value.filterNot { it.folderId == folderId }
        saveRecentDriveLinks(updated)
    }

    private fun saveRecentDriveLinks(links: List<SavedDriveLink>) {
        val array = org.json.JSONArray()
        links.forEach { link ->
            array.put(
                org.json.JSONObject().apply {
                    put("folderId", link.folderId)
                    put("url", link.url)
                    put("name", link.name)
                    put("lastOpenedAt", link.lastOpenedAt)
                }
            )
        }
        prefs.edit().putString("recent_drive_links_json", array.toString()).apply()
        _recentDriveLinks.value = links
    }

    private fun loadDriveLocalCopies(): Map<String, String> {
        val raw = prefs.getString("drive_local_copies_json", null) ?: return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            val map = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key)
                if (key.isNotBlank() && value.isNotBlank()) {
                    map[key] = value
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun setDriveLocalCopy(driveUriString: String, localUriString: String) {
        val updated = _driveLocalCopies.value.toMutableMap().apply {
            put(driveUriString, localUriString)
        }
        val obj = org.json.JSONObject()
        updated.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("drive_local_copies_json", obj.toString()).apply()
        _driveLocalCopies.value = updated
    }

    fun removeDriveLocalCopy(driveUriString: String) {
        if (!_driveLocalCopies.value.containsKey(driveUriString)) return
        val updated = _driveLocalCopies.value.toMutableMap().apply {
            remove(driveUriString)
        }
        val obj = org.json.JSONObject()
        updated.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("drive_local_copies_json", obj.toString()).apply()
        _driveLocalCopies.value = updated
    }

    fun clearConnectedDriveFolder() {
        prefs.edit()
            .remove("connected_drive_folder_uri")
            .remove("connected_drive_folder_name")
            .remove("synced_drive_uris")
            .remove("synced_drive_folders_json")
            .remove("synced_drive_files_json")
            .apply()
        _connectedDriveFolderUri.value = null
        _connectedDriveFolderName.value = null
        _syncedDriveUris.value = emptySet()
        _driveFolders.value = emptyList()
        _driveFiles.value = emptyList()
    }

    fun setLastDriveSyncTime(time: Long) {
        prefs.edit().putLong("last_drive_sync_time", time).apply()
        _lastDriveSyncTime.value = time
    }

    fun setDriveAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("drive_auto_sync", enabled).apply()
        _driveAutoSyncEnabled.value = enabled
    }

    fun addSyncedDriveUris(uris: Set<String>) {
        val updated = _syncedDriveUris.value.toMutableSet().apply { addAll(uris) }
        prefs.edit().putStringSet("synced_drive_uris", updated).apply()
        _syncedDriveUris.value = updated
    }

    fun isDriveSyncedUri(uriString: String): Boolean {
        if (uriString.contains("com.google.android.apps.docs") ||
            uriString.contains("google.drive") ||
            uriString.contains("drive.google.com")
        ) {
            return true
        }
        return _syncedDriveUris.value.contains(uriString)
    }

    fun getLastReadPage(docId: String): Int {
        return prefs.getInt("last_page_${docId.hashCode()}", 0)
    }

    fun getLastReadPage(document: DocumentItem): Int {
        val uriKey = document.uriString.trim()
        if (uriKey.isNotEmpty()) {
            val byUri = prefs.getInt("last_page_${uriKey.hashCode()}", -1)
            if (byUri >= 0) return byUri
        }
        if (document.id > 0L) {
            return prefs.getInt("last_page_${document.id.toString().hashCode()}", 0)
        }
        return 0
    }

    fun setLastReadPage(docId: String, page: Int) {
        prefs.edit().putInt("last_page_${docId.hashCode()}", page).apply()
    }

    fun setLastReadPage(document: DocumentItem, page: Int) {
        val editor = prefs.edit()
        val uriKey = document.uriString.trim()
        if (uriKey.isNotEmpty()) {
            editor.putInt("last_page_${uriKey.hashCode()}", page)
        }
        if (document.id > 0L) {
            editor.putInt("last_page_${document.id.toString().hashCode()}", page)
        }
        editor.apply()
    }

    private fun calculateDirectorySize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.listFiles()?.forEach { child ->
            total += calculateDirectorySize(child)
        }
        return total
    }

    fun getCacheSizeMB(context: Context): String {
        val sizeBytes = calculateDirectorySize(context.cacheDir)
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        return String.format("%.1f MB", mb)
    }

    fun getManagedStorageSummary(context: Context): Pair<Int, String> {
        val docsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val managedFolders = listOf("Converted_PDFs", "Imported_PDFs", "Images_to_PDF", "Split_PDFs")
        var fileCount = 0
        var totalBytes = 0L
        for (folderName in managedFolders) {
            val dir = java.io.File(docsRoot, folderName)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0L) {
                        fileCount++
                        totalBytes += file.length()
                    }
                }
            }
        }
        val formatted = when {
            totalBytes >= 1024L * 1024L -> String.format("%.1f MB", totalBytes.toDouble() / (1024 * 1024))
            totalBytes >= 1024L -> "${totalBytes / 1024} KB"
            else -> "0.0 MB"
        }
        return fileCount to formatted
    }

    fun clearCache(context: Context): Long {
        val cacheDir = context.cacheDir
        val clearedBytes = calculateDirectorySize(cacheDir)
        cacheDir.listFiles()?.forEach { file ->
            file.deleteRecursively()
        }
        return clearedBytes
    }

    // --- Download Management ---

    private val _downloadDestinationType = MutableStateFlow(
        runCatching {
            DownloadDestinationType.valueOf(
                prefs.getString("download_destination_type", DownloadDestinationType.DOCUMENTS_YOMI.name)
                    ?: DownloadDestinationType.DOCUMENTS_YOMI.name
            )
        }.getOrDefault(DownloadDestinationType.DOCUMENTS_YOMI)
    )
    val downloadDestinationType: StateFlow<DownloadDestinationType> = _downloadDestinationType.asStateFlow()

    private val _downloadCustomTreeUri = MutableStateFlow(
        prefs.getString("download_custom_tree_uri", null)
    )
    val downloadCustomTreeUri: StateFlow<String?> = _downloadCustomTreeUri.asStateFlow()

    private val _downloadCustomFolderName = MutableStateFlow(
        prefs.getString("download_custom_folder_name", null)
    )
    val downloadCustomFolderName: StateFlow<String?> = _downloadCustomFolderName.asStateFlow()

    private val _downloadedFiles = MutableStateFlow(loadDownloadedFiles())
    val downloadedFiles: StateFlow<List<DownloadedFileRecord>> = _downloadedFiles.asStateFlow()

    fun getDownloadLocationDisplayLabel(): String {
        return when (_downloadDestinationType.value) {
            DownloadDestinationType.DOCUMENTS_YOMI -> "Documents/Yomi (Default)"
            DownloadDestinationType.DOWNLOADS_YOMI -> "Downloads/Yomi"
            DownloadDestinationType.CUSTOM_FOLDER -> {
                val custom = _downloadCustomFolderName.value?.takeIf { it.isNotBlank() }
                if (custom != null) "Custom: $custom" else "Documents/Yomi (Default)"
            }
        }
    }

    fun setBuiltInDownloadDestination(type: DownloadDestinationType) {
        val safeType = if (type == DownloadDestinationType.CUSTOM_FOLDER) {
            DownloadDestinationType.DOCUMENTS_YOMI
        } else {
            type
        }
        prefs.edit()
            .putString("download_destination_type", safeType.name)
            .apply()
        _downloadDestinationType.value = safeType
    }

    fun setCustomDownloadDestination(treeUriString: String, folderName: String) {
        val cleanName = folderName.trim().ifBlank { "Selected Folder" }
        prefs.edit()
            .putString("download_destination_type", DownloadDestinationType.CUSTOM_FOLDER.name)
            .putString("download_custom_tree_uri", treeUriString)
            .putString("download_custom_folder_name", cleanName)
            .apply()
        _downloadDestinationType.value = DownloadDestinationType.CUSTOM_FOLDER
        _downloadCustomTreeUri.value = treeUriString
        _downloadCustomFolderName.value = cleanName
    }

    private fun loadDownloadedFiles(): List<DownloadedFileRecord> {
        val raw = prefs.getString("downloaded_files_history_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<DownloadedFileRecord>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title")
                val dest = obj.optString("destinationLabel")
                val target = obj.optString("targetUriOrPath")
                if (id.isNotBlank() && title.isNotBlank()) {
                    list.add(
                        DownloadedFileRecord(
                            id = id,
                            title = title,
                            destinationLabel = dest.ifBlank { "Documents/Yomi" },
                            targetUriOrPath = target,
                            sourceUriString = obj.optString("sourceUriString", ""),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            downloadedAt = obj.optLong("downloadedAt", 0L)
                        )
                    )
                }
            }
            list.sortedByDescending { it.downloadedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveDownloadedFiles(records: List<DownloadedFileRecord>) {
        val array = org.json.JSONArray()
        records.forEach { rec ->
            array.put(
                org.json.JSONObject().apply {
                    put("id", rec.id)
                    put("title", rec.title)
                    put("destinationLabel", rec.destinationLabel)
                    put("targetUriOrPath", rec.targetUriOrPath)
                    put("sourceUriString", rec.sourceUriString)
                    put("sizeBytes", rec.sizeBytes)
                    put("downloadedAt", rec.downloadedAt)
                }
            )
        }
        prefs.edit().putString("downloaded_files_history_json", array.toString()).apply()
        _downloadedFiles.value = records
    }

    fun recordDownloadedFile(record: DownloadedFileRecord) {
        val deduped = _downloadedFiles.value.filterNot {
            it.id == record.id ||
                (it.targetUriOrPath.isNotBlank() && it.targetUriOrPath == record.targetUriOrPath && it.title == record.title)
        }
        val updated = (listOf(record) + deduped).take(100)
        saveDownloadedFiles(updated)
    }

    fun removeDownloadedFile(id: String) {
        val updated = _downloadedFiles.value.filterNot { it.id == id }
        saveDownloadedFiles(updated)
    }

    fun clearDownloadedFilesHistory() {
        saveDownloadedFiles(emptyList())
    }

    private val _recentPdfs = MutableStateFlow(loadRecentPdfs())
    val recentPdfs: StateFlow<List<DocumentItem>> = _recentPdfs.asStateFlow()

    private fun loadRecentPdfs(): List<DocumentItem> {
        val raw = prefs.getString("recent_opened_pdfs_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            val list = mutableListOf<DocumentItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uri = obj.optString("uriString", "")
                val title = obj.optString("title", "")
                if (uri.isNotBlank() && title.isNotBlank()) {
                    list.add(
                        DocumentItem(
                            id = obj.optLong("id", 0L),
                            uriString = uri,
                            title = title,
                            type = DocumentType.PDF,
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            lastModified = obj.optLong("lastModified", 0L),
                            isFavorite = obj.optBoolean("isFavorite", false),
                            lastOpened = obj.optLong("lastOpened", 0L).takeIf { it > 0L },
                            parentPath = obj.optString("parentPath", "")
                        )
                    )
                }
            }
            list.sortedByDescending { it.lastOpened ?: it.lastModified }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentPdfs(items: List<DocumentItem>) {
        val array = org.json.JSONArray()
        items.forEach { doc ->
            array.put(
                org.json.JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("uriString", doc.uriString)
                    put("sizeBytes", doc.sizeBytes)
                    put("lastModified", doc.lastModified)
                    put("lastOpened", doc.lastOpened ?: 0L)
                    put("parentPath", doc.parentPath)
                    put("isFavorite", doc.isFavorite)
                }
            )
        }
        prefs.edit().putString("recent_opened_pdfs_json", array.toString()).apply()
        _recentPdfs.value = items
    }

    fun recordRecentPdf(document: DocumentItem) {
        if (document.type != DocumentType.PDF || document.uriString.isBlank()) return
        val now = System.currentTimeMillis()
        val entry = document.copy(
            lastOpened = now
        )
        val remaining = _recentPdfs.value.filterNot {
            it.uriString == document.uriString ||
                (document.id != 0L && it.id == document.id)
        }
        val updated = (listOf(entry) + remaining).take(MAX_RECENT_PDFS)
        saveRecentPdfs(updated)
    }

    companion object {
        private const val MAX_RECENT_PDFS = 20
    }
}

object RecentPdfHistoryHelper {
    /**
     * Merges recorded PDF viewer entries with database recent documents so converted PDFs,
     * Converted Files entries (id == 0), and library PDFs all appear once in recency order.
     */
    fun mergeRecentPdfDocuments(
        recordedPdfs: List<DocumentItem>,
        dbRecentDocs: List<DocumentItem>,
        limit: Int = 20
    ): List<DocumentItem> {
        val mergedByUri = linkedMapOf<String, DocumentItem>()
        val candidates = (recordedPdfs + dbRecentDocs)
            .filter { it.type == DocumentType.PDF && it.uriString.isNotBlank() }

        for (doc in candidates) {
            val existing = mergedByUri[doc.uriString]
            if (existing == null) {
                mergedByUri[doc.uriString] = doc
            } else {
                val existingTime = existing.lastOpened ?: existing.lastModified
                val candidateTime = doc.lastOpened ?: doc.lastModified
                val preferredId = if (existing.id > 0L) existing.id else doc.id
                val preferredLastOpened = maxOf(existing.lastOpened ?: 0L, doc.lastOpened ?: 0L)
                    .takeIf { it > 0L }
                mergedByUri[doc.uriString] = (if (candidateTime > existingTime) doc else existing).copy(
                    id = preferredId,
                    lastOpened = preferredLastOpened,
                    isFavorite = existing.isFavorite || doc.isFavorite
                )
            }
        }

        return mergedByUri.values
            .sortedByDescending { it.lastOpened ?: it.lastModified }
            .take(limit)
    }
}
