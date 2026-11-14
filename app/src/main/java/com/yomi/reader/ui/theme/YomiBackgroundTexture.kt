package com.yomi.reader.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private object PaperTextureTileCache {
    private val tiles = ConcurrentHashMap<Int, ImageBitmap>()

    // Soft, muted Yomi palette sprinkles so the texture feels playful yet calm
    private val SprinklePalette = listOf(
        Color(0xFFABA1C9), // Muted Logo Lavender
        Color(0xFF9AC2B4), // Muted Eucalyptus Mint
        Color(0xFFA7B8CE), // Soft Slate Periwinkle
        Color(0xFFC9C4B8), // Warm Ivory Stone
        Color(0xFFD6C2BA)  // Soft Warm Blush-Clay
    )

    fun getOrCreateTile(tileSizePx: Int): ImageBitmap {
        return tiles.getOrPut(tileSizePx) {
            buildSprinkleTextureTile(tileSizePx)
        }
    }

    private fun buildSprinkleTextureTile(tileSizePx: Int): ImageBitmap {
        val bitmap = ImageBitmap(tileSizePx, tileSizePx)
        val canvas = Canvas(bitmap)
        val rng = Random(20261114)
        val sizeF = tileSizePx.toFloat()
        val unit = sizeF / 360f // Baseline calibrated around ~120dp @ xxxhdpi

        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Fill
        }
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
        }

        // 1. Whisper-light ivory grain for subtle paper depth
        repeat(240) {
            val x = rng.nextFloat() * sizeF
            val y = rng.nextFloat() * sizeF
            val r = (0.7f + rng.nextFloat() * 0.9f) * unit
            fillPaint.color = if (it % 2 == 0) {
                Color(0xFF848073).copy(alpha = 0.025f + rng.nextFloat() * 0.02f)
            } else {
                Color.White.copy(alpha = 0.18f + rng.nextFloat() * 0.14f)
            }
            drawWrappedCircle(canvas, x, y, r, sizeF, fillPaint)
        }

        // 2. Airy, well-spaced pastel & warm-stone sprinkles
        val minSpacingSq = (46f * unit) * (46f * unit)
        val placedCenters = mutableListOf<Offset>()
        var attempts = 0
        val targetSprinkles = 28

        while (placedCenters.size < targetSprinkles && attempts < 400) {
            attempts++
            val cx = rng.nextFloat() * sizeF
            val cy = rng.nextFloat() * sizeF

            val tooClose = placedCenters.any { existing ->
                val dx = minOf(abs(cx - existing.x), sizeF - abs(cx - existing.x))
                val dy = minOf(abs(cy - existing.y), sizeF - abs(cy - existing.y))
                (dx * dx + dy * dy) < minSpacingSq
            }
            if (tooClose) continue

            placedCenters.add(Offset(cx, cy))
            val baseHue = SprinklePalette[(placedCenters.size - 1) % SprinklePalette.size]
            val alpha = 0.24f + rng.nextFloat() * 0.14f
            val sprinkleColor = baseHue.copy(alpha = alpha)

            if (placedCenters.size % 3 == 0) {
                // Tiny round dot sprinkle
                val r = (3.0f + rng.nextFloat() * 2.2f) * unit
                fillPaint.color = sprinkleColor
                drawWrappedCircle(canvas, cx, cy, r, sizeF, fillPaint)
            } else {
                // Small soft-rounded capsule sprinkle at a relaxed angle
                val angle = rng.nextFloat() * (2f * PI.toFloat())
                val halfLen = (4.2f + rng.nextFloat() * 3.6f) * unit
                val dx = cos(angle) * halfLen
                val dy = sin(angle) * halfLen
                strokePaint.strokeWidth = (3.8f + rng.nextFloat() * 1.4f) * unit
                strokePaint.color = sprinkleColor
                drawWrappedLine(
                    canvas = canvas,
                    x1 = cx - dx,
                    y1 = cy - dy,
                    x2 = cx + dx,
                    y2 = cy + dy,
                    tileSize = sizeF,
                    paint = strokePaint
                )
            }
        }

        return bitmap
    }

    private fun drawWrappedCircle(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        tileSize: Float,
        paint: Paint
    ) {
        val offsetsX = wrapOffsets(x, radius, tileSize)
        val offsetsY = wrapOffsets(y, radius, tileSize)
        for (ox in offsetsX) {
            for (oy in offsetsY) {
                canvas.drawCircle(Offset(x + ox, y + oy), radius, paint)
            }
        }
    }

    private fun drawWrappedLine(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        tileSize: Float,
        paint: Paint
    ) {
        val margin = paint.strokeWidth
        val minX = minOf(x1, x2) - margin
        val maxX = maxOf(x1, x2) + margin
        val minY = minOf(y1, y2) - margin
        val maxY = maxOf(y1, y2) + margin

        val oxList = buildList {
            add(0f)
            if (minX < 0f) add(tileSize)
            if (maxX > tileSize) add(-tileSize)
        }
        val oyList = buildList {
            add(0f)
            if (minY < 0f) add(tileSize)
            if (maxY > tileSize) add(-tileSize)
        }

        for (ox in oxList) {
            for (oy in oyList) {
                canvas.drawLine(Offset(x1 + ox, y1 + oy), Offset(x2 + ox, y2 + oy), paint)
            }
        }
    }

    private fun wrapOffsets(coord: Float, margin: Float, tileSize: Float): List<Float> = buildList {
        add(0f)
        if (coord < margin) add(tileSize)
        if (coord > tileSize - margin) add(-tileSize)
    }
}

/**
 * Applies Yomi's exact background color, layering a subtle, airy sprinkle texture
 * in light mode while keeping dark mode flat and clean.
 */
@SuppressLint("UnnecessaryComposedModifier")
fun Modifier.yomiPaperBackground(
    backgroundColor: Color? = null
): Modifier = composed {
    val baseColor = backgroundColor ?: MaterialTheme.colorScheme.background
    val isLight = baseColor.luminance() > 0.5f

    if (!isLight) {
        val glowTop = SagePrimaryContainerDark.copy(alpha = 0.20f)
        val glowMid = SageSecondaryContainerDark.copy(alpha = 0.08f)
        this.drawBehind {
            drawRect(baseColor)
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to glowTop,
                    0.26f to glowMid,
                    0.55f to Color.Transparent
                )
            )
        }
    } else {
        val density = LocalDensity.current
        val tileSizePx = remember(density.density) {
            with(density) { 120.dp.toPx().roundToInt().coerceIn(220, 520) }
        }
        val textureBrush = remember(tileSizePx) {
            val tile = PaperTextureTileCache.getOrCreateTile(tileSizePx)
            ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        }

        this.drawBehind {
            drawRect(color = baseColor)
            drawRect(brush = textureBrush)
        }
    }
}
