package com.yomi.reader.util.conversion

import android.content.Context
import android.content.res.AssetManager
import com.yomi.reader.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Unpacks LibreOffice runtime registries, configuration, and bundled fonts into app storage once
 * per app version and generates a runtime `fonts.conf` pointing to Yomi's actual directories.
 */
object LibreOfficeAssetExtractor {

    private const val STAMP_FILE_NAME = ".lo_assets_stamp"
    private const val ASSET_UNPACK_ROOT = "unpack"
    private const val ASSET_FONTS_ROOT = "fonts"

    data class ExtractedPaths(
        val dataDir: File,
        val fontConfigFile: File
    )

    @Synchronized
    fun ensureExtracted(context: Context): ExtractedPaths {
        val appContext = context.applicationContext
        val dataDir = File(appContext.applicationInfo.dataDir)
        val filesDir = appContext.filesDir.apply { mkdirs() }
        val fontConfigCacheDir = File(appContext.cacheDir, "fontconfig").apply { mkdirs() }
        val fontsConfFile = File(dataDir, "etc/fonts/fonts.conf")
        val sofficercFile = File(dataDir, "program/sofficerc")
        val yomiFontsDir = File(filesDir, ASSET_FONTS_ROOT)
        val loUserFontsDir = File(dataDir, "user/fonts")

        val expectedStamp = "${BuildConfig.VERSION_CODE}-${BuildConfig.VERSION_NAME}-fc4"
        val stampFile = File(filesDir, STAMP_FILE_NAME)
        val currentStamp = if (stampFile.isFile) stampFile.readText().trim() else null

        if (currentStamp == expectedStamp && fontsConfFile.isFile && sofficercFile.isFile && yomiFontsDir.isDirectory) {
            return ExtractedPaths(dataDir = dataDir, fontConfigFile = fontsConfFile)
        }

        val assetManager = appContext.assets
        fontConfigCacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        copyAssetTree(assetManager, ASSET_UNPACK_ROOT, dataDir)
        copyAssetTree(assetManager, ASSET_FONTS_ROOT, yomiFontsDir)

        patchFontConfig(
            fontsConfFile = fontsConfFile,
            loUserFontsDir = loUserFontsDir,
            yomiFontsDir = yomiFontsDir,
            fontConfigCacheDir = fontConfigCacheDir
        )

        stampFile.writeText(expectedStamp)
        return ExtractedPaths(dataDir = dataDir, fontConfigFile = fontsConfFile)
    }

    private fun copyAssetTree(assetManager: AssetManager, assetPath: String, targetDir: File) {
        val children = assetManager.list(assetPath) ?: return
        if (children.isEmpty()) {
            // Leaf file
            targetDir.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                FileOutputStream(targetDir).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        targetDir.mkdirs()
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childTarget = File(targetDir, child)
            copyAssetTree(assetManager, childAssetPath, childTarget)
        }
    }

    private fun patchFontConfig(
        fontsConfFile: File,
        loUserFontsDir: File,
        yomiFontsDir: File,
        fontConfigCacheDir: File
    ) {
        if (!fontsConfFile.isFile) return
        val updated = fontsConfFile.readText()
            .replace("/data/data/com.yomi.app/user/fonts", loUserFontsDir.absolutePath)
            .replace("/data/data/com.yomi.app/files/fonts", yomiFontsDir.absolutePath)
            .replace("/data/data/com.yomi.app/cache/fontconfig", fontConfigCacheDir.absolutePath)
        fontsConfFile.writeText(updated)
    }
}

