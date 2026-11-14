package com.yomi.reader.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.PictureAsPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import com.yomi.reader.ui.theme.DocsAiPillMint
import com.yomi.reader.ui.theme.DocsFabLime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import android.provider.OpenableColumns
import com.yomi.reader.domain.model.DocumentType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import com.yomi.reader.ui.components.YomiExpressiveLoadingIndicator
import com.yomi.reader.ui.dashboard.components.CustomFoldersSection
import com.yomi.reader.ui.dashboard.components.FolderGridCard
import com.yomi.reader.ui.dashboard.components.ImagesToPdfDialog
import com.yomi.reader.ui.dashboard.components.SplitPdfDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.auth.GoogleUser
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.dashboard.components.CenterFabBottomBar
import com.yomi.reader.ui.dashboard.components.DashboardTopBar
import com.yomi.reader.ui.dashboard.components.DocumentCard
import com.yomi.reader.ui.dashboard.components.DocumentGridCard
import com.yomi.reader.ui.dashboard.components.FolderCard
import com.yomi.reader.ui.dashboard.components.FolderDetailTopBar
import com.yomi.reader.ui.dashboard.components.HomeUtilityBar
import com.yomi.reader.ui.dashboard.components.RecentDocumentsSection
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Brush
import com.yomi.reader.ui.dashboard.components.CustomizeFolderStyleDialog
import com.yomi.reader.ui.dashboard.components.FolderAppearanceCatalog
import com.yomi.reader.ui.theme.YomiHeroEndLight
import com.yomi.reader.ui.theme.YomiHeroStartLight
import com.yomi.reader.ui.theme.yomiPaperBackground
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.yomi.reader.data.local.PreferencesManager
import java.io.File
import java.io.FileOutputStream

import com.yomi.reader.ui.dashboard.components.AddFilesToFolderDialog
import com.yomi.reader.ui.dashboard.components.BatchDeleteDocumentsDialog
import com.yomi.reader.ui.dashboard.components.ConversionProgressDialog
import com.yomi.reader.ui.dashboard.components.CreateCustomFolderDialog
import com.yomi.reader.ui.dashboard.components.DuplicateImportDialog
import com.yomi.reader.ui.dashboard.components.ImportDocumentChooserDialog
import com.yomi.reader.ui.dashboard.components.MoveToFolderDialog
import com.yomi.reader.ui.dashboard.components.RemoveCustomFolderDialog
import com.yomi.reader.ui.dashboard.components.RenameCustomFolderDialog
import com.yomi.reader.ui.dashboard.components.RenameDocumentDialog
import com.yomi.reader.ui.dashboard.components.UnsupportedFormatDialog
import com.yomi.reader.ui.dashboard.converted.ConvertedFilesScreen
import com.yomi.reader.ui.dashboard.components.CategoryViewHeader
import com.yomi.reader.ui.dashboard.components.ContinueReadingHeroCard
import com.yomi.reader.ui.dashboard.components.DriveWorkspaceCard
import com.yomi.reader.ui.dashboard.components.EmptyDocumentsState
import com.yomi.reader.ui.dashboard.components.EmptyFolderDetailState
import com.yomi.reader.data.remote.GoogleDriveSharedLinkClient
import com.yomi.reader.data.remote.RestrictedDriveFolderException
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.ui.dashboard.components.AddToDriveFolderDialog
import com.yomi.reader.ui.dashboard.components.CreateDriveSubfolderDialog
import com.yomi.reader.ui.dashboard.components.DownloadedFilesBottomSheet
import com.yomi.reader.ui.dashboard.components.DriveFileCard
import com.yomi.reader.ui.dashboard.components.DriveFileGridCard
import com.yomi.reader.ui.dashboard.components.DriveSignInRequiredDialog
import com.yomi.reader.ui.dashboard.components.HomeEmptyLibraryCard
import com.yomi.reader.ui.dashboard.components.PasteDriveFolderLinkDialog
import com.yomi.reader.ui.dashboard.components.UnlinkDriveFolderDialog
import com.yomi.reader.ui.dashboard.components.UploadYomiPdfToDriveDialog
import com.yomi.reader.ui.dashboard.components.YomiBottomNavBar
import com.yomi.reader.ui.dashboard.components.SelectionTopBar
import com.yomi.reader.ui.dashboard.components.YomiQuickActionsBottomSheet
import com.yomi.reader.util.DocumentConverter
import com.yomi.reader.util.PublicDocumentsExporter
import com.yomi.reader.util.DriveFolderSafetyHelper
import com.yomi.reader.util.PdfToolsHelper
import com.yomi.reader.util.ShareUtils

