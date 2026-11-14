package com.yomi.reader.ui.viewer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfViewMode(val label: String) {
    VERTICAL_CONTINUOUS("Vertical scroll"),
    SINGLE_PAGE("Single page"),
    TWO_PAGE("Two pages view")
}

enum class PdfZoomOption(val label: String, val scaleValue: Float?) {
    FIT_PAGE("Fit Page", null),
    FIT_WIDTH("Fit Width", 1.0f),
    PERCENT_50("50%", 0.5f),
    PERCENT_75("75%", 0.75f),
    PERCENT_100("100%", 1.0f),
    PERCENT_125("125%", 1.25f),
    PERCENT_150("150%", 1.5f),
    PERCENT_200("200%", 2.0f)
}

object PdfViewerLayoutHelper {
    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 3.5f

    fun computeSpreadCount(pageCount: Int): Int {
        return if (pageCount <= 0) 1 else (pageCount + 1) / 2
    }

    fun pageIndexToSpreadIndex(pageIndex: Int): Int {
        return pageIndex.coerceAtLeast(0) / 2
    }

    fun spreadIndexToFirstPageIndex(spreadIndex: Int): Int {
        return spreadIndex.coerceAtLeast(0) * 2
    }

    /**
     * Computes a zoom scale factor that keeps an entire page visible within the viewport height.
     */
    fun computeFitPageScale(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        pageBitmapWidth: Int?,
        pageBitmapHeight: Int?,
        horizontalPaddingPx: Int = 32,
        verticalPaddingPx: Int = 32
    ): Float {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return 0.75f
        val availWidth = (viewportWidthPx - horizontalPaddingPx).coerceAtLeast(1).toFloat()
        val availHeight = (viewportHeightPx - verticalPaddingPx).coerceAtLeast(1).toFloat()
        val aspectWidthOverHeight = if (pageBitmapWidth != null && pageBitmapHeight != null && pageBitmapWidth > 0 && pageBitmapHeight > 0) {
            pageBitmapWidth.toFloat() / pageBitmapHeight.toFloat()
        } else {
            8.5f / 11f
        }
        val unscaledPageHeight = availWidth / aspectWidthOverHeight
        if (unscaledPageHeight <= 0f) return 1.0f
        val fitScale = min(1.0f, availHeight / unscaledPageHeight)
        return fitScale.coerceIn(MIN_ZOOM, 1.0f)
    }

    fun formatZoomLabel(scale: Float, activeZoomOption: PdfZoomOption?): String {
        return when (activeZoomOption) {
            PdfZoomOption.FIT_PAGE -> PdfZoomOption.FIT_PAGE.label
            PdfZoomOption.FIT_WIDTH -> PdfZoomOption.FIT_WIDTH.label
            else -> "${(scale * 100f).roundToInt()}%"
        }
    }

    fun resolveOptionForScale(scale: Float, activeZoomOption: PdfZoomOption?): PdfZoomOption? {
        if (activeZoomOption == PdfZoomOption.FIT_PAGE || activeZoomOption == PdfZoomOption.FIT_WIDTH) {
            return activeZoomOption
        }
        return PdfZoomOption.entries.firstOrNull { option ->
            val target = option.scaleValue ?: return@firstOrNull false
            option != PdfZoomOption.FIT_WIDTH && abs(scale - target) < 0.015f
        }
    }
}

