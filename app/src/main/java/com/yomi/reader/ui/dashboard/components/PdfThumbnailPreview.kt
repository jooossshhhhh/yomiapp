package com.yomi.reader.ui.dashboard.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Shared LRU memory + disk cache for first-page PDF preview thumbnails.
 * Bounds concurrent native PdfRenderer usage and reuses decoded bitmaps
 * across Home, Continue Reading, List, Grid, and Converted Files views.
 */
internal object PdfThumbnailCache {
    private const val DEFAULT_THUMB_WIDTH_PX = 360
    private const val MAX_DISK_CACHE_FILES = 150
    private const val PRUNE_KEEP_FILES = 100
    private const val MAX_FAILED_CACHE_KEYS = 500
    private const val THUMB_CACHE_DIR = "pdf_thumbs"

    private val memoryCache = object : LruCache<String, Bitmap>(20 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val failedKeys = ConcurrentHashMap.newKeySet<String>()
    private val inFlight = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val renderSemaphore = Semaphore(2)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal fun buildCacheKey(uriString: String, lastModified: Long): String {
        val raw = "$uriString|$lastModified|$DEFAULT_THUMB_WIDTH_PX"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() ushr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
    }

    fun getCachedSync(uriString: String, lastModified: Long): Bitmap? {
        if (uriString.isBlank()) return null
        return memoryCache.get(buildCacheKey(uriString, lastModified))
    }

    fun isKnownFailure(uriString: String, lastModified: Long): Boolean {
        if (uriString.isBlank()) return true
        return failedKeys.contains(buildCacheKey(uriString, lastModified))
    }

    suspend fun loadFirstPage(
        context: Context,
        uriString: String,
        lastModified: Long
    ): Bitmap? {
        if (uriString.isBlank()) return null
        val key = buildCacheKey(uriString, lastModified)
        memoryCache.get(key)?.let { return it }
        if (failedKeys.contains(key)) return null

        val appContext = context.applicationContext
        val deferred = getOrCreateLoadJob(appContext, key, uriString)
        return deferred.await()
    }

    private fun getOrCreateLoadJob(
        appContext: Context,
        key: String,
        uriString: String
    ): Deferred<Bitmap?> {
        inFlight[key]?.let { return it }

        val created = CompletableDeferred<Bitmap?>()
        val existing = inFlight.putIfAbsent(key, created)
        if (existing != null) {
            return existing
        }

        cacheScope.launch {
            try {
                val loaded = loadOrRenderLocked(appContext, key, uriString)
                if (loaded != null) {
                    memoryCache.put(key, loaded)
                } else {
                    if (failedKeys.size >= MAX_FAILED_CACHE_KEYS) {
                        failedKeys.clear()
                    }
                    failedKeys.add(key)
                }
                created.complete(loaded)
            } catch (t: Throwable) {
                created.complete(null)
            } finally {
                inFlight.remove(key, created)
            }
        }
        return created
    }

    private suspend fun loadOrRenderLocked(
        appContext: Context,
        key: String,
        uriString: String
    ): Bitmap? {
        val thumbDir = File(appContext.cacheDir, THUMB_CACHE_DIR).apply { mkdirs() }
        val diskFile = File(thumbDir, "$key.jpg")

        if (diskFile.exists() && diskFile.length() > 0L) {
            val decoded = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
            if (decoded != null) {
                return decoded
            }
            runCatching { diskFile.delete() }
        }

        val rendered = renderSemaphore.withPermit {
            renderFirstPageBitmap(appContext, uriString, DEFAULT_THUMB_WIDTH_PX)
        } ?: return null

        writeDiskThumbnail(thumbDir, diskFile, rendered)
        return rendered
    }

    private fun renderFirstPageBitmap(
        appContext: Context,
        uriString: String,
        targetWidthPx: Int
    ): Bitmap? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

        val directBitmap = runCatching {
            val pfd: ParcelFileDescriptor? = if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                appContext.contentResolver.openFileDescriptor(uri, "r")
            }

            pfd?.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderPageZero(renderer, targetWidthPx)
                }
            }
        }.getOrNull()

        if (directBitmap != null) return directBitmap
        if (uri.scheme == "file") return null

        // Fallback for non-seekable content:// providers (e.g. cloud / SAF streams)
        var tempFile: File? = null
        return try {
            val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                val tmp = File.createTempFile("yomi_thumb_", ".pdf", appContext.cacheDir)
                tempFile = tmp
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
            if (!copied) return null
            val file = tempFile ?: return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderPageZero(renderer, targetWidthPx)
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { tempFile?.delete() }
        }
    }

    private fun renderPageZero(renderer: PdfRenderer, targetWidthPx: Int): Bitmap? {
        if (renderer.pageCount <= 0) return null
        return renderer.openPage(0).use { page ->
            val rawAspect = if (page.width > 0) {
                page.height.toFloat() / page.width.toFloat()
            } else {
                1.414f
            }
            val safeAspect = rawAspect.coerceIn(0.4f, 2.2f)
            val width = targetWidthPx.coerceAtLeast(1)
            val height = (width * safeAspect).roundToInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private fun writeDiskThumbnail(thumbDir: File, targetFile: File, bitmap: Bitmap) {
        runCatching {
            val tempOut = File(thumbDir, "${targetFile.name}.tmp")
            FileOutputStream(tempOut).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempOut.renameTo(targetFile)) {
                tempOut.delete()
            }
            pruneDiskCacheIfNeeded(thumbDir)
        }
    }

    private fun pruneDiskCacheIfNeeded(thumbDir: File) {
        val files = thumbDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") } ?: return
        if (files.size <= MAX_DISK_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - PRUNE_KEEP_FILES)
            .forEach { runCatching { it.delete() } }
    }
}

