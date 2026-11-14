package com.yomi.reader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.parser.docx.DocxContentParser
import com.yomi.reader.parser.docx.DocxElement
import com.yomi.reader.parser.pdf.PdfTextExtractor
import com.yomi.reader.util.conversion.CloudProxyConverter
import com.yomi.reader.util.conversion.ConversionQuotaHelper
import com.yomi.reader.util.conversion.ConversionQuotaStore
import com.yomi.reader.util.conversion.OfflineDocumentConverter
import com.yomi.reader.util.conversion.QuotaExhaustedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ConversionSuccess(
    val file: File,
    val isOffline: Boolean,
    val engineName: String
)

object DocumentConverter {

    /**
     * Converts any supported Office document (DOCX, XLSX, PPTX) to a high-fidelity PDF.
     * Automatically uses the cloud API when online and the offline engine when offline.
     */
    suspend fun convertOfficeToPdf(
        context: Context,
        document: DocumentItem,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = convertUriToPdf(
        context = context,
        fileUri = Uri.parse(document.uriString),
        fileName = document.title,
        onProgress = onProgress
    )

    /**
     * Converts an office file URI directly to a PDF before indexing.
     * Automatically detects connectivity: online API when connected, offline engine when disconnected.
     */
    suspend fun convertUriToPdf(
        context: Context,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit = {},
        outputBaseName: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        convertUriToPdfDetailed(context, fileUri, fileName, onProgress, outputBaseName).map { it.file }
    }

    /**
     * Detailed conversion returning information on whether the result was generated offline or online.
     * Automatically chooses the cloud API when internet connectivity is present, otherwise converts offline.
     */
    suspend fun convertUriToPdfDetailed(
        context: Context,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit = {},
        outputBaseName: String? = null
    ): Result<ConversionSuccess> = withContext(Dispatchers.IO) {
        val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Converted_PDFs").apply {
            if (!exists()) mkdirs()
        }
        val baseName = outputBaseName?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
        val targetFile = File(outputDir, "${baseName}_converted.pdf")

        onProgress(5)
        val isOnline = NetworkUtils.isOnline(context)
        val hasDailyBudget = ConversionQuotaStore.hasDailyCloudQuotaRemaining(context)

        // Automatic Offline-First: always use the local offline engine first (0 credits, stays in Yomi),
        // and only fail over to Cloud APIs automatically if offline conversion fails.
        val offlineRes = OfflineDocumentConverter.convertToPdf(
            context = context,
            fileUri = fileUri,
            fileName = fileName,
            onProgress = onProgress,
            outputBaseName = baseName
        )
        if (offlineRes.isSuccess) {
            onProgress(100)
            return@withContext offlineRes.map {
                ConversionQuotaStore.recordLastConversionEngine(context, "Offline Engine")
                ConversionSuccess(file = it, isOffline = true, engineName = "Offline Engine")
            }
        }

        var backupCloudError: Throwable? = null
        if (isOnline && hasDailyBudget) {
            val (cloudBackup, cloudErr) = tryCloudCascade(
                context = context,
                fileUri = fileUri,
                fileName = fileName,
                targetFile = targetFile,
                onProgress = onProgress
            )
            if (cloudBackup != null) {
                return@withContext Result.success(cloudBackup)
            }
            backupCloudError = cloudErr
        }

        val errorMsg = offlineRes.exceptionOrNull()?.message
            ?: backupCloudError?.message
            ?: if (isOnline && !hasDailyBudget) {
                "Offline conversion failed and daily cloud limit (${ConversionQuotaHelper.DAILY_CLOUD_CONVERSION_LIMIT}/day) reached"
            } else {
                "Conversion failed"
            }
        Result.failure(IllegalStateException(errorMsg))
    }

