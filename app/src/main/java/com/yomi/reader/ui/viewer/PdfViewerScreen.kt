package com.yomi.reader.ui.viewer

import com.yomi.reader.ui.components.YomiExpressiveLoadingIndicator

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TextFieldDefaults
import com.yomi.reader.parser.pdf.PdfSearchResult
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.filled.Draw
import com.yomi.reader.data.local.PdfAnnotationsRepository
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.data.local.RecentPdfHistoryHelper
import com.yomi.reader.parser.pdf.PdfPasswordRequiredException
import com.yomi.reader.ui.theme.YomiTheme
import com.yomi.reader.ui.viewer.annotation.AnnotationHistoryOp
import com.yomi.reader.ui.viewer.annotation.AnnotationTool
import com.yomi.reader.ui.viewer.annotation.DetectedTextLine
import com.yomi.reader.ui.viewer.annotation.DrawingPoint
import com.yomi.reader.ui.viewer.annotation.DrawingStroke
import com.yomi.reader.ui.viewer.annotation.NormalizedRect
import com.yomi.reader.ui.viewer.annotation.PdfPageAnnotation
import com.yomi.reader.ui.viewer.annotation.PdfTextHighlightHelper
import com.yomi.reader.ui.viewer.annotation.StickyNote
import com.yomi.reader.ui.viewer.annotation.TextHighlight
import com.yomi.reader.ui.viewer.annotation.hitsPoint
import kotlin.math.abs
import kotlin.math.hypot
import com.yomi.reader.ui.viewer.components.AnnotationsDialog
import com.yomi.reader.ui.viewer.components.HighlightedPassageActionPill
import com.yomi.reader.ui.viewer.components.PdfAddNoteDialog
import com.yomi.reader.ui.viewer.components.PdfBookmarksDialog
import com.yomi.reader.ui.viewer.components.PdfFloatingEditOverlay
import com.yomi.reader.ui.viewer.components.PdfZoomStepperPill
import com.yomi.reader.ui.viewer.components.PdfDocumentInfoDialog
import com.yomi.reader.ui.viewer.components.PdfEditSpeedDialItem
import com.yomi.reader.ui.viewer.components.PdfJumpToPageDialog
import com.yomi.reader.ui.viewer.components.PdfPageCard
import com.yomi.reader.ui.viewer.components.PdfPasswordDialog
import com.yomi.reader.ui.viewer.components.PdfThumbnailsSheet
import com.yomi.reader.ui.viewer.components.PdfViewNoteDialog
import com.yomi.reader.ui.viewer.components.RecentPdfSidePaneOverlay
import java.io.File
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.yomi.reader.ui.viewer.components.PdfViewMode
import com.yomi.reader.ui.viewer.components.PdfViewerBottomToolbar
import com.yomi.reader.ui.viewer.components.PdfViewerLayoutHelper
import com.yomi.reader.ui.viewer.components.PdfZoomOption
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.parser.pdf.PdfPageRenderer
import com.yomi.reader.parser.pdf.PdfTextExtractor
import com.yomi.reader.util.PdfAnnotatedExporter
import com.yomi.reader.util.PdfPrintHelper
import com.yomi.reader.util.ShareUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    document: DocumentItem,
    onBack: () -> Unit,
    recentPdfDocuments: List<DocumentItem> = emptyList(),
    onOpenRecentPdf: ((DocumentItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val renderer = remember { PdfPageRenderer(context) }
    var session by remember { mutableStateOf<PdfPageRenderer.PdfSession?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val thumbnailBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    // Navigation & View Modes
    val listState = rememberLazyListState()
    var viewMode by remember { mutableStateOf(PdfViewMode.VERTICAL_CONTINUOUS) }
    val pagerState = rememberPagerState(pageCount = { session?.pageCount ?: 1 })
    val spreadPagerState = rememberPagerState(
        pageCount = { PdfViewerLayoutHelper.computeSpreadCount(session?.pageCount ?: 1) }
    )

    // Zoom & Rotation
    var scale by remember { mutableFloatStateOf(1f) }
    var activeZoomOption by remember { mutableStateOf<PdfZoomOption?>(PdfZoomOption.FIT_WIDTH) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Bookmarks (0-indexed page indices)
    val bookmarkedPages = remember { mutableStateListOf<Int>() }

    // Dialogs & Sheets
    var showJumpDialog by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showRecentPdfsPane by remember { mutableStateOf(false) }

    // In-PDF Search State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PdfSearchResult>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }

    // Fullscreen Mode
    var isFullscreen by remember { mutableStateOf(false) }

    val preferencesManager = remember {
        (context.applicationContext as? com.yomi.reader.YomiApplication)?.container?.preferencesManager
            ?: PreferencesManager(context)
    }
    val recordedRecentPdfs by preferencesManager.recentPdfs.collectAsState()
    val mergedRecentPdfs = remember(recentPdfDocuments, recordedRecentPdfs) {
        RecentPdfHistoryHelper.mergeRecentPdfDocuments(
            recordedPdfs = recordedRecentPdfs,
            dbRecentDocs = recentPdfDocuments
        ).filter { item ->
            if (!item.uriString.startsWith("file://")) {
                true
            } else {
                val path = runCatching { Uri.parse(item.uriString).path }.getOrNull()
                path != null && File(path).exists()
            }
        }
    }
    val annotationsRepo = remember { (context.applicationContext as? com.yomi.reader.YomiApplication)?.container?.pdfAnnotationsRepository ?: PdfAnnotationsRepository(context) }
    val docAnnotationKey = remember(document.uriString, document.id) {
        document.uriString.ifBlank { document.id.toString() }
    }

    // Annotations, Text Highlights, Eraser & Text-Based Notes (per-page Undo/Redo + persistent storage)
    var isAnnotationMode by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(AnnotationTool.HIGHLIGHT) }
    var activePenColor by remember { mutableStateOf(Color(0xFFFACC15)) } // Warm Highlighter Yellow default
    var penStrokeWidth by remember { mutableFloatStateOf(4f) }
    val pageMarks = remember { mutableStateMapOf<Int, SnapshotStateList<PdfPageAnnotation>>() }
    val pageUndoOps = remember { mutableStateMapOf<Int, SnapshotStateList<AnnotationHistoryOp>>() }
    val pageRedoOps = remember { mutableStateMapOf<Int, SnapshotStateList<AnnotationHistoryOp>>() }
    val pageDetectedLines = remember { mutableStateMapOf<Int, List<DetectedTextLine>>() }
    val stickyNotes = remember { mutableStateListOf<StickyNote>() }
    val noteSnippets = remember { mutableStateMapOf<String, Bitmap>() }
    var hasLoadedSavedAnnotations by remember(docAnnotationKey) { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var pendingNotePageIndex by remember { mutableIntStateOf(0) }
    var pendingNoteAnchorRects by remember { mutableStateOf<List<NormalizedRect>>(emptyList()) }
    var pendingNoteSnippetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingNoteRelativeRects by remember { mutableStateOf<List<NormalizedRect>>(emptyList()) }
    var pendingNoteInitialColor by remember { mutableStateOf<Long?>(null) }
    var recentHighlightForNote by remember { mutableStateOf<Triple<Int, List<NormalizedRect>, Long>?>(null) }
    var showAnnotationsDialog by remember { mutableStateOf(false) }
    var noteToView by remember { mutableStateOf<StickyNote?>(null) }
    var isEditToolsExpanded by remember { mutableStateOf(false) }

    fun openNoteComposer(
        pageIdx: Int,
        anchorRects: List<NormalizedRect>,
        initialColor: Long? = null
    ) {
        pendingNotePageIndex = pageIdx
        pendingNoteAnchorRects = anchorRects
        pendingNoteInitialColor = initialColor
        val croppedPair = if (anchorRects.isNotEmpty()) {
            pageBitmaps[pageIdx]?.let { bmp ->
                PdfTextHighlightHelper.cropHighlightedSnippetBitmap(bmp, anchorRects)
            }
        } else {
            null
        }
        pendingNoteSnippetBitmap = croppedPair?.first
        pendingNoteRelativeRects = croppedPair?.second ?: emptyList()
        recentHighlightForNote = null
        showAddNoteDialog = true
    }

    LaunchedEffect(stickyNotes.toList(), pageBitmaps.size) {
        stickyNotes.forEach { note ->
            if (note.anchorRects.isNotEmpty() && !noteSnippets.containsKey(note.id)) {
                val loaded = annotationsRepo.loadNoteSnippet(note.id)
                if (loaded != null) {
                    noteSnippets[note.id] = loaded
                } else {
                    pageBitmaps[note.pageIndex]?.let { pageBmp ->
                        PdfTextHighlightHelper.cropHighlightedSnippetBitmap(pageBmp, note.anchorRects)?.let { (bmp, _) ->
                            noteSnippets[note.id] = bmp
                            annotationsRepo.saveNoteSnippet(note.id, bmp)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(recentHighlightForNote) {
        if (recentHighlightForNote != null) {
            delay(4500L)
            recentHighlightForNote = null
        }
    }

    fun cachePageBitmapBounded(pageIdx: Int, bitmap: Bitmap, anchorPageIdx: Int) {
        pageBitmaps[pageIdx] = bitmap
        if (pageBitmaps.size > 8) {
            val toEvict = pageBitmaps.keys
                .filter { it != pageIdx }
                .sortedByDescending { abs(it - anchorPageIdx) }
                .take(pageBitmaps.size - 8)
            toEvict.forEach { pageBitmaps.remove(it) }
        }
    }

    fun cacheThumbnailBitmapBounded(pageIdx: Int, bitmap: Bitmap, anchorPageIdx: Int) {
        thumbnailBitmaps[pageIdx] = bitmap
        if (thumbnailBitmaps.size > 24) {
            val toEvict = thumbnailBitmaps.keys
                .filter { it != pageIdx }
                .sortedByDescending { abs(it - anchorPageIdx) }
                .take(thumbnailBitmaps.size - 24)
            toEvict.forEach { thumbnailBitmaps.remove(it) }
        }
    }

    fun persistAnnotationsNow() {
        val marksSnapshot = pageMarks.mapValues { it.value.toList() }
        val notesSnapshot = stickyNotes.toList()
        val bookmarksSnapshot = bookmarkedPages.toList()
        coroutineScope.launch {
            annotationsRepo.saveAnnotations(docAnnotationKey, marksSnapshot, notesSnapshot, bookmarksSnapshot)
        }
    }

    LaunchedEffect(docAnnotationKey) {
        val saved = annotationsRepo.loadAnnotations(docAnnotationKey)
        pageMarks.clear()
        pageUndoOps.clear()
        pageRedoOps.clear()
        saved.marksByPage.forEach { (pageIdx, list) ->
            pageMarks[pageIdx] = mutableStateListOf<PdfPageAnnotation>().apply { addAll(list) }
        }
        stickyNotes.clear()
        stickyNotes.addAll(saved.stickyNotes)
        bookmarkedPages.clear()
        bookmarkedPages.addAll(saved.bookmarkedPages)
        hasLoadedSavedAnnotations = true
    }

    DisposableEffect(docAnnotationKey) {
        onDispose {
            if (hasLoadedSavedAnnotations) {
                annotationsRepo.saveAnnotationsSync(
                    docAnnotationKey,
                    pageMarks.mapValues { it.value.toList() },
                    stickyNotes.toList(),
                    bookmarkedPages.toList()
                )
            }
        }
    }

    fun recordAddMark(pageIdx: Int, mark: PdfPageAnnotation) {
        pageMarks.getOrPut(pageIdx) { mutableStateListOf() }.add(mark)
        pageUndoOps.getOrPut(pageIdx) { mutableStateListOf() }.add(AnnotationHistoryOp.AddMark(pageIdx, mark))
        pageRedoOps[pageIdx]?.clear()
        if (mark is PdfPageAnnotation.Highlight && mark.highlight.rects.isNotEmpty()) {
            recentHighlightForNote = Triple(
                pageIdx,
                mark.highlight.rects,
                mark.highlight.color.toArgb().toLong() and 0xFFFFFFFFL
            )
        }
        persistAnnotationsNow()
    }

    fun recordEraseAt(pageIdx: Int, point: DrawingPoint) {
        val marks = pageMarks[pageIdx]
        if (marks != null) {
            val hitIdx = marks.indexOfLast { it.hitsPoint(point) }
            if (hitIdx >= 0) {
                val removed = marks.removeAt(hitIdx)
                pageUndoOps.getOrPut(pageIdx) { mutableStateListOf() }.add(
                    AnnotationHistoryOp.EraseMark(pageIdx, removed, hitIdx)
                )
                pageRedoOps[pageIdx]?.clear()
                persistAnnotationsNow()
                return
            }
        }
        val anchoredNote = stickyNotes.lastOrNull { note ->
            note.pageIndex == pageIdx && note.anchorRects.any { it.contains(point, tolerance = 0.016f) }
        }
        if (anchoredNote != null) {
            stickyNotes.remove(anchoredNote)
            pageUndoOps.getOrPut(pageIdx) { mutableStateListOf() }.add(
                AnnotationHistoryOp.EraseNote(pageIdx, anchoredNote)
            )
            pageRedoOps[pageIdx]?.clear()
            persistAnnotationsNow()
        }
    }

    fun recordAddNote(note: StickyNote) {
        stickyNotes.add(note)
        pageUndoOps.getOrPut(note.pageIndex) { mutableStateListOf() }.add(
            AnnotationHistoryOp.AddNote(note.pageIndex, note)
        )
        pageRedoOps[note.pageIndex]?.clear()
        persistAnnotationsNow()
    }

    fun performUndo(pageIdx: Int) {
        val undoStack = pageUndoOps[pageIdx] ?: return
        if (undoStack.isEmpty()) return
        val op = undoStack.removeAt(undoStack.lastIndex)
        when (op) {
            is AnnotationHistoryOp.AddMark -> {
                pageMarks[pageIdx]?.removeAll { it.id == op.mark.id }
            }
            is AnnotationHistoryOp.EraseMark -> {
                val list = pageMarks.getOrPut(pageIdx) { mutableStateListOf() }
                val insertAt = op.index.coerceIn(0, list.size)
                list.add(insertAt, op.mark)
            }
            is AnnotationHistoryOp.AddNote -> {
                stickyNotes.removeAll { it.id == op.note.id }
            }
            is AnnotationHistoryOp.EraseNote -> {
                if (stickyNotes.none { it.id == op.note.id }) {
                    stickyNotes.add(op.note)
                }
            }
        }
        pageRedoOps.getOrPut(pageIdx) { mutableStateListOf() }.add(op)
        persistAnnotationsNow()
    }

    fun performRedo(pageIdx: Int) {
        val redoStack = pageRedoOps[pageIdx] ?: return
        if (redoStack.isEmpty()) return
        val op = redoStack.removeAt(redoStack.lastIndex)
        when (op) {
            is AnnotationHistoryOp.AddMark -> {
                pageMarks.getOrPut(pageIdx) { mutableStateListOf() }.add(op.mark)
            }
            is AnnotationHistoryOp.EraseMark -> {
                pageMarks[pageIdx]?.removeAll { it.id == op.mark.id }
            }
            is AnnotationHistoryOp.AddNote -> {
                if (stickyNotes.none { it.id == op.note.id }) {
                    stickyNotes.add(op.note)
                }
            }
            is AnnotationHistoryOp.EraseNote -> {
                stickyNotes.removeAll { it.id == op.note.id }
            }
        }
        pageUndoOps.getOrPut(pageIdx) { mutableStateListOf() }.add(op)
        persistAnnotationsNow()
    }

    // Scroll idle detection for Floating PDF Edit Button
    val isUserScrolling = when (viewMode) {
        PdfViewMode.VERTICAL_CONTINUOUS -> listState.isScrollInProgress
        PdfViewMode.SINGLE_PAGE -> pagerState.isScrollInProgress
        PdfViewMode.TWO_PAGE -> spreadPagerState.isScrollInProgress
    }
    var isScrollIdle by remember { mutableStateOf(true) }
    LaunchedEffect(isUserScrolling) {
        if (isUserScrolling) {
            isEditToolsExpanded = false
            isScrollIdle = false
        } else {
            delay(260L)
            isScrollIdle = true
        }
    }

    suspend fun scrollToPageIndex(pageIndex: Int, targetMode: PdfViewMode = viewMode, animate: Boolean = false) {
        val total = session?.pageCount ?: 1
        val clamped = pageIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        when (targetMode) {
            PdfViewMode.VERTICAL_CONTINUOUS -> {
                if (animate) listState.animateScrollToItem(clamped) else listState.scrollToItem(clamped)
            }
            PdfViewMode.SINGLE_PAGE -> {
                if (animate) pagerState.animateScrollToPage(clamped) else pagerState.scrollToPage(clamped)
            }
            PdfViewMode.TWO_PAGE -> {
                val spreadIdx = PdfViewerLayoutHelper.pageIndexToSpreadIndex(clamped)
                if (animate) spreadPagerState.animateScrollToPage(spreadIdx) else spreadPagerState.scrollToPage(spreadIdx)
            }
        }
    }

    // Password Protected State
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // In-document text search effect with debouncing
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isNotBlank() && session != null) {
            delay(300L) // Debounce search input to avoid scanning entire PDF on every keystroke
            isSearching = true
            val results = PdfTextExtractor.searchInPdf(
                context = context,
                uri = Uri.parse(document.uriString),
                query = trimmed,
                totalPages = session!!.pageCount
            )
            searchResults = results
            currentMatchIndex = 0
            isSearching = false
            if (results.isNotEmpty()) {
                val firstMatchPage = results[0].pageNumber
                scrollToPageIndex(firstMatchPage - 1, animate = true)
            }
        } else {
            searchResults = emptyList()
            currentMatchIndex = 0
            isSearching = false
        }
    }

    fun jumpToMatch(index: Int) {
        if (searchResults.isNotEmpty()) {
            val targetIdx = (index + searchResults.size) % searchResults.size
            currentMatchIndex = targetIdx
            val page = searchResults[targetIdx].pageNumber
            coroutineScope.launch {
                scrollToPageIndex(page - 1, animate = true)
            }
        }
    }

    val currentVisiblePage by remember {
        derivedStateOf {
            val total = session?.pageCount ?: 1
            when (viewMode) {
                PdfViewMode.VERTICAL_CONTINUOUS -> (listState.firstVisibleItemIndex + 1).coerceIn(1, total)
                PdfViewMode.SINGLE_PAGE -> (pagerState.currentPage + 1).coerceIn(1, total)
                PdfViewMode.TWO_PAGE -> {
                    val firstIdx = PdfViewerLayoutHelper.spreadIndexToFirstPageIndex(spreadPagerState.currentPage)
                    (firstIdx + 1).coerceIn(1, total)
                }
            }
        }
    }

    fun applyZoomOption(option: PdfZoomOption) {
        activeZoomOption = option
        offsetX = 0f
        offsetY = 0f
        if (option == PdfZoomOption.FIT_PAGE) {
            val activeBmp = pageBitmaps[(currentVisiblePage - 1).coerceAtLeast(0)]
            scale = PdfViewerLayoutHelper.computeFitPageScale(
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                pageBitmapWidth = activeBmp?.width,
                pageBitmapHeight = activeBmp?.height
            )
        } else {
            scale = option.scaleValue ?: 1f
        }
    }

    LaunchedEffect(activeZoomOption, viewportWidthPx, viewportHeightPx, viewMode, currentVisiblePage) {
        if (activeZoomOption == PdfZoomOption.FIT_PAGE && viewportWidthPx > 0 && viewportHeightPx > 0) {
            val activeBmp = pageBitmaps[(currentVisiblePage - 1).coerceAtLeast(0)]
            scale = PdfViewerLayoutHelper.computeFitPageScale(
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                pageBitmapWidth = activeBmp?.width,
                pageBitmapHeight = activeBmp?.height
            )
        }
    }

    LaunchedEffect(document.uriString) {
        try {
            session?.close()
            session = null
            pageBitmaps.clear()
            thumbnailBitmaps.clear()
            val uri = Uri.parse(document.uriString)
            val newSession = renderer.openSession(uri)
            session = newSession
            preferencesManager.recordRecentPdf(document)
        } catch (sec: PdfPasswordRequiredException) {
            showPasswordDialog = true
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Failed to open PDF"
        }
    }

    // Restore last read position
    LaunchedEffect(session) {
        val activeSession = session ?: return@LaunchedEffect
        val lastPage = preferencesManager.getLastReadPage(document)
        if (lastPage > 0 && lastPage < activeSession.pageCount) {
            delay(120)
            scrollToPageIndex(lastPage, animate = false)
            Toast.makeText(context, "Resumed at page ${lastPage + 1}", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-save last read page as user navigates and trim distant page bitmaps
    LaunchedEffect(currentVisiblePage) {
        if (session != null && currentVisiblePage > 0) {
            val anchorIdx = (currentVisiblePage - 1).coerceAtLeast(0)
            preferencesManager.setLastReadPage(document, anchorIdx)
            if (pageBitmaps.size > 8) {
                val toEvict = pageBitmaps.keys
                    .sortedByDescending { abs(it - anchorIdx) }
                    .take(pageBitmaps.size - 8)
                toEvict.forEach { pageBitmaps.remove(it) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            session?.close()
            pageBitmaps.clear()
            thumbnailBitmaps.clear()
        }
    }

    // PDF documents always render in dedicated Light Mode
    YomiTheme(darkTheme = false) {
    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (!isFullscreen) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = document.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                session?.let {
                                    Text(
                                        text = "Page $currentVisiblePage of ${it.pageCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            // Find in Document search toggle
                            IconButton(
                                onClick = { isSearchActive = !isSearchActive },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Find in PDF",
                                    tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Bookmark Page toggle
                            val isCurrentBookmarked = bookmarkedPages.contains(currentVisiblePage - 1)
                            IconButton(
                                onClick = {
                                    val pageIdx = currentVisiblePage - 1
                                    if (isCurrentBookmarked) {
                                        bookmarkedPages.remove(pageIdx)
                                        Toast.makeText(context, "Bookmark removed for page $currentVisiblePage", Toast.LENGTH_SHORT).show()
                                    } else {
                                        bookmarkedPages.add(pageIdx)
                                        Toast.makeText(context, "Page $currentVisiblePage bookmarked!", Toast.LENGTH_SHORT).show()
                                    }
                                    persistAnnotationsNow()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Recent PDFs quick-jump slide-over right pane
                            if (onOpenRecentPdf != null) {
                                IconButton(
                                    onClick = { showRecentPdfsPane = !showRecentPdfsPane },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = "Recent PDFs",
                                        tint = if (showRecentPdfsPane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // More Menu (essential viewer & document actions only)
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Page Thumbnails Grid") },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showThumbnailsSheet = true
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("View Bookmarks (${bookmarkedPages.size})") },
                                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showBookmarksDialog = true
                                        }
                                    )

                                    val totalAnnotations = stickyNotes.size + pageMarks.values.sumOf { it.size }
                                    DropdownMenuItem(
                                        text = { Text("Notes & Annotations ($totalAnnotations)") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showAnnotationsDialog = true
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = { Text("Rotate 90°") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            rotationAngle = (rotationAngle + 90f) % 360f
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Fullscreen Reading") },
                                        leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            isFullscreen = true
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = { Text("Export Annotated PDF") },
                                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            Toast.makeText(context, "Exporting to Documents/Yomi...", Toast.LENGTH_SHORT).show()
                                            val marksSnapshot = pageMarks.mapValues { (_, list) -> list.toList() }
                                            val notesSnapshot = stickyNotes.toList()
                                            coroutineScope.launch {
                                                val res = PdfAnnotatedExporter.exportAnnotatedPdf(
                                                    context = context,
                                                    sourceUri = Uri.parse(document.uriString),
                                                    documentTitle = document.title,
                                                    marksByPage = marksSnapshot,
                                                    stickyNotes = notesSnapshot
                                                )
                                                res.onSuccess { exportedPath ->
                                                    Toast.makeText(context, "Saved to $exportedPath", Toast.LENGTH_LONG).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Print / Save as PDF") },
                                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            PdfPrintHelper.printOrExportPdf(context, Uri.parse(document.uriString), document.title)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Share Document") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            ShareUtils.shareDocument(context, document)
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Document Info") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showInfoDialog = true
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Expandable In-PDF Search Bar
                    AnimatedVisibility(visible = isSearchActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Find in document text...", fontSize = 14.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )

                                    if (isSearching) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else if (searchResults.isNotEmpty()) {
                                        Text(
                                            text = "${currentMatchIndex + 1}/${searchResults.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(onClick = { jumpToMatch(currentMatchIndex - 1) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
                                        }
                                        IconButton(onClick = { jumpToMatch(currentMatchIndex + 1) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
                                        }
                                    } else if (searchQuery.isNotBlank()) {
                                        Text("0 matches", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }

                                    IconButton(onClick = { isSearchActive = false; searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Search")
                                    }
                                }

                                if (searchResults.isNotEmpty()) {
                                    val curSnippet = searchResults.getOrNull(currentMatchIndex)
                                    if (curSnippet != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Page ${curSnippet.pageNumber}: \"${curSnippet.snippet}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                PdfViewerBottomToolbar(
                    currentVisiblePage = currentVisiblePage,
                    pageCount = session?.pageCount ?: 1,
                    viewMode = viewMode,
                    isThumbnailsOpen = showThumbnailsSheet,
                    scale = scale,
                    activeZoomOption = activeZoomOption,
                    onOpenJumpDialog = { showJumpDialog = true },
                    onSelectViewMode = { targetMode ->
                        if (targetMode != viewMode) {
                            val targetPage = (currentVisiblePage - 1).coerceAtLeast(0)
                            viewMode = targetMode
                            coroutineScope.launch {
                                scrollToPageIndex(targetPage, targetMode = targetMode)
                            }
                        }
                    },
                    onOpenThumbnails = { showThumbnailsSheet = true },
                    onZoomOut = {
                        activeZoomOption = null
                        val nextScale = (scale - 0.25f).coerceAtLeast(PdfViewerLayoutHelper.MIN_ZOOM)
                        scale = nextScale
                        if (nextScale <= 1.01f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onZoomIn = {
                        activeZoomOption = null
                        scale = (scale + 0.25f).coerceAtMost(PdfViewerLayoutHelper.MAX_ZOOM)
                    },
                    onSelectZoomOption = { option ->
                        applyZoomOption(option)
                    }
                )
            }
        },
        containerColor = Color(0xFFF6F6F2),
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorMessage ?: "Error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                session == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        YomiExpressiveLoadingIndicator(
                            size = 48.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
                else -> {
                    val currentSession = session!!

                    // Document Display Area with Zoom & Scroll support (works even while editing!)
                    val isFreeScrollActive = !isAnnotationMode || activeTool == AnnotationTool.SCROLL
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                viewportWidthPx = size.width
                                viewportHeightPx = size.height
                            }
                            .pointerInput(isFreeScrollActive) {
                                if (isFreeScrollActive) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isEditToolsExpanded) {
                                                isEditToolsExpanded = false
                                            }
                                        },
                                        onDoubleTap = {
                                            if (scale > 1.05f) {
                                                activeZoomOption = PdfZoomOption.FIT_WIDTH
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                activeZoomOption = null
                                                scale = 2.0f
                                            }
                                        }
                                    )
                                }
                            }
                            .pointerInput(isFreeScrollActive, viewMode) {
                                if (isFreeScrollActive) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextScale = (scale * zoom).coerceIn(
                                            PdfViewerLayoutHelper.MIN_ZOOM,
                                            PdfViewerLayoutHelper.MAX_ZOOM
                                        )
                                        if (abs(nextScale - scale) > 0.005f) {
                                            activeZoomOption = null
                                        }
                                        scale = nextScale
                                        if (scale > 1.01f) {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                } else {
                                    // Two-finger pinch-to-zoom, pan & page scroll while keeping 1-finger free for Highlight/Pen/Eraser/Note
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressedCount = event.changes.count { it.pressed }
                                            if (pressedCount >= 2) {
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                                    val isZoomingGesture = abs(zoomChange - 1f) > 0.01f
                                                    if (isZoomingGesture || scale > 1.04f) {
                                                        activeZoomOption = null
                                                        scale = (scale * zoomChange).coerceIn(
                                                            PdfViewerLayoutHelper.MIN_ZOOM,
                                                            PdfViewerLayoutHelper.MAX_ZOOM
                                                        )
                                                        if (scale > 1.01f) {
                                                            offsetX += panChange.x
                                                            offsetY += panChange.y
                                                        } else {
                                                            offsetX = 0f
                                                            offsetY = 0f
                                                        }
                                                    } else {
                                                        // At 100% zoom, 2-finger swipe smoothly scrolls pages without switching tools
                                                        coroutineScope.launch {
                                                            when (viewMode) {
                                                                PdfViewMode.SINGLE_PAGE -> pagerState.scrollBy(-panChange.x)
                                                                PdfViewMode.TWO_PAGE -> spreadPagerState.scrollBy(-panChange.x)
                                                                PdfViewMode.VERTICAL_CONTINUOUS -> listState.scrollBy(-panChange.y)
                                                            }
                                                        }
                                                    }
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                                rotationZ = rotationAngle
                            }
                    ) {
                        when (viewMode) {
                            PdfViewMode.SINGLE_PAGE -> {
                                // Single-Page Horizontal Flip Mode
                                HorizontalPager(
                                    state = pagerState,
                                    key = { it },
                                    userScrollEnabled = isFreeScrollActive,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    pageSpacing = 16.dp
                                ) { pageIndex ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PdfPageCard(
                                            pageIndex = pageIndex,
                                            bitmap = pageBitmaps[pageIndex],
                                            onRequestRender = {
                                                coroutineScope.launch {
                                                    if (pageBitmaps[pageIndex] == null) {
                                                        val rendered = renderer.renderPage(currentSession, pageIndex)
                                                        cachePageBitmapBounded(pageIndex, rendered, (currentVisiblePage - 1).coerceAtLeast(0))
                                                    }
                                                }
                                            },
                                            detectedLines = pageDetectedLines[pageIndex],
                                            onTextLinesDetected = { lines -> pageDetectedLines[pageIndex] = lines },
                                            marks = pageMarks[pageIndex] ?: emptyList(),
                                            stickyNotes = stickyNotes.filter { it.pageIndex == pageIndex },
                                            isAnnotationMode = isAnnotationMode,
                                            activeTool = activeTool,
                                            activePenColor = activePenColor,
                                            penStrokeWidth = penStrokeWidth,
                                            onAddMark = { mark -> recordAddMark(pageIndex, mark) },
                                            onEraseAt = { pt -> recordEraseAt(pageIndex, pt) },
                                            onMarkTextForNote = { rects ->
                                                openNoteComposer(pageIndex, rects)
                                            },
                                            onOpenNote = { note -> noteToView = note }
                                        )
                                    }
                                }
                            }

                            PdfViewMode.TWO_PAGE -> {
                                // Two-Page / Facing Pages Spread View
                                HorizontalPager(
                                    state = spreadPagerState,
                                    key = { it },
                                    userScrollEnabled = isFreeScrollActive,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                                    pageSpacing = 12.dp
                                ) { spreadIndex ->
                                    val leftIdx = PdfViewerLayoutHelper.spreadIndexToFirstPageIndex(spreadIndex)
                                    val rightIdx = leftIdx + 1
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (leftIdx < currentSession.pageCount) {
                                                PdfPageCard(
                                                    pageIndex = leftIdx,
                                                    bitmap = pageBitmaps[leftIdx],
                                                    onRequestRender = {
                                                        coroutineScope.launch {
                                                            if (pageBitmaps[leftIdx] == null) {
                                                                val rendered = renderer.renderPage(currentSession, leftIdx)
                                                                cachePageBitmapBounded(leftIdx, rendered, leftIdx)
                                                            }
                                                        }
                                                    },
                                                    detectedLines = pageDetectedLines[leftIdx],
                                                    onTextLinesDetected = { lines -> pageDetectedLines[leftIdx] = lines },
                                                    marks = pageMarks[leftIdx] ?: emptyList(),
                                                    stickyNotes = stickyNotes.filter { it.pageIndex == leftIdx },
                                                    isAnnotationMode = isAnnotationMode,
                                                    activeTool = activeTool,
                                                    activePenColor = activePenColor,
                                                    penStrokeWidth = penStrokeWidth,
                                                    onAddMark = { mark -> recordAddMark(leftIdx, mark) },
                                                    onEraseAt = { pt -> recordEraseAt(leftIdx, pt) },
                                                    onMarkTextForNote = { rects ->
                                                        openNoteComposer(leftIdx, rects)
                                                    },
                                                    onOpenNote = { note -> noteToView = note }
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (rightIdx < currentSession.pageCount) {
                                                PdfPageCard(
                                                    pageIndex = rightIdx,
                                                    bitmap = pageBitmaps[rightIdx],
                                                    onRequestRender = {
                                                        coroutineScope.launch {
                                                            if (pageBitmaps[rightIdx] == null) {
                                                                val rendered = renderer.renderPage(currentSession, rightIdx)
                                                                cachePageBitmapBounded(rightIdx, rendered, leftIdx)
                                                            }
                                                        }
                                                    },
                                                    detectedLines = pageDetectedLines[rightIdx],
                                                    onTextLinesDetected = { lines -> pageDetectedLines[rightIdx] = lines },
                                                    marks = pageMarks[rightIdx] ?: emptyList(),
                                                    stickyNotes = stickyNotes.filter { it.pageIndex == rightIdx },
                                                    isAnnotationMode = isAnnotationMode,
                                                    activeTool = activeTool,
                                                    activePenColor = activePenColor,
                                                    penStrokeWidth = penStrokeWidth,
                                                    onAddMark = { mark -> recordAddMark(rightIdx, mark) },
                                                    onEraseAt = { pt -> recordEraseAt(rightIdx, pt) },
                                                    onMarkTextForNote = { rects ->
                                                        openNoteComposer(rightIdx, rects)
                                                    },
                                                    onOpenNote = { note -> noteToView = note }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            PdfViewMode.VERTICAL_CONTINUOUS -> {
                                // Vertical Continuous Scroll Mode
                                LazyColumn(
                                    state = listState,
                                    userScrollEnabled = isFreeScrollActive,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(count = currentSession.pageCount, key = { it }) { pageIndex ->
                                        PdfPageCard(
                                            pageIndex = pageIndex,
                                            bitmap = pageBitmaps[pageIndex],
                                            onRequestRender = {
                                                coroutineScope.launch {
                                                    if (pageBitmaps[pageIndex] == null) {
                                                        val rendered = renderer.renderPage(currentSession, pageIndex)
                                                        cachePageBitmapBounded(pageIndex, rendered, (currentVisiblePage - 1).coerceAtLeast(0))
                                                    }
                                                }
                                            },
                                            detectedLines = pageDetectedLines[pageIndex],
                                            onTextLinesDetected = { lines -> pageDetectedLines[pageIndex] = lines },
                                            marks = pageMarks[pageIndex] ?: emptyList(),
                                            stickyNotes = stickyNotes.filter { it.pageIndex == pageIndex },
                                            isAnnotationMode = isAnnotationMode,
                                            activeTool = activeTool,
                                            activePenColor = activePenColor,
                                            penStrokeWidth = penStrokeWidth,
                                            onAddMark = { mark -> recordAddMark(pageIndex, mark) },
                                            onEraseAt = { pt -> recordEraseAt(pageIndex, pt) },
                                            onMarkTextForNote = { rects ->
                                                openNoteComposer(pageIndex, rects)
                                            },
                                            onOpenNote = { note -> noteToView = note }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Exit Fullscreen Button
            if (isFullscreen) {
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            CircleShape
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .size(44.dp)
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Floating PDF Editing Tools (stays inside the floating FAB / corner overlay, no editing navbar)
            AnimatedVisibility(
                visible = session != null && !isFullscreen && (isAnnotationMode || isScrollIdle),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                if (isAnnotationMode) {
                    val activePageIdx = (currentVisiblePage - 1).coerceAtLeast(0)
                    val canUndoCurrentPage = !pageUndoOps[activePageIdx].isNullOrEmpty()
                    val canRedoCurrentPage = !pageRedoOps[activePageIdx].isNullOrEmpty()
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickNoteAnchor = recentHighlightForNote
                        HighlightedPassageActionPill(
                            visible = quickNoteAnchor != null,
                            pageIndex = quickNoteAnchor?.first ?: activePageIdx,
                            accentColor = quickNoteAnchor?.third?.let { Color(it) } ?: activePenColor,
                            onAddNoteClick = {
                                quickNoteAnchor?.let { (hlPageIdx, hlRects, hlColor) ->
                                    openNoteComposer(hlPageIdx, hlRects, hlColor)
                                }
                            },
                            onDismiss = { recentHighlightForNote = null }
                        )
                        PdfFloatingEditOverlay(
                            activeTool = activeTool,
                            onSelectTool = { tool ->
                                activeTool = tool
                                if (tool == AnnotationTool.PEN) {
                                    penStrokeWidth = 4f
                                }
                            },
                            activePenColor = activePenColor,
                            onSelectPenColor = { activePenColor = it },
                            isPaletteExpanded = isEditToolsExpanded,
                            onTogglePaletteExpanded = { isEditToolsExpanded = !isEditToolsExpanded },
                            canUndo = canUndoCurrentPage,
                            canRedo = canRedoCurrentPage,
                            onUndo = { performUndo(activePageIdx) },
                            onRedo = { performRedo(activePageIdx) },
                            scale = scale,
                            onZoomOut = {
                                activeZoomOption = null
                                val nextScale = (scale - 0.25f).coerceAtLeast(PdfViewerLayoutHelper.MIN_ZOOM)
                                scale = nextScale
                                if (nextScale <= 1.01f) {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            onToggleZoomPreset = {
                                if (scale > 1.05f) {
                                    activeZoomOption = PdfZoomOption.FIT_WIDTH
                                    scale = 1f
                                } else {
                                    activeZoomOption = null
                                    scale = 1.75f
                                }
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onZoomIn = {
                                activeZoomOption = null
                                scale = (scale + 0.25f).coerceAtMost(PdfViewerLayoutHelper.MAX_ZOOM)
                            },
                            notesCount = stickyNotes.size,
                            onOpenNotesList = { showAnnotationsDialog = true },
                            onRequestPageNote = { openNoteComposer(activePageIdx, emptyList()) },
                            onDone = {
                                recentHighlightForNote = null
                                isEditToolsExpanded = false
                                isAnnotationMode = false
                                persistAnnotationsNow()
                            }
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = isEditToolsExpanded,
                            enter = fadeIn() + slideInVertically { it / 3 },
                            exit = fadeOut() + slideOutVertically { it / 3 }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfEditSpeedDialItem(
                                    label = "Highlight",
                                    icon = Icons.Default.Edit,
                                    accentColor = Color(0xFFEAB308),
                                    onClick = {
                                        isEditToolsExpanded = false
                                        activeTool = AnnotationTool.HIGHLIGHT
                                        if (activePenColor == Color(0xFF1E293B)) {
                                            activePenColor = Color(0xFFFACC15)
                                        }
                                        isAnnotationMode = true
                                    }
                                )
                                PdfEditSpeedDialItem(
                                    label = "Draw & Pen",
                                    icon = Icons.Default.Draw,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        isEditToolsExpanded = false
                                        activeTool = AnnotationTool.PEN
                                        penStrokeWidth = 4f
                                        isAnnotationMode = true
                                    }
                                )
                                PdfEditSpeedDialItem(
                                    label = "Eraser",
                                    icon = Icons.Default.CleaningServices,
                                    accentColor = Color(0xFFEA580C),
                                    onClick = {
                                        isEditToolsExpanded = false
                                        activeTool = AnnotationTool.ERASER
                                        isAnnotationMode = true
                                    }
                                )
                                PdfEditSpeedDialItem(
                                    label = "Add Note",
                                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                                    accentColor = Color(0xFF0D9488),
                                    onClick = {
                                        isEditToolsExpanded = false
                                        activeTool = AnnotationTool.NOTE
                                        isAnnotationMode = true
                                    }
                                )
                                val totalSavedAnnotations = stickyNotes.size + pageMarks.values.sumOf { it.size }
                                PdfEditSpeedDialItem(
                                    label = if (totalSavedAnnotations > 0) "Annotations ($totalSavedAnnotations)" else "Annotations",
                                    icon = Icons.AutoMirrored.Filled.Comment,
                                    accentColor = Color(0xFF4F46E5),
                                    onClick = {
                                        isEditToolsExpanded = false
                                        showAnnotationsDialog = true
                                    }
                                )
                            }
                        }

                        FloatingActionButton(
                            onClick = { isEditToolsExpanded = !isEditToolsExpanded },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditToolsExpanded) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditToolsExpanded) "Close PDF edit tools" else "PDF editing tools"
                            )
                        }
                    }
                }
            }
        }
    }

    if (onOpenRecentPdf != null) {
        RecentPdfSidePaneOverlay(
            visible = showRecentPdfsPane,
            currentDocument = document,
            currentPageNumber = currentVisiblePage,
            recentPdfs = mergedRecentPdfs,
            lastReadPageProvider = { item ->
                if (item.uriString.isNotBlank() || item.id > 0L) preferencesManager.getLastReadPage(item) else -1
            },
            onSelectPdf = { targetDoc ->
                showRecentPdfsPane = false
                persistAnnotationsNow()
                onOpenRecentPdf(targetDoc)
            },
            onDismiss = { showRecentPdfsPane = false }
        )
    }
    } // end root Box

    val activeDialogSession = session

    // 1. Jump to Page Dialog
    if (showJumpDialog && activeDialogSession != null) {
        PdfJumpToPageDialog(
            currentVisiblePage = currentVisiblePage,
            maxPage = activeDialogSession.pageCount,
            onJumpToPage = { target ->
                coroutineScope.launch {
                    scrollToPageIndex(target - 1, animate = true)
                }
            },
            onDismiss = { showJumpDialog = false }
        )
    }

    // 2. Thumbnails Bottom Sheet
    if (showThumbnailsSheet && activeDialogSession != null) {
        PdfThumbnailsSheet(
            maxPage = activeDialogSession.pageCount,
            currentVisiblePage = currentVisiblePage,
            thumbnailBitmaps = thumbnailBitmaps,
            onRequestThumbnail = { pageIdx ->
                val rendered = renderer.renderPage(activeDialogSession, pageIdx, targetWidth = 360)
                cacheThumbnailBitmapBounded(pageIdx, rendered, (currentVisiblePage - 1).coerceAtLeast(0))
            },
            onSelectPage = { pageIdx ->
                coroutineScope.launch {
                    scrollToPageIndex(pageIdx)
                    showThumbnailsSheet = false
                }
            },
            onDismiss = { showThumbnailsSheet = false }
        )
    }

    // 4. Bookmarks List Dialog
    if (showBookmarksDialog) {
        PdfBookmarksDialog(
            bookmarkedPages = bookmarkedPages.toList(),
            onSelectPage = { pageIdx ->
                coroutineScope.launch {
                    scrollToPageIndex(pageIdx)
                    showBookmarksDialog = false
                }
            },
            onRemoveBookmark = { pageIdx ->
                bookmarkedPages.remove(pageIdx)
                persistAnnotationsNow()
            },
            onDismiss = { showBookmarksDialog = false }
        )
    }

    // 5. Document Info Dialog
    if (showInfoDialog) {
        PdfDocumentInfoDialog(
            document = document,
            totalPages = session?.pageCount,
            bookmarkCount = bookmarkedPages.size,
            onDismiss = { showInfoDialog = false }
        )
    }

    // 7. Annotations Management Dialog
    if (showAnnotationsDialog) {
        val totalMarks = pageMarks.values.sumOf { it.size }
        AnnotationsDialog(
            stickyNotes = stickyNotes,
            noteSnippets = noteSnippets,
            totalStrokesCount = totalMarks,
            onJumpToPage = { targetPage ->
                coroutineScope.launch {
                    scrollToPageIndex(targetPage)
                    showAnnotationsDialog = false
                }
            },
            onDeleteNote = { note ->
                stickyNotes.remove(note)
                noteSnippets.remove(note.id)
                annotationsRepo.deleteNoteSnippet(note.id)
                pageUndoOps.getOrPut(note.pageIndex) { mutableStateListOf() }.add(
                    AnnotationHistoryOp.EraseNote(note.pageIndex, note)
                )
                pageRedoOps[note.pageIndex]?.clear()
                persistAnnotationsNow()
            },
            onClearAllDrawings = {
                pageMarks.clear()
                pageUndoOps.clear()
                pageRedoOps.clear()
                persistAnnotationsNow()
            },
            onDismiss = { showAnnotationsDialog = false }
        )
    }

    // 8. Add Sticky Note Dialog (Text-anchored or Page-level)
    if (showAddNoteDialog) {
        val noteTargetPage = pendingNotePageIndex.coerceAtLeast(0)
        val hasAnchoredText = pendingNoteAnchorRects.isNotEmpty()
        PdfAddNoteDialog(
            documentTitle = document.title,
            targetPageIndex = noteTargetPage,
            anchorRects = pendingNoteAnchorRects,
            snippetBitmap = pendingNoteSnippetBitmap,
            relativeAnchorRects = pendingNoteRelativeRects,
            initialColorValue = pendingNoteInitialColor,
            onConfirm = { noteText, selectedColorValue ->
                val newNoteId = java.util.UUID.randomUUID().toString()
                pendingNoteSnippetBitmap?.let { bmp ->
                    noteSnippets[newNoteId] = bmp
                    coroutineScope.launch {
                        annotationsRepo.saveNoteSnippet(newNoteId, bmp)
                    }
                }
                recordAddNote(
                    StickyNote(
                        id = newNoteId,
                        pageIndex = noteTargetPage,
                        text = noteText,
                        colorValue = selectedColorValue,
                        anchorRects = pendingNoteAnchorRects
                    )
                )
                pendingNoteAnchorRects = emptyList()
                pendingNoteSnippetBitmap = null
                pendingNoteRelativeRects = emptyList()
                pendingNoteInitialColor = null
                showAddNoteDialog = false
                Toast.makeText(
                    context,
                    if (hasAnchoredText) "Text note pinned on page ${noteTargetPage + 1}" else "Note added to page ${noteTargetPage + 1}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                pendingNoteAnchorRects = emptyList()
                pendingNoteSnippetBitmap = null
                pendingNoteRelativeRects = emptyList()
                pendingNoteInitialColor = null
                showAddNoteDialog = false
            }
        )
    }

    // 9. View / Delete Selected Sticky Note
    noteToView?.let { note ->
        val cachedSnippet = noteSnippets[note.id]
        val computedPair = remember(note.id, cachedSnippet, pageBitmaps[note.pageIndex]) {
            if (cachedSnippet != null && note.anchorRects.isNotEmpty()) {
                val crop = PdfTextHighlightHelper.computeSnippetCropBounds(note.anchorRects)
                if (crop != null) {
                    cachedSnippet to note.anchorRects.map { crop.remapRect(it) }
                } else {
                    cachedSnippet to emptyList()
                }
            } else if (note.anchorRects.isNotEmpty()) {
                pageBitmaps[note.pageIndex]?.let { pageBmp ->
                    PdfTextHighlightHelper.cropHighlightedSnippetBitmap(pageBmp, note.anchorRects)
                }
            } else {
                null
            }
        }
        PdfViewNoteDialog(
            documentTitle = document.title,
            note = note,
            snippetBitmap = computedPair?.first,
            relativeAnchorRects = computedPair?.second ?: emptyList(),
            onDelete = {
                stickyNotes.remove(note)
                noteSnippets.remove(note.id)
                annotationsRepo.deleteNoteSnippet(note.id)
                pageUndoOps.getOrPut(note.pageIndex) { mutableStateListOf() }.add(
                    AnnotationHistoryOp.EraseNote(note.pageIndex, note)
                )
                pageRedoOps[note.pageIndex]?.clear()
                noteToView = null
                persistAnnotationsNow()
                Toast.makeText(context, "Note removed", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { noteToView = null }
        )
    }

    // 10. Password Protected PDF Dialog
    if (showPasswordDialog) {
        PdfPasswordDialog(
            documentTitle = document.title,
            errorMessage = passwordError,
            onSubmitPassword = { _ ->
                passwordError = "Standard Android engine cannot decrypt AES-256 PDF yet. Please open an unencrypted copy."
            },
            onDismiss = {
                showPasswordDialog = false
                onBack()
            }
        )
    }
    } // end YomiTheme(darkTheme = false)
}
