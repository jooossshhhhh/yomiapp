package com.yomi.reader.util.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxPreProcessorTest {

    @Test
    fun normalizesWideSheetToSingleLandscapeA4PageAndRemovesBreaks() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetPr/>
              <dimension ref="A1:K18"/>
              <sheetData>
                <row r="1"><c r="K1" t="inlineStr"><is><t>Total</t></is></c></row>
              </sheetData>
              <rowBreaks count="1" manualBreakCount="1"><brk id="10" max="16383" man="1"/></rowBreaks>
              <colBreaks count="1" manualBreakCount="1"><brk id="5" max="1048575" man="1"/></colBreaks>
            </worksheet>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val out = String(XlsxPreProcessor.normalizeWorksheetXml(inputXml), Charsets.UTF_8)

        assertTrue(out.contains("""<pageSetUpPr fitToPage="1"/>"""))
        assertTrue(out.contains("""paperSize="9""""))
        assertTrue(out.contains("""orientation="landscape""""))
        assertTrue(out.contains("""fitToWidth="1""""))
        assertTrue(out.contains("""fitToHeight="1""""))
        assertFalse(out.contains("<rowBreaks"))
        assertFalse(out.contains("<colBreaks"))
    }

    @Test
    fun choosesPortraitForNarrowTallSheet() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <dimension ref="A1:C45"/>
              <sheetData>
                <row r="45"><c r="C45" t="inlineStr"><is><t>End</t></is></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        assertEquals("portrait", XlsxPreProcessor.detectBestOrientation(String(inputXml, Charsets.UTF_8)))
        val out = String(XlsxPreProcessor.normalizeWorksheetXml(inputXml), Charsets.UTF_8)
        assertTrue(out.contains("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>"""))
        assertTrue(out.contains("""orientation="portrait""""))
    }
}

