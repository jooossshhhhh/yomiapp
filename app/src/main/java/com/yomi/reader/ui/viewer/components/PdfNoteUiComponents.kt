package com.yomi.reader.ui.viewer.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.ui.viewer.annotation.AnnotationTool
import com.yomi.reader.ui.viewer.annotation.NormalizedRect
import com.yomi.reader.ui.viewer.annotation.StickyNote

internal val PdfNotePalette = listOf(
    0xFFFACC15L, // Warm Yellow
    0xFF4ADE80L, // Mint Green
    0xFF38BDF8L, // Sky Blue
    0xFFFB923CL, // Warm Amber
    0xFFF472B6L, // Rose Pink
    0xFFA855F7L  // Violet
)

/**
 * YouVersion-inspired blockquote preview showing a vertical colored bar alongside
 * the cropped visual text snippet captured from the PDF page plus document/page citation.
 */
@Composable
internal fun HighlightedPassagePreviewCard(
    documentTitle: String,
    pageIndex: Int,
    accentColor: Color,
    snippetBitmap: Bitmap?,
    relativeAnchorRects: List<NormalizedRect>,
    hasAnchorRects: Boolean,
    modifier: Modifier = Modifier
) {
    val cleanTitle = documentTitle.substringBeforeLast('.').ifBlank { documentTitle }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (snippetBitmap != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                    ) {
                        Image(
                            bitmap = snippetBitmap.asImageBitmap(),
                            contentDescription = "Highlighted passage preview",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (relativeAnchorRects.isNotEmpty()) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val highlightTint = accentColor.copy(alpha = 0.30f)
                                val underlineTint = accentColor.copy(alpha = 0.85f)
                                relativeAnchorRects.forEach { rect ->
                                    val l = rect.left * size.width
                                    val t = rect.top * size.height
                                    val b = rect.bottom * size.height
                                    val w = ((rect.right - rect.left) * size.width).coerceAtLeast(2f)
                                    val h = (b - t).coerceAtLeast(4f)
                                    drawRoundRect(
                                        color = highlightTint,
                                        topLeft = Offset(l, t),
                                        size = Size(w, h),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                    drawLine(
                                        color = underlineTint,
                                        start = Offset(l, b),
                                        end = Offset(l + w, b),
                                        strokeWidth = 2.5f
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (hasAnchorRects) {
                            "Highlighted selection pinned on Page ${pageIndex + 1}"
                        } else {
                            "General page note • Drag over text in Note mode to quote a specific passage"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$cleanTitle · Page ${pageIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfAddNoteDialog(
    documentTitle: String,
    targetPageIndex: Int,
    anchorRects: List<NormalizedRect>,
    snippetBitmap: Bitmap?,
    relativeAnchorRects: List<NormalizedRect>,
    initialColorValue: Long? = null,
    onConfirm: (text: String, colorValue: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var newNoteText by remember { mutableStateOf("") }
    var selectedColorValue by remember(initialColorValue) {
        mutableLongStateOf(initialColorValue ?: PdfNotePalette.first())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val noteTargetPage = targetPageIndex.coerceAtLeast(0)
    val activeAccent = Color(selectedColorValue)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // YouVersion-inspired Top Header: Back arrow + "Note" + pill "Save" button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel note",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Note",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        if (newNoteText.isNotBlank()) {
                            onConfirm(newNoteText.trim(), selectedColorValue)
                        }
                    },
                    enabled = newNoteText.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // Soft Note Editor Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = newNoteText,
                        onValueChange = { newNoteText = it },
                        placeholder = {
                            Text(
                                text = "What would you like to say?",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 180.dp),
                        maxLines = 6
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quoted Text Snippet Preview (with YouVersion vertical accent bar + citation)
                HighlightedPassagePreviewCard(
                    documentTitle = documentTitle,
                    pageIndex = noteTargetPage,
                    accentColor = activeAccent,
                    snippetBitmap = snippetBitmap,
                    relativeAnchorRects = relativeAnchorRects,
                    hasAnchorRects = anchorRects.isNotEmpty()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(10.dp))

            // Highlight & Note Color Picker Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Highlight color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PdfNotePalette.forEach { colorVal ->
                        val isSelected = selectedColorValue == colorVal
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(colorVal))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.35f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColorValue = colorVal },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected color",
                                    tint = Color(0xFF111827),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfViewNoteDialog(
    documentTitle: String,
    note: StickyNote,
    snippetBitmap: Bitmap?,
    relativeAnchorRects: List<NormalizedRect>,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(note.color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Note on Page ${note.pageIndex + 1}")
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = note.color.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, note.color.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (snippetBitmap != null || note.anchorRects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HighlightedPassagePreviewCard(
                        documentTitle = documentTitle,
                        pageIndex = note.pageIndex,
                        accentColor = note.color,
                        snippetBitmap = snippetBitmap,
                        relativeAnchorRects = relativeAnchorRects,
                        hasAnchorRects = note.anchorRects.isNotEmpty()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}

/**
 * Floating YouVersion-inspired quick action pill shown right after highlighting text
 * so users can immediately attach a note with text preview without switching modes.
 */
@Composable
internal fun HighlightedPassageActionPill(
    visible: Boolean,
    pageIndex: Int,
    accentColor: Color,
    onAddNoteClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = "Highlighted on Page ${pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onAddNoteClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Note", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unmistakable [ - | 100% | + ] zoom stepper pill shared by the PDF bottom bar and floating editor.
 */
@Composable
internal fun PdfZoomStepperPill(
    scale: Float,
    onZoomOut: () -> Unit,
    onToggleZoomPreset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .clickable(enabled = scale > 0.51f, onClick = onZoomOut),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = if (scale > 0.51f) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clickable(onClick = onToggleZoomPreset)
                    .padding(horizontal = 4.dp)
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .clickable(enabled = scale < 3.49f, onClick = onZoomIn),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = if (scale < 3.49f) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Floating PDF Editing Overlay (replaces any docked bottom editing navbar):
 * - Expands a compact floating card above the FAB with all 5 tools (Scroll, Highlight, Pen, Eraser, Note),
 *   color swatches, framed zoom controls, and Notes list access.
 * - Keeps Undo, Redo, Done, and the Active Tool Floating Action Button always within 1-tap thumb reach
 *   at the bottom-right corner without consuming bottom-bar screen height.
 */
@Composable
internal fun PdfFloatingEditOverlay(
    activeTool: AnnotationTool,
    onSelectTool: (AnnotationTool) -> Unit,
    activePenColor: Color,
    onSelectPenColor: (Color) -> Unit,
    isPaletteExpanded: Boolean,
    onTogglePaletteExpanded: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    scale: Float,
    onZoomOut: () -> Unit,
    onToggleZoomPreset: () -> Unit,
    onZoomIn: () -> Unit,
    notesCount: Int,
    onOpenNotesList: () -> Unit,
    onRequestPageNote: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeToolAccent = when (activeTool) {
        AnnotationTool.SCROLL -> Color(0xFF475569)
        AnnotationTool.HIGHLIGHT -> Color(0xFFCA8A04)
        AnnotationTool.PEN -> MaterialTheme.colorScheme.primary
        AnnotationTool.ERASER -> Color(0xFFEA580C)
        AnnotationTool.NOTE -> Color(0xFF0D9488)
    }
    val activeToolIcon = when (activeTool) {
        AnnotationTool.SCROLL -> Icons.Default.PanTool
        AnnotationTool.HIGHLIGHT -> Icons.Default.Edit
        AnnotationTool.PEN -> Icons.Default.Draw
        AnnotationTool.ERASER -> Icons.Default.CleaningServices
        AnnotationTool.NOTE -> Icons.AutoMirrored.Filled.NoteAdd
    }
    val activeToolLabel = when (activeTool) {
        AnnotationTool.SCROLL -> "Scroll"
        AnnotationTool.HIGHLIGHT -> "Highlight"
        AnnotationTool.PEN -> "Pen"
        AnnotationTool.ERASER -> "Eraser"
        AnnotationTool.NOTE -> "Note"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = isPaletteExpanded,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                ),
                modifier = Modifier.widthIn(min = 290.dp, max = 336.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row inside floating card: Active tool badge + Notes pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = activeToolAccent.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = activeToolIcon,
                                        contentDescription = null,
                                        tint = activeToolAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = activeToolLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = activeToolAccent
                                    )
                                }
                            }
                        }

                        // Notes count button
                        Surface(
                            onClick = onOpenNotesList,
                            shape = RoundedCornerShape(10.dp),
                            color = if (notesCount > 0) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = "Notes",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Notes ($notesCount)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = activeTool == AnnotationTool.NOTE) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tap/drag text to quote",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = onRequestPageNote,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Page Note", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // All 5 editing tools evenly spaced in the floating palette
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        EditToolPillButton(
                            label = "Scroll",
                            icon = Icons.Default.PanTool,
                            selected = activeTool == AnnotationTool.SCROLL,
                            accentColor = Color(0xFF475569),
                            onClick = { onSelectTool(AnnotationTool.SCROLL) },
                            modifier = Modifier.weight(1f)
                        )
                        EditToolPillButton(
                            label = "Highlight",
                            icon = Icons.Default.Edit,
                            selected = activeTool == AnnotationTool.HIGHLIGHT,
                            accentColor = Color(0xFFCA8A04),
                            onClick = { onSelectTool(AnnotationTool.HIGHLIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        EditToolPillButton(
                            label = "Pen",
                            icon = Icons.Default.Draw,
                            selected = activeTool == AnnotationTool.PEN,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { onSelectTool(AnnotationTool.PEN) },
                            modifier = Modifier.weight(1f)
                        )
                        EditToolPillButton(
                            label = "Eraser",
                            icon = Icons.Default.CleaningServices,
                            selected = activeTool == AnnotationTool.ERASER,
                            accentColor = Color(0xFFEA580C),
                            onClick = { onSelectTool(AnnotationTool.ERASER) },
                            modifier = Modifier.weight(1f)
                        )
                        EditToolPillButton(
                            label = "Note",
                            icon = Icons.AutoMirrored.Filled.NoteAdd,
                            selected = activeTool == AnnotationTool.NOTE,
                            accentColor = Color(0xFF0D9488),
                            onClick = { onSelectTool(AnnotationTool.NOTE) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Bottom row inside floating palette: Color swatches / hint + Zoom [- | % | +]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (activeTool == AnnotationTool.ERASER) {
                            Text(
                                text = "Drag over marks to erase",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            )
                        } else if (activeTool == AnnotationTool.SCROLL) {
                            Text(
                                text = "1-finger scroll active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                val colors = listOf(
                                    Color(0xFFFACC15), // Yellow
                                    Color(0xFFEF4444), // Coral Red
                                    Color(0xFF10B981), // Green
                                    Color(0xFF38BDF8), // Blue
                                    Color(0xFF1E293B)  // Dark
                                )
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (activePenColor == color) 2.5.dp else 1.dp,
                                                color = if (activePenColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { onSelectPenColor(color) }
                                    )
                                }
                            }
                        }

                        PdfZoomStepperPill(
                            scale = scale,
                            onZoomOut = onZoomOut,
                            onToggleZoomPreset = onToggleZoomPreset,
                            onZoomIn = onZoomIn
                        )
                    }
                }
            }
        }

        // Always-available floating action cluster beside the main tool FAB
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeTool == AnnotationTool.NOTE && !isPaletteExpanded) {
                Surface(
                    onClick = onRequestPageNote,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(40.dp)
                            .padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = "Add Page Note",
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Page Note",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Floating Undo / Redo capsule
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 4.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    ToolbarActionIconBox(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        enabled = canUndo,
                        onClick = onUndo
                    )
                    ToolbarActionIconBox(
                        icon = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        enabled = canRedo,
                        onClick = onRedo
                    )
                }
            }

            // Floating Done pill
            SmallFloatingActionButton(
                onClick = onDone,
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done editing",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Main Floating Tool Icon FAB (shows active tool + color dot, toggles floating palette)
            FloatingActionButton(
                onClick = onTogglePaletteExpanded,
                shape = RoundedCornerShape(16.dp),
                containerColor = if (isPaletteExpanded) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    activeToolAccent
                },
                contentColor = if (isPaletteExpanded) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    Color.White
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPaletteExpanded) Icons.Default.Close else activeToolIcon,
                        contentDescription = if (isPaletteExpanded) "Close editing tools" else "Open editing tools ($activeToolLabel)"
                    )
                    if (!isPaletteExpanded && activeTool != AnnotationTool.SCROLL && activeTool != AnnotationTool.ERASER) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(activePenColor)
                                .border(1.5.dp, Color.White, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditToolPillButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) {
        accentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = if (selected) {
        accentColor.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val contentColor = if (selected) {
        accentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun ToolbarActionIconBox(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