    private suspend fun tryCloudCascade(
        context: Context,
        fileUri: Uri,
        fileName: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ): Pair<ConversionSuccess?, Throwable?> {
        var lastCloudError: Throwable? = null

        // Step 0: Serverless Middleman Proxy (Cloudflare Worker) — keeps API keys off the APK
        if (ConversionQuotaHelper.isValidProxyUrl(ApiConfig.CONVERSION_PROXY_URL)) {
            val proxyResult = CloudProxyConverter.convertOfficeToPdf(
                context = context,
                fileUri = fileUri,
                fileName = fileName,
                onProgress = onProgress
            )
            if (proxyResult.isSuccess) {
                val proxyOut = proxyResult.getOrThrow()
                proxyOut.pdfFile.copyTo(targetFile, overwrite = true)
                ConversionQuotaStore.recordSuccessfulCloudConversion(context)
                ConversionQuotaStore.recordLastConversionEngine(context, proxyOut.providerLabel)
                onProgress(100)
                return ConversionSuccess(
                    file = targetFile,
                    isOffline = false,
                    engineName = proxyOut.providerLabel
                ) to null
            }
            lastCloudError = proxyResult.exceptionOrNull()
        }

        val iloveKeys = ApiConfig.collectOrderedKeys(
            ApiConfig.ILOVEPDF_PUBLIC_KEY,
            ApiConfig.ILOVEPDF_BACKUP_PUBLIC_KEY,
            ConversionQuotaStore.getBackupIlovePdfKey(context)
        )
        for ((index, key) in iloveKeys.withIndex()) {
            if (!ConversionQuotaStore.isSlotAvailable(context, "ilove", key)) continue
            val iloveResult = ILovePdfConverter.convertOfficeToPdf(
                context = context,
                fileUri = fileUri,
                fileName = fileName,
                publicKey = key,
                onProgress = onProgress
            )
            if (iloveResult.isSuccess) {
                val converted = iloveResult.getOrThrow()
                converted.copyTo(targetFile, overwrite = true)
                val engineLabel = if (index == 0) "iLovePDF Primary" else "iLovePDF Backup"
                ConversionQuotaStore.recordSuccessfulCloudConversion(context)
                ConversionQuotaStore.recordLastConversionEngine(context, engineLabel)
                onProgress(100)
                return ConversionSuccess(file = targetFile, isOffline = false, engineName = engineLabel) to null
            }
            val err = iloveResult.exceptionOrNull()
            lastCloudError = err
            if (err is QuotaExhaustedException) {
                ConversionQuotaStore.markSlotQuotaExhausted(
                    context = context,
                    providerPrefix = "ilove",
                    apiKey = key,
                    reason = err.message ?: "Quota exhausted"
                )
            }
        }

        val ccKeys = ApiConfig.collectOrderedKeys(
            ApiConfig.CLOUDCONVERT_API_KEY,
            ApiConfig.CLOUDCONVERT_BACKUP_API_KEY,
            ConversionQuotaStore.getBackupCloudConvertKey(context)
        )
        for ((index, key) in ccKeys.withIndex()) {
            if (!ConversionQuotaStore.isSlotAvailable(context, "cc", key)) continue
            val ccResult = CloudConvertConverter.convertOfficeToPdf(
                context = context,
                fileUri = fileUri,
                fileName = fileName,
                apiKey = key,
                onProgress = onProgress
            )
            if (ccResult.isSuccess) {
                val converted = ccResult.getOrThrow()
                converted.copyTo(targetFile, overwrite = true)
                val engineLabel = if (index == 0) "CloudConvert Engine" else "CloudConvert Backup"
                ConversionQuotaStore.recordSuccessfulCloudConversion(context)
                ConversionQuotaStore.recordLastConversionEngine(context, engineLabel)
                onProgress(100)
                return ConversionSuccess(file = targetFile, isOffline = false, engineName = engineLabel) to null
            }
            val err = ccResult.exceptionOrNull()
            lastCloudError = err
            if (err is QuotaExhaustedException) {
                ConversionQuotaStore.markSlotQuotaExhausted(
                    context = context,
                    providerPrefix = "cc",
                    apiKey = key,
                    reason = err.message ?: "Quota exhausted"
                )
            }
        }

        return null to lastCloudError
    }

    /**
     * Converts a DOCX document into a standard PDF file using the on-device engine.
     */
    suspend fun convertDocxToPdf(
        context: Context,
        document: DocumentItem
    ): Result<File> = withContext(Dispatchers.IO) {
        OfflineDocumentConverter.convertToPdf(
            context = context,
            fileUri = Uri.parse(document.uriString),
            fileName = document.title
        )
    }

    /**
     * Extracts text from PDF or DOCX and saves as a .txt document.
     */
    suspend fun convertToPlainText(
        context: Context,
        document: DocumentItem
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val textContent = when (document.type) {
                DocumentType.PDF -> {
                    PdfTextExtractor.extractText(context, Uri.parse(document.uriString))
                }
                DocumentType.WORD -> {
                    val parser = DocxContentParser(context)
                    val model = parser.parseDocumentModel(Uri.parse(document.uriString))
                    model.elements.filterIsInstance<DocxElement.Paragraph>().joinToString("\n\n") { it.text }
                }
                else -> {
                    return@withContext Result.failure(IllegalArgumentException("Unsupported format for text extraction"))
                }
            }

            val outputDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Extracted_Notes")
            if (!outputDir.exists()) outputDir.mkdirs()

            val baseName = document.title.substringBeforeLast('.')
            val targetFile = File(outputDir, "${baseName}_notes.txt")

            targetFile.writeText(textContent, Charsets.UTF_8)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Scans and retrieves all converted PDF documents.
     */
    fun getAllConvertedFiles(context: Context): List<ConvertedFileItem> {
        val baseDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return emptyList()
        val dirs = listOf(
            File(baseDocsDir, "Converted_PDFs") to "Converted PDF"
        )

        val results = mutableListOf<ConvertedFileItem>()
        for ((dir, conversionType) in dirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.length() > 0 && file.extension.equals("pdf", ignoreCase = true)) {
                        results.add(
                            ConvertedFileItem(
                                name = file.name,
                                file = file,
                                type = DocumentType.PDF,
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                conversionType = conversionType,
                                uriString = Uri.fromFile(file).toString()
                            )
                        )
                    }
                }
            }
        }

        return results.sortedByDescending { it.lastModified }
    }

    fun deleteConvertedFile(file: File): Boolean {
        return runCatching { file.delete() }.getOrDefault(false)
    }
}

data class ConvertedFileItem(
    val name: String,
    val file: File,
    val type: DocumentType,
    val sizeBytes: Long,
    val lastModified: Long,
    val conversionType: String,
    val uriString: String
) {
    val formattedSize: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb < 1024) String.format("%.1f KB", kb) else String.format("%.1f MB", kb / 1024.0)
        }

    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastModified))
        }
}
