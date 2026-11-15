package com.yomi.reader.data.remote

import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.DriveFolderItem
import com.yomi.reader.util.DriveLinkKind
import com.yomi.reader.util.DriveLinkParseOutcome
import com.yomi.reader.util.DriveSharedLinkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FetchedDriveLinkFolder(
    val folderId: String,
    val folderName: String,
    val canonicalUrl: String,
    val subfolders: List<DriveFolderItem>,
    val files: List<DriveFileItem>
)

class RestrictedDriveFolderException(
    val folderUrl: String,
    override val message: String
) : IllegalStateException(message)

class IncompleteDriveSyncException(
    val folderName: String,
    val cachedCount: Int,
    val totalCount: Int,
    override val message: String
) : IllegalStateException(message)

/**
 * Fetches Google Drive shared folder listings and downloads supported files cleanly without API keys
 * for link-shared ("Anyone with the link") folders, or via Drive API v3 when an OAuth token is passed.
 */
object GoogleDriveSharedLinkClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    suspend fun fetchFolderContents(
        folderUrlOrId: String,
        parentPath: String = "",
        accessToken: String? = null
    ): Result<FetchedDriveLinkFolder> = withContext(Dispatchers.IO) {
        val parsed = DriveSharedLinkHelper.parseDriveLink(folderUrlOrId)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Please paste a valid Google Drive folder link.")
            )

        if (parsed.kind != DriveLinkKind.FOLDER) {
            return@withContext Result.failure(
                IllegalArgumentException("That looks like a single file link. Please paste a Google Drive folder link.")
            )
        }

        // 1. Try authenticated Drive API v3 if an OAuth token is already available
        if (!accessToken.isNullOrBlank()) {
            val apiResult = fetchViaDriveApiV3(parsed.id, parentPath, accessToken)
            if (apiResult != null) {
                return@withContext Result.success(
                    FetchedDriveLinkFolder(
                        folderId = parsed.id,
                        folderName = apiResult.folderTitle ?: "Shared Drive Folder",
                        canonicalUrl = parsed.canonicalUrl,
                        subfolders = apiResult.subfolders,
                        files = apiResult.files
                    )
                )
            }
        }

        // 2. Public / Anyone-with-the-link folder listing via Google Drive embeddedfolderview
        val embeddedResponse = httpGetText(parsed.embeddedViewUrl)
        if (embeddedResponse.isFailure) {
            return@withContext Result.failure(
                embeddedResponse.exceptionOrNull() ?: IllegalStateException("Could not reach Google Drive")
            )
        }

        val (statusCode, finalUrl, html) = embeddedResponse.getOrThrow()
        if (statusCode == 401 || statusCode == 403 || finalUrl.contains("accounts.google.com", ignoreCase = true)) {
            return@withContext Result.failure(
                RestrictedDriveFolderException(
                    folderUrl = parsed.canonicalUrl,
                    message = "This Drive folder is restricted. Ask the owner to set access to \"Anyone with the link\", or open it in Google Drive."
                )
            )
        }
        if (statusCode == 404) {
            return@withContext Result.failure(
                IllegalStateException("Google Drive folder not found. Check that the shared link is still valid.")
            )
        }

        val outcome = DriveSharedLinkHelper.parseEmbeddedFolderHtml(
            html = html,
            parentPath = parentPath,
            resourceKey = parsed.resourceKey
        )

        if (outcome.requiresSignInOrRestricted) {
            return@withContext Result.failure(
                RestrictedDriveFolderException(
                    folderUrl = parsed.canonicalUrl,
                    message = "This Drive folder requires sign-in or access approval. Set sharing to \"Anyone with the link\" or open in Google Drive."
                )
            )
        }

        var resolvedTitle = outcome.folderTitle
        if (resolvedTitle.isNullOrBlank()) {
            val pageResult = httpGetText(parsed.canonicalUrl).getOrNull()
            if (pageResult != null && !pageResult.second.contains("accounts.google.com", ignoreCase = true)) {
                resolvedTitle = DriveSharedLinkHelper.extractPageTitle(pageResult.third)
            }
        }

        val fallbackTitle = parentPath.substringAfterLast('/').takeIf { parentPath.isNotBlank() }
            ?: "Shared Drive Folder"

        Result.success(
            FetchedDriveLinkFolder(
                folderId = parsed.id,
                folderName = resolvedTitle ?: fallbackTitle,
                canonicalUrl = parsed.canonicalUrl,
                subfolders = outcome.subfolders,
                files = outcome.files
            )
        )
    }

    suspend fun downloadDriveUrlToFile(
        sourceUrl: String,
        targetFile: File,
        accessToken: String? = null,
        onProgressBytes: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val driveId = DriveSharedLinkHelper.extractDriveIdFromDownloadUrl(sourceUrl)
            val candidateUrl = if (!accessToken.isNullOrBlank() && driveId != null && sourceUrl.contains("drive.google.com/uc")) {
                "https://www.googleapis.com/drive/v3/files/$driveId?alt=media&supportsAllDrives=true"
            } else {
                sourceUrl
            }

            var currentUrl = candidateUrl
            var redirectCount = 0
            while (redirectCount < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 45_000
                    setRequestProperty("User-Agent", USER_AGENT)
                    if (!accessToken.isNullOrBlank() && currentUrl.contains("googleapis.com")) {
                        setRequestProperty("Authorization", "Bearer $accessToken")
                    }
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) throw IllegalStateException("Redirect missing target URL")
                    if (location.contains("accounts.google.com", ignoreCase = true)) {
                        throw IllegalStateException("Access denied by Google Drive. Set file sharing to \"Anyone with the link\".")
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirectCount++
                    continue
                }

                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("Drive download failed (HTTP $code)")
                }

                val contentType = conn.contentType.orEmpty().lowercase()
                if (contentType.contains("text/html")) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val confirmUrl = extractVirusScanConfirmUrl(html, driveId)
                    if (confirmUrl != null && redirectCount < 5) {
                        currentUrl = confirmUrl
                        redirectCount++
                        continue
                    }
                    throw IllegalStateException("Google Drive denied download or returned a sign-in page.")
                }

                targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                val totalLength = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var copied = 0L
                        var lastReported = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                copied += read
                                if (onProgressBytes != null && (copied - lastReported >= 65_536L)) {
                                    lastReported = copied
                                    onProgressBytes(copied, totalLength)
                                }
                            }
                        }
                        onProgressBytes?.invoke(copied, totalLength)
                    }
                }
                conn.disconnect()
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    throw IllegalStateException("Downloaded file is empty")
                }
                return@runCatching targetFile
            }
            throw IllegalStateException("Too many redirects from Google Drive")
        }
    }

    private fun extractVirusScanConfirmUrl(html: String, fileId: String?): String? {
        val actionMatch = Regex("""action="(https://drive\.usercontent\.google\.com/download[^"]*)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        val confirmVal = Regex("""name="confirm"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
        val uuidVal = Regex("""name="uuid"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
        val atVal = Regex("""name="at"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
        val idVal = Regex("""name="id"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1) ?: fileId

        if (idVal.isNullOrBlank()) {
            val hrefMatch = Regex("""href="([^"]*export=download[^"]*)"""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)
                ?.replace("&amp;", "&")
            return hrefMatch
        }
        val base = actionMatch?.replace("&amp;", "&") ?: "https://drive.usercontent.google.com/download"
        val sep = if (base.contains('?')) "&" else "?"
        val confirm = confirmVal ?: "t"
        val uuidPart = if (!uuidVal.isNullOrBlank()) "&uuid=${URLEncoder.encode(uuidVal, "UTF-8")}" else ""
        val atPart = if (!atVal.isNullOrBlank()) "&at=${URLEncoder.encode(atVal, "UTF-8")}" else ""
        return if (base.contains("id=")) {
            "${base}${sep}export=download&confirm=$confirm$uuidPart$atPart"
        } else {
            "${base}${sep}id=$idVal&export=download&confirm=$confirm$uuidPart$atPart"
        }
    }

    private fun fetchViaDriveApiV3(
        folderId: String,
        parentPath: String,
        accessToken: String
    ): DriveLinkParseOutcome? {
        return runCatching {
            val metaUrl = "https://www.googleapis.com/drive/v3/files/$folderId?fields=id,name,mimeType&supportsAllDrives=true"
            val metaConn = (URL(metaUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            val metaName = if (metaConn.responseCode in 200..299) {
                val body = metaConn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body).optString("name").takeIf { it.isNotBlank() }
            } else null
            metaConn.disconnect()

            val query = URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name,mimeType,size)&includeItemsFromAllDrives=true&supportsAllDrives=true&pageSize=200"
            val listConn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            if (listConn.responseCode !in 200..299) {
                listConn.disconnect()
                return null
            }
            val listBody = listConn.inputStream.bufferedReader().use { it.readText() }
            listConn.disconnect()
            DriveSharedLinkHelper.parseDriveApiV3FilesJson(listBody, parentPath, metaName)
        }.getOrNull()
    }

    private fun httpGetText(urlString: String): Result<Triple<Int, String, String>> = runCatching {
        var currentUrl = urlString
        var hops = 0
        while (hops < 5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) return@runCatching Triple(code, currentUrl, "")
                currentUrl = URL(URL(currentUrl), loc).toString()
                if (currentUrl.contains("accounts.google.com", ignoreCase = true)) {
                    return@runCatching Triple(code, currentUrl, "")
                }
                hops++
                continue
            }
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()
            return@runCatching Triple(code, currentUrl, body)
        }
        Triple(200, currentUrl, "")
    }
}

