package com.yomi.reader.util.conversion

import android.content.Context
import android.net.Uri
import com.yomi.reader.util.ApiConfig
import com.yomi.reader.util.ILovePdfConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class ProxyConversionResult(
    val pdfFile: File,
    val providerLabel: String
)

/**
 * Middleman Serverless Proxy client (Cloudflare Workers).
 * Sends source Office/image documents to the Yomi Conversion Proxy so that iLovePDF and
 * CloudConvert API keys stay locked in server-side environment secrets rather than inside the APK.
 */
object CloudProxyConverter {

    suspend fun convertOfficeToPdf(
        context: Context,
        fileUri: Uri,
        fileName: String,
        proxyUrl: String = ApiConfig.CONVERSION_PROXY_URL,
        proxyToken: String = ApiConfig.CONVERSION_PROXY_TOKEN,
        onProgress: (Int) -> Unit = {}
    ): Result<ProxyConversionResult> = withContext(Dispatchers.IO) {
        try {
            if (!ConversionQuotaHelper.isValidProxyUrl(proxyUrl)) {
                return@withContext Result.failure(
                    IllegalStateException("Conversion proxy URL is not configured.")
                )
            }

            onProgress(10)
            val fileBytes = try {
                if (fileUri.scheme == "file") {
                    File(fileUri.path ?: "").readBytes()
                } else {
                    context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                }
            } catch (_: Exception) {
                null
            } ?: return@withContext Result.failure(
                IllegalArgumentException("Cannot read source file: $fileUri")
            )

            val cacheDir = File(context.cacheDir, "proxy_pdf_cache").apply { mkdirs() }
            val cacheKey = ILovePdfConverter.buildContentCacheKey(
                prefix = "proxy_v1",
                fileName = fileName,
                uriString = fileUri.toString(),
                fileBytes = fileBytes
            )
            val cachedPdf = File(cacheDir, cacheKey)
            if (cachedPdf.exists() && cachedPdf.length() > 0L) {
                onProgress(100)
                return@withContext Result.success(
                    ProxyConversionResult(pdfFile = cachedPdf, providerLabel = "Cloud Proxy (Cached)")
                )
            }

            onProgress(30)
            val uploadBytes = ExcelFitToPagePreprocessor.prepareSinglePageWorkbookBytes(fileBytes, fileName)
            val endpoint = ConversionQuotaHelper.normalizeProxyConvertUrl(proxyUrl)
            val userScope = ConversionQuotaHelper.resolveUserScope(
                ConversionQuotaStore.resolveActiveUserEmail(context)
            )

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("X-File-Name", URLEncoder.encode(fileName, "UTF-8"))
                setRequestProperty("X-User-Scope", userScope)
                if (proxyToken.isNotBlank()) {
                    setRequestProperty("X-Yomi-Client-Token", proxyToken.trim())
                }
            }

            try {
                conn.outputStream.use { os ->
                    os.write(uploadBytes)
                    os.flush()
                }
                onProgress(70)

                val code = conn.responseCode
                if (code in 200..299) {
                    val providerHeader = conn.getHeaderField("X-Conversion-Provider")
                        ?.takeIf { it.isNotBlank() }
                        ?: "Yomi Cloud Proxy"
                    val tempFile = File(cacheDir, "temp_proxy_${UUID.randomUUID()}.pdf")
                    conn.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                            }
                            output.flush()
                        }
                    }

                    if (!tempFile.exists() || tempFile.length() < 8L) {
                        tempFile.delete()
                        return@withContext Result.failure(
                            IllegalStateException("Proxy returned an empty PDF response.")
                        )
                    }

                    tempFile.copyTo(cachedPdf, overwrite = true)
                    tempFile.delete()
                    onProgress(100)
                    Result.success(
                        ProxyConversionResult(pdfFile = cachedPdf, providerLabel = providerHeader)
                    )
                } else {
                    val errBody = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val parsedMsg = try {
                        JSONObject(errBody).optString("error").takeIf { it.isNotEmpty() }
                    } catch (_: Exception) {
                        null
                    } ?: errBody.ifBlank { "Proxy conversion failed (HTTP $code)" }

                    if (ConversionQuotaHelper.isQuotaExhaustedHttpError(code, errBody)) {
                        throw QuotaExhaustedException(code, parsedMsg)
                    }
                    Result.failure(IllegalStateException(parsedMsg))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