private data class PendingDuplicateImport(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val isPdf: Boolean,
    val isOffice: Boolean,
    val existingDoc: DocumentItem,
    val suggestedBase: String,
    val suggestedTitle: String,
    val remainingUris: List<Uri>,
    val totalSelectedCount: Int,
    val knownDocs: MutableList<DocumentItem>,
    val lastImportedDocSoFar: DocumentItem?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    preferencesManager: PreferencesManager,
    currentUser: GoogleUser? = null,
    pendingExternalUris: List<Uri> = emptyList(),
    onExternalUrisConsumed: () -> Unit = {},
    onOpenDocument: (DocumentItem) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedBottomNav by rememberSaveable { mutableIntStateOf(0) } // 0 = Home, 1 = Folders, 2 = Favorites, 3 = History
    var showImportChooserDialog by remember { mutableStateOf(false) }
    var showQuickActionsSheet by remember { mutableStateOf(false) }
    var showDownloadsScreen by rememberSaveable { mutableStateOf(false) }
    var showDownloadedFilesSheet by remember { mutableStateOf(false) }
    val downloadedFiles by preferencesManager.downloadedFiles.collectAsState()
    var convertedRefreshTrigger by remember { mutableIntStateOf(0) }
    var documentToMove by remember { mutableStateOf<DocumentItem?>(null) }
    var documentToRename by remember { mutableStateOf<DocumentItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var selectedDocIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showAddFilesToFolderDialog by remember { mutableStateOf(false) }
    var showDriveAddActionDialog by remember { mutableStateOf(false) }
    var showCreateDriveSubfolderDialog by remember { mutableStateOf(false) }
    var showUploadYomiPdfToDriveDialog by remember { mutableStateOf(false) }
    var showPasteDriveLinkDialog by remember { mutableStateOf(false) }
    var showUnlinkDriveConfirmDialog by remember { mutableStateOf(false) }
    var showDriveSignInRequiredDialog by remember { mutableStateOf(false) }
    var isOpeningDriveLink by remember { mutableStateOf(false) }
    var driveLinkErrorMessage by remember { mutableStateOf<String?>(null) }
    var driveLinkRestrictedFallbackUrl by remember { mutableStateOf<String?>(null) }
    val recentDriveLinks by preferencesManager.recentDriveLinks.collectAsState()
    var driveOperationMessage by remember { mutableStateOf<String?>(null) }
    var customFolderToRename by remember { mutableStateOf<String?>(null) }
    var customFolderToConfirmDelete by remember { mutableStateOf<String?>(null) }
    var customFolderToCustomize by remember { mutableStateOf<String?>(null) }
    val gridColumnCount by preferencesManager.gridColumnCount.collectAsState()
    val openedCustomFolderAppearance = uiState.selectedCustomFolder?.let {
        FolderAppearanceCatalog.resolve(it, uiState.customFolderStyles)
    }
    var unsupportedFileName by remember { mutableStateOf<String?>(null) }
    var convertingDocTitle by remember { mutableStateOf<String?>(null) }
    var conversionProgress by remember { mutableIntStateOf(0) }
    var pendingDuplicateImport by remember { mutableStateOf<PendingDuplicateImport?>(null) }
    var selectedImagesForPdf by remember { mutableStateOf<List<Uri>?>(null) }
    var showSplitPdfDialog by remember { mutableStateOf(false) }
    var splitExternalPdfUri by remember { mutableStateOf<Uri?>(null) }
    var splitExternalPdfTitle by remember { mutableStateOf<String?>(null) }
    var launchImportPickerRequested by remember { mutableStateOf(false) }
    val pageCounts = remember { mutableStateMapOf<Long, Int>() }
    val tabListStates = remember { List(4) { LazyListState() } }
    val tabGridStates = remember { List(4) { LazyGridState() } }

    LaunchedEffect(uiState.documents, uiState.recentDocuments) {
        val pdfDocs = (uiState.recentDocuments + uiState.documents)
            .distinctBy { it.id }
            .filter { it.type == DocumentType.PDF && !pageCounts.containsKey(it.id) }
            .take(30)
        if (pdfDocs.isNotEmpty()) {
            val computed = withContext(Dispatchers.IO) {
                val results = HashMap<Long, Int>(pdfDocs.size)
                for (doc in pdfDocs) {
                    val count = runCatching {
                        val uri = Uri.parse(doc.uriString)
                        val pfd: ParcelFileDescriptor? = if (uri.scheme == "file") {
                            val path = uri.path
                            if (path != null) ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) else null
                        } else {
                            context.contentResolver.openFileDescriptor(uri, "r")
                        }
                        pfd?.use { fd ->
                            PdfRenderer(fd).use { renderer -> renderer.pageCount }
                        }
                    }.getOrNull()
                    if (count != null && count > 0) {
                        results[doc.id] = count
                    }
                }
                results
            }
            if (computed.isNotEmpty()) {
                pageCounts.putAll(computed)
            }
        }
    }

    val isInsideCustomFolder = selectedBottomNav == 1 && uiState.selectedCustomFolder != null
    val isInsideDriveFolder = selectedBottomNav == 1 && uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE
    val isInsideFolder = isInsideCustomFolder || isInsideDriveFolder

    fun navigateToRootHome() {
        selectedBottomNav = 0
        showDownloadsScreen = false
        selectedDocIds = emptySet()
        viewModel.resetToRootHome()
    }

    fun requireSignedInForDrive(onAllowed: () -> Unit) {
        if (DriveAccessPolicy.canUseDriveWorkspace(currentUser != null)) {
            onAllowed()
        } else {
            showDriveSignInRequiredDialog = true
        }
    }

    fun openGoogleDriveFolder() {
        requireSignedInForDrive {
            selectedDocIds = emptySet()
            showDownloadsScreen = false
            viewModel.onSearchQueryChange("")
            viewModel.setFavoritesOnly(false)
            viewModel.selectCustomFolder(null)
            viewModel.onCategorySelect(DocumentCategory.GOOGLE_DRIVE)
            selectedBottomNav = 1
        }
    }

    LaunchedEffect(currentUser, uiState.selectedCategory) {
        if (DriveAccessPolicy.shouldExitDriveCategory(currentUser != null, uiState.selectedCategory)) {
            viewModel.onCategorySelect(DocumentCategory.ALL)
        }
    }

    LaunchedEffect(uiState.selectedCategory) {
        if (uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE && selectedBottomNav != 1) {
            selectedBottomNav = 1
        }
    }

    fun exitFolderDetail() {
        selectedDocIds = emptySet()
        if (uiState.currentFolderPath.isNotEmpty()) {
            viewModel.navigateUpFolder()
        } else {
            viewModel.selectCustomFolder(null)
            if (uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE) {
                viewModel.onCategorySelect(DocumentCategory.ALL)
            }
            viewModel.onSearchQueryChange("")
            viewModel.setFavoritesOnly(false)
        }
    }

    // Intercept back button and swipe left/right gestures for folders, search, categories, tabs, selection, and favorites
    BackHandler(
        enabled = selectedDocIds.isNotEmpty() ||
                showDownloadsScreen ||
                uiState.isFavoritesOnly ||
                uiState.currentFolderPath.isNotEmpty() ||
                uiState.selectedCustomFolder != null ||
                uiState.searchQuery.isNotEmpty() ||
                uiState.selectedCategory != DocumentCategory.ALL ||
                selectedBottomNav != 0
    ) {
        when {
            selectedDocIds.isNotEmpty() -> {
                selectedDocIds = emptySet()
            }
            showDownloadsScreen -> {
                showDownloadsScreen = false
            }
            uiState.searchQuery.isNotEmpty() -> {
                viewModel.onSearchQueryChange("")
            }
            isInsideFolder -> {
                exitFolderDetail()
            }
            uiState.isFavoritesOnly -> {
                navigateToRootHome()
            }
            uiState.currentFolderPath.isNotEmpty() -> {
                viewModel.navigateUpFolder()
            }
            uiState.selectedCustomFolder != null -> {
                viewModel.selectCustomFolder(null)
            }
            uiState.selectedCategory != DocumentCategory.ALL -> {
                viewModel.onCategorySelect(DocumentCategory.ALL)
            }
            selectedBottomNav != 0 -> {
                navigateToRootHome()
            }
        }
    }

    val imagesToPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImagesForPdf = uris
        }
    }

    val splitPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            var resolvedTitle = "Selected_PDF.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    resolvedTitle = cursor.getString(nameIndex) ?: resolvedTitle
                }
            }
            splitExternalPdfUri = uri
            splitExternalPdfTitle = resolvedTitle
            showSplitPdfDialog = true
        }
    }

    fun launchImagesToPdfPicker() {
        imagesToPdfPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchSplitPdfTool() {
        splitExternalPdfUri = null
        splitExternalPdfTitle = null
        showSplitPdfDialog = true
    }

    fun registerGeneratedPdfAndOpen(file: File) {
        coroutineScope.launch {
            val targetFolder = if (isInsideCustomFolder) uiState.selectedCustomFolder ?: "" else ""
            val importedDoc = viewModel.importSingleDocument(
                uriString = Uri.fromFile(file).toString(),
                title = file.name,
                type = DocumentType.PDF,
                sizeBytes = file.length(),
                parentPath = targetFolder
            )
            convertedRefreshTrigger++
            Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
            onOpenDocument(importedDoc)
        }
    }

    selectedImagesForPdf?.let { pickedImages ->
        ImagesToPdfDialog(
            imageUris = pickedImages,
            onRepickImages = { launchImagesToPdfPicker() },
            onDismiss = { selectedImagesForPdf = null },
            onPdfCreated = { createdFile ->
                selectedImagesForPdf = null
                registerGeneratedPdfAndOpen(createdFile)
            }
        )
    }

    if (showSplitPdfDialog) {
        SplitPdfDialog(
            libraryPdfs = uiState.allDocuments.filter { it.type == DocumentType.PDF },
            externalSourceUri = splitExternalPdfUri,
            externalSourceTitle = splitExternalPdfTitle,
            onPickExternalPdf = {
                splitPdfPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onDismiss = {
                showSplitPdfDialog = false
                splitExternalPdfUri = null
                splitExternalPdfTitle = null
            },
            onPdfCreated = { createdFile ->
                showSplitPdfDialog = false
                splitExternalPdfUri = null
                splitExternalPdfTitle = null
                registerGeneratedPdfAndOpen(createdFile)
            }
        )
    }

    val connectedDriveFolderUri by preferencesManager.connectedDriveFolderUri.collectAsState()
    val connectedDriveFolderName by preferencesManager.connectedDriveFolderName.collectAsState()
    val lastDriveSyncTime by preferencesManager.lastDriveSyncTime.collectAsState()
    val driveAutoSyncEnabled by preferencesManager.driveAutoSyncEnabled.collectAsState()

    // SAF Folder Tree Picker for connecting a Google Drive or local folder
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = folderDoc?.name ?: "Drive Sync Folder"
            preferencesManager.setConnectedDriveFolder(treeUri.toString(), folderName)

            viewModel.syncDriveFolder(treeUri) { count ->
                Toast.makeText(
                    context,
                    "Connected \"$folderName\"! Synced $count document(s)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun downloadDocumentsToDevice(docs: List<DocumentItem>) {
        if (docs.isEmpty()) return
        coroutineScope.launch {
            var successCount = 0
            var lastDest = preferencesManager.getDownloadLocationDisplayLabel()
            var lastError: String? = null
            for (doc in docs) {
                val res = PublicDocumentsExporter.downloadDocumentUriToDevice(
                    context = context,
                    sourceUri = Uri.parse(doc.uriString),
                    displayName = doc.title,
                    preferencesManager = preferencesManager,
                    sizeHintBytes = doc.sizeBytes
                )
                res.onSuccess {
                    successCount++
                    lastDest = it.destinationLabel
                }.onFailure { err ->
                    lastError = err.message
                }
            }
            if (successCount > 0) {
                val label = if (docs.size == 1) "\"${docs.first().title}\"" else "$successCount file(s)"
                Toast.makeText(context, "Downloaded $label to $lastDest", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Download failed: ${lastError ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Auto-sync connected Drive folder once on screen launch if enabled
    LaunchedEffect(connectedDriveFolderUri, currentUser) {
        if (DriveAccessPolicy.shouldAutoSyncDrive(currentUser != null, driveAutoSyncEnabled, connectedDriveFolderUri)) {
            viewModel.refreshConnectedDriveFolder()
        }
    }

    // SAF File Picker for PDF, Word, Excel, PPT, etc.
    fun generateUniqueTargetBaseAndTitle(
        rawFileName: String,
        existingTitlesLower: Set<String>
    ): Pair<String, String> {
        val rawBase = rawFileName.substringBeforeLast('.', rawFileName).ifBlank { "Document" }
        val docsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val convertedDir = File(docsRoot, "Converted_PDFs")
        val importedDir = File(docsRoot, "Imported_PDFs")
        var counter = 1
        while (true) {
            val candidateBase = "$rawBase ($counter)"
            val candidateTitle = "$candidateBase.pdf"
            val titleConflict = existingTitlesLower.contains(candidateTitle.lowercase())
            val convertedConflict = File(convertedDir, "${candidateBase}_converted.pdf").exists()
            val importedConflict = File(importedDir, candidateTitle).exists()
            if (!titleConflict && !convertedConflict && !importedConflict) {
                return candidateBase to candidateTitle
            }
            counter++
        }
    }

    suspend fun performSingleImport(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        isPdf: Boolean,
        isOffice: Boolean,
        uniqueBaseOverride: String? = null,
        uniqueTitleOverride: String? = null
    ): DocumentItem? {
        val activeTargetFolder = if (selectedBottomNav == 1) uiState.selectedCustomFolder.orEmpty() else ""
        val isFromGoogleDrive = uri.toString().contains("com.google.android.apps.docs") ||
            uri.toString().contains("google.drive")
        val isExternalContentUri = uri.scheme == "content" && !uri.toString().contains("media/external")
        if (isPdf) {
            val shouldCopyLocally = uniqueTitleOverride != null || isFromGoogleDrive || isExternalContentUri
            val resolvedTitle = uniqueTitleOverride ?: fileName
            val imported = if (shouldCopyLocally) {
                val copiedFile = withContext(Dispatchers.IO) {
                    val docsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                    val importedDir = File(docsRoot, "Imported_PDFs").apply { if (!exists()) mkdirs() }
                    var targetFile = File(importedDir, resolvedTitle)
                    if (targetFile.exists() && uniqueTitleOverride == null) {
                        val base = resolvedTitle.substringBeforeLast('.', resolvedTitle)
                        targetFile = File(importedDir, "${base}_${System.currentTimeMillis()}.pdf")
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("Unable to read selected PDF")
                    targetFile
                }
                val finalDoc = viewModel.importSingleDocument(
                    uriString = Uri.fromFile(copiedFile).toString(),
                    title = resolvedTitle,
                    type = DocumentType.PDF,
                    sizeBytes = copiedFile.length(),
                    parentPath = activeTargetFolder
                )
                if (isFromGoogleDrive) {
                    preferencesManager.addSyncedDriveUris(setOf(finalDoc.uriString))
                }
                finalDoc
            } else {
                viewModel.importSingleDocument(
                    uriString = uri.toString(),
                    title = fileName,
                    type = DocumentType.PDF,
                    sizeBytes = fileSize,
                    parentPath = activeTargetFolder
                )
            }
            if (activeTargetFolder.isNotEmpty() && imported.parentPath != activeTargetFolder) {
                viewModel.moveDocumentToFolder(imported, activeTargetFolder)
            }
            return imported
        } else if (isOffice) {
            conversionProgress = 0
            convertingDocTitle = uniqueTitleOverride ?: fileName
            val conversionResult = withContext(Dispatchers.IO) {
                DocumentConverter.convertUriToPdfDetailed(
                    context = context,
                    fileUri = uri,
                    fileName = fileName,
                    onProgress = { pct -> conversionProgress = pct },
                    outputBaseName = uniqueBaseOverride
                )
            }
            convertingDocTitle = null
            var importedConverted: DocumentItem? = null
            conversionResult.onSuccess { success ->
                val pdfFile = success.file
                val finalTitle = uniqueTitleOverride ?: "${fileName.substringBeforeLast('.')}.pdf"
                // Save ONLY the converted PDF file to the library (do not save the raw word file)
                val convertedDoc = viewModel.importSingleDocument(
                    uriString = Uri.fromFile(pdfFile).toString(),
                    title = finalTitle,
                    type = DocumentType.PDF,
                    sizeBytes = pdfFile.length(),
                    parentPath = activeTargetFolder
                )
                if (activeTargetFolder.isNotEmpty() && convertedDoc.parentPath != activeTargetFolder) {
                    viewModel.moveDocumentToFolder(convertedDoc, activeTargetFolder)
                }
                importedConverted = convertedDoc
                val modeMsg = when {
                    success.isOffline -> " (Offline)"
                    success.engineName != "iLovePDF Primary" -> " (${success.engineName})"
                    else -> ""
                }
                Toast.makeText(context, "Converted $finalTitle to PDF$modeMsg", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Conversion failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
            return importedConverted
        }
        return null
    }

    suspend fun processUriQueue(
        queue: List<Uri>,
        totalSelectedCount: Int,
        knownDocs: MutableList<DocumentItem>,
        lastImportedSoFar: DocumentItem?
    ) {
        val contentResolver = context.contentResolver
        var lastImportedDoc = lastImportedSoFar

        for ((index, uri) in queue.withIndex()) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var fileName = "Document"
            var fileSize = 0L
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isPdf = ext == "pdf"
            val isOffice = ext in listOf("docx", "doc", "xlsx", "xls", "pptx", "ppt")

            if (!isPdf && !isOffice) {
                unsupportedFileName = fileName
                continue
            }

            val targetPdfTitle = if (isPdf) fileName else "${fileName.substringBeforeLast('.')}.pdf"
            val existingDoc = knownDocs.firstOrNull { existing ->
                existing.uriString == uri.toString() ||
                    existing.title.equals(targetPdfTitle, ignoreCase = true) ||
                    existing.title.equals(fileName, ignoreCase = true)
            }

            if (existingDoc != null) {
                val existingTitlesLower = knownDocs.map { it.title.lowercase() }.toSet()
                val (uniqueBase, uniqueTitle) = generateUniqueTargetBaseAndTitle(fileName, existingTitlesLower)
                pendingDuplicateImport = PendingDuplicateImport(
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    isPdf = isPdf,
                    isOffice = isOffice,
                    existingDoc = existingDoc,
                    suggestedBase = uniqueBase,
                    suggestedTitle = uniqueTitle,
                    remainingUris = queue.drop(index + 1),
                    totalSelectedCount = totalSelectedCount,
                    knownDocs = knownDocs,
                    lastImportedDocSoFar = lastImportedDoc
                )
                return
            }

            val imported = performSingleImport(
                uri = uri,
                fileName = fileName,
                fileSize = fileSize,
                isPdf = isPdf,
                isOffice = isOffice
            )
            if (imported != null) {
                knownDocs.add(imported)
                lastImportedDoc = imported
                convertedRefreshTrigger++
            }
        }

        if (totalSelectedCount == 1 && lastImportedDoc != null) {
            onOpenDocument(lastImportedDoc)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                processUriQueue(
                    queue = uris,
                    totalSelectedCount = uris.size,
                    knownDocs = uiState.documents.toMutableList(),
                    lastImportedSoFar = null
                )
            }
        }
    }

    LaunchedEffect(launchImportPickerRequested) {
        if (launchImportPickerRequested) {
            launchImportPickerRequested = false
            filePickerLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "*/*"
                )
            )
        }
    }

    // Permission launcher for scanning storage
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.checkAndTriggerInitialScan()
        }
    }

    LaunchedEffect(Unit) {
        val autoScanEnabled = preferencesManager.autoScanOnLaunch.value
        if (autoScanEnabled) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            } else {
                viewModel.checkAndTriggerInitialScan()
            }
        }
    }

    val driveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = PdfToolsHelper.extractUrisFromActivityResult(result.data)
            if (uris.isNotEmpty()) {
                coroutineScope.launch {
                    processUriQueue(
                        queue = uris,
                        totalSelectedCount = uris.size,
                        knownDocs = uiState.documents.toMutableList(),
                        lastImportedSoFar = null
                    )
                }
            }
        }
    }

    val drivePdfUploadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        val treeUriStr = connectedDriveFolderUri
        if (pickedUri != null && treeUriStr != null) {
            val pickedName = runCatching {
                DocumentFile.fromSingleUri(context, pickedUri)?.name
            }.getOrNull().orEmpty().ifBlank { "Document.pdf" }
            if (!pickedName.lowercase().endsWith(".pdf")) {
                Toast.makeText(
                    context,
                    "Only PDF files can be uploaded to the Drive folder",
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }
            driveOperationMessage = "Uploading \"$pickedName\" to Google Drive..."
            viewModel.uploadPdfToDriveFolder(
                treeUri = Uri.parse(treeUriStr),
                parentPath = uiState.currentFolderPath,
                sourcePdfUri = pickedUri,
                desiredFileName = pickedName
            ) { result ->
                driveOperationMessage = null
                result.onSuccess { uploaded ->
                    Toast.makeText(
                        context,
                        "Uploaded \"${uploaded.name}\" to Drive folder",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "Drive upload failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun openDriveLinkInExternalApp(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link in Google Drive", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPasteDriveLinkDialog() {
        requireSignedInForDrive {
            driveLinkErrorMessage = null
            driveLinkRestrictedFallbackUrl = null
            showPasteDriveLinkDialog = true
        }
    }

    fun submitDriveFolderLink(rawLink: String) {
        isOpeningDriveLink = true
        driveLinkErrorMessage = null
        driveLinkRestrictedFallbackUrl = null
        viewModel.openDriveFolderByLink(rawLink) { result ->
            isOpeningDriveLink = false
            result.onSuccess { resolvedName ->
                showPasteDriveLinkDialog = false
                openGoogleDriveFolder()
                Toast.makeText(
                    context,
                    "Opened \"$resolvedName\" (read-safe Drive view)",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                if (error is RestrictedDriveFolderException) {
                    driveLinkErrorMessage = error.message
                    driveLinkRestrictedFallbackUrl = error.folderUrl
                } else {
                    driveLinkErrorMessage = error.message ?: "Could not open Google Drive folder link"
                }
            }
        }
    }

    fun openDriveFileAsLocalYomiCopy(
        driveFile: DriveFileItem,
        forceFreshCopy: Boolean = false
    ) {
        coroutineScope.launch {
            if (!forceFreshCopy) {
                val existingLocal = viewModel.resolveExistingLocalCopy(driveFile.uriString)
                val existingFileOk = if (existingLocal != null && existingLocal.uriString.startsWith("file://")) {
                    Uri.parse(existingLocal.uriString).path?.let { File(it).exists() } ?: false
                } else {
                    existingLocal != null
                }
                if (existingLocal != null && existingFileOk) {
                    onOpenDocument(existingLocal)
                    coroutineScope.launch {
                        delay(280L)
                        viewModel.recordOpened(existingLocal)
                    }
                    return@launch
                }
            }

            if (!DriveFolderSafetyHelper.isSupportedDriveFile(driveFile.name)) {
                unsupportedFileName = driveFile.name
                return@launch
            }

            val isHttpSource = driveFile.uriString.startsWith("http", ignoreCase = true)
            val driveUri = Uri.parse(driveFile.uriString)
            val isPdf = driveFile.type == DocumentType.PDF || driveFile.name.lowercase().endsWith(".pdf")
            val existingTitlesLower = uiState.allDocuments.map { it.title.lowercase() }.toSet()
            val desiredPdfTitle = DriveFolderSafetyHelper.targetLocalPdfTitle(driveFile.name)
            val resolvedTitle = if (forceFreshCopy || existingTitlesLower.contains(desiredPdfTitle.lowercase())) {
                DriveFolderSafetyHelper.resolveNonOverwritingPdfName(desiredPdfTitle, existingTitlesLower)
            } else {
                desiredPdfTitle
            }
            val resolvedBase = resolvedTitle.substringBeforeLast('.', resolvedTitle)

            if (isPdf) {
                driveOperationMessage = "Downloading local Yomi copy of \"${driveFile.name}\"..."
                val copyResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val docsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
                        val importedDir = File(docsRoot, "Imported_PDFs").apply { if (!exists()) mkdirs() }
                        var targetFile = File(importedDir, resolvedTitle)
                        if (targetFile.exists()) {
                            targetFile = File(importedDir, "${resolvedBase}_${System.currentTimeMillis()}.pdf")
                        }
                        if (isHttpSource) {
                            GoogleDriveSharedLinkClient.downloadDriveUrlToFile(
                                sourceUrl = driveFile.uriString,
                                targetFile = targetFile
                            ).getOrThrow()
                        } else {
                            context.contentResolver.openInputStream(driveUri)?.use { input ->
                                FileOutputStream(targetFile).use { output ->
                                    input.copyTo(output)
                                }
                            } ?: throw IllegalStateException("Unable to read \"${driveFile.name}\" from Drive")
                            targetFile
                        }
                    }
                }
                driveOperationMessage = null

                copyResult.onSuccess { copiedFile ->
                    val importedDoc = viewModel.importSingleDocument(
                        uriString = Uri.fromFile(copiedFile).toString(),
                        title = resolvedTitle,
                        type = DocumentType.PDF,
                        sizeBytes = copiedFile.length(),
                        parentPath = ""
                    )
                    preferencesManager.setDriveLocalCopy(driveFile.uriString, importedDoc.uriString)
                    convertedRefreshTrigger++
                    Toast.makeText(
                        context,
                        "Saved local Yomi copy: $resolvedTitle",
                        Toast.LENGTH_SHORT
                    ).show()
                    onOpenDocument(importedDoc)
                    coroutineScope.launch {
                        delay(280L)
                        viewModel.recordOpened(importedDoc)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "Could not download Drive PDF: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                conversionProgress = 0
                convertingDocTitle = resolvedTitle
                val tempCachedSource: File? = if (isHttpSource) {
                    val safeTempName = driveFile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    File(context.cacheDir, "drive_link_${System.currentTimeMillis()}_$safeTempName")
                } else null

                val conversionResult = withContext(Dispatchers.IO) {
                    try {
                        val sourceUriForConversion = if (isHttpSource && tempCachedSource != null) {
                            val dl = GoogleDriveSharedLinkClient.downloadDriveUrlToFile(
                                sourceUrl = driveFile.uriString,
                                targetFile = tempCachedSource
                            )
                            if (dl.isFailure) {
                                return@withContext Result.failure(
                                    dl.exceptionOrNull() ?: IllegalStateException("Failed downloading file from Drive link")
                                )
                            }
                            Uri.fromFile(tempCachedSource)
                        } else {
                            driveUri
                        }
                        DocumentConverter.convertUriToPdfDetailed(
                            context = context,
                            fileUri = sourceUriForConversion,
                            fileName = driveFile.name,
                            onProgress = { pct -> conversionProgress = pct },
                            outputBaseName = resolvedBase
                        )
                    } finally {
                        runCatching { tempCachedSource?.delete() }
                    }
                }
                convertingDocTitle = null

                conversionResult.onSuccess { success ->
                    val pdfFile = success.file
                    val convertedDoc = viewModel.importSingleDocument(
                        uriString = Uri.fromFile(pdfFile).toString(),
                        title = resolvedTitle,
                        type = DocumentType.PDF,
                        sizeBytes = pdfFile.length(),
                        parentPath = ""
                    )
                    preferencesManager.setDriveLocalCopy(driveFile.uriString, convertedDoc.uriString)
                    convertedRefreshTrigger++
                    val modeMsg = when {
                    success.isOffline -> " (Offline)"
                    success.engineName != "iLovePDF Primary" -> " (${success.engineName})"
                    else -> ""
                }
                    Toast.makeText(
                        context,
                        "Converted & saved local copy: $resolvedTitle$modeMsg",
                        Toast.LENGTH_SHORT
                    ).show()
                    onOpenDocument(convertedDoc)
                    coroutineScope.launch {
                        delay(280L)
                        viewModel.recordOpened(convertedDoc)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "Drive conversion failed: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(pendingExternalUris) {
        if (pendingExternalUris.isNotEmpty()) {
            val urisToProcess = pendingExternalUris.toList()
            onExternalUrisConsumed()
            processUriQueue(
                queue = urisToProcess,
                totalSelectedCount = urisToProcess.size,
                knownDocs = uiState.documents.toMutableList(),
                lastImportedSoFar = null
            )
        }
    }

    fun launchGoogleDriveApp() {
        requireSignedInForDrive {
            try {
                driveFilePickerLauncher.launch(PdfToolsHelper.buildGoogleDriveFilePickerIntent())
            } catch (_: ActivityNotFoundException) {
                try {
                    driveFilePickerLauncher.launch(PdfToolsHelper.buildDeviceDocumentPickerIntent())
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open Google Drive picker", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDownloadsScreen) {
        ConvertedFilesScreen(
            onBack = { showDownloadsScreen = false },
            onOpenDocument = onOpenDocument,
            onOpenQuickSuite = { showImportChooserDialog = true },
            preferencesManager = preferencesManager,
            refreshTrigger = convertedRefreshTrigger,
            initialIsGridView = uiState.isGridView,
            gridColumnCount = gridColumnCount,
            modifier = modifier
        )
    } else {
        Scaffold(
        topBar = {
            val tabDocuments = when (selectedBottomNav) {
                2 -> uiState.documents.filter { it.isFavorite }
                3 -> uiState.recentDocuments
                else -> uiState.documents
            }
            if (selectedDocIds.isNotEmpty()) {
                val isAllSelected = tabDocuments.isNotEmpty() && tabDocuments.all { it.id in selectedDocIds }
                SelectionTopBar(
                    selectedCount = selectedDocIds.size,
                    isAllSelected = isAllSelected,
                    onCloseSelection = { selectedDocIds = emptySet() },
                    onToggleSelectAll = {
                        selectedDocIds = if (isAllSelected) {
                            emptySet()
                        } else {
                            tabDocuments.map { it.id }.toSet()
                        }
                    },
                    onShareSelected = {
                        val docsToShare = tabDocuments.filter { it.id in selectedDocIds }
                        ShareUtils.shareDocuments(context, docsToShare)
                    },
                    onDownloadSelected = {
                        val docsToDownload = tabDocuments.filter { it.id in selectedDocIds }
                        downloadDocumentsToDevice(docsToDownload)
                        selectedDocIds = emptySet()
                    },
                    onDeleteSelected = {
                        showBatchDeleteDialog = true
                    },
                    onMoveSelected = {
                        showBatchMoveDialog = true
                    },
                    onStarSelected = {
                        viewModel.toggleFavorites(selectedDocIds)
                        Toast.makeText(context, "Updated Favorites for ${selectedDocIds.size} document(s)", Toast.LENGTH_SHORT).show()
                        selectedDocIds = emptySet()
                    },
                    onRemoveFromFolder = if (isInsideCustomFolder) {
                        {
                            val count = selectedDocIds.size
                            val folder = uiState.selectedCustomFolder ?: ""
                            viewModel.removeDocumentsFromFolder(selectedDocIds)
                            Toast.makeText(context, "Removed $count file(s) from \"$folder\"", Toast.LENGTH_SHORT).show()
                            selectedDocIds = emptySet()
                        }
                    } else null
                )
            } else if (isInsideFolder) {
                val openedFolderTitle = when {
                    isInsideCustomFolder -> uiState.selectedCustomFolder ?: "Folder"
                    uiState.currentFolderPath.isNotEmpty() -> uiState.currentFolderDisplayName
                    else -> connectedDriveFolderName ?: "Google Drive"
                }
                val folderSubtitle = if (isInsideDriveFolder) {
                    if (connectedDriveFolderUri == null) {
                        "Not connected"
                    } else {
                        val subCount = uiState.currentSubfolders.size
                        val docCount = uiState.driveFiles.size
                        val baseCount = if (subCount > 0) {
                            "$subCount folder${if (subCount != 1) "s" else ""} • $docCount file${if (docCount != 1) "s" else ""}"
                        } else {
                            "$docCount file${if (docCount != 1) "s" else ""}"
                        }
                        if (uiState.currentFolderPath.isNotEmpty()) "${uiState.currentFolderPath} • $baseCount" else baseCount
                    }
                } else {
                    val docCount = uiState.documents.size
                    "$docCount PDF${if (docCount != 1) "s" else ""}"
                }
                FolderDetailTopBar(
                    folderName = openedFolderTitle,
                    subtitle = folderSubtitle,
                    isDriveFolder = isInsideDriveFolder,
                    isDriveConnected = connectedDriveFolderUri != null,
                    isDriveLinkMode = uiState.isDriveLinkMode,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    currentSortOrder = uiState.sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    isGridView = uiState.isGridView,
                    onToggleViewMode = viewModel::toggleViewMode,
                    hasFiles = if (isInsideDriveFolder) false else uiState.documents.isNotEmpty(),
                    onBack = { exitFolderDetail() },
                    onAddFilesToFolder = { showAddFilesToFolderDialog = true },
                    onEnterSelectionMode = {
                        if (!isInsideDriveFolder && uiState.documents.isNotEmpty()) {
                            selectedDocIds = setOf(uiState.documents.first().id)
                        }
                    },
                    onRenameFolder = {
                        uiState.selectedCustomFolder?.let { customFolderToRename = it }
                    },
                    onRemoveFolder = {
                        uiState.selectedCustomFolder?.let { customFolderToConfirmDelete = it }
                    },
                    onSyncDrive = {
                        if (connectedDriveFolderUri != null) {
                            viewModel.refreshConnectedDriveFolder()
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    onConnectDriveFolder = {
                        folderPickerLauncher.launch(null)
                    },
                    onOpenDriveApp = {
                        if (uiState.isDriveLinkMode && !connectedDriveFolderUri.isNullOrBlank()) {
                            openDriveLinkInExternalApp(connectedDriveFolderUri!!)
                        } else {
                            launchGoogleDriveApp()
                        }
                    },
                    onCreateDriveSubfolder = {
                        if (connectedDriveFolderUri != null && !uiState.isDriveLinkMode) {
                            showCreateDriveSubfolderDialog = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    onUploadPdfToDrive = {
                        if (connectedDriveFolderUri != null && !uiState.isDriveLinkMode) {
                            showUploadYomiPdfToDriveDialog = true
                        } else {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    onPasteDriveLink = {
                        openPasteDriveLinkDialog()
                    },
                    onUnlinkDriveFolder = {
                        showUnlinkDriveConfirmDialog = true
                    },
                    folderIcon = openedCustomFolderAppearance?.icon ?: Icons.Default.Folder,
                    folderAccentColor = openedCustomFolderAppearance?.accentColor
                        ?: MaterialTheme.colorScheme.primary,
                    folderContainerColor = openedCustomFolderAppearance?.containerColor
                        ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    onCustomizeFolder = uiState.selectedCustomFolder?.let { folder ->
                        { customFolderToCustomize = folder }
                    }
                )
            } else {
                DashboardTopBar(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { cat ->
                        if (cat == DocumentCategory.GOOGLE_DRIVE) {
                            openGoogleDriveFolder()
                        } else {
                            viewModel.onCategorySelect(cat)
                        }
                    },
                    isScanning = uiState.isScanning,
                    currentUser = currentUser,
                    onMenuClick = { showQuickActionsSheet = true },
                    onProfileClick = onOpenAccount,
                    onFolderClick = { showDownloadsScreen = true },
                    onOpenDownloads = { showDownloadedFilesSheet = true },
                    downloadCount = downloadedFiles.size
                )
            }
        },
        floatingActionButton = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(56.dp)
                    .clickable {
                        if (isInsideDriveFolder) {
                            if (connectedDriveFolderUri != null) {
                                showDriveAddActionDialog = true
                            } else {
                                openPasteDriveLinkDialog()
                            }
                        } else {
                            showImportChooserDialog = true
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add / Import document",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        bottomBar = {
            if (selectedDocIds.isEmpty() && !isInsideFolder) {
                YomiBottomNavBar(
                    selectedTab = selectedBottomNav,
                    onSelectTab = { tab ->
                        if (tab != selectedBottomNav) {
                            selectedDocIds = emptySet()
                            viewModel.onSearchQueryChange("")
                            viewModel.selectCustomFolder(null)
                            if (tab != 1 && uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE) {
                                viewModel.onCategorySelect(DocumentCategory.ALL)
                            }
                            selectedBottomNav = tab
                        }
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier
            .fillMaxSize()
            .yomiPaperBackground()
    ) { innerPadding ->
        val pullToRefreshState = rememberPullToRefreshState()
        val isRefreshing = uiState.isScanning || uiState.isDriveSyncing
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = {
                viewModel.triggerScan()
                if (currentUser != null && !connectedDriveFolderUri.isNullOrBlank()) {
                    viewModel.refreshConnectedDriveFolder()
                }
            },
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Google Play Store / Material 3 Fade-Through tab transition (clean scale + fade with zero overlap judder)
            AnimatedContent(
                targetState = selectedBottomNav,
                contentKey = { it },
                transitionSpec = {
                    (fadeIn(animationSpec = tween(175, delayMillis = 80, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.975f, animationSpec = tween(195, delayMillis = 80, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(animationSpec = tween(80, easing = FastOutLinearInEasing)))
                        .using(SizeTransform(clip = false) { _, _ -> snap() })
                },
                label = "DashboardTabContentTransition",
                modifier = Modifier.fillMaxSize()
            ) { activeTab ->
                val tabDocuments = remember(activeTab, uiState.documents, uiState.recentDocuments) {
                    when (activeTab) {
                        2 -> uiState.documents.filter { it.isFavorite }
                        3 -> uiState.recentDocuments
                        else -> uiState.documents
                    }
                }

                val tabIsFoldersRootOnly = activeTab == 1 &&
                    uiState.selectedCustomFolder == null &&
                    uiState.selectedCategory != DocumentCategory.GOOGLE_DRIVE
                val tabIsInsideFolder = activeTab == 1 && !tabIsFoldersRootOnly

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header for Favorites / History view
                    if (activeTab == 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Favorite Documents",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${tabDocuments.size} ${if (tabDocuments.size == 1) "file" else "files"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (activeTab == 3) {
                        Text(
                            text = "Recently Opened Documents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Folders Root Page: Strictly Folders only (Custom folders + Google Drive folder card)
                    val isHomeRootFeed = activeTab == 0 &&
                        uiState.searchQuery.isEmpty() &&
                        uiState.selectedCategory == DocumentCategory.ALL &&
                        uiState.selectedCustomFolder == null
                    val hasDriveFiles = isInsideDriveFolder && uiState.driveFiles.isNotEmpty()
                    val hasItems = tabDocuments.isNotEmpty() || hasDriveFiles || uiState.currentSubfolders.isNotEmpty()
                    val isInitialScanningFeed = (uiState.isScanning || uiState.isDriveSyncing) && !hasItems &&
                        uiState.searchQuery.isEmpty() && uiState.selectedCustomFolder == null

                    if (tabIsFoldersRootOnly) {
                        LazyColumn(
                            state = tabListStates[1],
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                DriveWorkspaceCard(
                                    connectedFolderUri = connectedDriveFolderUri,
                                    connectedFolderName = connectedDriveFolderName,
                                    isSyncing = uiState.isDriveSyncing,
                                    isSignedIn = currentUser != null,
                                    onOpenDriveFolder = { openGoogleDriveFolder() },
                                    onConnectDriveFolder = {
                                        requireSignedInForDrive {
                                            folderPickerLauncher.launch(null)
                                        }
                                    },
                                    onSyncNow = {
                                        requireSignedInForDrive {
                                            viewModel.refreshConnectedDriveFolder()
                                        }
                                    },
                                    onOpenDriveApp = {
                                        requireSignedInForDrive {
                                            if (uiState.isDriveLinkMode && !connectedDriveFolderUri.isNullOrBlank()) {
                                                openDriveLinkInExternalApp(connectedDriveFolderUri!!)
                                            } else {
                                                launchGoogleDriveApp()
                                            }
                                        }
                                    },
                                    onPasteDriveLink = { openPasteDriveLinkDialog() },
                                    onUnlinkDriveFolder = { showUnlinkDriveConfirmDialog = true }
                                )
                            }

                            item {
                                CustomFoldersSection(
                                    customFolders = uiState.customFolders,
                                    customFolderStyles = uiState.customFolderStyles,
                                    selectedFolder = uiState.selectedCustomFolder,
                                    allDocuments = uiState.allDocuments,
                                    onSelectFolder = { folder ->
                                        viewModel.onSearchQueryChange("")
                                        viewModel.setFavoritesOnly(false)
                                        viewModel.selectCustomFolder(folder)
                                    },
                                    onCreateFolder = viewModel::createCustomFolder,
                                    onDeleteFolder = viewModel::removeCustomFolder,
                                    onRenameFolder = viewModel::renameCustomFolder,
                                    onUpdateFolderStyle = viewModel::updateCustomFolderStyle,
                                    filterQuery = uiState.searchQuery
                                )
                            }
                        }
                    } else if (isInitialScanningFeed || ((uiState.isScanning || uiState.isDriveSyncing) && tabIsInsideFolder && !hasItems)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            YomiExpressiveLoadingIndicator(
                                size = 48.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    } else if (!hasItems && !isHomeRootFeed) {
                        if (activeTab == 0 && uiState.searchQuery.isEmpty()) {
                            if (uiState.selectedCategory != DocumentCategory.ALL && uiState.selectedCategory != DocumentCategory.GOOGLE_DRIVE && !uiState.isFavoritesOnly) {
                                CategoryViewHeader(
                                    category = uiState.selectedCategory,
                                    documentCount = 0,
                                    onBack = { navigateToRootHome() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (tabIsInsideFolder) {
                            val folderLabel = uiState.selectedCustomFolder
                                ?: if (uiState.currentFolderPath.isNotEmpty()) uiState.currentFolderDisplayName else (connectedDriveFolderName ?: "Google Drive")
                            EmptyFolderDetailState(
                                folderName = folderLabel,
                                searchQuery = uiState.searchQuery,
                                isFavoritesOnly = uiState.isFavoritesOnly,
                                isDriveFolder = isInsideDriveFolder,
                                isDriveConnected = connectedDriveFolderUri != null,
                                isDriveLinkMode = uiState.isDriveLinkMode,
                                onClearSearch = { viewModel.onSearchQueryChange("") },
                                onClearFavorites = { viewModel.setFavoritesOnly(false) },
                                onAddExistingPdfs = { showAddFilesToFolderDialog = true },
                                onImportNewPdf = { launchImportPickerRequested = true },
                                onSyncDrive = {
                                    if (connectedDriveFolderUri != null) {
                                        viewModel.refreshConnectedDriveFolder()
                                    } else {
                                        folderPickerLauncher.launch(null)
                                    }
                                },
                                onConnectDriveFolder = { folderPickerLauncher.launch(null) },
                                onOpenDriveApp = {
                                    if (uiState.isDriveLinkMode && !connectedDriveFolderUri.isNullOrBlank()) {
                                        openDriveLinkInExternalApp(connectedDriveFolderUri!!)
                                    } else {
                                        launchGoogleDriveApp()
                                    }
                                },
                                onUploadPdfToDrive = { showUploadYomiPdfToDriveDialog = true },
                                onCreateDriveSubfolder = { showCreateDriveSubfolderDialog = true },
                                onPasteDriveLink = { openPasteDriveLinkDialog() },
                                onUnlinkDriveFolder = { showUnlinkDriveConfirmDialog = true },
                                folderIcon = openedCustomFolderAppearance?.icon,
                                folderAccentColor = openedCustomFolderAppearance?.accentColor,
                                folderContainerColor = openedCustomFolderAppearance?.containerColor,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            val emptyMessage = when {
                                activeTab == 2 -> "No favorite files yet. Tap the menu (⋮) on any document and choose \"Add to Favorites\" to save it here."
                                activeTab == 3 -> "No recently opened documents yet. Open a PDF document to see it here."
                                uiState.isFavoritesOnly -> "No favorite documents found. Choose \"Add to Favorites\" from any document's options menu."
                                uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE && uiState.currentFolderPath.isNotEmpty() -> "This folder does not contain supported documents or subfolders."
                                uiState.selectedCategory != DocumentCategory.ALL -> "No ${uiState.selectedCategory.label} found yet. Scan storage or import files."
                                else -> "No documents found yet. Scan your storage or import files from Google Drive."
                            }
                            EmptyDocumentsState(
                                message = emptyMessage,
                                showImportButton = activeTab == 0 && !uiState.isFavoritesOnly,
                                onImportClick = {
                                    showImportChooserDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else if (uiState.isGridView) {
                        val safeGridColumns = gridColumnCount.coerceIn(2, 4)
                        val gridSpacing = if (safeGridColumns >= 3) 8.dp else 10.dp
                        LazyVerticalGrid(
                            state = tabGridStates[activeTab.coerceIn(0, 3)],
                            columns = GridCells.Fixed(safeGridColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            verticalArrangement = Arrangement.spacedBy(gridSpacing)
                        ) {
                            // Category Header on Home (Tab 0)
                            if (activeTab == 0 && uiState.searchQuery.isEmpty()) {
                                if (uiState.selectedCategory != DocumentCategory.ALL && uiState.selectedCategory != DocumentCategory.GOOGLE_DRIVE && !uiState.isFavoritesOnly) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        CategoryViewHeader(
                                            category = uiState.selectedCategory,
                                            documentCount = tabDocuments.size,
                                            onBack = { navigateToRootHome() }
                                        )
                                    }
                                }
                            }

                            // Home Hero Card, Filter Chips, and Section Header
                            if (activeTab == 0 && uiState.searchQuery.isEmpty() && uiState.selectedCategory == DocumentCategory.ALL) {
                                if (uiState.selectedCustomFolder == null) {
                                    val heroDoc = uiState.recentDocuments.firstOrNull() ?: uiState.documents.firstOrNull()
                                    if (heroDoc != null) {
                                        item(span = { GridItemSpan(maxLineSpan) }, contentType = "ContinueReading") {
                                            ContinueReadingHeroCard(
                                                document = heroDoc,
                                                lastPageZeroBased = preferencesManager.getLastReadPage(heroDoc),
                                                totalPages = pageCounts[heroDoc.id],
                                                onResumeClick = {
                                                    onOpenDocument(heroDoc)
                                                    coroutineScope.launch {
                                                        delay(280L)
                                                        viewModel.recordOpened(heroDoc)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                if (uiState.selectedCustomFolder != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Viewing folder: \"${uiState.selectedCustomFolder}\"",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                IconButton(
                                                    onClick = { viewModel.selectCustomFolder(null) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear folder filter",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HomeUtilityBar(
                                        totalDocuments = tabDocuments.size,
                                        totalStorageFormatted = uiState.totalFormattedSize,
                                        selectedCategory = uiState.selectedCategory,
                                        onClearCategoryFilter = { viewModel.onCategorySelect(DocumentCategory.ALL) },
                                        currentSortOrder = uiState.sortOrder,
                                        onSortOrderChange = viewModel::setSortOrder,
                                        isGridView = uiState.isGridView,
                                        onToggleViewMode = viewModel::toggleViewMode,
                                        isFavoritesOnly = uiState.isFavoritesOnly,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                                        sectionTitle = if (uiState.isFavoritesOnly) "Favorites" else "Recent",
                                        onEnterSelectionMode = {
                                            if (tabDocuments.isNotEmpty()) {
                                                selectedDocIds = setOf(tabDocuments.first().id)
                                            }
                                        }
                                    )
                                }
                            }

                            // Category-filtered Utility Bar on Home (Tab 0)
                            if (activeTab == 0 && uiState.searchQuery.isEmpty() && uiState.selectedCategory != DocumentCategory.ALL) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HomeUtilityBar(
                                        totalDocuments = tabDocuments.size,
                                        totalStorageFormatted = uiState.totalFormattedSize,
                                        selectedCategory = uiState.selectedCategory,
                                        onClearCategoryFilter = { viewModel.onCategorySelect(DocumentCategory.ALL) },
                                        currentSortOrder = uiState.sortOrder,
                                        onSortOrderChange = viewModel::setSortOrder,
                                        isGridView = uiState.isGridView,
                                        onToggleViewMode = viewModel::toggleViewMode,
                                        isFavoritesOnly = uiState.isFavoritesOnly,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                                        sectionTitle = uiState.selectedCategory.label,
                                        onEnterSelectionMode = {
                                            if (tabDocuments.isNotEmpty()) {
                                                selectedDocIds = setOf(tabDocuments.first().id)
                                            }
                                        }
                                    )
                                }
                            }

                            // Folders Workspace items inside Grid are handled by tabIsFoldersRootOnly above (no mixed files on Folders root)

                            // Subfolders section
                            if (uiState.currentSubfolders.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "Folders (${uiState.currentSubfolders.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(
                                    items = uiState.currentSubfolders,
                                    key = { "folder_${it.path}" }
                                ) { folder ->
                                    FolderGridCard(
                                        folder = folder,
                                        onClick = { viewModel.navigateToFolder(folder.path) }
                                    )
                                }
                            }

                            // Files section
                            if (isInsideDriveFolder) {
                                if (uiState.driveFiles.isNotEmpty()) {
                                    if (uiState.currentSubfolders.isNotEmpty()) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Text(
                                                text = "Files (${uiState.driveFiles.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                            )
                                        }
                                    }
                                    items(
                                        items = uiState.driveFiles,
                                        key = { "drive_${it.uriString}" },
                                        contentType = { "DriveFileGridCard" }
                                    ) { driveFile ->
                                        val localCopyDoc = viewModel.resolveExistingLocalCopy(driveFile.uriString)
                                        DriveFileGridCard(
                                            driveFile = driveFile,
                                            localCopyUri = localCopyDoc?.uriString,
                                            columnCount = safeGridColumns,
                                            onOpenAsLocalCopy = {
                                                openDriveFileAsLocalYomiCopy(driveFile, forceFreshCopy = false)
                                            },
                                            onSaveFreshCopy = {
                                                openDriveFileAsLocalYomiCopy(driveFile, forceFreshCopy = true)
                                            },
                                            onShareLocalCopy = localCopyDoc?.let { doc ->
                                                { ShareUtils.shareDocument(context, doc) }
                                            }
                                        )
                                    }
                                }
                            } else if (tabDocuments.isNotEmpty()) {
                                if (uiState.currentSubfolders.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = "Files (${tabDocuments.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                }
                                items(
                                    items = tabDocuments,
                                    key = { it.id },
                                    contentType = { "DocumentGridCard" }
                                ) { document ->
                                    val isSelected = document.id in selectedDocIds
                                    DocumentGridCard(
                                        document = document,
                                        isSelectionMode = selectedDocIds.isNotEmpty(),
                                        isSelected = isSelected,
                                        columnCount = safeGridColumns,
                                        onClick = {
                                            if (selectedDocIds.isNotEmpty()) {
                                                selectedDocIds = if (isSelected) selectedDocIds - document.id else selectedDocIds + document.id
                                            } else {
                                                onOpenDocument(document)
                                                coroutineScope.launch {
                                                    delay(280L)
                                                    viewModel.recordOpened(document)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            selectedDocIds = selectedDocIds + document.id
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(document) },
                                        onRename = { documentToRename = document },
                                        onMoveToFolder = { documentToMove = document },
                                        onShare = { ShareUtils.shareDocument(context, document) },
                                        onDownload = { downloadDocumentsToDevice(listOf(document)) },
                                        onDelete = { viewModel.deleteDocument(document) }
                                    )
                                }
                            } else if (isHomeRootFeed && uiState.currentSubfolders.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    HomeEmptyLibraryCard(
                                        onImportClick = { showImportChooserDialog = true }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = tabListStates[activeTab.coerceIn(0, 3)],
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Category Header on Home (Tab 0)
                            if (activeTab == 0 && uiState.searchQuery.isEmpty()) {
                                if (uiState.selectedCategory != DocumentCategory.ALL && uiState.selectedCategory != DocumentCategory.GOOGLE_DRIVE && !uiState.isFavoritesOnly) {
                                    item {
                                        CategoryViewHeader(
                                            category = uiState.selectedCategory,
                                            documentCount = tabDocuments.size,
                                            onBack = { navigateToRootHome() }
                                        )
                                    }
                                }
                            }

                            // Home Hero Card, Filter Chips, and Section Header
                            if (activeTab == 0 && uiState.searchQuery.isEmpty() && uiState.selectedCategory == DocumentCategory.ALL) {
                                if (uiState.selectedCustomFolder == null) {
                                    val heroDoc = uiState.recentDocuments.firstOrNull() ?: uiState.documents.firstOrNull()
                                    if (heroDoc != null) {
                                        item(contentType = "ContinueReading") {
                                            ContinueReadingHeroCard(
                                                document = heroDoc,
                                                lastPageZeroBased = preferencesManager.getLastReadPage(heroDoc),
                                                totalPages = pageCounts[heroDoc.id],
                                                onResumeClick = {
                                                    onOpenDocument(heroDoc)
                                                    coroutineScope.launch {
                                                        delay(280L)
                                                        viewModel.recordOpened(heroDoc)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                if (uiState.selectedCustomFolder != null) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Viewing folder: \"${uiState.selectedCustomFolder}\"",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                IconButton(
                                                    onClick = { viewModel.selectCustomFolder(null) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Clear folder filter",
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    HomeUtilityBar(
                                        totalDocuments = tabDocuments.size,
                                        totalStorageFormatted = uiState.totalFormattedSize,
                                        selectedCategory = uiState.selectedCategory,
                                        onClearCategoryFilter = { viewModel.onCategorySelect(DocumentCategory.ALL) },
                                        currentSortOrder = uiState.sortOrder,
                                        onSortOrderChange = viewModel::setSortOrder,
                                        isGridView = uiState.isGridView,
                                        onToggleViewMode = viewModel::toggleViewMode,
                                        isFavoritesOnly = uiState.isFavoritesOnly,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                                        sectionTitle = if (uiState.isFavoritesOnly) "Favorites" else "Recent",
                                        onEnterSelectionMode = {
                                            if (tabDocuments.isNotEmpty()) {
                                                selectedDocIds = setOf(tabDocuments.first().id)
                                            }
                                        }
                                    )
                                }
                            }

                            // Category-filtered Utility Bar on Home (Tab 0)
                            if (activeTab == 0 && uiState.searchQuery.isEmpty() && uiState.selectedCategory != DocumentCategory.ALL) {
                                item {
                                    HomeUtilityBar(
                                        totalDocuments = tabDocuments.size,
                                        totalStorageFormatted = uiState.totalFormattedSize,
                                        selectedCategory = uiState.selectedCategory,
                                        onClearCategoryFilter = { viewModel.onCategorySelect(DocumentCategory.ALL) },
                                        currentSortOrder = uiState.sortOrder,
                                        onSortOrderChange = viewModel::setSortOrder,
                                        isGridView = uiState.isGridView,
                                        onToggleViewMode = viewModel::toggleViewMode,
                                        isFavoritesOnly = uiState.isFavoritesOnly,
                                        onToggleFavoritesOnly = viewModel::toggleFavoritesOnly,
                                        sectionTitle = uiState.selectedCategory.label,
                                        onEnterSelectionMode = {
                                            if (tabDocuments.isNotEmpty()) {
                                                selectedDocIds = setOf(tabDocuments.first().id)
                                            }
                                        }
                                    )
                                }
                            }

                            // Folders Workspace items inside List are handled by tabIsFoldersRootOnly above (no mixed files on Folders root)

                            // Subfolders section
                            if (uiState.currentSubfolders.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Folders (${uiState.currentSubfolders.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(
                                    items = uiState.currentSubfolders,
                                    key = { "folder_${it.path}" }
                                ) { folder ->
                                    FolderCard(
                                        folder = folder,
                                        onClick = { viewModel.navigateToFolder(folder.path) }
                                    )
                                }
                            }

                            // Files section
                            if (isInsideDriveFolder) {
                                if (uiState.driveFiles.isNotEmpty()) {
                                    if (uiState.currentSubfolders.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Files (${uiState.driveFiles.size})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                            )
                                        }
                                    }
                                    items(
                                        items = uiState.driveFiles,
                                        key = { "drive_${it.uriString}" },
                                        contentType = { "DriveFileCard" }
                                    ) { driveFile ->
                                        val localCopyDoc = viewModel.resolveExistingLocalCopy(driveFile.uriString)
                                        DriveFileCard(
                                            driveFile = driveFile,
                                            isSavedLocally = localCopyDoc != null,
                                            onOpenAsLocalCopy = {
                                                openDriveFileAsLocalYomiCopy(driveFile, forceFreshCopy = false)
                                            },
                                            onSaveFreshCopy = {
                                                openDriveFileAsLocalYomiCopy(driveFile, forceFreshCopy = true)
                                            },
                                            onShareLocalCopy = localCopyDoc?.let { doc ->
                                                { ShareUtils.shareDocument(context, doc) }
                                            }
                                        )
                                    }
                                }
                            } else if (tabDocuments.isNotEmpty()) {
                                if (uiState.currentSubfolders.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Files (${tabDocuments.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                        )
                                    }
                                }
                                items(
                                    items = tabDocuments,
                                    key = { it.id },
                                    contentType = { "DocumentCard" }
                                ) { document ->
                                    val isSelected = document.id in selectedDocIds
                                    DocumentCard(
                                        document = document,
                                        isSelectionMode = selectedDocIds.isNotEmpty(),
                                        isSelected = isSelected,
                                        onClick = {
                                            if (selectedDocIds.isNotEmpty()) {
                                                selectedDocIds = if (isSelected) selectedDocIds - document.id else selectedDocIds + document.id
                                            } else {
                                                onOpenDocument(document)
                                                coroutineScope.launch {
                                                    delay(280L)
                                                    viewModel.recordOpened(document)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            selectedDocIds = selectedDocIds + document.id
                                        },
                                        onSelectOption = {
                                            selectedDocIds = selectedDocIds + document.id
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(document) },
                                        onRename = { documentToRename = document },
                                        onDelete = { viewModel.deleteDocument(document) },
                                        onMoveToFolder = { documentToMove = document },
                                        onShare = { ShareUtils.shareDocument(context, document) },
                                        onDownload = { downloadDocumentsToDevice(listOf(document)) },
                                        pageCount = pageCounts[document.id]
                                    )
                                }
                            } else if (isHomeRootFeed && uiState.currentSubfolders.isEmpty()) {
                                item {
                                    HomeEmptyLibraryCard(
                                        onImportClick = { showImportChooserDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }

    // Import Chooser Modal Dialog (Google Drive vs Device Storage vs Rescan)
    if (showImportChooserDialog) {
        ImportDocumentChooserDialog(
            connectedDriveFolderName = connectedDriveFolderName,
            hasConnectedDriveFolder = connectedDriveFolderUri != null,
            isSignedIn = currentUser != null,
            onPickDriveFile = { launchGoogleDriveApp() },
            onOpenConnectedDriveFolder = {
                requireSignedInForDrive {
                    showDownloadsScreen = false
                    viewModel.refreshConnectedDriveFolder()
                    viewModel.onCategorySelect(DocumentCategory.GOOGLE_DRIVE)
                    selectedBottomNav = 1
                }
            },
            onChangeConnectedDriveFolder = {
                requireSignedInForDrive {
                    folderPickerLauncher.launch(null)
                }
            },
            onOpenDriveFolderLink = { openPasteDriveLinkDialog() },
            onUnlinkDriveFolder = { showUnlinkDriveConfirmDialog = true },
            onImportLocalDocument = {
                filePickerLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "*/*"
                    )
                )
            },
            onDismiss = { showImportChooserDialog = false }
        )
    }

    unsupportedFileName?.let { badFileName ->
        UnsupportedFormatDialog(
            fileName = badFileName,
            onDismiss = { unsupportedFileName = null }
        )
    }

    documentToRename?.let { doc ->
        RenameDocumentDialog(
            document = doc,
            onConfirmRename = { trimmed ->
                viewModel.renameDocument(doc.id, trimmed)
                Toast.makeText(context, "Renamed document", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { documentToRename = null }
        )
    }

    if (showCreateFolderDialog) {
        CreateCustomFolderDialog(
            existingFolderCount = uiState.customFolders.size,
            onConfirmCreate = { trimmed, colorKey, iconKey ->
                viewModel.createCustomFolder(trimmed, colorKey, iconKey)
                Toast.makeText(context, "Folder \"$trimmed\" created", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }

    if (showAddFilesToFolderDialog && uiState.selectedCustomFolder != null) {
        val targetFolder = uiState.selectedCustomFolder!!
        AddFilesToFolderDialog(
            targetFolder = targetFolder,
            allDocuments = uiState.allDocuments,
            onConfirmAdd = { pickedIds ->
                viewModel.moveDocumentsToFolder(pickedIds, targetFolder)
                Toast.makeText(context, "Added ${pickedIds.size} file(s) to \"$targetFolder\"", Toast.LENGTH_SHORT).show()
            },
            onRequestImportPdf = { launchImportPickerRequested = true },
            onDismiss = { showAddFilesToFolderDialog = false }
        )
    }

    customFolderToRename?.let { targetFolder ->
        RenameCustomFolderDialog(
            targetFolder = targetFolder,
            onConfirmRename = { trimmed ->
                viewModel.renameCustomFolder(targetFolder, trimmed)
                Toast.makeText(context, "Renamed folder to \"$trimmed\"", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { customFolderToRename = null }
        )
    }

    customFolderToConfirmDelete?.let { targetFolder ->
        RemoveCustomFolderDialog(
            targetFolder = targetFolder,
            onConfirmRemove = {
                viewModel.removeCustomFolder(targetFolder)
                Toast.makeText(context, "Removed folder \"$targetFolder\"", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { customFolderToConfirmDelete = null }
        )
    }

    customFolderToCustomize?.let { targetFolder ->
        CustomizeFolderStyleDialog(
            folderName = targetFolder,
            customFolderStyles = uiState.customFolderStyles,
            onSave = { colorKey, iconKey ->
                viewModel.updateCustomFolderStyle(targetFolder, colorKey, iconKey)
                customFolderToCustomize = null
                Toast.makeText(context, "Updated \"$targetFolder\" style", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { customFolderToCustomize = null }
        )
    }

    convertingDocTitle?.let { activeTitle ->
        ConversionProgressDialog(
            documentTitle = activeTitle,
            progressPercent = conversionProgress
        )
    }

    pendingDuplicateImport?.let { dup ->
        DuplicateImportDialog(
            existingTitle = dup.existingDoc.title,
            suggestedTitle = dup.suggestedTitle,
            onImportCopy = {
                pendingDuplicateImport = null
                coroutineScope.launch {
                    val imported = try {
                        performSingleImport(
                            uri = dup.uri,
                            fileName = dup.fileName,
                            fileSize = dup.fileSize,
                            isPdf = dup.isPdf,
                            isOffice = dup.isOffice,
                            uniqueBaseOverride = dup.suggestedBase,
                            uniqueTitleOverride = dup.suggestedTitle
                        )
                    } catch (e: Exception) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        null
                    }
                    if (imported != null) {
                        dup.knownDocs.add(imported)
                        convertedRefreshTrigger++
                    }
                    processUriQueue(
                        queue = dup.remainingUris,
                        totalSelectedCount = dup.totalSelectedCount,
                        knownDocs = dup.knownDocs,
                        lastImportedSoFar = imported ?: dup.lastImportedDocSoFar
                    )
                }
            },
            onOpenExisting = {
                val existing = dup.existingDoc
                pendingDuplicateImport = null
                onOpenDocument(existing)
            },
            onDismiss = { pendingDuplicateImport = null }
        )
    }

    documentToMove?.let { targetDoc ->
        MoveToFolderDialog(
            title = "Move \"${targetDoc.title.take(24)}\"",
            currentFolderPath = targetDoc.parentPath,
            customFolders = uiState.customFolders,
            customFolderStyles = uiState.customFolderStyles,
            onSelectFolder = { folderPath ->
                viewModel.moveDocumentToFolder(targetDoc, folderPath)
                documentToMove = null
                Toast.makeText(
                    context,
                    if (folderPath.isEmpty()) "Moved to All Files" else "Moved to \"$folderPath\"",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onCreateAndMove = { trimmed, colorKey, iconKey ->
                viewModel.createCustomFolder(trimmed, colorKey, iconKey)
                viewModel.moveDocumentToFolder(targetDoc, trimmed)
                documentToMove = null
                Toast.makeText(context, "Created & moved to \"$trimmed\"", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { documentToMove = null }
        )
    }

    if (showBatchDeleteDialog && selectedDocIds.isNotEmpty()) {
        BatchDeleteDocumentsDialog(
            selectedCount = selectedDocIds.size,
            onConfirmDelete = {
                val count = selectedDocIds.size
                viewModel.deleteDocuments(selectedDocIds)
                Toast.makeText(context, "Deleted $count document(s)", Toast.LENGTH_SHORT).show()
                selectedDocIds = emptySet()
                showBatchDeleteDialog = false
            },
            onDismiss = { showBatchDeleteDialog = false }
        )
    }

    if (showBatchMoveDialog && selectedDocIds.isNotEmpty()) {
        MoveToFolderDialog(
            title = "Move ${selectedDocIds.size} Document(s)",
            currentFolderPath = null,
            customFolders = uiState.customFolders,
            customFolderStyles = uiState.customFolderStyles,
            onSelectFolder = { folderPath ->
                val count = selectedDocIds.size
                viewModel.moveDocumentsToFolder(selectedDocIds, folderPath)
                Toast.makeText(
                    context,
                    if (folderPath.isEmpty()) "Moved $count documents to All Files" else "Moved $count documents to \"$folderPath\"",
                    Toast.LENGTH_SHORT
                ).show()
                selectedDocIds = emptySet()
                showBatchMoveDialog = false
            },
            onCreateAndMove = { trimmed, colorKey, iconKey ->
                val count = selectedDocIds.size
                viewModel.createCustomFolder(trimmed, colorKey, iconKey)
                viewModel.moveDocumentsToFolder(selectedDocIds, trimmed)
                Toast.makeText(context, "Created & moved $count documents to \"$trimmed\"", Toast.LENGTH_SHORT).show()
                selectedDocIds = emptySet()
                showBatchMoveDialog = false
            },
            onDismiss = { showBatchMoveDialog = false }
        )
    }

    // Yomi Logo Quick Actions Bottom Sheet
    if (showQuickActionsSheet) {
        val isStarredActive = uiState.isFavoritesOnly && !showDownloadsScreen
        val isDownloadsActive = showDownloadsScreen
        val isDriveActive = !showDownloadsScreen && !uiState.isFavoritesOnly && uiState.selectedCategory == DocumentCategory.GOOGLE_DRIVE

        YomiQuickActionsBottomSheet(
            onDismiss = { showQuickActionsSheet = false },
            allDocuments = uiState.documents,
            isStarredActive = isStarredActive,
            isDownloadsActive = isDownloadsActive,
            isDriveActive = isDriveActive,
            isSignedIn = currentUser != null,
            onNavigateHome = {
                navigateToRootHome()
            },
            onFilterStarred = {
                showDownloadsScreen = false
                selectedBottomNav = 2
            },
            onCategorySelected = { cat ->
                showDownloadsScreen = false
                if (cat == DocumentCategory.GOOGLE_DRIVE) {
                    openGoogleDriveFolder()
                } else {
                    viewModel.onCategorySelect(cat)
                }
            },
            onDocumentCreatedOrConverted = { newDoc ->
                if (isInsideCustomFolder) {
                    uiState.selectedCustomFolder?.let { folder ->
                        viewModel.moveDocumentToFolder(newDoc, folder)
                    }
                }
                onOpenDocument(newDoc)
            },
            onOpenDownloads = {
                showDownloadsScreen = true
            },
            onTriggerImport = {
                launchImportPickerRequested = true
            },
            onTriggerImagesToPdf = {
                launchImagesToPdfPicker()
            },
            onTriggerSplitPdf = {
                launchSplitPdfTool()
            }
        )
    }

    if (showDriveAddActionDialog) {
        val targetFolderLabel = uiState.currentFolderPath.ifEmpty {
            connectedDriveFolderName ?: "Google Drive"
        }
        AddToDriveFolderDialog(
            folderDisplayName = targetFolderLabel,
            isLinkMode = uiState.isDriveLinkMode,
            onOpenDriveLink = { openPasteDriveLinkDialog() },
            onUploadPdfFromYomi = {
                showDriveAddActionDialog = false
                showUploadYomiPdfToDriveDialog = true
            },
            onUploadPdfFromDevice = {
                showDriveAddActionDialog = false
                drivePdfUploadPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onCreateSubfolder = {
                showDriveAddActionDialog = false
                showCreateDriveSubfolderDialog = true
            },
            onDismiss = { showDriveAddActionDialog = false }
        )
    }

    if (showPasteDriveLinkDialog) {
        val currentLink = connectedDriveFolderUri?.takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty()
        PasteDriveFolderLinkDialog(
            recentLinks = recentDriveLinks,
            initialLink = currentLink,
            isLoading = isOpeningDriveLink,
            errorMessage = driveLinkErrorMessage,
            restrictedFallbackUrl = driveLinkRestrictedFallbackUrl,
            onSubmitLink = { submitted -> submitDriveFolderLink(submitted) },
            onSelectSavedLink = { saved -> submitDriveFolderLink(saved.url) },
            onRemoveSavedLink = { folderId -> preferencesManager.removeRecentDriveLink(folderId) },
            onOpenInDriveApp = { fallbackUrl ->
                showPasteDriveLinkDialog = false
                openDriveLinkInExternalApp(fallbackUrl)
            },
            onDismiss = {
                if (!isOpeningDriveLink) {
                    showPasteDriveLinkDialog = false
                }
            }
        )
    }

    if (showUnlinkDriveConfirmDialog) {
        UnlinkDriveFolderDialog(
            folderName = connectedDriveFolderName ?: "Google Drive Folder",
            onConfirmUnlink = {
                viewModel.unlinkDriveFolder()
                showUnlinkDriveConfirmDialog = false
                Toast.makeText(context, "Unlinked Google Drive folder", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showUnlinkDriveConfirmDialog = false }
        )
    }

    if (showCreateDriveSubfolderDialog) {
        val parentLabel = uiState.currentFolderPath.ifEmpty {
            connectedDriveFolderName ?: "Google Drive"
        }
        CreateDriveSubfolderDialog(
            parentFolderName = parentLabel,
            onConfirmCreate = { rawFolderName ->
                val treeUriStr = connectedDriveFolderUri
                if (treeUriStr != null) {
                    driveOperationMessage = "Creating folder \"$rawFolderName\" in Google Drive..."
                    viewModel.createDriveSubfolder(
                        treeUri = Uri.parse(treeUriStr),
                        parentPath = uiState.currentFolderPath,
                        folderName = rawFolderName
                    ) { result ->
                        driveOperationMessage = null
                        result.onSuccess { created ->
                            Toast.makeText(
                                context,
                                "Created Drive folder \"${created.name}\"",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: "Could not create Drive folder",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = { showCreateDriveSubfolderDialog = false }
        )
    }

    if (showUploadYomiPdfToDriveDialog) {
        val localPdfs = remember(uiState.allDocuments) {
            uiState.allDocuments.filter { it.type == DocumentType.PDF }
        }
        val targetFolderLabel = uiState.currentFolderPath.ifEmpty {
            connectedDriveFolderName ?: "Google Drive"
        }
        UploadYomiPdfToDriveDialog(
            targetFolderName = targetFolderLabel,
            localPdfs = localPdfs,
            onPickFromDevice = {
                showUploadYomiPdfToDriveDialog = false
                drivePdfUploadPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onSelectPdf = { localDoc ->
                showUploadYomiPdfToDriveDialog = false
                val treeUriStr = connectedDriveFolderUri ?: return@UploadYomiPdfToDriveDialog
                val desiredName = if (localDoc.title.lowercase().endsWith(".pdf")) {
                    localDoc.title
                } else {
                    "${localDoc.title}.pdf"
                }
                driveOperationMessage = "Uploading \"$desiredName\" to Google Drive..."
                viewModel.uploadPdfToDriveFolder(
                    treeUri = Uri.parse(treeUriStr),
                    parentPath = uiState.currentFolderPath,
                    sourcePdfUri = Uri.parse(localDoc.uriString),
                    desiredFileName = desiredName
                ) { result ->
                    driveOperationMessage = null
                    result.onSuccess { uploaded ->
                        Toast.makeText(
                            context,
                            "Uploaded \"${uploaded.name}\" to Drive folder",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Drive upload failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDismiss = { showUploadYomiPdfToDriveDialog = false }
        )
    }

    driveOperationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Google Drive Portal") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }

    if (showDownloadedFilesSheet) {
        DownloadedFilesBottomSheet(
            downloadedFiles = downloadedFiles,
            currentLocationLabel = preferencesManager.getDownloadLocationDisplayLabel(),
            onOpenRecord = { record ->
                showDownloadedFilesSheet = false
                val usableUri = record.targetUriOrPath
                    .takeIf { it.startsWith("content://") || it.startsWith("file://") }
                    ?: record.sourceUriString
                if (usableUri.isNotBlank()) {
                    onOpenDocument(
                        DocumentItem(
                            id = (record.id.hashCode().toLong() and 0x7FFFFFFFL).coerceAtLeast(1L),
                            uriString = usableUri,
                            title = record.title,
                            type = DocumentType.PDF,
                            sizeBytes = record.sizeBytes,
                            lastModified = record.downloadedAt
                        )
                    )
                } else {
                    Toast.makeText(context, "Saved at ${record.destinationLabel}", Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveRecord = { id ->
                preferencesManager.removeDownloadedFile(id)
            },
            onClearAll = {
                preferencesManager.clearDownloadedFilesHistory()
                showDownloadedFilesSheet = false
                Toast.makeText(context, "Downloaded files list cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDownloadedFilesSheet = false }
        )
    }

    if (showDriveSignInRequiredDialog) {
        DriveSignInRequiredDialog(
            onSignIn = {
                showDriveSignInRequiredDialog = false
                onOpenAccount()
            },
            onContinueWithoutDrive = {
                showDriveSignInRequiredDialog = false
            }
        )
    }
}
