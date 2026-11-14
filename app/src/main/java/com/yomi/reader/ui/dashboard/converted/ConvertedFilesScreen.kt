package com.yomi.reader.ui.dashboard.converted

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.yomi.reader.ui.dashboard.components.PdfMiniaturePageTile
import com.yomi.reader.ui.theme.yomiPaperBackground
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.yomi.reader.data.local.PreferencesManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.ui.components.YomiExpressiveLoadingIndicator
import com.yomi.reader.ui.theme.yomiExcelBadgeColors
import com.yomi.reader.ui.theme.yomiPdfBadgeColors
import com.yomi.reader.ui.theme.yomiWordBadgeColors
import com.yomi.reader.util.ConvertedFileItem
import com.yomi.reader.util.DocumentConverter
import com.yomi.reader.util.PublicDocumentsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class DownloadsSortOrder(val label: String) {
    NEWEST("Newest first"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Size (Largest)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertedFilesScreen(
    onBack: () -> Unit,
    onOpenDocument: (DocumentItem) -> Unit,
    onOpenQuickSuite: () -> Unit = {},
    preferencesManager: PreferencesManager? = null,
    refreshTrigger: Int = 0,
    initialIsGridView: Boolean = false,
    gridColumnCount: Int = 2,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePrefs = remember(context, preferencesManager) {
        preferencesManager ?: PreferencesManager(context)
    }

    val coroutineScope = rememberCoroutineScope()
    var convertedFiles by remember { mutableStateOf<List<ConvertedFileItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(true) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    BackHandler {
        if (selectedPaths.isNotEmpty()) {
            selectedPaths = emptySet()
        } else {
            onBack()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember(initialIsGridView) { mutableStateOf(initialIsGridView) }
    var sortOrder by remember { mutableStateOf(DownloadsSortOrder.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTrigger) {
        val files = withContext(Dispatchers.IO) {
            DocumentConverter.getAllConvertedFiles(context)
        }
        convertedFiles = files
        isLoadingFiles = false
    }

    fun refreshFiles() {
        coroutineScope.launch {
            val files = withContext(Dispatchers.IO) {
                DocumentConverter.getAllConvertedFiles(context)
            }
            convertedFiles = files
        }
    }

    val displayedFiles = remember(convertedFiles, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            convertedFiles
        } else {
            val q = searchQuery.trim()
            convertedFiles.filter { it.name.contains(q, ignoreCase = true) }
        }
        when (sortOrder) {
            DownloadsSortOrder.NEWEST -> filtered.sortedByDescending { it.lastModified }
            DownloadsSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            DownloadsSortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    fun openItem(item: ConvertedFileItem) {
        val docItem = DocumentItem(
            id = (item.file.absolutePath.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L),
            uriString = item.uriString,
            title = item.name,
            type = item.type,
            sizeBytes = item.sizeBytes,
            lastModified = item.lastModified,
            parentPath = "Converted"
        )
        onOpenDocument(docItem)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (selectedPaths.isNotEmpty()) {
                val selectedItems = displayedFiles.filter { it.file.absolutePath in selectedPaths }
                val isAllSelected = displayedFiles.isNotEmpty() && displayedFiles.all { it.file.absolutePath in selectedPaths }
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedPaths = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Exit selection mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedPaths = if (isAllSelected) {
                                    emptySet()
                                } else {
                                    displayedFiles.map { it.file.absolutePath }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedItems.isNotEmpty()) {
                                    coroutineScope.launch {
                                        var successCount = 0
                                        var lastDest = activePrefs.getDownloadLocationDisplayLabel()
                                        selectedItems.forEach { item ->
                                            PublicDocumentsExporter.downloadFileToDevice(
                                                context = context,
                                                sourceFile = item.file,
                                                preferencesManager = activePrefs,
                                                displayName = item.name,
                                                sourceUriString = item.uriString
                                            ).onSuccess { res ->
                                                successCount++
                                                lastDest = res.destinationLabel
                                            }
                                        }
                                        selectedPaths = emptySet()
                                        if (successCount > 0) {
                                            Toast.makeText(
                                                context,
                                                "Downloaded $successCount file(s) to $lastDest",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Download selected",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                shareMultipleFiles(context, selectedItems.map { it.file })
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share selected",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                // Header row aligned with FolderDetailTopBar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Converted Files",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${displayedFiles.size} ${if (displayedFiles.size == 1) "file" else "files"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Sort control
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DownloadsSortOrder.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (option == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == sortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = if (option == sortOrder) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        sortOrder = option
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Grid / List view toggle
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Quick Import action
                    IconButton(
                        onClick = onOpenQuickSuite,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import or convert document",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (displayedFiles.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedPaths = setOf(displayedFiles.first().file.absolutePath)
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = "Select files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // In-page Search Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search converted files...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        bottomBar = bottomBar,
        modifier = modifier
            .fillMaxSize()
            .yomiPaperBackground()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoadingFiles) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    YomiExpressiveLoadingIndicator(size = 44.dp)
                }
            } else if (displayedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No Matching Files" else "No Converted Files Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No files match \"$searchQuery\"."
                            } else {
                                "Converted Office documents, Split PDFs, and Images-to-PDF files appear here for quick access."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        if (searchQuery.isNotBlank()) {
                            Button(onClick = { searchQuery = "" }, shape = RoundedCornerShape(20.dp)) {
                                Text("Clear Search")
                            }
                        } else {
                            Button(onClick = onOpenQuickSuite, shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import & Convert")
                            }
                        }
                    }
                }
            } else if (isGridView) {
                val safeColumns = gridColumnCount.coerceIn(2, 4)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(safeColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (safeColumns >= 3) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (safeColumns >= 3) 8.dp else 12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(displayedFiles, key = { it.file.absolutePath }) { item ->
                        val itemPath = item.file.absolutePath
                        val isSelected = itemPath in selectedPaths
                        CompactConvertedGridCard(
                            item = item,
                            isSelectionMode = selectedPaths.isNotEmpty(),
                            isSelected = isSelected,
                            onOpen = {
                                if (selectedPaths.isNotEmpty()) {
                                    selectedPaths = if (isSelected) selectedPaths - itemPath else selectedPaths + itemPath
                                } else {
                                    openItem(item)
                                }
                            },
                            onLongClick = { selectedPaths = selectedPaths + itemPath },
                            onSelectOption = { selectedPaths = selectedPaths + itemPath },
                            onDownloadToDevice = {
                                coroutineScope.launch {
                                    val result = PublicDocumentsExporter.downloadFileToDevice(
                                        context = context,
                                        sourceFile = item.file,
                                        preferencesManager = activePrefs,
                                        displayName = item.name,
                                        sourceUriString = item.uriString
                                    )
                                    result.onSuccess { res ->
                                        Toast.makeText(
                                            context,
                                            "Downloaded ${res.fileName} to ${res.destinationLabel}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onShare = { shareFile(context, item.file) },
                            onDelete = { fileToDelete = item.file }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedFiles, key = { it.file.absolutePath }) { item ->
                        val itemPath = item.file.absolutePath
                        val isSelected = itemPath in selectedPaths
                        CompactConvertedListCard(
                            item = item,
                            isSelectionMode = selectedPaths.isNotEmpty(),
                            isSelected = isSelected,
                            onOpen = {
                                if (selectedPaths.isNotEmpty()) {
                                    selectedPaths = if (isSelected) selectedPaths - itemPath else selectedPaths + itemPath
                                } else {
                                    openItem(item)
                                }
                            },
                            onLongClick = { selectedPaths = selectedPaths + itemPath },
                            onSelectOption = { selectedPaths = selectedPaths + itemPath },
                            onDownloadToDevice = {
                                coroutineScope.launch {
                                    val result = PublicDocumentsExporter.downloadFileToDevice(
                                        context = context,
                                        sourceFile = item.file,
                                        preferencesManager = activePrefs,
                                        displayName = item.name,
                                        sourceUriString = item.uriString
                                    )
                                    result.onSuccess { res ->
                                        Toast.makeText(
                                            context,
                                            "Downloaded ${res.fileName} to ${res.destinationLabel}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onShare = { shareFile(context, item.file) },
                            onDelete = { fileToDelete = item.file }
                        )
                    }
                }
            }
        }
    }

    if (showBatchDeleteDialog && selectedPaths.isNotEmpty()) {
        val count = selectedPaths.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete $count Converted File(s)?") },
            text = { Text("Are you sure you want to delete the $count selected file(s)? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        var deletedCount = 0
                        displayedFiles
                            .filter { it.file.absolutePath in selectedPaths }
                            .forEach { item ->
                                if (DocumentConverter.deleteConvertedFile(item.file)) {
                                    deletedCount++
                                }
                            }
                        selectedPaths = emptySet()
                        showBatchDeleteDialog = false
                        refreshFiles()
                        Toast.makeText(
                            context,
                            "Deleted $deletedCount file(s)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (fileToDelete != null) {
        val target = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Converted File?") },
            text = { Text("Are you sure you want to delete \"${target.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = DocumentConverter.deleteConvertedFile(target)
                        if (deleted) {
                            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                            refreshFiles()
                        } else {
                            Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                        }
                        fileToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactConvertedListCard(
    item: ConvertedFileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onSelectOption: () -> Unit,
    onDownloadToDevice: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val (badgeBg, badgeFg, shortLabel) = when (item.type) {
        DocumentType.PDF -> yomiPdfBadgeColors().let { (bg, fg) -> Triple(bg, fg, "PDF") }
        DocumentType.WORD -> yomiWordBadgeColors().let { (bg, fg) -> Triple(bg, fg, "DOC") }
        else -> yomiExcelBadgeColors().let { (bg, fg) -> Triple(bg, fg, "TXT") }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.type == DocumentType.PDF) {
                PdfMiniaturePageTile(
                    uriString = item.uriString,
                    lastModified = item.lastModified,
                    isPdf = true,
                    contentDescription = item.name,
                    fallbackLabel = shortLabel,
                    fallbackBadgeBg = badgeBg,
                    fallbackBadgeFg = badgeFg,
                    cornerRadius = 8.dp,
                    shadowElevation = 1.dp,
                    badgeFontSize = 9.sp,
                    modifier = Modifier
                        .width(42.dp)
                        .height(52.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortLabel,
                        color = badgeFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.formattedSize} • ${item.formattedDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            } else {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "File options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ConvertedFileMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        onOpen = onOpen,
                        onSelectOption = onSelectOption,
                        onDownloadToDevice = onDownloadToDevice,
                        onShare = onShare,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactConvertedGridCard(
    item: ConvertedFileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onSelectOption: () -> Unit,
    onDownloadToDevice: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val (badgeBg, badgeFg, shortLabel) = when (item.type) {
        DocumentType.PDF -> yomiPdfBadgeColors().let { (bg, fg) -> Triple(bg, fg, "PDF") }
        DocumentType.WORD -> yomiWordBadgeColors().let { (bg, fg) -> Triple(bg, fg, "DOC") }
        else -> yomiExcelBadgeColors().let { (bg, fg) -> Triple(bg, fg, "TXT") }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = shortLabel,
                        color = badgeFg,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "File options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        ConvertedFileMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            onOpen = onOpen,
                            onSelectOption = onSelectOption,
                            onDownloadToDevice = onDownloadToDevice,
                            onShare = onShare,
                            onDelete = onDelete
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${item.formattedSize} • ${item.formattedDate}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.type == DocumentType.PDF) {
                Spacer(modifier = Modifier.height(8.dp))
                PdfMiniaturePageTile(
                    uriString = item.uriString,
                    lastModified = item.lastModified,
                    isPdf = true,
                    contentDescription = item.name,
                    fallbackLabel = shortLabel,
                    fallbackBadgeBg = badgeBg,
                    fallbackBadgeFg = badgeFg,
                    cornerRadius = 8.dp,
                    shadowElevation = 1.dp,
                    badgeFontSize = 9.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.95f)
                )
            }
        }
    }
}

@Composable
private fun ConvertedFileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelectOption: () -> Unit,
    onDownloadToDevice: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Open") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpen()
            }
        )
        DropdownMenuItem(
            text = { Text("Select") },
            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
            onClick = {
                onDismiss()
                onSelectOption()
            }
        )
        DropdownMenuItem(
            text = { Text("Download to Device") },
            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
            onClick = {
                onDismiss()
                onDownloadToDevice()
            }
        )
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when (file.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                else -> "text/plain"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMultipleFiles(context: Context, files: List<File>) {
    if (files.isEmpty()) return
    if (files.size == 1) {
        shareFile(context, files.first())
        return
    }
    try {
        val uris = ArrayList<Uri>(
            files.map { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${files.size} files"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share files: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
