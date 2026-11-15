package com.yomi.reader.util.conversion

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs LibreOfficeKit inside an isolated `:converter` process so native C++ memory spikes or
 * crashes on malformed Office files cannot bring down Yomi's main UI process.
 */
class LibreOfficeConversionService : Service() {

    companion object {
        private const val TAG = "LibreOfficeConvSvc"
        const val WHAT_CONVERT = 1
        const val WHAT_RESULT = 2

        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_PATH = "output_path"
        const val KEY_FORMAT = "format"
        const val KEY_FILTER_OPTIONS = "filter_options"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "yomi-lok-worker")
    }

    @Volatile
    private var office: Office? = null

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != WHAT_CONVERT) {
                super.handleMessage(msg)
                return
            }

            val replyTo = msg.replyTo ?: return
            val data = Bundle(msg.data)
            workerExecutor.execute {
                val (success, error) = performConversion(data)
                val reply = Message.obtain(null, WHAT_RESULT).apply {
                    this.data = Bundle().apply {
                        putBoolean(KEY_SUCCESS, success)
                        if (error != null) {
                            putString(KEY_ERROR_MESSAGE, error)
                        }
                    }
                }
                runCatching { replyTo.send(reply) }
                    .onFailure { Log.w(TAG, "Failed to deliver conversion reply", it) }
            }
        }
    }

    private val messenger = Messenger(incomingHandler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        workerExecutor.execute {
            runCatching { office?.destroy() }
            office = null
        }
        workerExecutor.shutdown()
        super.onDestroy()
    }

    private fun ensureOfficeInitialized(): Office {
        office?.let { return it }

        val extracted = LibreOfficeAssetExtractor.ensureExtracted(applicationContext)
        val ok = LibreOfficeKit.init(applicationContext, extracted.fontConfigFile.absolutePath)
        if (!ok) {
            throw IllegalStateException("Failed to initialize LibreOfficeKit native engine")
        }

        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            ?: throw IllegalStateException("LibreOfficeKit returned a null native handle")

        return Office(handle).also {
            office = it
        }
    }

    private fun performConversion(data: Bundle): Pair<Boolean, String?> {
        val inputPath = data.getString(KEY_INPUT_PATH).orEmpty()
        val outputPath = data.getString(KEY_OUTPUT_PATH).orEmpty()
        val format = data.getString(KEY_FORMAT).orEmpty().ifBlank { "pdf" }
        val filterOptions = data.getString(KEY_FILTER_OPTIONS).orEmpty()

        if (inputPath.isBlank() || outputPath.isBlank()) {
            return false to "Invalid conversion file paths"
        }

        val inputFile = File(inputPath)
        if (!inputFile.isFile || inputFile.length() <= 0L) {
            return false to "Input document is missing or empty"
        }

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        return try {
            val loOffice = ensureOfficeInitialized()
            val inputUrl = Uri.fromFile(inputFile).toString()
            val outputUrl = Uri.fromFile(outputFile).toString()

            val document = loOffice.documentLoad(inputUrl)
            if (document == null) {
                val nativeErr = runCatching { loOffice.error }.getOrNull()?.takeIf { it.isNotBlank() }
                return false to (nativeErr ?: "LibreOfficeKit could not open ${inputFile.name}")
            }

            try {
                val resultCode = document.saveAs(outputUrl, format, filterOptions)
                if (resultCode == 0 || !outputFile.isFile || outputFile.length() <= 0L) {
                    val nativeErr = runCatching { loOffice.error }.getOrNull()?.takeIf { it.isNotBlank() }
                    outputFile.delete()
                    return false to (nativeErr ?: "LibreOfficeKit failed to export $format")
                }
                true to null
            } finally {
                runCatching { document.destroy() }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "LibreOfficeKit conversion error", t)
            outputFile.delete()
            false to (t.message ?: "Offline conversion failed")
        }
    }
}

