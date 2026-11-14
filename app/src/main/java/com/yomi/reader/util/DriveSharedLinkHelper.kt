package com.yomi.reader.util

import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import org.json.JSONObject

enum class DriveLinkKind {
    FOLDER,
    FILE,
    GOOGLE_DOC,
    GOOGLE_SHEET,
    GOOGLE_SLIDE
}

data class ParsedDriveLink(
    val kind: DriveLinkKind,
    val id: String,
    val resourceKey: String?,
    val canonicalUrl: String,
    val embeddedViewUrl: String
)

data class DriveLinkParseOutcome(
    val folderTitle: String?,
    val subfolders: List<DriveFolderItem>,
    val files: List<DriveFileItem>,
    val requiresSignInOrRestricted: Boolean = false
)

/**
 * Pure-Kotlin helper for parsing shared Google Drive folder URLs, Drive HTML embedded folder
 * listings, and Drive v3 API payloads without requiring any native Android dependencies.
 */
object DriveSharedLinkHelper {

    private val urlInTextRegex = Regex("""https?://[^\s<>"'\])]+""", RegexOption.IGNORE_CASE)
    private val folderPathRegex = Regex(
        """https?://drive\.google\.com/(?:drive/(?:u/\d+/)?(?:mobile/)?folders/|folderview\b[^#]*?[?&]id=|embeddedfolderview\b[^#]*?[?&]id=)([A-Za-z0-9_-]{10,})""",
        RegexOption.IGNORE_CASE
    )
    private val openIdRegex = Regex(
        """https?://drive\.google\.com/open\?[^#]*?\bid=([A-Za-z0-9_-]{10,})""",
        RegexOption.IGNORE_CASE
    )
    private val filePathRegex = Regex(
        """https?://drive\.google\.com/file/d/([A-Za-z0-9_-]{10,})""",
        RegexOption.IGNORE_CASE
    )
    private val docsPathRegex = Regex(
        """https?://docs\.google\.com/(document|spreadsheets|presentation)/(?:u/\d+/)?d/([A-Za-z0-9_-]{10,})""",
        RegexOption.IGNORE_CASE
    )
    private val resourceKeyRegex = Regex("""[?&]resourcekey=([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val rawIdRegex = Regex("""^[A-Za-z0-9_-]{18,}$""")

    fun parseDriveLink(rawInput: String): ParsedDriveLink? {
        val cleaned = rawInput.trim()
        if (cleaned.isEmpty()) return null

        val candidateUrl = urlInTextRegex.find(cleaned)?.value?.trimEnd('.', ',', ';', ')', ']') ?: cleaned
        val resourceKey = resourceKeyRegex.find(candidateUrl)?.groupValues?.getOrNull(1)

        folderPathRegex.find(candidateUrl)?.groupValues?.getOrNull(1)?.let { folderId ->
            return buildFolderParsedLink(folderId, resourceKey)
        }
        openIdRegex.find(candidateUrl)?.groupValues?.getOrNull(1)?.let { id ->
            return buildFolderParsedLink(id, resourceKey)
        }
        filePathRegex.find(candidateUrl)?.groupValues?.getOrNull(1)?.let { fileId ->
            return ParsedDriveLink(
                kind = DriveLinkKind.FILE,
                id = fileId,
                resourceKey = resourceKey,
                canonicalUrl = appendResourceKey("https://drive.google.com/file/d/$fileId/view", resourceKey, firstQuery = true),
                embeddedViewUrl = ""
            )
        }
        docsPathRegex.find(candidateUrl)?.let { match ->
            val docType = match.groupValues[1].lowercase()
            val id = match.groupValues[2]
            val kind = when (docType) {
                "document" -> DriveLinkKind.GOOGLE_DOC
                "spreadsheets" -> DriveLinkKind.GOOGLE_SHEET
                "presentation" -> DriveLinkKind.GOOGLE_SLIDE
                else -> DriveLinkKind.FILE
            }
            return ParsedDriveLink(
                kind = kind,
                id = id,
                resourceKey = resourceKey,
                canonicalUrl = "https://docs.google.com/$docType/d/$id/view",
                embeddedViewUrl = ""
            )
        }

        if (rawIdRegex.matches(cleaned)) {
            return buildFolderParsedLink(cleaned, resourceKey = null)
        }
        return null
    }

    private fun buildFolderParsedLink(folderId: String, resourceKey: String?): ParsedDriveLink {
        val canonical = appendResourceKey(
            "https://drive.google.com/drive/folders/$folderId",
            resourceKey,
            firstQuery = true
        )
        val embedded = appendResourceKey(
            "https://drive.google.com/embeddedfolderview?id=$folderId",
            resourceKey,
            firstQuery = false
        ) + "#list"
        return ParsedDriveLink(
            kind = DriveLinkKind.FOLDER,
            id = folderId,
            resourceKey = resourceKey,
            canonicalUrl = canonical,
            embeddedViewUrl = embedded
        )
    }

    private fun appendResourceKey(url: String, resourceKey: String?, firstQuery: Boolean): String {
        if (resourceKey.isNullOrBlank()) return url
        val sep = if (firstQuery) "?" else "&"
        return "$url${sep}resourcekey=$resourceKey"
    }

    fun buildDirectDownloadUrl(
        itemId: String,
        kind: DriveLinkKind,
        resourceKey: String? = null
    ): String {
        return when (kind) {
            DriveLinkKind.GOOGLE_DOC -> "https://docs.google.com/document/d/$itemId/export?format=pdf"
            DriveLinkKind.GOOGLE_SHEET -> "https://docs.google.com/spreadsheets/d/$itemId/export?format=pdf"
            DriveLinkKind.GOOGLE_SLIDE -> "https://docs.google.com/presentation/d/$itemId/export?format=pdf"
            DriveLinkKind.FILE, DriveLinkKind.FOLDER -> {
                val base = "https://drive.google.com/uc?export=download&confirm=t&id=$itemId"
                if (!resourceKey.isNullOrBlank()) "$base&resourcekey=$resourceKey" else base
            }
        }
    }

    fun extractDriveIdFromDownloadUrl(url: String): String? {
        val idParam = Regex("""[?&]id=([A-Za-z0-9_-]{10,})""").find(url)?.groupValues?.getOrNull(1)
        if (idParam != null) return idParam
        return filePathRegex.find(url)?.groupValues?.getOrNull(1)
            ?: folderPathRegex.find(url)?.groupValues?.getOrNull(1)
    }

    fun resolveDocumentType(
        fileName: String,
        kind: DriveLinkKind = DriveLinkKind.FILE,
        mimeTypeOrHint: String = ""
    ): DocumentType {
        if (kind == DriveLinkKind.GOOGLE_DOC) return DocumentType.WORD
        if (kind == DriveLinkKind.GOOGLE_SHEET) return DocumentType.EXCEL
        if (kind == DriveLinkKind.GOOGLE_SLIDE) return DocumentType.POWERPOINT

        val lowerName = fileName.lowercase()
        val ext = lowerName.substringAfterLast('.', "")
        val lowerMime = mimeTypeOrHint.lowercase()
        return when {
            ext == "pdf" || lowerMime.contains("pdf") -> DocumentType.PDF
            ext in listOf("doc", "docx") || lowerMime.contains("word") || lowerMime.contains("document") -> DocumentType.WORD
            ext in listOf("xls", "xlsx") || lowerMime.contains("sheet") || lowerMime.contains("excel") -> DocumentType.EXCEL
            ext in listOf("ppt", "pptx") || lowerMime.contains("presentation") || lowerMime.contains("powerpoint") -> DocumentType.POWERPOINT
            else -> DocumentType.UNKNOWN
        }
    }

    fun normalizeTitleWithExtension(rawTitle: String, docType: DocumentType, kind: DriveLinkKind): String {
        val trimmed = rawTitle.trim().ifBlank { "Drive Document" }
        val ext = trimmed.substringAfterLast('.', "").lowercase()
        if (ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")) {
            return trimmed
        }
        // Native Google Docs/Sheets/Slides export cleanly directly as PDF
        if (kind == DriveLinkKind.GOOGLE_DOC || kind == DriveLinkKind.GOOGLE_SHEET || kind == DriveLinkKind.GOOGLE_SLIDE) {
            return "$trimmed.pdf"
        }
        return when (docType) {
            DocumentType.PDF -> "$trimmed.pdf"
            DocumentType.WORD -> "$trimmed.docx"
            DocumentType.EXCEL -> "$trimmed.xlsx"
            DocumentType.POWERPOINT -> "$trimmed.pptx"
            DocumentType.UNKNOWN -> trimmed
        }
    }

    fun extractPageTitle(html: String): String? {
        val match = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html) ?: return null
        val decoded = decodeHtmlEntities(match.groupValues[1])
            .replace(Regex("""\s*-\s*Google Drive\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (decoded.isBlank() ||
            decoded.equals("Google Drive", ignoreCase = true) ||
            decoded.equals("Sign in", ignoreCase = true) ||
            decoded.contains("Sign in - Google Accounts", ignoreCase = true)
        ) {
            return null
        }
        return decoded
    }

