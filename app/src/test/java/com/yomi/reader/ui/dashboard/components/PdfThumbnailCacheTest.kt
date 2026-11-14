package com.yomi.reader.ui.dashboard.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfThumbnailCacheTest {

    @Test
    fun buildCacheKey_isDeterministicHexSha256() {
        val key1 = PdfThumbnailCache.buildCacheKey("content://media/external/file/42", 1_700_000_000_000L)
        val key2 = PdfThumbnailCache.buildCacheKey("content://media/external/file/42", 1_700_000_000_000L)

        assertEquals(key1, key2)
        assertEquals(64, key1.length)
        assertTrue(key1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun buildCacheKey_changesOnUriOrLastModifiedChange() {
        val base = PdfThumbnailCache.buildCacheKey("file:///storage/emulated/0/doc.pdf", 100L)
        val diffTimestamp = PdfThumbnailCache.buildCacheKey("file:///storage/emulated/0/doc.pdf", 200L)
        val diffUri = PdfThumbnailCache.buildCacheKey("file:///storage/emulated/0/other.pdf", 100L)

        assertNotEquals(base, diffTimestamp)
        assertNotEquals(base, diffUri)
    }
}

