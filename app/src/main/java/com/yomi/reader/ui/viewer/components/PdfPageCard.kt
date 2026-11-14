package com.yomi.reader.ui.viewer.components

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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.material.icons.filled.Draw
import com.yomi.reader.data.local.PdfAnnotationsRepository
import com.yomi.reader.data.local.PreferencesManager
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
import com.yomi.reader.ui.viewer.components.PdfPasswordDialog
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.yomi.reader.util.PdfPrintHelper
import com.yomi.reader.util.ShareUtils
import kotlinx.coroutines.launch

@Composable
internal fun PdfEditSpeedDialItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            shadowElevation = 3.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = accentColor,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun PdfPageCard(
    pageIndex: Int,
    bitmap: Bitmap?,
    onRequestRender: () -> Unit,
    detectedLines: List<DetectedTextLine>?,
    onTextLinesDetected: (List<DetectedTextLine>) -> Unit,
    marks: List<PdfPageAnnotation>,
    stickyNotes: List<StickyNote>,
    isAnnotationMode: Boolean,
    activeTool: AnnotationTool,
    activePenColor: Color,
    penStrokeWidth: Float,
    onAddMark: (PdfPageAnnotation) -> Unit,
    onEraseAt: (DrawingPoint) -> Unit,
    onMarkTextForNote: (List<NormalizedRect>) -> Unit,
    onOpenNote: (StickyNote) -> Unit
) {
    LaunchedEffect(pageIndex, bitmap) {
        if (bitmap == null) {
            onRequestRender()
        } else if (detectedLines == null) {
            val lines = PdfTextHighlightHelper.detectTextLines(bitmap)
            onTextLinesDetected(lines)
        }
    }

    var currentPoints by remember { mutableStateOf<List<DrawingPoint>>(emptyList()) }
    var previewHighlightRects by remember { mutableStateOf<List<NormalizedRect>>(emptyList()) }
    var eraserPoint by remember { mutableStateOf<DrawingPoint?>(null) }

    val borderColor = when {
        !isAnnotationMode -> Color.Transparent
        activeTool == AnnotationTool.SCROLL -> Color(0xFF64748B)
        activeTool == AnnotationTool.ERASER -> Color(0xFFEA580C)
        activeTool == AnnotationTool.NOTE -> Color(0xFF0D9488)
        else -> activePenColor
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = if (isAnnotationMode) BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isAnnotationMode && activeTool != AnnotationTool.SCROLL) {
                        Modifier.pointerInput(activeTool, activePenColor, penStrokeWidth, detectedLines) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w <= 0f || h <= 0f) return@awaitEachGesture

                                val startPos = down.position
                                val startNorm = DrawingPoint(
                                    (startPos.x / w).coerceIn(0f, 1f),
                                    (startPos.y / h).coerceIn(0f, 1f)
                                )
                                val linesSnapshot = detectedLines ?: emptyList()
                                var canceledByMultiTouch = false
                                var movedBeyondTap = false

                                when (activeTool) {
                                    AnnotationTool.SCROLL -> Unit
                                    AnnotationTool.HIGHLIGHT, AnnotationTool.NOTE -> {
                                        var latestPreview = PdfTextHighlightHelper.resolveTapHighlightRects(
                                            lines = linesSnapshot,
                                            tapPoint = startNorm
                                        )
                                        previewHighlightRects = latestPreview

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                canceledByMultiTouch = true
                                                break
                                            }
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()
                                            val dx = change.position.x - startPos.x
                                            val dy = change.position.y - startPos.y
                                            if (hypot(dx, dy) >= 10f) {
                                                movedBeyondTap = true
                                            }
                                            val curNorm = DrawingPoint(
                                                (change.position.x / w).coerceIn(0f, 1f),
                                                (change.position.y / h).coerceIn(0f, 1f)
                                            )
                                            latestPreview = if (movedBeyondTap) {
                                                PdfTextHighlightHelper.resolveDragHighlightRects(
                                                    lines = linesSnapshot,
                                                    startPoint = startNorm,
                                                    endPoint = curNorm
                                                )
                                            } else {
                                                PdfTextHighlightHelper.resolveTapHighlightRects(
                                                    lines = linesSnapshot,
                                                    tapPoint = startNorm
                                                )
                                            }
                                            previewHighlightRects = latestPreview
                                        }

                                        if (!canceledByMultiTouch && latestPreview.isNotEmpty()) {
                                            if (activeTool == AnnotationTool.HIGHLIGHT) {
                                                onAddMark(
                                                    PdfPageAnnotation.Highlight(
                                                        pageIndex = pageIndex,
                                                        highlight = TextHighlight(
                                                            pageIndex = pageIndex,
                                                            rects = latestPreview,
                                                            color = activePenColor
                                                        )
                                                    )
                                                )
                                            } else {
                                                onMarkTextForNote(latestPreview)
                                            }
                                        }
                                        previewHighlightRects = emptyList()
                                    }

                                    AnnotationTool.PEN -> {
                                        var points = listOf(startNorm)
                                        currentPoints = points

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                canceledByMultiTouch = true
                                                break
                                            }
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()
                                            val curNorm = DrawingPoint(
                                                (change.position.x / w).coerceIn(0f, 1f),
                                                (change.position.y / h).coerceIn(0f, 1f)
                                            )
                                            points = points + curNorm
                                            currentPoints = points
                                        }

                                        if (!canceledByMultiTouch && points.isNotEmpty()) {
                                            onAddMark(
                                                PdfPageAnnotation.Stroke(
                                                    pageIndex = pageIndex,
                                                    stroke = DrawingStroke(
                                                        points = points,
                                                        color = activePenColor,
                                                        strokeWidth = penStrokeWidth,
                                                        isHighlighter = false
                                                    )
                                                )
                                            )
                                        }
                                        currentPoints = emptyList()
                                    }

                                    AnnotationTool.ERASER -> {
                                        eraserPoint = startNorm
                                        onEraseAt(startNorm)

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                break
                                            }
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()
                                            val curNorm = DrawingPoint(
                                                (change.position.x / w).coerceIn(0f, 1f),
                                                (change.position.y / h).coerceIn(0f, 1f)
                                            )
                                            eraserPoint = curNorm
                                            onEraseAt(curNorm)
                                        }
                                        eraserPoint = null
                                    }
                                }
                            }
                        }
                    } else if (stickyNotes.any { it.anchorRects.isNotEmpty() }) {
                        // Allow tapping directly on marked note text outside edit mode to read its note
                        Modifier.pointerInput(stickyNotes) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0f && h > 0f) {
                                    val tapNorm = DrawingPoint(offset.x / w, offset.y / h)
                                    val hitNote = stickyNotes.firstOrNull { note ->
                                        note.anchorRects.any { it.contains(tapNorm, tolerance = 0.01f) }
                                    }
                                    if (hitNote != null) {
                                        onOpenNote(hitNote)
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth()
                )

                Canvas(modifier = Modifier.matchParentSize()) {
                    // 1. Committed text highlights
                    marks.forEach { annotation ->
                        if (annotation is PdfPageAnnotation.Highlight) {
                            val highlightColor = annotation.highlight.color.copy(alpha = 0.36f)
                            annotation.highlight.rects.forEach { rect ->
                                val leftPx = rect.left * size.width
                                val topPx = rect.top * size.height
                                val rectW = ((rect.right - rect.left) * size.width).coerceAtLeast(2f)
                                val rectH = ((rect.bottom - rect.top) * size.height).coerceAtLeast(4f)
                                drawRoundRect(
                                    color = highlightColor,
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(rectW, rectH),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                    }

                    // 2. Text marks attached to Sticky Notes (soft pastel highlight + underline indicator)
                    stickyNotes.forEach { note ->
                        if (note.anchorRects.isNotEmpty()) {
                            val noteTint = note.color.copy(alpha = 0.34f)
                            val underlineColor = note.color.copy(alpha = 0.95f)
                            note.anchorRects.forEach { rect ->
                                val leftPx = rect.left * size.width
                                val topPx = rect.top * size.height
                                val bottomPx = rect.bottom * size.height
                                val rectW = ((rect.right - rect.left) * size.width).coerceAtLeast(2f)
                                val rectH = (bottomPx - topPx).coerceAtLeast(4f)
                                drawRoundRect(
                                    color = noteTint,
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(rectW, rectH),
                                    cornerRadius = CornerRadius(5f, 5f)
                                )
                                drawLine(
                                    color = underlineColor,
                                    start = Offset(leftPx, bottomPx),
                                    end = Offset(leftPx + rectW, bottomPx),
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    // 3. Live text-selection preview while Highlight or Note marking
                    if (previewHighlightRects.isNotEmpty()) {
                        val previewColor = if (activeTool == AnnotationTool.NOTE) {
                            Color(0xFF0D9488).copy(alpha = 0.30f)
                        } else {
                            activePenColor.copy(alpha = 0.36f)
                        }
                        previewHighlightRects.forEach { rect ->
                            val leftPx = rect.left * size.width
                            val topPx = rect.top * size.height
                            val rectW = ((rect.right - rect.left) * size.width).coerceAtLeast(2f)
                            val rectH = ((rect.bottom - rect.top) * size.height).coerceAtLeast(4f)
                            drawRoundRect(
                                color = previewColor,
                                topLeft = Offset(leftPx, topPx),
                                size = Size(rectW, rectH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }

                    // 4. Committed freehand Pen strokes
                    marks.forEach { annotation ->
                        if (annotation is PdfPageAnnotation.Stroke) {
                            val stroke = annotation.stroke
                            if (stroke.points.size > 1) {
                                val path = Path()
                                val first = stroke.points.first()
                                path.moveTo(first.xRatio * size.width, first.yRatio * size.height)
                                for (i in 1 until stroke.points.size) {
                                    val pt = stroke.points[i]
                                    path.lineTo(pt.xRatio * size.width, pt.yRatio * size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = if (stroke.isHighlighter) stroke.color.copy(alpha = 0.35f) else stroke.color,
                                    style = Stroke(
                                        width = if (stroke.isHighlighter) stroke.strokeWidth * 3.5f else stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else if (stroke.points.size == 1) {
                                val pt = stroke.points.first()
                                drawCircle(
                                    color = if (stroke.isHighlighter) stroke.color.copy(alpha = 0.35f) else stroke.color,
                                    radius = if (stroke.isHighlighter) stroke.strokeWidth * 1.75f else stroke.strokeWidth / 2f,
                                    center = Offset(pt.xRatio * size.width, pt.yRatio * size.height)
                                )
                            }
                        }
                    }

                    // 5. Active in-progress freehand Pen stroke
                    if (currentPoints.size > 1) {
                        val path = Path()
                        val first = currentPoints.first()
                        path.moveTo(first.xRatio * size.width, first.yRatio * size.height)
                        for (i in 1 until currentPoints.size) {
                            val pt = currentPoints[i]
                            path.lineTo(pt.xRatio * size.width, pt.yRatio * size.height)
                        }
                        drawPath(
                            path = path,
                            color = activePenColor,
                            style = Stroke(
                                width = penStrokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else if (currentPoints.size == 1) {
                        val pt = currentPoints.first()
                        drawCircle(
                            color = activePenColor,
                            radius = penStrokeWidth / 2f,
                            center = Offset(pt.xRatio * size.width, pt.yRatio * size.height)
                        )
                    }

                    // 6. Eraser cursor ring while erasing
                    eraserPoint?.let { ep ->
                        val cx = ep.xRatio * size.width
                        val cy = ep.yRatio * size.height
                        drawCircle(
                            color = Color(0xFFEA580C).copy(alpha = 0.22f),
                            radius = 22f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = Color(0xFFEA580C),
                            radius = 22f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }

                // Text-anchored Note pins positioned beside marked text
                val anchoredNotes = stickyNotes.filter { it.anchorRects.isNotEmpty() }
                val unanchoredNotes = stickyNotes.filter { it.anchorRects.isEmpty() }

                anchoredNotes.forEach { note ->
                    val anchor = note.anchorRects.last()
                    Surface(
                        shape = CircleShape,
                        color = note.color,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, Color(0xFF1F2937).copy(alpha = 0.25f)),
                        modifier = Modifier
                            .offset {
                                val badgeSizePx = 28.dp.roundToPx()
                                val maxX = (constraints.maxWidth - badgeSizePx - 4).coerceAtLeast(0)
                                val maxY = (constraints.maxHeight - badgeSizePx - 4).coerceAtLeast(0)
                                val targetX = ((anchor.right * constraints.maxWidth).toInt() + 4).coerceIn(0, maxX)
                                val targetY = ((anchor.top * constraints.maxHeight).toInt() - 4).coerceIn(0, maxY)
                                IntOffset(targetX, targetY)
                            }
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onOpenNote(note) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "View Note",
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // General unanchored page note badges in top-right corner
                if (unanchoredNotes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        unanchoredNotes.forEach { note ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = note.color,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenNote(note) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Comment,
                                        contentDescription = "Sticky Note",
                                        tint = Color(0xFF1F2937),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = note.text.take(12) + if (note.text.length > 12) "..." else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF1F2937),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}
