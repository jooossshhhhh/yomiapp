package com.yomi.reader.ui.viewer.annotation

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DetectedWordBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class DetectedTextLine(
    val top: Float,
    val bottom: Float,
    val words: List<DetectedWordBox>
)

object PdfTextHighlightHelper {

    /**
     * Scans a rendered PDF page [Bitmap] into normalized text lines and individual word boxes.
     * Uses high horizontal resolution and font-height-scaled kerning thresholds so words stay
     * cleanly separated word-by-word rather than merging into full sentences.
     */
    suspend fun detectTextLines(bitmap: Bitmap): List<DetectedTextLine> = withContext(Dispatchers.Default) {
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw < 24 || bh < 24 || bitmap.isRecycled) return@withContext emptyList()

        val step = max(1, min(bw, bh) / 760)
        val gridW = bw / step
        val gridH = bh / step
        if (gridW < 24 || gridH < 24) return@withContext emptyList()

        val rowPixels = IntArray(bw)
        val lumaGrid = IntArray(gridW * gridH)

        for (gy in 0 until gridH) {
            val py = gy * step
            bitmap.getPixels(rowPixels, 0, bw, 0, py, bw, 1)
            val rowOffset = gy * gridW
            for (gx in 0 until gridW) {
                val px = rowPixels[gx * step]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                lumaGrid[rowOffset + gx] = (77 * r + 150 * g + 29 * b) shr 8
            }
        }

        // Compute foreground ink mask relative to dominant local luminance in 36-col chunks
        val inkMask = BooleanArray(gridW * gridH)
        val rowInkCounts = IntArray(gridH)
        val rowLongestRun = IntArray(gridH)
        val chunkSize = 36
        val bins = IntArray(16)

        for (gy in 0 until gridH) {
            val rowOffset = gy * gridW
            var gxStart = 0
            while (gxStart < gridW) {
                val gxEnd = min(gridW, gxStart + chunkSize)
                bins.fill(0)
                for (gx in gxStart until gxEnd) {
                    val bucket = (lumaGrid[rowOffset + gx] ushr 4).coerceIn(0, 15)
                    bins[bucket]++
                }
                var bestBucket = 15
                var bestCount = -1
                for (b in 0 until 16) {
                    if (bins[b] > bestCount) {
                        bestCount = bins[b]
                        bestBucket = b
                    }
                }
                val bgLuma = bestBucket * 16 + 8
                for (gx in gxStart until gxEnd) {
                    if (abs(lumaGrid[rowOffset + gx] - bgLuma) > 36) {
                        inkMask[rowOffset + gx] = true
                        rowInkCounts[gy]++
                    }
                }
                gxStart = gxEnd
            }

            // Track longest unbroken horizontal ink run per row to identify solid rules/borders
            var run = 0
            var maxRun = 0
            for (gx in 0 until gridW) {
                if (inkMask[rowOffset + gx]) {
                    run++
                    if (run > maxRun) maxRun = run
                } else {
                    run = 0
                }
            }
            rowLongestRun[gy] = maxRun
        }

        // Suppress horizontal rules, underlines, and table/banner border edges
        val borderRunThreshold = (gridW * 0.24f).toInt().coerceAtLeast(36)
        for (gy in 0 until gridH) {
            if (rowLongestRun[gy] >= borderRunThreshold) {
                rowInkCounts[gy] = 0
                val rowOffset = gy * gridW
                for (gx in 0 until gridW) inkMask[rowOffset + gx] = false
            }
        }

        // Group active rows into tight text bands
        val minBandHeight = max(4, gridH / 190)
        val maxBandHeight = max(36, (gridH * 0.08f).toInt())
        val rawBands = mutableListOf<IntRange>()
        var bandStart = -1
        for (gy in 0 until gridH) {
            val hasInk = rowInkCounts[gy] >= 3
            if (hasInk) {
                if (bandStart == -1) bandStart = gy
            } else if (bandStart != -1) {
                val bandEnd = gy - 1
                val h = bandEnd - bandStart + 1
                if (h in minBandHeight..maxBandHeight) {
                    rawBands.add(bandStart..bandEnd)
                }
                bandStart = -1
            }
        }
        if (bandStart != -1) {
            val bandEnd = gridH - 1
            val h = bandEnd - bandStart + 1
            if (h in minBandHeight..maxBandHeight) {
                rawBands.add(bandStart..bandEnd)
            }
        }

