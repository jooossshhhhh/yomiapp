package com.yomi.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveFolderSafetyHelperTest {

    @Test
    fun isSupportedDriveFile_allowsPdfAndOfficeFormatsOnly() {
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Syllabus.pdf"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Budget.XLSX"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Notes.docx"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Legacy.doc"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Deck.pptx"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Sheet.xls"))
        assertTrue(DriveFolderSafetyHelper.isSupportedDriveFile("Slides.ppt"))

        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile("Photo.png"))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile("Archive.zip"))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile("README"))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile(""))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile("~\$ARMACEUTICAL_RESOURCES_DISTRIBUTION.docx"))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile(".~lock.Notes.docx#"))
        assertFalse(DriveFolderSafetyHelper.isSupportedDriveFile("Thumbs.db"))
    }

    @Test
    fun targetLocalPdfTitle_convertsOfficeExtensionsAndKeepsPdf() {
        assertEquals("Lecture1.pdf", DriveFolderSafetyHelper.targetLocalPdfTitle("Lecture1.pdf"))
        assertEquals("Budget.pdf", DriveFolderSafetyHelper.targetLocalPdfTitle("Budget.xlsx"))
        assertEquals("Proposal.pdf", DriveFolderSafetyHelper.targetLocalPdfTitle("Proposal.docx"))
        assertEquals("Deck.pdf", DriveFolderSafetyHelper.targetLocalPdfTitle("Deck.pptx"))
        assertEquals("Document.pdf", DriveFolderSafetyHelper.targetLocalPdfTitle("   "))
    }

    @Test
    fun resolveNonOverwritingPdfName_appendsCounterOnCaseInsensitiveCollision() {
        val existing = listOf("report.pdf", "Report (1).PDF", "Notes.pdf")

        assertEquals(
            "Report (2).pdf",
            DriveFolderSafetyHelper.resolveNonOverwritingPdfName("Report.pdf", existing)
        )
        assertEquals(
            "notes (1).pdf",
            DriveFolderSafetyHelper.resolveNonOverwritingPdfName("notes", existing)
        )
        assertEquals(
            "Fresh_Guide.pdf",
            DriveFolderSafetyHelper.resolveNonOverwritingPdfName("Fresh/Guide.pdf", existing)
        )
    }

    @Test
    fun validateNewSubfolderName_rejectsDuplicatesAndInvalidPaths() {
        val existing = listOf("Readings", "Exams")

        val valid = DriveFolderSafetyHelper.validateNewSubfolderName("  Handouts  ", existing)
        assertTrue(valid.isSuccess)
        assertEquals("Handouts", valid.getOrNull())

        val duplicate = DriveFolderSafetyHelper.validateNewSubfolderName("readings", existing)
        assertTrue(duplicate.isFailure)
        assertTrue(duplicate.exceptionOrNull() is IllegalStateException)

        assertTrue(DriveFolderSafetyHelper.validateNewSubfolderName("   ", existing).isFailure)
        assertTrue(DriveFolderSafetyHelper.validateNewSubfolderName("..", existing).isFailure)
        assertTrue(DriveFolderSafetyHelper.validateNewSubfolderName("a/b", existing).isFailure)
        assertTrue(DriveFolderSafetyHelper.validateNewSubfolderName("a\\b", existing).isFailure)
    }
}