/**
 * Renders the cached first page of a PDF document cleanly, showing [loadingContent]
 * briefly until decoded and [fallbackContent] only if rendering fails.
 */
@Composable
internal fun PdfFirstPageThumbnail(
    uriString: String,
    lastModified: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    loadingContent: @Composable BoxScope.() -> Unit = {},
    fallbackContent: @Composable BoxScope.() -> Unit = loadingContent
) {
    val context = LocalContext.current
    var bitmap by remember(uriString, lastModified) {
        mutableStateOf(PdfThumbnailCache.getCachedSync(uriString, lastModified))
    }
    var failed by remember(uriString, lastModified) {
        mutableStateOf(bitmap == null && PdfThumbnailCache.isKnownFailure(uriString, lastModified))
    }

    LaunchedEffect(uriString, lastModified) {
        if (bitmap != null || failed) return@LaunchedEffect
        val loaded = PdfThumbnailCache.loadFirstPage(context, uriString, lastModified)
        if (loaded != null) {
            bitmap = loaded
            failed = false
        } else {
            failed = true
        }
    }

    Box(modifier = modifier) {
        val activeBitmap = bitmap
        when {
            activeBitmap != null -> {
                Image(
                    bitmap = activeBitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            failed -> fallbackContent()
            else -> loadingContent()
        }
    }
}

/**
 * Miniature framed document page preview shared across List, Grid, Continue Reading,
 * and Converted Files cards. Renders PDF page 1 and falls back cleanly when unreadable.
 */
@Composable
internal fun PdfMiniaturePageTile(
    uriString: String,
    lastModified: Long,
    isPdf: Boolean,
    contentDescription: String?,
    fallbackLabel: String,
    fallbackBadgeBg: Color,
    fallbackBadgeFg: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    shadowElevation: Dp = 1.dp,
    badgeFontSize: TextUnit = 10.sp
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fallback: @Composable BoxScope.() -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = fallbackBadgeBg,
                border = BorderStroke(0.5.dp, fallbackBadgeFg.copy(alpha = 0.15f))
            ) {
                Text(
                    text = fallbackLabel,
                    color = fallbackBadgeFg,
                    fontSize = badgeFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .shadow(elevation = shadowElevation, shape = shape)
            .clip(shape)
            .background(Color.White)
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = shape
            )
    ) {
        if (isPdf) {
            PdfFirstPageThumbnail(
                uriString = uriString,
                lastModified = lastModified,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                loadingContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )
                },
                fallbackContent = fallback
            )
        } else {
            fallback()
        }
    }
}
