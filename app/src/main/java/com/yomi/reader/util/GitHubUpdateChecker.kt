package com.yomi.reader.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val publishedDate: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkSizeLabel: String?,
    val isPrerelease: Boolean,
    val isUpdateAvailable: Boolean,
    val installedVersion: String
)

object GitHubUpdateChecker {

    const val GITHUB_OWNER = "jooossshhhhh"
    const val GITHUB_REPO = "yomiapp"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val ALL_RELEASES_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=5"

    private const val PREFS_NAME = "yomi_update_prefs"
    private const val KEY_DISMISSED_TAG = "dismissed_update_tag"
    private const val KEY_DISMISSED_AT = "dismissed_update_at"
    private const val REMIND_COOLDOWN_MS = 12L * 60L * 60L * 1000L // 12 hours

    fun getInstalledVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "1.0.0-beta"
    }

    fun shouldAutoPromptForTag(context: Context, tagName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissedTag = sp.getString(KEY_DISMISSED_TAG, null)
        val dismissedAt = sp.getLong(KEY_DISMISSED_AT, 0L)
        if (dismissedTag != tagName) return true
        return (nowMs - dismissedAt) >= REMIND_COOLDOWN_MS
    }

    fun markUpdatePromptDismissed(context: Context, tagName: String, nowMs: Long = System.currentTimeMillis()) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_TAG, tagName)
            .putLong(KEY_DISMISSED_AT, nowMs)
            .apply()
    }

    suspend fun checkForUpdates(context: Context): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val installedVersion = getInstalledVersionName(context)

            // Try /releases/latest first; if 404 (e.g., only pre-releases exist on GitHub), fall back to /releases
            val releaseJson = fetchLatestReleaseJson()
                ?: return@withContext Result.failure(
                    IllegalStateException("No published releases found on GitHub ($GITHUB_OWNER/$GITHUB_REPO) yet.")
                )

            val parsed = parseReleaseJson(releaseJson, installedVersion)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchLatestReleaseJson(): JSONObject? {
        // 1. Check all recent releases (includes both stable and beta pre-releases, ordered newest first)
        val listConn = (URL(ALL_RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Yomi-Android-UpdateChecker")
        }
        try {
            val code = listConn.responseCode
            if (code in 200..299) {
                val body = listConn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (!item.optBoolean("draft", false)) {
                        return item
                    }
                }
            }
        } finally {
            listConn.disconnect()
        }

        // 2. Fallback to /releases/latest
        val latestConn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Yomi-Android-UpdateChecker")
        }
        return try {
            val code = latestConn.responseCode
            if (code in 200..299) {
                val body = latestConn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(body)
            } else {
                null
            }
        } finally {
            latestConn.disconnect()
        }
    }

    internal fun parseReleaseJson(json: JSONObject, installedVersion: String): GitHubReleaseInfo {
        val rawTag = json.optString("tag_name", "").trim().ifEmpty { "v1.0.0-beta" }
        val normalizedRemoteVersion = normalizeVersionString(rawTag)
        val title = json.optString("name", "").trim().ifEmpty { "Yomi $rawTag" }
        val rawBody = json.optString("body", "").trim()
        val releaseNotes = formatReleaseNotes(rawBody)
        val publishedAtRaw = json.optString("published_at", "")
        val publishedDate = formatPublishedDate(publishedAtRaw)
        val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL).ifEmpty { RELEASES_PAGE_URL }
        val isPrerelease = json.optBoolean("prerelease", false)

        var apkUrl: String? = null
        var apkSizeLabel: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                    apkUrl = downloadUrl
                    val sizeBytes = asset.optLong("size", 0L)
                    if (sizeBytes > 0L) {
                        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
                        apkSizeLabel = String.format(Locale.US, "%.1f MB", mb)
                    }
                    break
                }
            }
        }

        val updateAvailable = isRemoteVersionNewer(
            installedVersion = installedVersion,
            remoteTagOrVersion = rawTag
        )

        return GitHubReleaseInfo(
            tagName = rawTag,
            versionName = normalizedRemoteVersion,
            title = title,
            releaseNotes = releaseNotes,
            publishedDate = publishedDate,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkSizeLabel = apkSizeLabel,
            isPrerelease = isPrerelease,
            isUpdateAvailable = updateAvailable,
            installedVersion = normalizeVersionString(installedVersion)
        )
    }

    fun normalizeVersionString(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Semantic version comparator supporting pre-release suffixes (e.g. 1.0.0-beta < 1.0.0 < 1.0.1).
     */
    fun isRemoteVersionNewer(installedVersion: String, remoteTagOrVersion: String): Boolean {
        val localClean = normalizeVersionString(installedVersion)
        val remoteClean = normalizeVersionString(remoteTagOrVersion)
        if (remoteClean.isEmpty() || localClean.equals(remoteClean, ignoreCase = true)) {
            return false
        }

        val localMain = localClean.substringBefore('-')
        val remoteMain = remoteClean.substringBefore('-')
        val localSuffix = localClean.substringAfter('-', missingDelimiterValue = "").lowercase(Locale.US)
        val remoteSuffix = remoteClean.substringAfter('-', missingDelimiterValue = "").lowercase(Locale.US)

        val localNums = localMain.split('.').map { it.toIntOrNull() ?: 0 }
        val remoteNums = remoteMain.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(localNums.size, remoteNums.size, 3)

        for (i in 0 until maxLen) {
            val l = localNums.getOrElse(i) { 0 }
            val r = remoteNums.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }

        // Same numeric version (e.g., 1.0.0-beta vs 1.0.0, or 1.0.0-beta.1 vs 1.0.0-beta.2)
        if (localSuffix.isNotEmpty() && remoteSuffix.isEmpty()) {
            // Remote is stable release, local is pre-release -> remote is newer
            return true
        }
        if (localSuffix.isEmpty() && remoteSuffix.isNotEmpty()) {
            // Local is already stable release, remote is pre-release -> remote is not newer
            return false
        }
        return remoteSuffix > localSuffix
    }

    internal fun formatReleaseNotes(rawMarkdown: String): String {
        if (rawMarkdown.isBlank()) {
            return "• Bug fixes, stability improvements, and performance optimizations."
        }
        return rawMarkdown
            .lines()
            .map { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("### ") -> trimmed.removePrefix("### ").trim()
                    trimmed.startsWith("## ") -> trimmed.removePrefix("## ").trim()
                    trimmed.startsWith("# ") -> trimmed.removePrefix("# ").trim()
                    trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ").trim()
                    trimmed.startsWith("* ") -> "• " + trimmed.removePrefix("* ").trim()
                    else -> trimmed
                }
            }
            .map { it.replace("**", "") }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun formatPublishedDate(isoString: String): String {
        if (isoString.isBlank()) return "Latest GitHub Release"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(isoString) ?: return isoString.take(10)
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
            formatter.format(date)
        } catch (_: Exception) {
            isoString.take(10)
        }
    }
}

