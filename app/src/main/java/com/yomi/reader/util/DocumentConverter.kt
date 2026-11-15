package com.yomi.reader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Xml
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.parser.pdf.PdfTextExtractor
import com.yomi.reader.util.conversion.LibreOfficePdfBridge
import com.yomi.reader.util.conversion.OfflineDocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ConversionSuccess(
    val file: File,
    val isOffline: Boolean,
    val engineName: String
)

object DocumentConverter {

    /**
     * Converts any supported Office document (DOCX, XLSX, PPTX) to a PDF using the on-device
     * offline conversion engine.
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
     * Converts an office file URI directly to a PDF on-device before indexing.
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
     * Converts an office or supported image/text URI to PDF using Yomi's on-device Offline Engine.
     */
    suspend fun convertUriToPdfDetailed(
        context: Context,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit = {},
        outputBaseName: String? = null
    ): Result<ConversionSuccess> = withContext(Dispatchers.IO) {
        val baseName = outputBaseName?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')

        onProgress(5)
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
                ConversionSuccess(file = it, isOffline = true, engineName = "LibreOfficeKit Offline")
            }
        }

        val errorMsg = offlineRes.exceptionOrNull()?.message ?: "Conversion failed"
        Result.failure(IllegalStateException(errorMsg))
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
                    extractWordPlainText(context, Uri.parse(document.uriString), document.title)
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

    private suspend fun extractWordPlainText(
        context: Context,
        uri: Uri,
        title: String
    ): String {
        val ext = title.substringAfterLast('.', "docx").lowercase()
        if (ext == "docx") {
            val xmlText = extractDocxXmlPlainText(context, uri)
            if (xmlText != null) {
                return xmlText
            }
        }

        // Legacy .doc / fallback: extract via LibreOfficeKit's plain text filter
        val workDir = File(context.cacheDir, "lok_work").apply { mkdirs() }
        val jobId = UUID.randomUUID().toString()
        val tempInput = File(workDir, "$jobId.$ext")
        val tempOutput = File(workDir, "${jobId}_out.txt")
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open Word file at $uri")
            stream.use { input ->
                FileOutputStream(tempInput).use { output -> input.copyTo(output) }
            }
            val result = LibreOfficePdfBridge.convert(
                context = context,
                inputFile = tempInput,
                outputFile = tempOutput,
                format = "txt"
            )
            result.getOrThrow().readText(Charsets.UTF_8)
        } finally {
            tempInput.delete()
            tempOutput.delete()
        }
    }

    private fun extractDocxXmlPlainText(context: Context, uri: Uri): String? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        stream.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(zip, "UTF-8")

                        val paragraphs = mutableListOf<String>()
                        val currentPara = StringBuilder()
                        var inText = false
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    when (parser.name) {
                                        "w:p" -> currentPara.setLength(0)
                                        "w:t" -> inText = true
                                        "w:tab" -> currentPara.append('\t')
                                        "w:br", "w:cr" -> currentPara.append('\n')
                                    }
                                }
                                XmlPullParser.TEXT -> if (inText) {
                                    currentPara.append(parser.text)
                                }
                                XmlPullParser.END_TAG -> {
                                    when (parser.name) {
                                        "w:t" -> inText = false
                                        "w:p" -> {
                                            val text = currentPara.toString().trimEnd()
                                            if (text.isNotEmpty()) {
                                                paragraphs.add(text)
                                            }
                                        }
                                    }
                                }
                            }
                            event = parser.next()
                        }
                        return paragraphs.joinToString("\n\n")
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
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
