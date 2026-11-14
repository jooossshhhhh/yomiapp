package com.yomi.reader.ui.viewer.annotation

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextHighlightAndAnnotationTest {

    private val sampleLines = listOf(
        DetectedTextLine(
            top = 0.10f,
            bottom = 0.13f,
            words = listOf(
                DetectedWordBox(0.10f, 0.10f, 0.20f, 0.13f),
                DetectedWordBox(0.24f, 0.10f, 0.36f, 0.13f),
                DetectedWordBox(0.40f, 0.10f, 0.55f, 0.13f)
            )
        ),
        DetectedTextLine(
            top = 0.16f,
            bottom = 0.19f,
            words = listOf(
                DetectedWordBox(0.10f, 0.16f, 0.25f, 0.19f),
                DetectedWordBox(0.29f, 0.16f, 0.48f, 0.19f)
            )
        )
    )

    @Test
    fun tapHighlight_returnsOnlyTappedWord() {
        val rects = PdfTextHighlightHelper.resolveTapHighlightRects(
            lines = sampleLines,
            tapPoint = DrawingPoint(0.28f, 0.115f)
        )

        assertEquals(1, rects.size)
        assertEquals(0.24f, rects.first().left, 0.0001f)
        assertEquals(0.36f, rects.first().right, 0.0001f)
    }

    @Test
    fun tapHighlight_missesGapBetweenWords() {
        val rects = PdfTextHighlightHelper.resolveTapHighlightRects(
            lines = sampleLines,
            tapPoint = DrawingPoint(0.22f, 0.115f)
        )

        assertTrue(rects.isEmpty())
    }

    @Test
    fun dragHighlight_keepsWordsSeparateOnSingleLine() {
        val rects = PdfTextHighlightHelper.resolveDragHighlightRects(
            lines = sampleLines,
            startPoint = DrawingPoint(0.12f, 0.115f),
            endPoint = DrawingPoint(0.34f, 0.118f)
        )

        assertEquals(2, rects.size)
        assertEquals(0.10f, rects[0].left, 0.0001f)
        assertEquals(0.20f, rects[0].right, 0.0001f)
        assertEquals(0.24f, rects[1].left, 0.0001f)
        assertEquals(0.36f, rects[1].right, 0.0001f)
    }

    @Test
    fun dragHighlight_selectsWordByWordAcrossMultipleLines() {
        val rects = PdfTextHighlightHelper.resolveDragHighlightRects(
            lines = sampleLines,
            startPoint = DrawingPoint(0.25f, 0.115f),
            endPoint = DrawingPoint(0.20f, 0.175f)
        )

        // First line: words 2 and 3; Second line: word 1
        assertEquals(3, rects.size)
        assertEquals(0.24f, rects[0].left, 0.0001f)
        assertEquals(0.40f, rects[1].left, 0.0001f)
        assertEquals(0.10f, rects[2].left, 0.0001f)
        assertEquals(0.25f, rects[2].right, 0.0001f)
    }

    @Test
    fun hitsPoint_detectsHighlightAndStrokeSeparately() {
        val highlight = PdfPageAnnotation.Highlight(
            pageIndex = 0,
            highlight = TextHighlight(
                id = "h1",
                pageIndex = 0,
                rects = listOf(NormalizedRect(0.20f, 0.30f, 0.35f, 0.33f)),
                color = Color.Yellow
            )
        )
        val stroke = PdfPageAnnotation.Stroke(
            pageIndex = 0,
            stroke = DrawingStroke(
                id = "s1",
                points = listOf(DrawingPoint(0.10f, 0.50f), DrawingPoint(0.40f, 0.50f)),
                color = Color.Red,
                strokeWidth = 6f
            )
        )

        assertTrue(highlight.hitsPoint(DrawingPoint(0.25f, 0.315f)))
        assertFalse(highlight.hitsPoint(DrawingPoint(0.80f, 0.80f)))
        assertTrue(stroke.hitsPoint(DrawingPoint(0.25f, 0.51f)))
        assertFalse(stroke.hitsPoint(DrawingPoint(0.25f, 0.65f)))
    }

    @Test
    fun computeSnippetCropBounds_padsSmallAnchorAndRemapsInsideUnitBox() {
        val anchor = NormalizedRect(left = 0.45f, top = 0.50f, right = 0.52f, bottom = 0.52f)
        val region = PdfTextHighlightHelper.computeSnippetCropBounds(listOf(anchor))

        assertNotNull(region)
        region!!
        // Enforces minimum readable horizontal context width (~0.42)
        assertTrue((region.right - region.left) >= 0.419f)
        // Includes vertical padding above and below
        assertTrue(region.top < anchor.top)
        assertTrue(region.bottom > anchor.bottom)

        val remapped = region.remapRect(anchor)
        assertTrue(remapped.left in 0.05f..0.95f)
        assertTrue(remapped.right in remapped.left..0.95f)
        assertTrue(remapped.top > 0f && remapped.bottom < 1f)
    }

    @Test
    fun computeSnippetCropBounds_emptyAnchorsReturnsNull() {
        assertNull(PdfTextHighlightHelper.computeSnippetCropBounds(emptyList()))
    }
}