    fun parseEmbeddedFolderHtml(
        html: String,
        parentPath: String,
        resourceKey: String? = null
    ): DriveLinkParseOutcome {
        val title = extractPageTitle(html)
        val subfolders = mutableListOf<DriveFolderItem>()
        val files = mutableListOf<DriveFileItem>()
        val seenIds = mutableSetOf<String>()

        // Primary embeddedfolderview entry cards
        val entrySplit = html.split(Regex("""<div[^>]*class="[^"]*flip-entry[^"]*"[^>]*>""", RegexOption.IGNORE_CASE))
        if (entrySplit.size > 1) {
            for (chunk in entrySplit.drop(1)) {
                val href = Regex("""href="([^"]+)"""", RegexOption.IGNORE_CASE).find(chunk)?.groupValues?.getOrNull(1)
                    ?.let(::decodeHtmlEntities) ?: continue
                val titleRaw = Regex(
                    """class="[^"]*flip-entry-title[^"]*"[^>]*>(.*?)</div>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(chunk)?.groupValues?.getOrNull(1)?.let(::stripHtmlTags)?.let(::decodeHtmlEntities)
                    ?: continue

                val iconHint = Regex("""<img[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(chunk)?.groupValues?.getOrNull(1).orEmpty()

                val parsed = parseDriveLink(href) ?: continue
                if (!seenIds.add(parsed.id)) continue

                if (parsed.kind == DriveLinkKind.FOLDER) {
                    val folderName = titleRaw.trim().ifBlank { "Folder" }
                    val subPath = if (parentPath.isBlank()) folderName else "$parentPath/$folderName"
                    subfolders.add(
                        DriveFolderItem(
                            name = folderName,
                            path = subPath,
                            parentPath = parentPath,
                            fileCount = 0,
                            uriString = parsed.canonicalUrl
                        )
                    )
                } else {
                    val docType = resolveDocumentType(titleRaw, parsed.kind, iconHint)
                    val normalizedName = normalizeTitleWithExtension(titleRaw, docType, parsed.kind)
                    if (docType != DocumentType.UNKNOWN && DriveFolderSafetyHelper.isSupportedDriveFile(normalizedName)) {
                        files.add(
                            DriveFileItem(
                                name = normalizedName,
                                uriString = buildDirectDownloadUrl(parsed.id, parsed.kind, resourceKey ?: parsed.resourceKey),
                                parentPath = parentPath,
                                type = if (parsed.kind in listOf(DriveLinkKind.GOOGLE_DOC, DriveLinkKind.GOOGLE_SHEET, DriveLinkKind.GOOGLE_SLIDE)) {
                                    DocumentType.PDF
                                } else {
                                    docType
                                },
                                sizeBytes = 0L,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        // Secondary anchor scan fallback in case Google updates embedded class names
        if (subfolders.isEmpty() && files.isEmpty()) {
            val anchorRegex = Regex(
                """<a[^>]*href="(https://(?:drive|docs)\.google\.com/[^"]+)"[^>]*>(.*?)</a>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (match in anchorRegex.findAll(html)) {
                val href = decodeHtmlEntities(match.groupValues[1])
                val text = stripHtmlTags(decodeHtmlEntities(match.groupValues[2])).trim()
                if (text.isBlank()) continue
                val parsed = parseDriveLink(href) ?: continue
                if (!seenIds.add(parsed.id)) continue
                if (parsed.kind == DriveLinkKind.FOLDER) {
                    val subPath = if (parentPath.isBlank()) text else "$parentPath/$text"
                    subfolders.add(
                        DriveFolderItem(
                            name = text,
                            path = subPath,
                            parentPath = parentPath,
                            fileCount = 0,
                            uriString = parsed.canonicalUrl
                        )
                    )
                } else {
                    val docType = resolveDocumentType(text, parsed.kind, "")
                    val normalizedName = normalizeTitleWithExtension(text, docType, parsed.kind)
                    if (docType != DocumentType.UNKNOWN && DriveFolderSafetyHelper.isSupportedDriveFile(normalizedName)) {
                        files.add(
                            DriveFileItem(
                                name = normalizedName,
                                uriString = buildDirectDownloadUrl(parsed.id, parsed.kind, resourceKey ?: parsed.resourceKey),
                                parentPath = parentPath,
                                type = if (parsed.kind in listOf(DriveLinkKind.GOOGLE_DOC, DriveLinkKind.GOOGLE_SHEET, DriveLinkKind.GOOGLE_SLIDE)) {
                                    DocumentType.PDF
                                } else {
                                    docType
                                },
                                sizeBytes = 0L,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }

        val lowerHtml = html.lowercase()
        val restricted = (subfolders.isEmpty() && files.isEmpty()) && (
            lowerHtml.contains("accounts.google.com/servicelogin") ||
                lowerHtml.contains("you need access") ||
                lowerHtml.contains("request access") ||
                lowerHtml.contains("sign in - google accounts")
            )

        return DriveLinkParseOutcome(
            folderTitle = title,
            subfolders = subfolders,
            files = files,
            requiresSignInOrRestricted = restricted
        )
    }

    fun parseDriveApiV3FilesJson(
        jsonBody: String,
        parentPath: String,
        folderTitle: String? = null
    ): DriveLinkParseOutcome {
        val root = JSONObject(jsonBody)
        val array = root.optJSONArray("files") ?: return DriveLinkParseOutcome(folderTitle, emptyList(), emptyList())
        val subfolders = mutableListOf<DriveFolderItem>()
        val files = mutableListOf<DriveFileItem>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optString("id")
            val name = item.optString("name")
            val mime = item.optString("mimeType")
            val size = item.optString("size").toLongOrNull() ?: 0L
            if (id.isBlank() || name.isBlank()) continue

            if (mime == "application/vnd.google-apps.folder") {
                val subPath = if (parentPath.isBlank()) name else "$parentPath/$name"
                subfolders.add(
                    DriveFolderItem(
                        name = name,
                        path = subPath,
                        parentPath = parentPath,
                        fileCount = 0,
                        uriString = "https://drive.google.com/drive/folders/$id"
                    )
                )
            } else {
                val linkKind = when (mime) {
                    "application/vnd.google-apps.document" -> DriveLinkKind.GOOGLE_DOC
                    "application/vnd.google-apps.spreadsheet" -> DriveLinkKind.GOOGLE_SHEET
                    "application/vnd.google-apps.presentation" -> DriveLinkKind.GOOGLE_SLIDE
                    else -> DriveLinkKind.FILE
                }
                val docType = resolveDocumentType(name, linkKind, mime)
                val normalizedName = normalizeTitleWithExtension(name, docType, linkKind)
                if (docType != DocumentType.UNKNOWN && DriveFolderSafetyHelper.isSupportedDriveFile(normalizedName)) {
                    files.add(
                        DriveFileItem(
                            name = normalizedName,
                            uriString = buildDirectDownloadUrl(id, linkKind),
                            parentPath = parentPath,
                            type = if (linkKind != DriveLinkKind.FILE) DocumentType.PDF else docType,
                            sizeBytes = size,
                            lastModified = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return DriveLinkParseOutcome(
            folderTitle = folderTitle,
            subfolders = subfolders,
            files = files,
            requiresSignInOrRestricted = false
        )
    }

    private fun stripHtmlTags(raw: String): String {
        return raw.replace(Regex("<[^>]+>"), " ").replace(Regex("""\s+"""), " ").trim()
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#x2F;", "/")
            .replace("&nbsp;", " ")
    }
}

