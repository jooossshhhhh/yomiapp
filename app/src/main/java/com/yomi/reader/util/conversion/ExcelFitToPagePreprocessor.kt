package com.yomi.reader.util.conversion

import org.apache.poi.ss.usermodel.PageMargin
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Configures Excel workbooks (.xlsx / .xls) so each non-empty sheet prints cleanly
 * onto a single PDF page without horizontal column slicing or multi-page row splits.
 */
internal object ExcelFitToPagePreprocessor {

    fun isSpreadsheetFileName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext == "xlsx" || ext == "xls"
    }

    fun prepareSinglePageWorkbookBytes(fileBytes: ByteArray, fileName: String): ByteArray {
        if (!isSpreadsheetFileName(fileName) || fileBytes.isEmpty()) return fileBytes

        return runCatching {
            val out = ByteArrayOutputStream()
            ByteArrayInputStream(fileBytes).use { input ->
                WorkbookFactory.create(input).use { workbook ->
                    for (i in 0 until workbook.numberOfSheets) {
                        val sheet = workbook.getSheetAt(i)
                        configureSheetFitToOnePage(sheet)
                    }
                    workbook.write(out)
                }
            }
            out.toByteArray().takeIf { it.isNotEmpty() } ?: fileBytes
        }.getOrDefault(fileBytes)
    }

    private fun configureSheetFitToOnePage(sheet: Sheet) {
        val (estWidthPt, estHeightPt, usedCols) = estimateSheetBoundsPt(sheet)

        // Clear any explicit manual page breaks that would split tables across pages
        sheet.rowBreaks.toList().forEach { sheet.removeRowBreak(it) }
        sheet.columnBreaks.toList().forEach { sheet.removeColumnBreak(it) }

        sheet.fitToPage = true
        sheet.autobreaks = true

        val printSetup = sheet.printSetup
        printSetup.fitWidth = 1.toShort()
        printSetup.fitHeight = 1.toShort()

        // Prefer landscape when the sheet has many columns or is naturally wider than tall
        if (printSetup.landscape || usedCols >= 6 || estWidthPt > estHeightPt * 0.85f) {
            printSetup.landscape = true
        }

        // Tighten excessive default margins so tables stay larger when fit to 1 page
        if (sheet.getMargin(PageMargin.LEFT) > 0.35) {
            sheet.setMargin(PageMargin.LEFT, 0.3)
        }
        if (sheet.getMargin(PageMargin.RIGHT) > 0.35) {
            sheet.setMargin(PageMargin.RIGHT, 0.3)
        }
        if (sheet.getMargin(PageMargin.TOP) > 0.4) {
            sheet.setMargin(PageMargin.TOP, 0.35)
        }
        if (sheet.getMargin(PageMargin.BOTTOM) > 0.4) {
            sheet.setMargin(PageMargin.BOTTOM, 0.35)
        }
    }

    private data class SheetBounds(
        val widthPt: Float,
        val heightPt: Float,
        val usedCols: Int
    )

    private fun estimateSheetBoundsPt(sheet: Sheet): SheetBounds {
        val firstRow = sheet.firstRowNum.coerceAtLeast(0)
        val lastRow = sheet.lastRowNum
        if (lastRow < firstRow) return SheetBounds(0f, 0f, 0)

        var maxCols = 0
        var totalHeightPt = 0f
        for (r in firstRow..lastRow) {
            val row = sheet.getRow(r)
            totalHeightPt += if (row != null && row.heightInPoints > 0f) row.heightInPoints else 16f
            val lc = row?.lastCellNum?.toInt() ?: 0
            if (lc > maxCols) maxCols = lc
        }
        val safeCols = maxCols.coerceAtLeast(1)
        var totalWidthPt = 0f
        for (c in 0 until safeCols) {
            val chars = (sheet.getColumnWidth(c) / 256f).coerceIn(3f, 60f)
            totalWidthPt += chars * 6.2f
        }
        return SheetBounds(
            widthPt = max(totalWidthPt, 1f),
            heightPt = max(totalHeightPt, 1f),
            usedCols = safeCols
        )
    }
}