        val lines = mutableListOf<DetectedTextLine>()
        val verticalPadNorm = 0.0016f
        val horizPadNorm = 0.0010f

        for (band in rawBands) {
            val bandHeight = band.last - band.first + 1
            val maxIntraWordGap = max(1, (bandHeight * 0.14f).toInt())
            val minColInkPixels = max(2, bandHeight / 6)

            val topNorm = (band.first.toFloat() / gridH - verticalPadNorm).coerceIn(0f, 1f)
            val bottomNorm = ((band.last + 1).toFloat() / gridH + verticalPadNorm).coerceIn(0f, 1f)

            val colActive = BooleanArray(gridW)
            for (gx in 0 until gridW) {
                var inkInCol = 0
                for (gy in band) {
                    if (inkMask[gy * gridW + gx]) {
                        inkInCol++
                    }
                }
                if (inkInCol >= minColInkPixels) {
                    colActive[gx] = true
                }
            }

            // Split cleanly on spaces so every individual word gets its own box
            val wordRanges = mutableListOf<IntRange>()
            var wStart = -1
            var gapCount = 0
            var lastInkCol = -1
            for (gx in 0 until gridW) {
                if (colActive[gx]) {
                    if (wStart == -1) {
                        wStart = gx
                    }
                    lastInkCol = gx
                    gapCount = 0
                } else if (wStart != -1) {
                    gapCount++
                    if (gapCount > maxIntraWordGap) {
                        if (lastInkCol - wStart + 1 >= 2) {
                            wordRanges.add(wStart..lastInkCol)
                        }
                        wStart = -1
                        gapCount = 0
                    }
                }
            }
            if (wStart != -1 && lastInkCol - wStart + 1 >= 2) {
                wordRanges.add(wStart..lastInkCol)
            }

            if (wordRanges.isNotEmpty()) {
                val words = wordRanges.map { range ->
                    DetectedWordBox(
                        left = (range.first.toFloat() / gridW - horizPadNorm).coerceIn(0f, 1f),
                        top = topNorm,
                        right = ((range.last + 1).toFloat() / gridW + horizPadNorm).coerceIn(0f, 1f),
                        bottom = bottomNorm
                    )
                }
                lines.add(DetectedTextLine(top = topNorm, bottom = bottomNorm, words = words))
            }
        }

