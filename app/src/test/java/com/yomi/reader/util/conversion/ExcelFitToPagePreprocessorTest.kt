package com.yomi.reader.util.conversion

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExcelFitToPagePreprocessorTest {

    @Test
    fun prepareSinglePageWorkbookBytes_setsFitToOnePageAndClearsManualBreaksForXlsx() {
        val rawBytes = ByteArrayOutputStream().use { out ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("Budget")
                val row = sheet.createRow(0)
                for (c in 0 until 10) {
                    row.createCell(c).setCellValue("Col $c")
                }
                sheet.setRowBreak(0)
                sheet.setColumnBreak(4)
                sheet.fitToPage = false
                wb.write(out)
            }
            out.toByteArray()
        }

        val prepared = ExcelFitToPagePreprocessor.prepareSinglePageWorkbookBytes(rawBytes, "budget.xlsx")

        ByteArrayInputStream(prepared).use { input ->
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheetAt(0)
                assertTrue(sheet.fitToPage)
                assertTrue(sheet.autobreaks)
                assertEquals(1.toShort(), sheet.printSetup.fitWidth)
                assertEquals(1.toShort(), sheet.printSetup.fitHeight)
                assertTrue(sheet.printSetup.landscape)
                assertTrue(sheet.rowBreaks.isEmpty())
                assertTrue(sheet.columnBreaks.isEmpty())
            }
        }
    }

    @Test
    fun prepareSinglePageWorkbookBytes_returnsUntouchedBytesForNonExcel() {
        val sample = byteArrayOf(1, 2, 3, 4)
        val result = ExcelFitToPagePreprocessor.prepareSinglePageWorkbookBytes(sample, "notes.docx")
        assertTrue(result === sample)
    }

    @Test
    fun computeSinglePageFit_picksLandscapeForWideSheetsAndPortraitForTallNarrowSheets() {
        val wideFit = OfflineXlsxToPdfConverter.computeSinglePageFit(
            contentWidthPt = 950f,
            contentHeightPt = 420f,
            preferLandscape = false
        )
        assertTrue(wideFit.isLandscape)
        assertTrue(wideFit.scale > 0f && wideFit.scale <= 1f)
        assertTrue(950f * wideFit.scale <= wideFit.pageWidth - 48f + 0.01f)
        assertTrue(420f * wideFit.scale <= wideFit.pageHeight - 48f + 0.01f)

        val tallNarrowFit = OfflineXlsxToPdfConverter.computeSinglePageFit(
            contentWidthPt = 280f,
            contentHeightPt = 1100f,
            preferLandscape = false
        )
        assertFalse(tallNarrowFit.isLandscape)
        assertTrue(tallNarrowFit.scale > 0f && tallNarrowFit.scale <= 1f)
        assertTrue(280f * tallNarrowFit.scale <= tallNarrowFit.pageWidth - 48f + 0.01f)
        assertTrue(1100f * tallNarrowFit.scale <= tallNarrowFit.pageHeight - 48f + 0.01f)
    }
}

