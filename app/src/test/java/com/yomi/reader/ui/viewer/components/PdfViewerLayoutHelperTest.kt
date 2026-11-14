package com.yomi.reader.ui.viewer.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfViewerLayoutHelperTest {

    @Test
    fun spreadCalculations_handleOddAndEvenPageCounts() {
        assertEquals(1, PdfViewerLayoutHelper.computeSpreadCount(0))
        assertEquals(1, PdfViewerLayoutHelper.computeSpreadCount(1))
        assertEquals(1, PdfViewerLayoutHelper.computeSpreadCount(2))
        assertEquals(2, PdfViewerLayoutHelper.computeSpreadCount(3))
        assertEquals(3, PdfViewerLayoutHelper.computeSpreadCount(5))

        assertEquals(0, PdfViewerLayoutHelper.pageIndexToSpreadIndex(0))
        assertEquals(0, PdfViewerLayoutHelper.pageIndexToSpreadIndex(1))
        assertEquals(1, PdfViewerLayoutHelper.pageIndexToSpreadIndex(2))
        assertEquals(2, PdfViewerLayoutHelper.spreadIndexToFirstPageIndex(1))
    }

    @Test
    fun computeFitPageScale_fitsTallPageIntoShortViewport() {
        // Wide landscape viewport (1200x600) with US Letter portrait page (850x1100)
        val fitScale = PdfViewerLayoutHelper.computeFitPageScale(
            viewportWidthPx = 1200,
            viewportHeightPx = 600,
            pageBitmapWidth = 850,
            pageBitmapHeight = 1100,
            horizontalPaddingPx = 0,
            verticalPaddingPx = 0
        )
        // unscaledPageHeight = 1200 / (850/1100) = 1552.94; fit = 600 / 1552.94 ≈ 0.386 -> clamped to 0.5
        assertEquals(0.5f, fitScale, 0.01f)

        // Moderate landscape/square viewport where whole page fits around ~75%
        val fitModerate = PdfViewerLayoutHelper.computeFitPageScale(
            viewportWidthPx = 1000,
            viewportHeightPx = 970,
            pageBitmapWidth = 850,
            pageBitmapHeight = 1100,
            horizontalPaddingPx = 0,
            verticalPaddingPx = 0
        )
        // unscaledPageHeight = 1000 / (850/1100) = 1294.1; fit = 970 / 1294.1 ≈ 0.7495
        assertEquals(0.75f, fitModerate, 0.01f)
    }

    @Test
    fun formatZoomLabel_andResolveOption_workForPresetsAndCustomScale() {
        assertEquals("Fit Page", PdfViewerLayoutHelper.formatZoomLabel(0.75f, PdfZoomOption.FIT_PAGE))
        assertEquals("Fit Width", PdfViewerLayoutHelper.formatZoomLabel(1.0f, PdfZoomOption.FIT_WIDTH))
        assertEquals("125%", PdfViewerLayoutHelper.formatZoomLabel(1.25f, null))
        assertEquals("175%", PdfViewerLayoutHelper.formatZoomLabel(1.75f, null))

        assertEquals(PdfZoomOption.PERCENT_50, PdfViewerLayoutHelper.resolveOptionForScale(0.5f, null))
        assertEquals(PdfZoomOption.PERCENT_150, PdfViewerLayoutHelper.resolveOptionForScale(1.5f, null))
        assertEquals(PdfZoomOption.FIT_PAGE, PdfViewerLayoutHelper.resolveOptionForScale(0.74f, PdfZoomOption.FIT_PAGE))
        assertNull(PdfViewerLayoutHelper.resolveOptionForScale(1.75f, null))
    }
}

