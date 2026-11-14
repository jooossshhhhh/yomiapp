package com.yomi.reader.util

import com.yomi.reader.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSharedLinkHelperTest {

    @Test
    fun parseDriveLink_extractsFolderUrlsAndResourceKeys() {
        val standard = DriveSharedLinkHelper.parseDriveLink(
            "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz?usp=sharing"
        )
        assertNotNull(standard)
        assertEquals(DriveLinkKind.FOLDER, standard?.kind)
        assertEquals("1AbCdEfGhIjKlMnOpQrStUvWxYz", standard?.id)
        assertEquals(
            "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUvWxYz",
            standard?.canonicalUrl
        )

        val mobileWithKey = DriveSharedLinkHelper.parseDriveLink(
            "Check this out: https://drive.google.com/drive/u/0/folders/1ZyxWvUtSrQpOnMlKjIhGfEdCbA?usp=drive_link&resourcekey=0-TestKey99"
        )
        assertNotNull(mobileWithKey)
        assertEquals(DriveLinkKind.FOLDER, mobileWithKey?.kind)
        assertEquals("1ZyxWvUtSrQpOnMlKjIhGfEdCbA", mobileWithKey?.id)
        assertEquals("0-TestKey99", mobileWithKey?.resourceKey)
        assertTrue(mobileWithKey?.embeddedViewUrl?.contains("resourcekey=0-TestKey99") == true)

        val openUrl = DriveSharedLinkHelper.parseDriveLink(
            "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUvWxYz"
        )
        assertEquals(DriveLinkKind.FOLDER, openUrl?.kind)

        assertNull(DriveSharedLinkHelper.parseDriveLink("https://example.com/not-drive"))
        assertNull(DriveSharedLinkHelper.parseDriveLink("   "))
    }

    @Test
    fun parseDriveLink_detectsFileAndWorkspaceDocumentLinks() {
        val pdfFile = DriveSharedLinkHelper.parseDriveLink(
            "https://drive.google.com/file/d/1FileIdAbCdEfGhIjKlMnOpQr/view?usp=sharing"
        )
        assertEquals(DriveLinkKind.FILE, pdfFile?.kind)
        assertEquals(
            "https://drive.google.com/uc?export=download&confirm=t&id=1FileIdAbCdEfGhIjKlMnOpQr",
            DriveSharedLinkHelper.buildDirectDownloadUrl(pdfFile!!.id, pdfFile.kind)
        )

        val sheet = DriveSharedLinkHelper.parseDriveLink(
            "https://docs.google.com/spreadsheets/d/1SheetIdAbCdEfGhIjKlMnOpQr/edit"
        )
        assertEquals(DriveLinkKind.GOOGLE_SHEET, sheet?.kind)
        assertEquals(
            "https://docs.google.com/spreadsheets/d/1SheetIdAbCdEfGhIjKlMnOpQr/export?format=pdf",
            DriveSharedLinkHelper.buildDirectDownloadUrl(sheet!!.id, sheet.kind)
        )
    }

    @Test
    fun parseEmbeddedFolderHtml_extractsSubfoldersAndSupportedDocuments() {
        val sampleHtml = """
            <html>
              <head><title>Capstone Project Docs - Google Drive</title></head>
              <body>
                <div class="flip-entry" id="entry-sub1">
                  <a href="https://drive.google.com/drive/folders/1SubFolderAbCdEfGhIjKl?usp=drive_web">
                    <div class="flip-entry-title">Week 1 Readings</div>
                  </a>
                </div>
                <div class="flip-entry" id="entry-pdf1">
                  <a href="https://drive.google.com/file/d/1PdfFileAbCdEfGhIjKlMnOp/view?usp=drive_web">
                    <div class="flip-entry-title">Syllabus_2026.pdf</div>
                  </a>
                </div>
                <div class="flip-entry" id="entry-xls1">
                  <a href="https://drive.google.com/file/d/1ExcelFileAbCdEfGhIjKlMn/view">
                    <div class="flip-entry-title">Food_Planning.xlsx</div>
                  </a>
                </div>
                <div class="flip-entry" id="entry-gdoc1">
                  <a href="https://docs.google.com/document/d/1GoogleDocAbCdEfGhIjKlMn/edit">
                    <div class="flip-entry-title">Meeting Notes</div>
                  </a>
                </div>
                <div class="flip-entry" id="entry-img1">
                  <a href="https://drive.google.com/file/d/1ImageFileAbCdEfGhIjKlMn/view">
                    <div class="flip-entry-title">Banner.png</div>
                  </a>
                </div>
              </body>
            </html>
        """.trimIndent()

        val outcome = DriveSharedLinkHelper.parseEmbeddedFolderHtml(sampleHtml, parentPath = "")

        assertFalse(outcome.requiresSignInOrRestricted)
        assertEquals("Capstone Project Docs", outcome.folderTitle)
        assertEquals(1, outcome.subfolders.size)
        assertEquals("Week 1 Readings", outcome.subfolders[0].name)
        assertEquals(
            "https://drive.google.com/drive/folders/1SubFolderAbCdEfGhIjKl",
            outcome.subfolders[0].uriString
        )

        assertEquals(3, outcome.files.size)
        assertEquals("Syllabus_2026.pdf", outcome.files[0].name)
        assertEquals(DocumentType.PDF, outcome.files[0].type)
        assertEquals("Food_Planning.xlsx", outcome.files[1].name)
        assertEquals(DocumentType.EXCEL, outcome.files[1].type)
        assertEquals("Meeting Notes.pdf", outcome.files[2].name)
        assertEquals(DocumentType.PDF, outcome.files[2].type)
    }

    @Test
    fun parseEmbeddedFolderHtml_detectsRestrictedLoginPage() {
        val loginHtml = """
            <html>
              <head><title>Google Drive: Sign-in</title></head>
              <body>
                <a href="https://accounts.google.com/ServiceLogin?continue=...">Sign in</a>
                <p>You need access. Request access or switch to an account with access.</p>
              </body>
            </html>
        """.trimIndent()

        val outcome = DriveSharedLinkHelper.parseEmbeddedFolderHtml(loginHtml, parentPath = "")
        assertTrue(outcome.requiresSignInOrRestricted)
        assertTrue(outcome.files.isEmpty())
        assertTrue(outcome.subfolders.isEmpty())
    }
}

