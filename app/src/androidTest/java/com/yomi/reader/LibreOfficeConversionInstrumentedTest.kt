package com.yomi.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.util.DocumentConverter
import com.yomi.reader.util.conversion.OfflineDocumentConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LibreOfficeConversionInstrumentedTest {

    @Test
    fun convertDocxOfflineViaLibreOfficeKit() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            val docxFile = File(context.cacheDir, "lok_smoke_test.docx")
            createSampleDocx(docxFile)

            val docxResult = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(docxFile),
                fileName = docxFile.name
            )
            assertTrue(
                "DOCX conversion failed: ${docxResult.exceptionOrNull()?.message}",
                docxResult.isSuccess
            )
            val docxPdf = docxResult.getOrThrow()
            assertValidPdf(docxPdf)

            // Verify plain text extraction works without Apache POI
            val docItem = DocumentItem(
                id = 1L,
                title = docxFile.name,
                type = DocumentType.WORD,
                uriString = Uri.fromFile(docxFile).toString(),
                sizeBytes = docxFile.length(),
                lastModified = docxFile.lastModified()
            )
            val txtResult = DocumentConverter.convertToPlainText(context, docItem)
            assertTrue(
                "Plain text extraction failed: ${txtResult.exceptionOrNull()?.message}",
                txtResult.isSuccess
            )
            val extractedText = txtResult.getOrThrow().readText()
            assertTrue(
                "Extracted text missing expected paragraph, got: $extractedText",
                extractedText.contains("Yomi LibreOfficeKit Offline Test")
            )

            docxPdf.delete()
            txtResult.getOrThrow().delete()
            docxFile.delete()
        }
    }

    @Test
    fun convertXlsxOnePagePerSheetEvenWhenWideAndTall() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val xlsxFile = File(context.cacheDir, "lok_multi_sheet_large.xlsx")
            createLargeTwoSheetXlsx(xlsxFile)

            val result = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(xlsxFile),
                fileName = xlsxFile.name
            )
            assertTrue(
                "XLSX conversion failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            val pdf = result.getOrThrow()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertEquals(
                        "Expected 1 page per sheet (2 sheets -> 2 PDF pages)",
                        2,
                        renderer.pageCount
                    )
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            assertTrue(
                                "XLSX page $i should fit standard A4 dimensions instead of oversized canvas (${page.width}x${page.height})",
                                page.width <= 900 && page.height <= 900
                            )
                        }
                    }
                }
            }
            pdf.delete()
            xlsxFile.delete()
        }
    }

    @Test
    fun convertDocxBorderlessTablesDoNotRenderBorders() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "lok_borderless_table.docx")
            createBorderlessTableDocx(docxFile)

            val result = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(docxFile),
                fileName = docxFile.name
            )
            assertTrue(
                "DOCX borderless table conversion failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            val pdf = result.getOrThrow()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertEquals("Expected 1 page for short borderless table", 1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        // Scan vertical middle line between the 2 empty cells in the second row:
                        // If a border is drawn around the empty row, pixels across the row width will be non-white.
                        var nonWhiteSpanCount = 0
                        val midYStart = (bitmap.height * 0.25).toInt()
                        val midYEnd = (bitmap.height * 0.65).toInt()
                        val xLeft = (bitmap.width * 0.20).toInt()
                        val xRight = (bitmap.width * 0.80).toInt()
                        for (y in midYStart until midYEnd) {
                            var rowNonWhite = 0
                            for (x in xLeft until xRight step 2) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.red(pixel) < 245 || Color.green(pixel) < 245 || Color.blue(pixel) < 245) {
                                    rowNonWhite++
                                }
                            }
                            // A horizontal table border spans > 70% of the sampled table width
                            if (rowNonWhite > ((xRight - xLeft) / 2) * 0.7) {
                                nonWhiteSpanCount++
                            }
                        }
                        bitmap.recycle()
                        assertEquals(
                            "Borderless DOCX table rendered horizontal border line(s) ($nonWhiteSpanCount scanlines)",
                            0,
                            nonWhiteSpanCount
                        )
                    }
                }
            }
            pdf.delete()
            docxFile.delete()
        }
    }

    @Test
    fun convertDenseAptosCalibriOnePageDocxStaysOnSinglePage() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "lok_dense_single_page.docx")
            createDenseSinglePageDocx(docxFile)

            val result = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(docxFile),
                fileName = docxFile.name
            )
            assertTrue(
                "Dense DOCX conversion failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            val pdf = result.getOrThrow()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertEquals(
                        "Dense 42-line Aptos/Calibri 1-page DOCX overflowed onto multiple pages",
                        1,
                        renderer.pageCount
                    )
                }
            }
            pdf.delete()
            docxFile.delete()
        }
    }

    private fun assertValidPdf(pdfFile: File) {
        assertTrue("PDF file does not exist: ${pdfFile.absolutePath}", pdfFile.isFile)
        assertTrue("PDF file is empty", pdfFile.length() > 100L)

        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                assertTrue("Rendered PDF has 0 pages", renderer.pageCount >= 1)
                renderer.openPage(0).use { page ->
                    assertTrue("Page width must be > 0", page.width > 0)
                    assertTrue("Page height must be > 0", page.height > 0)
                }
            }
        }
    }

    private fun createSampleDocx(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent()
            )
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent()
            )
            addEntry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:r>
                        <w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr>
                        <w:t>Yomi LibreOfficeKit Offline Test</w:t>
                      </w:r>
                    </w:p>
                  </w:body>
                </w:document>""".trimIndent()
            )
        }
    }

    private fun createBorderlessTableDocx(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>""".trimIndent()
            )
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent()
            )
            addEntry(
                "word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""".trimIndent()
            )
            addEntry(
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="table" w:styleId="TableGrid">
                    <w:name w:val="Table Grid"/>
                    <w:tblPr>
                      <w:tblBorders>
                        <w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                      </w:tblBorders>
                    </w:tblPr>
                    <w:tblStylePr w:type="firstRow">
                      <w:tblCellMar/>
                      <w:tcPr>
                        <w:tcBorders>
                          <w:bottom w:val="single" w:sz="8" w:space="0" w:color="000000"/>
                        </w:tcBorders>
                      </w:tcPr>
                    </w:tblStylePr>
                  </w:style>
                </w:styles>""".trimIndent()
            )
            addEntry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:tbl>
                      <w:tblPr>
                        <w:tblStyle w:val="TableGrid"/>
                        <w:tblW w:w="9000" w:type="dxa"/>
                        <w:tblBorders>
                          <w:top w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                          <w:left w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                          <w:bottom w:val="single" w:sz="0" w:space="0" w:color="auto"/>
                          <w:right w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                          <w:insideH w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                          <w:insideV w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                        </w:tblBorders>
                      </w:tblPr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t> </w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t> </w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc>
                          <w:tcPr>
                            <w:tcBorders>
                              <w:top w:val="nil"/>
                              <w:left w:val="nil"/>
                              <w:bottom w:val="nil"/>
                              <w:right w:val="nil"/>
                            </w:tcBorders>
                          </w:tcPr>
                          <w:p><w:r><w:t> </w:t></w:r></w:p>
                        </w:tc>
                        <w:tc><w:p><w:r><w:t> </w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>""".trimIndent()
            )
        }
    }

    private fun createDenseSinglePageDocx(target: File) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent()
            )
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent()
            )
            val paragraphs = (1..42).joinToString("") { idx ->
                """<w:p><w:pPr><w:spacing w:before="0" w:after="40" w:line="240" w:lineRule="auto"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="Aptos" w:hAnsi="Aptos"/><w:sz w:val="22"/></w:rPr><w:t>Line $idx — Metric-accurate Aptos/Calibri text line for single page verification.</w:t></w:r></w:p>"""
            }
            addEntry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    $paragraphs
                    <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
                  </w:body>
                </w:document>""".trimIndent()
            )
        }
    }

    private fun createLargeTwoSheetXlsx(target: File) {
        fun buildSheetXml(prefix: String): String {
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            for (r in 1..90) {
                sb.append("""<row r="$r">""")
                for (c in 1..18) {
                    val colLetter = ('A' + (c - 1)).toString()
                    sb.append("""<c r="$colLetter$r" t="inlineStr"><is><t>${prefix}_${r}_${c}</t></is></c>""")
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            return sb.toString()
        }

        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun addEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""".trimIndent()
            )
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent()
            )
            addEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="SheetOne" sheetId="1" r:id="rId1"/>
                    <sheet name="SheetTwo" sheetId="2" r:id="rId2"/>
                  </sheets>
                </workbook>""".trimIndent()
            )
            addEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>""".trimIndent()
            )
            addEntry("xl/worksheets/sheet1.xml", buildSheetXml("S1"))
            addEntry("xl/worksheets/sheet2.xml", buildSheetXml("S2"))
        }
    }

    @Test
    fun convertDocxWithEmbeddedImageAndShapesPreservesGraphicsAndText() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "lok_image_and_shape.docx")
            createDocxWithRedPngAndBorderlessTable(docxFile)

            val result = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(docxFile),
                fileName = docxFile.name
            )
            assertTrue(
                "DOCX image/shape conversion failed: ${result.exceptionOrNull()?.message}",
                result.isSuccess
            )
            val pdf = result.getOrThrow()
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertEquals("Expected 1 page for sample image + shape DOCX", 1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        var redPixels = 0
                        for (y in 0 until bitmap.height step 2) {
                            for (x in 0 until bitmap.width step 2) {
                                val pixel = bitmap.getPixel(x, y)
                                if (Color.red(pixel) > 180 && Color.green(pixel) < 80 && Color.blue(pixel) < 80) {
                                    redPixels++
                                }
                            }
                        }
                        bitmap.recycle()
                        assertTrue(
                            "Expected embedded PNG image in DOCX to render visible pixels (found $redPixels)",
                            redPixels > 50
                        )
                    }
                }
            }
            pdf.delete()
            docxFile.delete()
        }
    }

    private fun createDocxWithRedPngAndBorderlessTable(target: File) {
        val pngBmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(220, 20, 20))
        }
        val pngBytes = java.io.ByteArrayOutputStream().use { baos ->
            pngBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            pngBmp.recycle()
            baos.toByteArray()
        }

        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun addEntry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="png" ContentType="image/png"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            addEntry(
                "word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            addEntry("word/media/image1.png", pngBytes)
            addEntry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
                            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                            mc:Ignorable="wps">
                  <w:body>
                    <w:tbl>
                      <w:tblPr>
                        <w:tblBorders>
                          <w:top w:val="none" w:sz="0" w:color="auto"/>
                          <w:left w:val="none" w:sz="0" w:color="auto"/>
                          <w:bottom w:val="none" w:sz="0" w:color="auto"/>
                          <w:right w:val="none" w:sz="0" w:color="auto"/>
                          <w:insideH w:val="none" w:sz="0" w:color="auto"/>
                          <w:insideV w:val="none" w:sz="0" w:color="auto"/>
                        </w:tblBorders>
                      </w:tblPr>
                      <w:tr><w:tc><w:p><w:r><w:t xml:space="preserve">Figure Caption Test </w:t></w:r></w:p></w:tc></w:tr>
                    </w:tbl>
                    <w:p>
                      <w:r>
                        <w:drawing>
                          <wp:inline distT="0" distB="0" distL="0" distR="0">
                            <wp:extent cx="1828800" cy="1219200"/>
                            <wp:docPr id="1" name="RedBox"/>
                            <a:graphic>
                              <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                <pic:pic>
                                  <pic:nvPicPr><pic:cNvPr id="0" name="image1.png"/><pic:cNvPicPr/></pic:nvPicPr>
                                  <pic:blipFill><a:blip r:embed="rIdImg1"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                  <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                </pic:pic>
                              </a:graphicData>
                            </a:graphic>
                          </wp:inline>
                        </w:drawing>
                      </w:r>
                    </w:p>
                  </w:body>
                </w:document>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
        }
    }

    @Test
    fun convertDocxFloatingTablesBreakPagesAndExtendedGraphicsPreserveOrderAndPixels() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "lok_layout_graphics_regression.docx")

            fun makeSolidPng(r: Int, g: Int, b: Int): ByteArray {
                val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(r, g, b))
                }
                return java.io.ByteArrayOutputStream().use { baos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    bmp.recycle()
                    baos.toByteArray()
                }
            }

            val redPng = makeSolidPng(220, 20, 20)
            val greenPng = makeSolidPng(20, 200, 20)
            val bluePng = makeSolidPng(20, 40, 220)
            val yellowPng = makeSolidPng(230, 210, 20)
            val webpBytes = java.io.ByteArrayOutputStream().use { baos ->
                val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.rgb(180, 20, 180))
                }
                @Suppress("DEPRECATION")
                bmp.compress(Bitmap.CompressFormat.WEBP, 95, baos)
                bmp.recycle()
                baos.toByteArray()
            }

            ZipOutputStream(FileOutputStream(docxFile)).use { zip ->
                fun addEntry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                addEntry(
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Default Extension="png" ContentType="image/png"/>
                      <Default Extension="webp" ContentType="image/webp"/>
                      <Default Extension="svg" ContentType="image/svg+xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>""".trimIndent().toByteArray(Charsets.UTF_8)
                )
                addEntry(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
                )
                addEntry(
                    "word/_rels/document.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rIdRed" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/red.png"/>
                      <Relationship Id="rIdGreen" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/green.png"/>
                      <Relationship Id="rIdBlue" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/blue.png"/>
                      <Relationship Id="rIdYellow" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/yellow.png"/>
                      <Relationship Id="rIdPurple" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/purple.webp"/>
                      <Relationship Id="rIdCyan" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/cyan.svg"/>
                    </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
                )
                addEntry("word/media/red.png", redPng)
                addEntry("word/media/green.png", greenPng)
                addEntry("word/media/blue.png", bluePng)
                addEntry("word/media/yellow.png", yellowPng)
                addEntry("word/media/purple.webp", webpBytes)
                addEntry(
                    "word/media/cyan.svg",
                    """<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="120" height="80" fill="rgb(20,210,210)"/></svg>""".toByteArray(Charsets.UTF_8)
                )

                val fillerParas = (1..20).joinToString("") {
                    """<w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr><w:r><w:t>Filler line $it before floating table five.</w:t></w:r></w:p>"""
                }
                // Floating Table 5 shaded light blue-purple vs Table 6 shaded green so vertical order is verifiable visually
                val floatingTblRows = (1..8).joinToString("") { r ->
                    """<w:tr><w:tc><w:tcPr><w:shd w:val="clear" w:fill="FFCC00"/></w:tcPr><w:p><w:r><w:t>FLOAT_TBL_ROW_$r</w:t></w:r></w:p></w:tc></w:tr>"""
                }
                val nearFullPageParas = (1..22).joinToString("") {
                    """<w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr><w:r><w:rPr><w:sz w:val="24"/></w:rPr><w:t>Near bottom line $it</w:t></w:r></w:p>"""
                }

                addEntry(
                    "word/document.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                                xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                                xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
                                xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                                xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
                                xmlns:asvg="http://schemas.microsoft.com/office/drawing/2016/SVG/main"
                                xmlns:v="urn:schemas-microsoft-com:vml"
                                xmlns:o="urn:schemas-microsoft-com:office:office"
                                mc:Ignorable="wps wpg asvg">
                      <w:body>
                        $fillerParas
                        <w:p><w:r><w:t>CAPTION_TABLE_5</w:t></w:r></w:p>
                        <w:tbl>
                          <w:tblPr>
                            <w:tblpPr w:leftFromText="180" w:rightFromText="180" w:vertAnchor="text" w:horzAnchor="margin" w:tblpXSpec="center" w:tblpY="1"/>
                            <w:tblW w:w="9000" w:type="dxa"/>
                            <w:tblBorders>
                              <w:top w:val="single" w:sz="6" w:color="000000"/>
                              <w:bottom w:val="single" w:sz="6" w:color="000000"/>
                            </w:tblBorders>
                          </w:tblPr>
                          <w:tblGrid><w:gridCol w:w="9000"/></w:tblGrid>
                          $floatingTblRows
                        </w:tbl>
                        <w:p><w:r><w:t>CAPTION_TABLE_6</w:t></w:r></w:p>
                        <w:tbl>
                          <w:tblPr><w:tblW w:w="9000" w:type="dxa"/></w:tblPr>
                          <w:tblGrid><w:gridCol w:w="9000"/></w:tblGrid>
                          <w:tr><w:tc><w:p><w:r><w:t>NEXT_TBL_ROW_1</w:t></w:r></w:p></w:tc></w:tr>
                        </w:tbl>
                        <w:p><w:r><w:br w:type="page"/></w:r></w:p>
                        $nearFullPageParas
                        <w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr><w:r><w:rPr><w:sz w:val="24"/></w:rPr><w:br w:type="page"/></w:r></w:p>
                        <w:p><w:r><w:t>AFTER_NEAR_FULL_PAGE_BREAK</w:t></w:r></w:p>
                        <w:p>
                          <w:r>
                            <w:drawing>
                              <wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="1" behindDoc="1" locked="0" layoutInCell="1" allowOverlap="1">
                                <wp:simplePos x="0" y="0"/>
                                <wp:positionH relativeFrom="margin"><wp:align>center</wp:align></wp:positionH>
                                <wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV>
                                <wp:extent cx="1828800" cy="1219200"/>
                                <wp:effectExtent l="0" t="0" r="0" b="0"/>
                                <wp:wrapNone/>
                                <wp:docPr id="10" name="RedAnchorBehindWrapNone"/>
                                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                  <pic:pic>
                                    <pic:nvPicPr><pic:cNvPr id="10" name="red.png"/><pic:cNvPicPr/></pic:nvPicPr>
                                    <pic:blipFill><a:blip r:embed="rIdRed"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                    <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                  </pic:pic>
                                </a:graphicData></a:graphic>
                              </wp:anchor>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:drawing>
                              <wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="2" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1">
                                <wp:simplePos x="0" y="0"/>
                                <wp:positionH relativeFrom="margin"><wp:align>center</wp:align></wp:positionH>
                                <wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV>
                                <wp:extent cx="1828800" cy="1219200"/>
                                <wp:effectExtent l="0" t="0" r="0" b="0"/>
                                <wp:wrapTopAndBottom/>
                                <wp:docPr id="11" name="GreenAnchorTopBottom"/>
                                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                  <pic:pic>
                                    <pic:nvPicPr><pic:cNvPr id="11" name="green.png"/><pic:cNvPicPr/></pic:nvPicPr>
                                    <pic:blipFill><a:blip r:embed="rIdGreen"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                    <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                  </pic:pic>
                                </a:graphicData></a:graphic>
                              </wp:anchor>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:drawing>
                              <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="1828800" cy="1219200"/>
                                <wp:docPr id="12" name="BlueGroup"/>
                                <a:graphic><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup">
                                  <wpg:wgp>
                                    <wpg:cNvGrpSpPr/><wpg:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/><a:chOff x="0" y="0"/><a:chExt cx="1828800" cy="1219200"/></a:xfrm></wpg:grpSpPr>
                                    <pic:pic>
                                      <pic:nvPicPr><pic:cNvPr id="120" name="blue.png"/><pic:cNvPicPr/></pic:nvPicPr>
                                      <pic:blipFill><a:blip r:embed="rIdBlue"><a:srcRect l="10000" t="-20000" r="10000" b="50000"/></a:blip><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                      <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                    </pic:pic>
                                  </wpg:wgp>
                                </a:graphicData></a:graphic>
                              </wp:inline>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:pict><v:shape style="width:144pt;height:96pt"><v:imagedata r:id="rIdYellow"/></v:shape></w:pict>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:drawing>
                              <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="1828800" cy="1219200"/>
                                <wp:docPr id="13" name="PurpleWebp"/>
                                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                  <pic:pic>
                                    <pic:nvPicPr><pic:cNvPr id="13" name="purple.webp"/><pic:cNvPicPr/></pic:nvPicPr>
                                    <pic:blipFill><a:blip r:embed="rIdPurple"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                    <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                  </pic:pic>
                                </a:graphicData></a:graphic>
                              </wp:inline>
                            </w:drawing>
                          </w:r>
                        </w:p>
                        <w:p>
                          <w:r>
                            <w:drawing>
                              <wp:inline distT="0" distB="0" distL="0" distR="0">
                                <wp:extent cx="1828800" cy="1219200"/>
                                <wp:docPr id="14" name="CyanSvgFallbackOnly"/>
                                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                  <pic:pic>
                                    <pic:nvPicPr><pic:cNvPr id="14" name="cyan.svg"/><pic:cNvPicPr/></pic:nvPicPr>
                                    <pic:blipFill><a:blip><a:extLst><a:ext uri="{96DAC541-7B7A-43D3-8B79-37D633B846F1}"><asvg:svgBlip r:embed="rIdCyan"/></a:ext></a:extLst></a:blip><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                                    <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                                  </pic:pic>
                                </a:graphicData></a:graphic>
                              </wp:inline>
                            </w:drawing>
                          </w:r>
                        </w:p>
                      </w:body>
                    </w:document>""".trimIndent().toByteArray(Charsets.UTF_8)
                )
            }

            val result = OfflineDocumentConverter.convertToPdf(
                context = context,
                fileUri = Uri.fromFile(docxFile),
                fileName = docxFile.name
            )
            assertTrue("DOCX conversion failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val pdf = result.getOrThrow()

            var totalRed = 0
            var totalGreen = 0
            var totalBlue = 0
            var totalYellow = 0
            var totalPurple = 0
            var totalCyan = 0

            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    assertTrue("Expected >= 4 pages, got ${renderer.pageCount}", renderer.pageCount >= 4)
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val bmp = Bitmap.createBitmap(page.width / 2, page.height / 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            var nonWhite = 0
                            for (y in 0 until bmp.height step 2) {
                                for (x in 0 until bmp.width step 2) {
                                    val p = bmp.getPixel(x, y)
                                    val rr = Color.red(p); val gg = Color.green(p); val bb = Color.blue(p)
                                    if (rr < 245 || gg < 245 || bb < 245) nonWhite++
                                    if (rr > 180 && gg < 80 && bb < 80) totalRed++
                                    if (gg > 160 && rr < 80 && bb < 80) totalGreen++
                                    if (bb > 180 && rr < 80 && gg < 100) totalBlue++
                                    if (rr > 190 && gg > 170 && bb < 80) totalYellow++
                                    if (rr > 140 && bb > 140 && gg < 80) totalPurple++
                                    if (gg > 170 && bb > 170 && rr < 80) totalCyan++
                                }
                            }
                            bmp.recycle()
                            assertTrue(
                                "Rendered page ${i + 1} of ${renderer.pageCount} should not be a phantom blank spillover page (nw=$nonWhite)",
                                nonWhite > 50
                            )
                        }
                    }
                }
            }

            assertTrue("Expected Red anchored graphic to render (pixels=$totalRed)", totalRed > 100)
            assertTrue("Expected Green top/bottom anchored graphic to render (pixels=$totalGreen)", totalGreen > 100)
            assertTrue("Expected Blue wpg group cropped graphic to render (pixels=$totalBlue)", totalBlue > 100)
            assertTrue("Expected Yellow VML graphic to render (pixels=$totalYellow)", totalYellow > 100)
            assertTrue("Expected Purple WebP graphic to render (pixels=$totalPurple)", totalPurple > 100)
            assertTrue("Expected Cyan svgBlip-fallback graphic to render (pixels=$totalCyan)", totalCyan > 100)

            pdf.delete()
            docxFile.delete()
        }
    }

    private data class ColorCounts(
        val nonWhite: Int = 0,
        val red: Int = 0,
        val green: Int = 0,
        val blue: Int = 0,
        val orange: Int = 0,
        val magenta: Int = 0,
        val cyan: Int = 0,
        val blueMinY: Int = Int.MAX_VALUE,
        val blueMaxY: Int = -1
    )

    private fun buildColoredProbeDocx(destFile: File, bodyXml: String) {
        fun makeSolidPng(r: Int, g: Int, b: Int): ByteArray {
            val bmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(r, g, b)) }
            return java.io.ByteArrayOutputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                bmp.recycle()
                it.toByteArray()
            }
        }
        ZipOutputStream(FileOutputStream(destFile)).use { zip ->
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            add(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Default Extension="png" ContentType="image/png"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            add(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            add(
                "word/_rels/document.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdR" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/r.png"/>
                  <Relationship Id="rIdG" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/g.png"/>
                  <Relationship Id="rIdB" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/b.png"/>
                  <Relationship Id="rIdO" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/o.png"/>
                  <Relationship Id="rIdM" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/m.png"/>
                  <Relationship Id="rIdC" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/c.png"/>
                </Relationships>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
            add("word/media/r.png", makeSolidPng(220, 20, 20))
            add("word/media/g.png", makeSolidPng(20, 200, 20))
            add("word/media/b.png", makeSolidPng(20, 40, 220))
            add("word/media/o.png", makeSolidPng(235, 130, 10))
            add("word/media/m.png", makeSolidPng(200, 20, 200))
            add("word/media/c.png", makeSolidPng(20, 200, 210))
            add(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                            xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
                            xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                            xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup"
                            xmlns:v="urn:schemas-microsoft-com:vml"
                            xmlns:o="urn:schemas-microsoft-com:office:office"
                            mc:Ignorable="wps wpg">
                  <w:body>$bodyXml</w:body>
                </w:document>""".trimIndent().toByteArray(Charsets.UTF_8)
            )
        }
    }

    private fun inspectRenderedPdfColors(pdfFile: File): List<ColorCounts> {
        val pages = mutableListOf<ColorCounts>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val w = page.width
                        val h = page.height
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val px = IntArray(w * h)
                        bmp.getPixels(px, 0, w, 0, 0, w, h)
                        bmp.recycle()

                        var nw = 0; var r = 0; var g = 0; var b = 0; var o = 0; var m = 0; var c = 0
                        var bMinY = Int.MAX_VALUE; var bMaxY = -1
                        for (idx in px.indices) {
                            val y = idx / w
                            val p = px[idx]
                            val rr = Color.red(p); val gg = Color.green(p); val bb = Color.blue(p)
                            if (rr < 245 || gg < 245 || bb < 245) nw++
                            if (rr > 180 && gg < 70 && bb < 70) r++
                            if (gg > 170 && rr < 70 && bb < 70) g++
                            if (bb > 180 && rr < 70 && gg < 100) {
                                b++
                                if (y < bMinY) bMinY = y
                                if (y > bMaxY) bMaxY = y
                            }
                            if (rr > 200 && gg in 100..170 && bb < 60) o++
                            if (rr > 160 && bb > 160 && gg < 70) m++
                            if (gg > 170 && bb > 170 && rr < 70) c++
                        }
                        pages.add(ColorCounts(nw, r, g, b, o, m, c, bMinY, bMaxY))
                    }
                }
            }
        }
        return pages
    }

    @Test
    fun docxBottomPageCompositeAnchorStaysUnclippedAndKeepsInsetTogether() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "test_bottom_composite_${System.currentTimeMillis()}.docx")
            val fillerLines = (1..18).joinToString("") {
                """<w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr><w:r><w:t>Filler line $it before composite figure.</w:t></w:r></w:p>"""
            }
            buildColoredProbeDocx(
                docxFile,
                """$fillerLines
                <w:p>
                  <w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="1" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>1600000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="3055620" cy="3944620"/><wp:wrapNone/><wp:docPr id="1" name="Map"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="r.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdR"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="3055620" cy="3944620"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                  <w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="2" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>3400000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>2750000</wp:posOffset></wp:positionV><wp:extent cx="1198880" cy="1183640"/><wp:wrapNone/><wp:docPr id="2" name="Inset"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="2" name="b.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdB"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1198880" cy="1183640"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                </w:p>"""
            )
            val result = OfflineDocumentConverter.convertToPdf(context, Uri.fromFile(docxFile), docxFile.name)
            assertTrue("Composite DOCX conversion failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val pdf = result.getOrThrow()
            try {
                val pages = inspectRenderedPdfColors(pdf)
                assertEquals("Expected 2 pages after moving composite figure atomically off Page 1", 2, pages.size)
                assertEquals("Page 1 must not slice bottom-of-page map", 0, pages[0].red)
                assertEquals("Page 1 must not orphan corner inset", 0, pages[0].blue)
                assertTrue("Page 2 must render full map (red=${pages[1].red})", pages[1].red > 40_000)
                assertTrue("Page 2 must render overlapping corner inset (blue=${pages[1].blue})", pages[1].blue > 5_000)
            } finally {
                pdf.delete()
                docxFile.delete()
            }
        }
    }

    @Test
    fun docxClippingConstraintsAndOffPageAnchorsRenderCompletely() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val docxFile = File(context.cacheDir, "test_clip_and_offpage_${System.currentTimeMillis()}.docx")
            buildColoredProbeDocx(
                docxFile,
                """
                <w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="1" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="900000" cy="300000"/><wp:wrapNone/><wp:docPr id="10" name="NoAutofitBox"/>
                  <a:graphic><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr txBox="1"/><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="900000" cy="300000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></wps:spPr>
                    <wps:txbx><w:txbxContent>
                      <w:p><w:r><w:t>Line 1 inside small box</w:t></w:r></w:p>
                      <w:p><w:r><w:t>Line 2 overflowing box</w:t></w:r></w:p>
                      <w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="FF0000"/></w:pPr><w:r><w:t>RED_OVERFLOW_TAIL</w:t></w:r></w:p>
                    </w:txbxContent></wps:txbx>
                    <wps:bodyPr lIns="0" tIns="0" rIns="0" bIns="0"><a:noAutofit/></wps:bodyPr>
                  </wps:wsp></a:graphicData></a:graphic>
                </wp:anchor></w:drawing></w:r></w:p>
                <w:p><w:pPr><w:spacing w:before="1500"/></w:pPr><w:r><w:t>AFTER_BOX</w:t></w:r></w:p>
                <w:tbl><w:tblPr><w:tblW w:w="1500" w:type="dxa"/></w:tblPr><w:tblGrid><w:gridCol w:w="1500"/></w:tblGrid>
                  <w:tr><w:trPr><w:trHeight w:val="220" w:hRule="exact"/></w:trPr><w:tc><w:p><w:r><w:t>Row 1 wrapped text</w:t></w:r></w:p><w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="00CC00"/></w:pPr><w:r><w:t>GREEN_ROW_TAIL</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
                <w:p><w:pPr><w:spacing w:line="240" w:lineRule="exact"/></w:pPr><w:r><w:drawing><wp:inline><wp:extent cx="1828800" cy="1219200"/><wp:docPr id="99" name="ExactInlineBlue"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="99" name="b.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdB"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
                <w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="2" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>-3000000</wp:posOffset></wp:positionV><wp:extent cx="1219200" cy="1219200"/><wp:wrapNone/><wp:docPr id="30" name="NegOffOrange"/>
                  <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="30" name="o.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdO"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1219200" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>
                </wp:anchor></w:drawing></w:r></w:p>
                <w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="3" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>2000000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>12000000</wp:posOffset></wp:positionV><wp:extent cx="1219200" cy="1219200"/><wp:wrapNone/><wp:docPr id="31" name="HugePosCyan"/>
                  <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="31" name="c.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdC"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1219200" cy="1219200"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>
                </wp:anchor></w:drawing></w:r></w:p>
                <w:p><w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="4" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>3500000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>-3000000</wp:posOffset></wp:positionV><wp:extent cx="900000" cy="400000"/><wp:wrapNone/><wp:docPr id="32" name="OffPageMagentaBox"/>
                  <a:graphic><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr txBox="1"/><wps:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="900000" cy="400000"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></wps:spPr>
                    <wps:txbx><w:txbxContent><w:p><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="DD00DD"/></w:pPr><w:r><w:t>MAGENTA_SHAPE</w:t></w:r></w:p></w:txbxContent></wps:txbx>
                    <wps:bodyPr lIns="0" tIns="0" rIns="0" bIns="0"><a:noAutofit/></wps:bodyPr>
                  </wps:wsp></a:graphicData></a:graphic>
                </wp:anchor></w:drawing></w:r></w:p>
                """
            )
            val result = OfflineDocumentConverter.convertToPdf(context, Uri.fromFile(docxFile), docxFile.name)
            assertTrue("Clip/offpage DOCX conversion failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
            val pdf = result.getOrThrow()
            try {
                val pages = inspectRenderedPdfColors(pdf)
                assertTrue("Expected at least 1 rendered page", pages.isNotEmpty())
                val red = pages.sumOf { it.red }
                val green = pages.sumOf { it.green }
                val blue = pages.sumOf { it.blue }
                val orange = pages.sumOf { it.orange }
                val cyan = pages.sumOf { it.cyan }
                val magenta = pages.sumOf { it.magenta }
                val maxBlueHeight = pages.maxOf { if (it.blueMaxY >= it.blueMinY) (it.blueMaxY - it.blueMinY) else 0 }

                assertTrue("Overflowing textbox tail must not be clipped (red=$red)", red > 400)
                assertTrue("Exact-height table row tail must not be clipped (green=$green)", green > 400)
                assertTrue("Exact-lineHeight inline image must not be sliced (blue=$blue height=$maxBlueHeight)", blue > 8000 && maxBlueHeight > 60)
                assertTrue("Negative-offset anchor picture must not be culled (orange=$orange)", orange > 5000)
                assertTrue("Large-positive-offset anchor picture must not be culled (cyan=$cyan)", cyan > 5000)
                assertTrue("Off-page anchored textbox shape must not be culled (magenta=$magenta)", magenta > 400)
            } finally {
                pdf.delete()
                docxFile.delete()
            }
        }
    }
}