@Composable
internal fun PdfViewerBottomToolbar(
    currentVisiblePage: Int,
    pageCount: Int,
    viewMode: PdfViewMode,
    isThumbnailsOpen: Boolean,
    scale: Float,
    activeZoomOption: PdfZoomOption?,
    onOpenJumpDialog: () -> Unit,
    onSelectViewMode: (PdfViewMode) -> Unit,
    onOpenThumbnails: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onSelectZoomOption: (PdfZoomOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showZoomMenu by remember { mutableStateOf(false) }
    val zoomLabel = remember(scale, activeZoomOption) {
        PdfViewerLayoutHelper.formatZoomLabel(scale, activeZoomOption)
    }
    val highlightedZoomOption = remember(scale, activeZoomOption) {
        PdfViewerLayoutHelper.resolveOptionForScale(scale, activeZoomOption)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Page jump pill + Page Thumbnails grid beside it
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenJumpDialog)
                ) {
                    Text(
                        text = "$currentVisiblePage/$pageCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                ViewModeToggleIcon(
                    selected = isThumbnailsOpen,
                    contentDescription = "Page thumbnails",
                    onClick = onOpenThumbnails
                ) { tint ->
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ToolbarVerticalDivider()

            // Page viewing display modes (Vertical Scroll, One Page, Two Pages View)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ViewModeToggleIcon(
                    selected = viewMode == PdfViewMode.VERTICAL_CONTINUOUS,
                    contentDescription = "Vertical continuous view",
                    onClick = { onSelectViewMode(PdfViewMode.VERTICAL_CONTINUOUS) }
                ) { tint ->
                    ContinuousPagesGlyph(tint = tint)
                }

                ViewModeToggleIcon(
                    selected = viewMode == PdfViewMode.SINGLE_PAGE,
                    contentDescription = "One page view",
                    onClick = { onSelectViewMode(PdfViewMode.SINGLE_PAGE) }
                ) { tint ->
                    SinglePageGlyph(tint = tint)
                }

                ViewModeToggleIcon(
                    selected = viewMode == PdfViewMode.TWO_PAGE,
                    contentDescription = "Two pages view",
                    onClick = { onSelectViewMode(PdfViewMode.TWO_PAGE) }
                ) { tint ->
                    TwoPagesGlyph(tint = tint)
                }
            }

            ToolbarVerticalDivider()

            // Zoom cluster: [ (-)  [ Fit Page / 100% v ]  (+) ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ZoomCircleStepButton(
                    enabled = scale > PdfViewerLayoutHelper.MIN_ZOOM + 0.01f,
                    contentDescription = "Zoom out",
                    onClick = onZoomOut
                ) { tint ->
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (showZoomMenu) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (showZoomMenu) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                            }
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showZoomMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(start = 9.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
                        ) {
                            Text(
                                text = zoomLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Choose zoom preset",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showZoomMenu,
                        onDismissRequest = { showZoomMenu = false }
                    ) {
                        PdfZoomOption.entries.forEach { option ->
                            val isSelected = highlightedZoomOption == option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                modifier = if (isSelected) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    )
                                } else {
                                    Modifier
                                },
                                onClick = {
                                    showZoomMenu = false
                                    onSelectZoomOption(option)
                                }
                            )
                        }
                    }
                }

                ZoomCircleStepButton(
                    enabled = scale < PdfViewerLayoutHelper.MAX_ZOOM - 0.01f,
                    contentDescription = "Zoom in",
                    onClick = onZoomIn
                ) { tint ->
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarVerticalDivider() {
    Spacer(modifier = Modifier.width(6.dp))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
    Spacer(modifier = Modifier.width(6.dp))
}

@Composable
private fun ViewModeToggleIcon(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (selected) activeColor else inactiveColor

    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) activeColor.copy(alpha = 0.12f) else Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        iconContent(tint)
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .width(16.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(activeColor)
            )
        }
    }
}

@Composable
private fun ZoomCircleStepButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    iconContent: @Composable (tint: Color) -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (enabled) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) {
            iconContent(tint)
        }
    }
}

@Composable
private fun ContinuousPagesGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        val lineLeft = size.width * 0.14f
        val lineWidth = size.width * 0.72f
        val rows = 4
        val topOffset = size.height * 0.22f
        val step = size.height * 0.18f
        for (i in 0 until rows) {
            val y = topOffset + i * step
            drawLine(
                color = tint,
                start = Offset(lineLeft, y),
                end = Offset(lineLeft + lineWidth, y),
                strokeWidth = stroke
            )
        }
    }
}

@Composable
private fun SinglePageGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        val left = size.width * 0.22f
        val top = size.height * 0.14f
        val w = size.width * 0.56f
        val h = size.height * 0.72f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun TwoPagesGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.6.dp.toPx()
        val top = size.height * 0.16f
        val h = size.height * 0.68f
        val w = size.width * 0.34f
        val radius = CornerRadius(1.2.dp.toPx())
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.11f, top),
            size = Size(w, h),
            cornerRadius = radius,
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.55f, top),
            size = Size(w, h),
            cornerRadius = radius,
            style = Stroke(width = stroke)
        )
    }
}

