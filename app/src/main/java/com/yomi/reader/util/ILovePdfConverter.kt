package com.yomi.reader.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yomi.reader.util.conversion.ConversionQuotaHelper
import com.yomi.reader.util.conversion.ExcelFitToPagePreprocessor
import com.yomi.reader.util.conversion.QuotaExhaustedException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

object ILovePdfConverter {

    private const val AUTH_URL = "https://api.ilovepdf.com/v1/auth"
    private const val START_TASK_URL = "https://api.ilovepdf.com/v1/start/officepdf"

    /**
     * Converts a DOCX, XLSX, or PPTX file to PDF using the iLovePDF / iLoveAPI REST engine.
     */
    suspend fun convertOfficeToPdf(
        context: Context,
        fileUri: Uri,
        fileName: String,
        publicKey: String = ApiConfig.ILOVEPDF_PUBLIC_KEY,
        secretKey: String = ApiConfig.ILOVEPDF_SECRET_KEY,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (publicKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("iLovePDF public key is not configured."))
            }

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
            val cacheDir = File(context.cacheDir, "ilovepdf_cache").apply { mkdirs() }
            val cacheKey = buildContentCacheKey("ilove_v3", fileName, fileUri.toString(), fileBytes)
            val cachedPdf = File(cacheDir, cacheKey)
            if (cachedPdf.exists() && cachedPdf.length() > 0) {
                onProgress(100)
                return@withContext Result.success(cachedPdf)
            }

            // Step 1: Authenticate to get JWT token
            onProgress(20)
            val token = authenticate(publicKey)
                ?: return@withContext Result.failure(IllegalStateException("iLovePDF authentication failed. Please check your Public Key."))

            // Step 2: Start officepdf task
            onProgress(35)
            val taskInfo = startTask(token)
                ?: return@withContext Result.failure(IllegalStateException("Failed to start iLovePDF office-to-pdf conversion task."))
            val server = taskInfo.first
            val taskId = taskInfo.second

            // Step 3: Upload the office document (with single-page sheet fit applied to spreadsheets)
            onProgress(55)
            val uploadBytes = ExcelFitToPagePreprocessor.prepareSinglePageWorkbookBytes(fileBytes, fileName)
            val serverFilename = uploadFile(server, taskId, token, uploadBytes, fileName)
                ?: return@withContext Result.failure(IllegalStateException("Failed to upload document to iLovePDF conversion server."))

            // Step 4: Process task
            onProgress(78)
            val processSuccess = processTask(server, taskId, token, serverFilename, fileName)
            if (!processSuccess) {
                return@withContext Result.failure(IllegalStateException("iLovePDF server failed to convert document to PDF."))
            }

            // Step 5: Download converted PDF
            onProgress(92)
            val downloadUrl = "https://$server/v1/download/$taskId"
            val tempFile = File(cacheDir, "temp_${UUID.randomUUID()}.pdf")
            downloadFile(downloadUrl, token, tempFile)

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

    internal fun buildContentCacheKey(
        prefix: String,
        fileName: String,
        uriString: String,
        fileBytes: ByteArray
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(fileName.toByteArray(Charsets.UTF_8))
            update('|'.code.toByte())
            update(uriString.toByteArray(Charsets.UTF_8))
            update('|'.code.toByte())
            update(fileBytes)
        }.digest()
        val hex = digest.take(12).joinToString("") { "%02x".format(it) }
        return "${prefix}_${fileBytes.size}_$hex.pdf"
    }

    private fun authenticate(publicKey: String): String? {
        val url = URL(AUTH_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val json = JSONObject().apply {
                put("public_key", publicKey)
            }.toString()

            conn.outputStream.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                obj.optString("token").takeIf { it.isNotEmpty() }
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                    throw QuotaExhaustedException(code, "iLoveAPI credits or quota exhausted (HTTP $code)")
                }
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun startTask(token: String): Pair<String, String>? {
        val url = URL(START_TASK_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $token")
        }

        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                val server = obj.getString("server")
                val task = obj.getString("task")
                Pair(server, task)
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                    throw QuotaExhaustedException(code, "iLoveAPI credits or quota exhausted (HTTP $code)")
                }
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun uploadFile(
        server: String,
        taskId: String,
        token: String,
        fileBytes: ByteArray,
        fileName: String
    ): String? {
        val boundary = "===Boundary_${System.currentTimeMillis()}==="
        val lineEnd = "\r\n"
        val url = URL("https://$server/v1/upload")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        return try {
            conn.outputStream.use { os ->
                val writer = PrintWriter(OutputStreamWriter(os, Charsets.UTF_8), true)

                // task parameter
                writer.append("--$boundary$lineEnd")
                writer.append("Content-Disposition: form-data; name=\"task\"$lineEnd$lineEnd")
                writer.append(taskId).append(lineEnd)
                writer.flush()

                // file parameter
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

            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                obj.getString("server_filename")
            } else {
                val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                    throw QuotaExhaustedException(code, "iLoveAPI credits or quota exhausted during upload (HTTP $code)")
                }
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun processTask(
        server: String,
        taskId: String,
        token: String,
        serverFilename: String,
        fileName: String
    ): Boolean {
        val url = URL("https://$server/v1/process")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            val fileObj = JSONObject().apply {
                put("server_filename", serverFilename)
                put("filename", fileName)
            }
            val filesArray = JSONArray().apply {
                put(fileObj)
            }
            val body = JSONObject().apply {
                put("task", taskId)
                put("tool", "officepdf")
                put("files", filesArray)
            }.toString()

            conn.outputStream.use { os ->
                os.write(body.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) return true
            val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                throw QuotaExhaustedException(code, "iLoveAPI credits or quota exhausted during processing (HTTP $code)")
            }
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(downloadUrl: String, token: String, outputFile: File) {
        val url = URL(downloadUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 60000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $token")
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

