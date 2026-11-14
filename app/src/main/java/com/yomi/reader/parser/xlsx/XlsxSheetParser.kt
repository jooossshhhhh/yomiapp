package com.yomi.reader.parser.xlsx

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hssf.util.HSSFColor
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Color
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont

data class MergedCellRegion(
    val firstRow: Int,
    val lastRow: Int,
    val firstCol: Int,
    val lastCol: Int
)

data class SpreadsheetCell(
    val colIndex: Int,
    val value: String,
    val backgroundColorHex: String? = null,
    val textColorHex: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val fontSizePt: Float = 10f,
    val fontName: String? = null,
    val horizontalAlignment: String = "left",
    val verticalAlignment: String = "center",
    val hasTopBorder: Boolean = false,
    val hasBottomBorder: Boolean = false,
    val hasLeftBorder: Boolean = false,
    val hasRightBorder: Boolean = false,
    val wrapText: Boolean = false
)

data class SpreadsheetRow(
    val rowIndex: Int,
    val heightPt: Float,
    val cells: List<SpreadsheetCell>
)

data class SpreadsheetSheet(
    val name: String,
    val isLandscape: Boolean = true,
    val displayGridlines: Boolean = true,
    val columnWidthsChars: List<Float> = emptyList(),
    val mergedRegions: List<MergedCellRegion> = emptyList(),
    val richRows: List<SpreadsheetRow> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

class XlsxSheetParser(private val context: Context) {

    private fun extractColorHex(color: Color?, workbook: Workbook? = null): String? {
        if (color == null) return null
        if (color is XSSFColor) {
            val rgb = runCatching { color.rgbWithTint ?: color.rgb }.getOrNull()
            if (rgb != null && rgb.size >= 3) {
                val r = rgb[rgb.size - 3].toInt() and 0xFF
                val g = rgb[rgb.size - 2].toInt() and 0xFF
                val b = rgb[rgb.size - 1].toInt() and 0xFF
                return "%02X%02X%02X".format(r, g, b)
            }
        } else if (color is HSSFColor) {
            val triplet = color.triplet
            if (triplet != null && triplet.size >= 3) {
                // Skip automatic black/white sentinel on HSSF if needed
                return "%02X%02X%02X".format(triplet[0], triplet[1], triplet[2])
            }
        }
        return null
    }

    private fun extractFontColorHex(font: Font?, workbook: Workbook): String? {
        if (font == null) return null
        if (font is XSSFFont) {
            return extractColorHex(font.xssfColor, workbook)
        }
        if (workbook is HSSFWorkbook) {
            val hssfColor = workbook.customPalette.getColor(font.color)
            if (hssfColor != null && font.color != Font.COLOR_NORMAL) {
                return extractColorHex(hssfColor, workbook)
            }
        }
        return null
    }

    private fun formatCellSafe(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator?): String {
        if (cell == null) return ""
        return try {
            if (cell.cellType == CellType.FORMULA && evaluator != null) {
                try {
                    formatter.formatCellValue(cell, evaluator).trim()
                } catch (_: Exception) {
                    when (cell.cachedFormulaResultType) {
                        CellType.NUMERIC -> formatter.formatRawCellContents(
                            cell.numericCellValue,
                            cell.cellStyle.dataFormat.toInt(),
                            cell.cellStyle.dataFormatString
                        ).trim()
                        CellType.STRING -> cell.stringCellValue.trim()
                        CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        else -> ""
                    }
                }
            } else {
                formatter.formatCellValue(cell).trim()
            }
        } catch (_: Exception) {
            runCatching { cell.toString().trim() }.getOrDefault("")
        }
    }

    suspend fun parseSpreadsheet(uri: Uri): List<SpreadsheetSheet> = withContext(Dispatchers.IO) {
        val sheets = mutableListOf<SpreadsheetSheet>()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open spreadsheet at $uri")

        inputStream.use { stream ->
            val workbook = WorkbookFactory.create(stream)
            val formatter = DataFormatter()
            val evaluator = runCatching { workbook.creationHelper.createFormulaEvaluator() }.getOrNull()

            for (i in 0 until workbook.numberOfSheets) {
                val poiSheet = workbook.getSheetAt(i)
                val sheetName = poiSheet.sheetName ?: "Sheet ${i + 1}"

                val firstRowNum = poiSheet.firstRowNum.coerceAtLeast(0)
                val lastRowNum = poiSheet.lastRowNum.coerceAtLeast(-1)
                if (lastRowNum < firstRowNum) continue

                var maxCols = 0
                for (r in firstRowNum..lastRowNum) {
                    val row = poiSheet.getRow(r) ?: continue
                    val lc = row.lastCellNum.toInt()
                    if (lc > maxCols) maxCols = lc
                }
                if (maxCols <= 0) continue

                val colWidthsChars = (0 until maxCols).map { c ->
                    val rawUnits = poiSheet.getColumnWidth(c)
                    (rawUnits / 256f).coerceIn(3f, 60f)
                }

                val mergedRegions = poiSheet.mergedRegions.map { region ->
                    MergedCellRegion(
                        firstRow = region.firstRow,
                        lastRow = region.lastRow,
                        firstCol = region.firstColumn,
                        lastCol = region.lastColumn
                    )
                }

                val richRows = mutableListOf<SpreadsheetRow>()
                val plainRows = mutableListOf<List<String>>()

                var lastNonEmptyRowIdx = firstRowNum
                for (r in firstRowNum..lastRowNum) {
                    val row = poiSheet.getRow(r) ?: continue
                    val hasAny = (0 until maxCols).any { c ->
                        val cell = row.getCell(c)
                        cell != null && formatCellSafe(cell, formatter, evaluator).isNotEmpty()
                    }
                    if (hasAny) lastNonEmptyRowIdx = r
                }

                for (r in firstRowNum..lastNonEmptyRowIdx) {
                    val row = poiSheet.getRow(r)
                    val rowHeightPt = if (row != null && row.heightInPoints > 0f) row.heightInPoints else 16f
                    val rowCells = mutableListOf<SpreadsheetCell>()
                    val plainRow = mutableListOf<String>()

                    for (c in 0 until maxCols) {
                        val cell = row?.getCell(c)
                        val value = formatCellSafe(cell, formatter, evaluator)
                        plainRow.add(value)

                        val style = cell?.cellStyle
                        val font = if (style != null) {
                            runCatching { workbook.getFontAt(style.fontIndex) }.getOrNull()
                        } else null

                        val bgHex = if (style != null && style.fillPattern != FillPatternType.NO_FILL) {
                            extractColorHex(style.fillForegroundColorColor, workbook)
                        } else null

                        val textColorHex = extractFontColorHex(font, workbook)

                        val hAlign = when (style?.alignment) {
                            HorizontalAlignment.CENTER, HorizontalAlignment.CENTER_SELECTION -> "center"
                            HorizontalAlignment.RIGHT -> "right"
                            HorizontalAlignment.LEFT -> "left"
                            else -> {
                                val effectiveType = if (cell?.cellType == CellType.FORMULA) {
                                    runCatching { cell.cachedFormulaResultType }.getOrDefault(CellType.STRING)
                                } else {
                                    cell?.cellType ?: CellType.BLANK
                                }
                                when (effectiveType) {
                                    CellType.NUMERIC -> "right"
                                    CellType.BOOLEAN -> "center"
                                    else -> "left"
                                }
                            }
                        }

                        val vAlign = when (style?.verticalAlignment) {
                            VerticalAlignment.TOP -> "top"
                            VerticalAlignment.BOTTOM -> "bottom"
                            else -> "center"
                        }

                        val hasTop = style?.borderTop != null && style.borderTop != BorderStyle.NONE
                        val hasBottom = style?.borderBottom != null && style.borderBottom != BorderStyle.NONE
                        val hasLeft = style?.borderLeft != null && style.borderLeft != BorderStyle.NONE
                        val hasRight = style?.borderRight != null && style.borderRight != BorderStyle.NONE

                        rowCells.add(
                            SpreadsheetCell(
                                colIndex = c,
                                value = value,
                                backgroundColorHex = bgHex,
                                textColorHex = textColorHex,
                                isBold = font?.bold == true,
                                isItalic = font?.italic == true,
                                isUnderline = font != null && font.underline != 0.toByte(),
                                fontSizePt = font?.fontHeightInPoints?.toFloat()?.coerceIn(7f, 24f) ?: 10f,
                                fontName = font?.fontName?.takeIf { it.isNotBlank() },
                                horizontalAlignment = hAlign,
                                verticalAlignment = vAlign,
                                hasTopBorder = hasTop,
                                hasBottomBorder = hasBottom,
                                hasLeftBorder = hasLeft,
                                hasRightBorder = hasRight,
                                wrapText = style?.wrapText == true
                            )
                        )
                    }

                    richRows.add(SpreadsheetRow(rowIndex = r, heightPt = rowHeightPt, cells = rowCells))
                    plainRows.add(plainRow)
                }

                val isLandscape = runCatching { poiSheet.printSetup.landscape }.getOrDefault(false) ||
                        colWidthsChars.sum() > 65f

                sheets.add(
                    SpreadsheetSheet(
                        name = sheetName,
                        isLandscape = isLandscape,
                        displayGridlines = poiSheet.isDisplayGridlines,
                        columnWidthsChars = colWidthsChars,
                        mergedRegions = mergedRegions,
                        richRows = richRows,
                        rows = plainRows
                    )
                )
            }
            workbook.close()
        }
        sheets
    }
}
