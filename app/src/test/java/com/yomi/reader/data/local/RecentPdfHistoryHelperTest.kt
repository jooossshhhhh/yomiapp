package com.yomi.reader.data.local

import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.ui.viewer.components.formatRecentOpenedTimestamp
import com.yomi.reader.ui.viewer.components.isSamePdfDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPdfHistoryHelperTest {

    @Test
    fun mergeRecentPdfDocuments_filtersNonPdfDeduplicatesAndSortsDescending() {
        val recorded = listOf(
            DocumentItem(
                id = 0L,
                title = "Converted_One.pdf",
                uriString = "file:///data/converted/one.pdf",
                type = DocumentType.PDF,
                sizeBytes = 1024L,
                lastModified = 100L,
                lastOpened = 5000L
            ),
            DocumentItem(
                id = 0L,
                title = "Activity_2.pdf",
                uriString = "content://docs/activity2",
                type = DocumentType.PDF,
                sizeBytes = 2048L,
                lastModified = 200L,
                lastOpened = 3000L
            )
        )
        val dbRecents = listOf(
            DocumentItem(
                id = 7L,
                title = "Activity_2.pdf",
                uriString = "content://docs/activity2",
                type = DocumentType.PDF,
                sizeBytes = 2048L,
                lastModified = 200L,
                lastOpened = 6000L,
                isFavorite = true
            ),
            DocumentItem(
                id = 8L,
                title = "Budget.xlsx",
                uriString = "content://docs/budget",
                type = DocumentType.EXCEL,
                sizeBytes = 4096L,
                lastModified = 300L,
                lastOpened = 9000L
            ),
            DocumentItem(
                id = 9L,
                title = "Chapter_4.pdf",
                uriString = "content://docs/chapter4",
                type = DocumentType.PDF,
                sizeBytes = 8192L,
                lastModified = 400L,
                lastOpened = 4000L
            )
        )

        val merged = RecentPdfHistoryHelper.mergeRecentPdfDocuments(recorded, dbRecents)

        assertEquals(listOf("Activity_2.pdf", "Converted_One.pdf", "Chapter_4.pdf"), merged.map { it.title })
        assertEquals(7L, merged.first().id)
        assertTrue(merged.first().isFavorite)
        assertEquals(6000L, merged.first().lastOpened)
    }

    @Test
    fun isSamePdfDocument_handlesZeroIdAndDistinctUris() {
        val convertedA = DocumentItem(id = 0L, uriString = "file:///a.pdf", title = "A.pdf", type = DocumentType.PDF, sizeBytes = 100L, lastModified = 1L)
        val convertedB = DocumentItem(id = 0L, uriString = "file:///b.pdf", title = "B.pdf", type = DocumentType.PDF, sizeBytes = 100L, lastModified = 1L)
        val libraryA = DocumentItem(id = 12L, uriString = "content://media/12", title = "A.pdf", type = DocumentType.PDF, sizeBytes = 100L, lastModified = 1L)
        val libraryACopy = DocumentItem(id = 12L, uriString = "content://media/12?alt=1", title = "A.pdf", type = DocumentType.PDF, sizeBytes = 100L, lastModified = 1L)

        assertFalse(isSamePdfDocument(convertedA, convertedB))
        assertTrue(isSamePdfDocument(convertedA, convertedA.copy()))
        assertTrue(isSamePdfDocument(libraryA, libraryACopy))
    }

    @Test
    fun formatRecentOpenedTimestamp_formatsRelativeIntervals() {
        val now = 10_000_000_000L
        assertEquals("Just now", formatRecentOpenedTimestamp(now - 20_000L, now))
        assertEquals("15m ago", formatRecentOpenedTimestamp(now - 15 * 60_000L, now))
        assertEquals("3h ago", formatRecentOpenedTimestamp(now - 3 * 3_600_000L, now))
        assertEquals("Yesterday", formatRecentOpenedTimestamp(now - 25 * 3_600_000L, now))
        assertEquals("4d ago", formatRecentOpenedTimestamp(now - 4 * 86_400_000L, now))
    }
}

