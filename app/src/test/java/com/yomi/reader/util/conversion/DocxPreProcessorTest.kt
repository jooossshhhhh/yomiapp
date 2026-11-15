package com.yomi.reader.util.conversion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPreProcessorTest {

    @Test
    fun normalizesHiddenTableAndCellBordersWhileKeepingVisibleOnes() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:tbl>
                  <w:tblPr>
                    <w:tblStyle w:val="TableGrid"/>
                    <w:tblBorders>
                      <w:top w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                      <w:left w:val="none" w:sz="4" w:space="0" w:color="auto"/>
                      <w:bottom w:val="single" w:sz="0" w:space="0" w:color="000000"/>
                      <w:right w:val="nil" w:sz="4"/>
                      <w:insideH w:val="single" w:sz="6" w:space="0" w:color="000000"/>
                      <w:insideV w:val="none" w:sz="4"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr>
                    <w:tc>
                      <w:tcPr><w:tcW w:w="5000" w:type="dxa"/><w:shd w:val="clear" w:fill="FFFFFF"/></w:tcPr>
                      <w:p><w:r><w:t>Hidden top/left/bottom/right/insideV, visible insideH</w:t></w:r></w:p>
                    </w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalized = String(DocxPreProcessor.normalizeWordXml(inputXml), Charsets.UTF_8)

        // Visible insideH border must remain intact
        assertTrue(normalized.contains("<w:insideH w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/>"))
        // None / sz=0 borders must be converted to nil without sz="4"
        assertFalse(normalized.contains("w:val=\"none\""))
        // tcBorders must be inserted BEFORE w:shd (schema order) and contain nil sides
        val tcBordersIdx = normalized.indexOf("<w:tcBorders>")
        val shdIdx = normalized.indexOf("<w:shd")
        assertTrue("Expected <w:tcBorders> before <w:shd>", tcBordersIdx in 0 until shdIdx)
        assertTrue(normalized.contains("""<w:top w:val="nil"/>"""))
        assertTrue(normalized.contains("""<w:insideV w:val="nil"/>"""))
    }

    @Test
    fun preservesDrawingsSmartArtOleAndWhitespaceByteForByte() {
        val drawingFragment = """
            <w:p><w:r><w:t xml:space="preserve">  Keep spacing  </w:t></w:r><w:r><mc:AlternateContent><mc:Choice Requires="wps"><w:drawing><wp:inline><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/diagram"><dgm:relIds r:dm="rId7"/></a:graphicData></a:graphic></wp:inline></w:drawing></mc:Choice></mc:AlternateContent></w:r></w:p>
        """.trimIndent()
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" xmlns:dgm="http://schemas.openxmlformats.org/drawingml/2006/diagram" mc:Ignorable="wps">
              <w:body>
                $drawingFragment
                <w:tbl>
                  <w:tblPr>
                    <w:tblBorders>
                      <w:top w:val="none" w:sz="4"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tr><w:tc><w:p><w:r><w:t>Cell</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalized = String(DocxPreProcessor.normalizeWordXml(inputXml), Charsets.UTF_8)
        assertTrue("Drawing/SmartArt/space fragment must remain verbatim", normalized.contains(drawingFragment))
        assertTrue(normalized.contains("""xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape""""))
        assertTrue(normalized.contains("""mc:Ignorable="wps""""))
    }

    @Test
    fun inlinesWideAndCenteredFloatingTablesWhilePreservingNarrowInsetTables() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:tbl>
                  <w:tblPr>
                    <w:tblpPr w:leftFromText="180" w:rightFromText="180" w:vertAnchor="text" w:horzAnchor="margin" w:tblpXSpec="center" w:tblpY="1"/>
                    <w:tblOverlap w:val="never"/>
                    <w:tblW w:w="9000" w:type="dxa"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="6"/>
                    </w:tblBorders>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="4500"/><w:gridCol w:w="4500"/></w:tblGrid>
                  <w:tr><w:tc><w:p><w:r><w:t>Wide Floating Table</w:t></w:r></w:p></w:tc><w:tc><w:p/></w:tc></w:tr>
                </w:tbl>
                <w:tbl>
                  <w:tblPr>
                    <w:tblpPr w:vertAnchor="text" w:horzAnchor="margin" w:tblpX="120" w:tblpY="120"/>
                    <w:tblW w:w="2200" w:type="dxa"/>
                  </w:tblPr>
                  <w:tblGrid><w:gridCol w:w="2200"/></w:tblGrid>
                  <w:tr><w:tc><w:p><w:r><w:t>Narrow Inset</w:t></w:r></w:p></w:tc></w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalized = String(DocxPreProcessor.normalizeWordXml(inputXml), Charsets.UTF_8)

        // Wide centered floating table must drop tblpPr/tblOverlap and preserve center alignment before tblBorders
        assertTrue(normalized.contains("<w:jc w:val=\"center\"/><w:tblBorders>"))
        assertFalse(normalized.contains("<w:tblOverlap"))
        // Only the narrow inset table retains tblpPr
        val tblpPrCount = normalized.split("<w:tblpPr").size - 1
        assertTrue("Expected exactly 1 remaining <w:tblpPr> for narrow inset table, got $tblpPrCount", tblpPrCount == 1)
    }

    @Test
    fun foldsPageBreakOnlyParagraphsAndClampsPreBreakEmptyLinesWithoutTouchingSectPr() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>Caption 1</w:t></w:r></w:p>
                <w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr></w:p>
                <w:p><w:pPr><w:spacing w:line="480" w:lineRule="auto"/></w:pPr><w:r><w:br w:type="page"/></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/><w:spacing w:before="240"/></w:pPr><w:r><w:t>Next Chapter</w:t></w:r></w:p>
                <w:p><w:pPr><w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr></w:pPr></w:p>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalized = String(DocxPreProcessor.normalizeWordXml(inputXml), Charsets.UTF_8)

        assertFalse("Pure page-break paragraph should be folded into next paragraph", normalized.contains("""w:type="page""""))
        assertTrue(normalized.contains("""<w:pStyle w:val="Heading1"/><w:pageBreakBefore/><w:spacing w:before="240"/>"""))
        assertTrue("Empty paragraph before break should be clamped to exact 1pt height", normalized.contains("""w:line="20" w:lineRule="exact""""))
        assertTrue("Section break paragraph must stay intact", normalized.contains("<w:sectPr>"))
    }

    @Test
    fun promotesSvgBlipEmbedOntoParentBlipWhenMissingPrimaryEmbed() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:asvg="http://schemas.microsoft.com/office/drawing/2016/SVG/main">
              <w:body>
                <w:p><w:r><a:blip><a:extLst><a:ext uri="{96DAC541-7B7A-43D3-8B79-37D633B846F1}"><asvg:svgBlip r:embed="rId15"/></a:ext></a:extLst></a:blip></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalized = String(DocxPreProcessor.normalizeWordXml(inputXml), Charsets.UTF_8)
        assertTrue(normalized.contains("""<a:blip r:embed="rId15">"""))
    }

    @Test
    fun relaxesExactClippingConstraintsWhilePreservingOnePtSpacerAndEmptyShapes() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                        xmlns:v="urn:schemas-microsoft-com:vml">
              <w:body>
                <w:p><w:pPr><w:spacing w:line="240" w:lineRule="exact"/></w:pPr><w:r><w:t>Inline image line</w:t></w:r></w:p>
                <w:p><w:pPr><w:spacing w:line="20" w:lineRule="exact"/></w:pPr></w:p>
                <w:tbl><w:tr><w:trPr><w:trHeight w:val="220" w:hRule="exact"/></w:trPr><w:tc><w:p><w:r><w:t>Cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                <wps:wsp><wps:txbx><w:txbxContent><w:p><w:r><w:t>Text</w:t></w:r></w:p></w:txbxContent></wps:txbx><wps:bodyPr lIns="0"><a:noAutofit/></wps:bodyPr></wps:wsp>
                <wps:wsp><wps:spPr/><wps:bodyPr lIns="0"><a:noAutofit/></wps:bodyPr></wps:wsp>
                <w:p><w:r><w:pict><v:shape style="width:90pt;height:40pt"><v:textbox style="margin:0"><w:txbxContent><w:p><w:r><w:t>VML</w:t></w:r></w:p></w:txbxContent></v:textbox></v:shape></w:pict></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalizedBytes = DocxPreProcessor.normalizeWordXml(inputXml)
        val normalized = String(normalizedBytes, Charsets.UTF_8)

        assertTrue(normalized.contains("""<w:spacing w:line="240" w:lineRule="atLeast"/>"""))
        assertTrue(normalized.contains("""<w:spacing w:line="20" w:lineRule="exact"/>"""))
        assertTrue(normalized.contains("""<w:trHeight w:val="220" w:hRule="atLeast"/>"""))
        assertTrue("Expected textbox noAutofit converted to spAutoFit", normalized.contains("""<wps:bodyPr lIns="0"><a:spAutoFit/></wps:bodyPr>"""))
        assertTrue("Expected empty non-textbox shape to retain noAutofit", normalized.contains("""<wps:spPr/><wps:bodyPr lIns="0"><a:noAutofit/></wps:bodyPr>"""))
        assertTrue(normalized.contains("mso-fit-shape-to-text:t"))

        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(normalizedBytes.inputStream())
    }

    @Test
    fun inlinesCompositeAndStandaloneFiguresWithFrameLocksAndClampsOffPageAnchors() {
        val inputXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                        xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                        xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                        xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                        xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
                        xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
                        xmlns:v="urn:schemas-microsoft-com:vml">
              <w:body>
                <w:p>
                  <w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="1" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>1600000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>0</wp:posOffset></wp:positionV><wp:extent cx="3055620" cy="3944620"/><wp:wrapNone/><wp:docPr id="1" name="Map"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="1" name="map.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId1"/></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="3055620" cy="3944620"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                  <w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="2" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>3400000</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>2750000</wp:posOffset></wp:positionV><wp:extent cx="1198880" cy="1183640"/><wp:wrapNone/><wp:docPr id="2" name="Inset"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="2" name="inset.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId2"/></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1198880" cy="1183640"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                </w:p>
                <w:p>
                  <w:r><w:drawing><wp:anchor distT="0" distB="0" distL="0" distR="0" simplePos="0" relativeHeight="3" behindDoc="1" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="page"><wp:posOffset>2609850</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>269236</wp:posOffset></wp:positionV><wp:extent cx="3006415" cy="4754022"/><wp:wrapTopAndBottom/><wp:docPr id="16" name="Figure"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="16" name="fig.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rId3"/></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="3006415" cy="4754022"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                </w:p>
                <w:p>
                  <w:r><w:drawing><wp:anchor simplePos="0" relativeHeight="4" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>-3000000</wp:posOffset></wp:positionV><wp:extent cx="900000" cy="300000"/><wp:wrapNone/><wp:docPr id="20" name="NegShape"/><a:graphic><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr txBox="1"/><wps:spPr/><wps:txbx><w:txbxContent><w:p><w:r><w:t>Keep</w:t></w:r></w:p></w:txbxContent></wps:txbx><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                </w:p>
                <w:p>
                  <w:r><w:drawing><wp:anchor simplePos="0" relativeHeight="5" behindDoc="0" locked="0" layoutInCell="1" allowOverlap="1"><wp:simplePos x="0" y="0"/><wp:positionH relativeFrom="margin"><wp:posOffset>0</wp:posOffset></wp:positionH><wp:positionV relativeFrom="paragraph"><wp:posOffset>-461798</wp:posOffset></wp:positionV><wp:extent cx="171450" cy="170815"/><wp:wrapNone/><wp:docPr id="21" name="PageNumBox"/><a:graphic><a:graphicData uri="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"><wps:wsp><wps:cNvSpPr txBox="1"/><wps:spPr/><wps:txbx><w:txbxContent><w:p><w:r><w:t>25</w:t></w:r></w:p></w:txbxContent></wps:txbx><wps:bodyPr/></wps:wsp></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r>
                </w:p>
                <w:p><w:r><w:pict><v:shape style="position:absolute;margin-top:-320pt;width:72pt;height:72pt"/></w:pict></w:r></w:p>
                <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val normalizedBytes = DocxPreProcessor.normalizeWordXml(inputXml)
        val normalized = String(normalizedBytes, Charsets.UTF_8)

        // Composite <pic:pic> pair merged into atomic <wp:inline><wpg:wgp> with relative inset offset
        assertTrue(normalized.contains("<wpg:wgp"))
        assertTrue(normalized.contains("""<a:off x="1800000" y="2750000"/>"""))
        // Standalone wrapTopAndBottom with graphicFrameLocks converted cleanly to single valid <wp:inline>
        assertFalse(normalized.contains("</wp:cNvGraphicFramePr></wp:cNvGraphicFramePr>"))
        assertTrue(normalized.contains("""<w:jc w:val="center"/>"""))
        // Off-page negative posOffset and VML margin-top clamped, small header offset (-461798) preserved
        assertFalse(normalized.contains("-3000000"))
        assertTrue(normalized.contains("-461798"))
        assertTrue(normalized.contains("margin-top:0pt"))

        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(normalizedBytes.inputStream())
    }
}