        lines
    }

    /**
     * Resolves a quick tap strictly onto the single word underneath the user's finger.
     */
    fun resolveTapHighlightRects(
        lines: List<DetectedTextLine>,
        tapPoint: DrawingPoint
    ): List<NormalizedRect> {
        val hitLine = lines.firstOrNull { line ->
            tapPoint.yRatio in (line.top - 0.004f)..(line.bottom + 0.004f)
        } ?: return emptyList()

        val hitWord = hitLine.words.firstOrNull { word ->
            tapPoint.xRatio in (word.left - 0.003f)..(word.right + 0.003f)
        } ?: return emptyList()

        return listOf(NormalizedRect(hitWord.left, hitWord.top, hitWord.right, hitWord.bottom))
    }

    /**
     * Resolves a drag gesture from [startPoint] to [endPoint] into crisp word-by-word highlight
     * rectangles across all touched words on single or multiple lines.
     */
    fun resolveDragHighlightRects(
        lines: List<DetectedTextLine>,
        startPoint: DrawingPoint,
        endPoint: DrawingPoint
    ): List<NormalizedRect> {
        val minY = min(startPoint.yRatio, endPoint.yRatio)
        val maxY = max(startPoint.yRatio, endPoint.yRatio)
        val minX = min(startPoint.xRatio, endPoint.xRatio)
        val maxX = max(startPoint.xRatio, endPoint.xRatio)

        val yPad = 0.003f
        val touchedLines = lines.filter { line ->
            line.bottom >= (minY - yPad) && line.top <= (maxY + yPad)
        }

        if (touchedLines.isEmpty()) {
            // Tight straight fallback only when user explicitly drags across blank/handwritten region
            if (maxX - minX < 0.025f) return emptyList()
            val midY = (startPoint.yRatio + endPoint.yRatio) / 2f
            return listOf(
                NormalizedRect(
                    left = minX.coerceIn(0f, 1f),
                    top = (midY - 0.009f).coerceIn(0f, 1f),
                    right = maxX.coerceIn(0f, 1f),
                    bottom = (midY + 0.009f).coerceIn(0f, 1f)
                )
            )
        }

        if (touchedLines.size == 1) {
            val line = touchedLines.first()
            val matchedWords = line.words.filter { word ->
                word.right >= (minX - 0.001f) && word.left <= (maxX + 0.001f)
            }
            return matchedWords.map { word ->
                NormalizedRect(word.left, word.top, word.right, word.bottom)
            }
        }

        // Multi-line selection returning crisp individual word rectangles in reading order
        val startIsUpper = startPoint.yRatio <= endPoint.yRatio
        val topAnchorX = if (startIsUpper) startPoint.xRatio else endPoint.xRatio
        val bottomAnchorX = if (startIsUpper) endPoint.xRatio else startPoint.xRatio

        val result = mutableListOf<NormalizedRect>()
        touchedLines.forEachIndexed { idx, line ->
            val selectedWords = when (idx) {
                0 -> line.words.filter { it.right >= topAnchorX - 0.002f }
                touchedLines.lastIndex -> line.words.filter { it.left <= bottomAnchorX + 0.002f }
                else -> line.words
            }
            selectedWords.forEach { word ->
                result.add(NormalizedRect(word.left, word.top, word.right, word.bottom))
            }
        }
        return result
    }

    data class NormalizedCropRegion(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun remapRect(rect: NormalizedRect): NormalizedRect {
            val w = (right - left).coerceAtLeast(0.001f)
            val h = (bottom - top).coerceAtLeast(0.001f)
            return NormalizedRect(
                left = ((rect.left - left) / w).coerceIn(0f, 1f),
                top = ((rect.top - top) / h).coerceIn(0f, 1f),
                right = ((rect.right - left) / w).coerceIn(0f, 1f),
                bottom = ((rect.bottom - top) / h).coerceIn(0f, 1f)
            )
        }
    }

    fun computeSnippetCropBounds(anchorRects: List<NormalizedRect>): NormalizedCropRegion? {
        if (anchorRects.isEmpty()) return null
        val minL = anchorRects.minOf { it.left }.coerceIn(0f, 1f)
        val minT = anchorRects.minOf { it.top }.coerceIn(0f, 1f)
        val maxR = anchorRects.maxOf { it.right }.coerceIn(0f, 1f)
        val maxB = anchorRects.maxOf { it.bottom }.coerceIn(0f, 1f)

        val padX = 0.05f
        val padY = 0.020f
        var cropL = (minL - padX).coerceIn(0f, 1f)
        var cropR = (maxR + padX).coerceIn(0f, 1f)
        val minWidth = 0.42f
        if (cropR - cropL < minWidth) {
            val extra = (minWidth - (cropR - cropL)) / 2f
            cropL = (cropL - extra).coerceIn(0f, 1f)
            cropR = (cropR + extra).coerceIn(0f, 1f)
        }
        val cropT = (minT - padY).coerceIn(0f, 1f)
        val cropB = (maxB + padY).coerceIn(0f, 1f)
        return NormalizedCropRegion(
            left = cropL,
            top = cropT,
            right = cropR.coerceAtLeast((cropL + 0.05f).coerceAtMost(1f)),
            bottom = cropB.coerceAtLeast((cropT + 0.03f).coerceAtMost(1f))
        )
    }

    fun cropHighlightedSnippetBitmap(
        pageBitmap: Bitmap,
        anchorRects: List<NormalizedRect>
    ): Pair<Bitmap, List<NormalizedRect>>? {
        val region = computeSnippetCropBounds(anchorRects) ?: return null
        val pageW = pageBitmap.width
        val pageH = pageBitmap.height
        if (pageW <= 1 || pageH <= 1) return null

        val pxLeft = (region.left * pageW).toInt().coerceIn(0, pageW - 2)
        val pxTop = (region.top * pageH).toInt().coerceIn(0, pageH - 2)
        val pxRight = (region.right * pageW).toInt().coerceIn(pxLeft + 1, pageW)
        val pxBottom = (region.bottom * pageH).toInt().coerceIn(pxTop + 1, pageH)

        val cropped = runCatching {
            Bitmap.createBitmap(pageBitmap, pxLeft, pxTop, pxRight - pxLeft, pxBottom - pxTop)
        }.getOrNull() ?: return null

        val actualRegion = NormalizedCropRegion(
            left = pxLeft.toFloat() / pageW,
            top = pxTop.toFloat() / pageH,
            right = pxRight.toFloat() / pageW,
            bottom = pxBottom.toFloat() / pageH
        )
        val relativeRects = anchorRects.map { actualRegion.remapRect(it) }
        return cropped to relativeRects
    }
}
