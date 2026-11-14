package com.yomi.reader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicDocumentsExporterTest {

    @Test
    fun sanitizeFileName_removesIllegalCharactersAndKeepsExtension() {
        assertEquals(
            "Q1_Report_Final.pdf",
            PublicDocumentsExporter.sanitizeFileName("Q1:Report*Final.pdf")
        )
        assertEquals(
            "Notes_1_2.pdf",
            PublicDocumentsExporter.sanitizeFileName("Notes\\1/2.pdf")
        )
    }

    @Test
    fun inferMimeType_detectsPdfDocxAndFallback() {
        assertEquals("application/pdf", PublicDocumentsExporter.inferMimeType("sample.PDF"))
        assertEquals("text/plain", PublicDocumentsExporter.inferMimeType("notes.txt"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            PublicDocumentsExporter.inferMimeType("draft.docx")
        )
    }

    @Test
    fun uniqueDisplayName_appendsSuffixOnlyOnCollision() {
        assertEquals(
            "Report.pdf",
            PublicDocumentsExporter.uniqueDisplayName("Report.pdf", setOf("Other.pdf"))
        )
        assertEquals(
            "Report_1.pdf",
            PublicDocumentsExporter.uniqueDisplayName("Report.pdf", setOf("Report.pdf"))
        )
        assertEquals(
            "Report_2.pdf",
            PublicDocumentsExporter.uniqueDisplayName("Report.pdf", setOf("Report.pdf", "Report_1.pdf"))
        )
    }
}

