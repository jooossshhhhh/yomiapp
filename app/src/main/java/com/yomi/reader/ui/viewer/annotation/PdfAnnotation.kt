package com.yomi.reader.ui.viewer.annotation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot

enum class AnnotationTool {
    SCROLL,
    HIGHLIGHT,
    PEN,
    ERASER,
    NOTE
}

data class DrawingPoint(
    val xRatio: Float,
    val yRatio: Float
) {
    fun toOffset(width: Float, height: Float): Offset {
        return Offset(xRatio * width, yRatio * height)
    }

    companion object {
        fun fromOffset(offset: Offset, width: Float, height: Float): DrawingPoint {
            val safeWidth = width.coerceAtLeast(1f)
            val safeHeight = height.coerceAtLeast(1f)
            return DrawingPoint(offset.x / safeWidth, offset.y / safeHeight)
        }
    }
}

data class DrawingStroke(
    val id: String = System.nanoTime().toString(),
    val points: List<DrawingPoint>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
) {
    val displayColor: Color
        get() = if (isHighlighter) {
            color.copy(alpha = 0.40f)
        } else {
            color
        }
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(point: DrawingPoint, tolerance: Float = 0.015f): Boolean {
        return point.xRatio in (left - tolerance)..(right + tolerance) &&
            point.yRatio in (top - tolerance)..(bottom + tolerance)
    }
}

data class TextHighlight(
    val id: String = System.nanoTime().toString(),
    val pageIndex: Int,
    val rects: List<NormalizedRect>,
    val color: Color
)

sealed interface PdfPageAnnotation {
    val id: String
    val pageIndex: Int

    data class Stroke(
        override val pageIndex: Int,
        val stroke: DrawingStroke
    ) : PdfPageAnnotation {
        override val id: String get() = stroke.id
    }

    data class Highlight(
        override val pageIndex: Int,
        val highlight: TextHighlight
    ) : PdfPageAnnotation {
        override val id: String get() = highlight.id
    }
}

data class StickyNote(
    val id: String = System.currentTimeMillis().toString(),
    val pageIndex: Int,
    val text: String,
    val author: String = "You",
    val timestamp: Long = System.currentTimeMillis(),
    val colorValue: Long = 0xFFFDE047L, // Warm amber/yellow ARGB
    val anchorRects: List<NormalizedRect> = emptyList()
) {
    val color: Color
        get() = Color(colorValue)
}

sealed interface AnnotationHistoryOp {
    val pageIndex: Int

    data class AddMark(
        override val pageIndex: Int,
        val mark: PdfPageAnnotation
    ) : AnnotationHistoryOp

    data class EraseMark(
        override val pageIndex: Int,
        val mark: PdfPageAnnotation,
        val index: Int
    ) : AnnotationHistoryOp

    data class AddNote(
        override val pageIndex: Int,
        val note: StickyNote
    ) : AnnotationHistoryOp

    data class EraseNote(
        override val pageIndex: Int,
        val note: StickyNote
    ) : AnnotationHistoryOp
}

fun PdfPageAnnotation.hitsPoint(point: DrawingPoint, tolerance: Float = 0.024f): Boolean {
    return when (this) {
        is PdfPageAnnotation.Highlight -> highlight.rects.any { it.contains(point, tolerance = 0.014f) }
        is PdfPageAnnotation.Stroke -> {
            val pts = stroke.points
            if (pts.isEmpty()) return false
            if (pts.size == 1) {
                return hypot(point.xRatio - pts[0].xRatio, point.yRatio - pts[0].yRatio) <= tolerance
            }
            for (i in 0 until pts.lastIndex) {
                if (distancePointToSegment(point, pts[i], pts[i + 1]) <= tolerance) {
                    return true
                }
            }
            false
        }
    }
}

private fun distancePointToSegment(p: DrawingPoint, a: DrawingPoint, b: DrawingPoint): Float {
    val abX = b.xRatio - a.xRatio
    val abY = b.yRatio - a.yRatio
    val lenSq = abX * abX + abY * abY
    if (lenSq <= 1e-8f) {
        return hypot(p.xRatio - a.xRatio, p.yRatio - a.yRatio)
    }
    val t = (((p.xRatio - a.xRatio) * abX + (p.yRatio - a.yRatio) * abY) / lenSq).coerceIn(0f, 1f)
    val projX = a.xRatio + t * abX
    val projY = a.yRatio + t * abY
    return hypot(p.xRatio - projX, p.yRatio - projY)
}
