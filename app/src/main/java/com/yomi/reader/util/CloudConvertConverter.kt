package com.yomi.reader.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.yomi.reader.util.conversion.ConversionQuotaHelper
import com.yomi.reader.util.conversion.ExcelFitToPagePreprocessor
import com.yomi.reader.util.conversion.QuotaExhaustedException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object CloudConvertConverter {

    private const val JOBS_URL = "https://api.cloudconvert.com/v2/jobs"

    /**
     * Converts a DOCX, XLSX, or PPTX file to PDF using the CloudConvert v2 Office API.
     */
    suspend fun convertOfficeToPdf(
        context: Context,
        fileUri: Uri,
        fileName: String,
        apiKey: String = ApiConfig.CLOUDCONVERT_API_KEY,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("CloudConvert API key is not configured."))
        }

        try {
            onProgress(10)
            // Read source file bytes safely from content:// or file://
            val fileBytes = try {
                if (fileUri.scheme == "file") {
                    File(fileUri.path ?: "").readBytes()
                } else {
                    context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                }
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot read source file: $fileUri"))

            // Check local cache using content SHA-256 fingerprint so edited files never serve stale PDFs
            val cacheDir = File(context.cacheDir, "cloudconvert_cache").apply { mkdirs() }
            val cacheKey = ILovePdfConverter.buildContentCacheKey("cc_v3", fileName, fileUri.toString(), fileBytes)
            val cachedPdf = File(cacheDir, cacheKey)
            if (cachedPdf.exists() && cachedPdf.length() > 0) {
                onProgress(100)
                return@withContext Result.success(cachedPdf)
            }

            // Step 1: Create Job
            onProgress(22)
            val jobInfo = createJob(apiKey)
                ?: return@withContext Result.failure(IllegalStateException("Failed to create CloudConvert job."))
            val uploadUrl = jobInfo.uploadUrl
            val uploadParams = jobInfo.uploadParams
            val exportTaskId = jobInfo.exportTaskId

            // Step 2: Upload file via multipart (with single-page sheet fit applied to spreadsheets)
            onProgress(45)
            val uploadBytes = ExcelFitToPagePreprocessor.prepareSinglePageWorkbookBytes(fileBytes, fileName)
            val uploadSuccess = uploadFile(uploadUrl, uploadParams, uploadBytes, fileName)
            if (!uploadSuccess) {
                return@withContext Result.failure(IllegalStateException("Failed to upload file to CloudConvert."))
            }

            // Step 3: Poll export task until finished
            onProgress(75)
            val downloadUrl = pollForDownloadUrl(exportTaskId, apiKey)
                ?: return@withContext Result.failure(IllegalStateException("CloudConvert processing timed out or failed."))

            // Step 4: Download converted PDF
            onProgress(92)
            val tempFile = File(cacheDir, "temp_${UUID.randomUUID()}.pdf")
            downloadFile(downloadUrl, tempFile)

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Downloaded PDF is empty."))
            }

            tempFile.renameTo(cachedPdf)
            onProgress(100)
            Result.success(cachedPdf)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class JobInfo(
        val uploadUrl: String,
        val uploadParams: Map<String, String>,
        val exportTaskId: String
    )

    private fun createJob(apiKey: String): JobInfo? {
        val url = URL(JOBS_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        val tasksObj = JSONObject().apply {
            put("upload-file", JSONObject().apply {
                put("operation", "import/upload")
            })
            put("convert-file", JSONObject().apply {
                put("operation", "convert")
                put("input", "upload-file")
                put("output_format", "pdf")
                put("engine", "office")
            })
            put("export-file", JSONObject().apply {
                put("operation", "export/url")
                put("input", "convert-file")
            })
        }
        val requestBody = JSONObject().apply {
            put("tasks", tasksObj)
        }.toString()

        return try {
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                    throw QuotaExhaustedException(code, "CloudConvert credits or quota exhausted (HTTP $code)")
                }
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            val data = root.getJSONObject("data")
            val tasks = data.getJSONArray("tasks")

            var uploadUrl: String? = null
            val uploadParams = mutableMapOf<String, String>()
            var exportTaskId: String? = null

            for (i in 0 until tasks.length()) {
                val task = tasks.getJSONObject(i)
                val name = task.optString("name")
                val id = task.getString("id")
                if (name == "upload-file") {
                    val result = task.optJSONObject("result")
                    val form = result?.optJSONObject("form")
                    uploadUrl = form?.optString("url")
                    val paramsObj = form?.optJSONObject("parameters")
                    if (paramsObj != null) {
                        val keys = paramsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            uploadParams[key] = paramsObj.getString(key)
                        }
                    }
                } else if (name == "export-file") {
                    exportTaskId = id
                }
            }

            if (uploadUrl != null && exportTaskId != null) {
                JobInfo(uploadUrl, uploadParams, exportTaskId)
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadFile(
        uploadUrl: String,
        parameters: Map<String, String>,
        fileBytes: ByteArray,
        fileName: String
    ): Boolean {
        val boundary = "===Boundary_${System.currentTimeMillis()}==="
        val lineEnd = "\r\n"
        val url = URL(uploadUrl)

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        return try {
            conn.outputStream.use { os ->
                val writer = PrintWriter(OutputStreamWriter(os, Charsets.UTF_8), true)

                // Parameters first
                for ((key, value) in parameters) {
                    writer.append("--$boundary$lineEnd")
                    writer.append("Content-Disposition: form-data; name=\"$key\"$lineEnd$lineEnd")
                    writer.append(value).append(lineEnd)
                    writer.flush()
                }

                // File parameter must be last
                writer.append("--$boundary$lineEnd")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd")
                writer.append("Content-Type: application/octet-stream$lineEnd$lineEnd")
                writer.flush()

                os.write(fileBytes)
                os.flush()

                writer.append(lineEnd)
                writer.append("--$boundary--$lineEnd")
                writer.flush()
            }

            conn.responseCode in 200..299 || conn.responseCode == 303
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun pollForDownloadUrl(exportTaskId: String, apiKey: String): String? {
        val pollUrl = "https://api.cloudconvert.com/v2/tasks/$exportTaskId"
        var attempts = 0
        while (attempts < 30) {
            delay(1500)
            attempts++

            val url = URL(pollUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $apiKey")
            }

            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(response)
                    val data = root.getJSONObject("data")
                    val status = data.getString("status")

                    if (status == "finished") {
                        val result = data.optJSONObject("result")
                        val files = result?.optJSONArray("files")
                        if (files != null && files.length() > 0) {
                            return files.getJSONObject(0).getString("url")
                        }
                    } else if (status == "error") {
                        val errCode = data.optString("code", "")
                        val errMsg = data.optString("message", "")
                        val combined = "$errCode $errMsg".trim()
                        if (combined.contains("credit", ignoreCase = true) ||
                            combined.contains("quota", ignoreCase = true) ||
                            combined.contains("limit", ignoreCase = true)
                        ) {
                            throw QuotaExhaustedException(402, "CloudConvert task failed due to credits/quota: $combined")
                        }
                        return null
                    }
                } else {
                    val errBody = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                    if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                        throw QuotaExhaustedException(code, "CloudConvert credits or quota exhausted (HTTP $code)")
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun downloadFile(downloadUrl: String, outputFile: File) {
        val url = URL(downloadUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 60000
            readTimeout = 60000
        }

        try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                throw IllegalStateException("Download returned HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }
}

