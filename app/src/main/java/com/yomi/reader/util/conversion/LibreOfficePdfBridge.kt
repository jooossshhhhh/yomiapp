package com.yomi.reader.util.conversion

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Serialized main-process client for [LibreOfficeConversionService].
 * Ensures only one document is converted at a time and turns `:converter` process crashes into
 * clean Kotlin [Result.failure] values without crashing Yomi's UI process.
 */
object LibreOfficePdfBridge {

    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val CONVERSION_TIMEOUT_MS = 180_000L

    private val conversionMutex = Mutex()

    suspend fun convertToPdf(
        context: Context,
        inputFile: File,
        outputFile: File,
        filterOptions: String = ""
    ): Result<File> = convert(
        context = context,
        inputFile = inputFile,
        outputFile = outputFile,
        format = "pdf",
        filterOptions = filterOptions
    )

    suspend fun convert(
        context: Context,
        inputFile: File,
        outputFile: File,
        format: String,
        filterOptions: String = ""
    ): Result<File> = conversionMutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val serviceConnected = CompletableDeferred<Messenger>()
            val conversionResult = CompletableDeferred<Result<File>>()
            var deathRecipient: IBinder.DeathRecipient? = null
            var remoteBinder: IBinder? = null

            val replyHandler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what != LibreOfficeConversionService.WHAT_RESULT) {
                        return
                    }
                    val success = msg.data.getBoolean(LibreOfficeConversionService.KEY_SUCCESS, false)
                    val errMsg = msg.data.getString(LibreOfficeConversionService.KEY_ERROR_MESSAGE)
                    if (success && outputFile.isFile && outputFile.length() > 0L) {
                        conversionResult.complete(Result.success(outputFile))
                    } else {
                        conversionResult.complete(
                            Result.failure(IllegalStateException(errMsg ?: "Offline conversion failed"))
                        )
                    }
                }
            }
            val replyMessenger = Messenger(replyHandler)

            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    if (service == null) {
                        serviceConnected.completeExceptionally(
                            IllegalStateException("Converter service returned null binder")
                        )
                        return
                    }
                    val recipient = IBinder.DeathRecipient {
                        conversionResult.complete(
                            Result.failure(
                                IllegalStateException(
                                    "Offline conversion engine stopped unexpectedly while processing this file"
                                )
                            )
                        )
                    }
                    runCatching { service.linkToDeath(recipient, 0) }
                    deathRecipient = recipient
                    remoteBinder = service
                    serviceConnected.complete(Messenger(service))
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    conversionResult.complete(
                        Result.failure(IllegalStateException("Offline conversion service disconnected"))
                    )
                }

                override fun onBindingDied(name: ComponentName?) {
                    conversionResult.complete(
                        Result.failure(IllegalStateException("Offline conversion service died"))
                    )
                }
            }

            try {
                val intent = Intent(appContext, LibreOfficeConversionService::class.java)
                val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    return@withContext Result.failure(
                        IllegalStateException("Unable to bind offline conversion service")
                    )
                }

                val remoteMessenger = withTimeout(CONNECT_TIMEOUT_MS) {
                    serviceConnected.await()
                }

                val msg = Message.obtain(null, LibreOfficeConversionService.WHAT_CONVERT).apply {
                    replyTo = replyMessenger
                    data = Bundle().apply {
                        putString(LibreOfficeConversionService.KEY_INPUT_PATH, inputFile.absolutePath)
                        putString(LibreOfficeConversionService.KEY_OUTPUT_PATH, outputFile.absolutePath)
                        putString(LibreOfficeConversionService.KEY_FORMAT, format)
                        putString(LibreOfficeConversionService.KEY_FILTER_OPTIONS, filterOptions)
                    }
                }
                remoteMessenger.send(msg)

                withTimeout(CONVERSION_TIMEOUT_MS) {
                    conversionResult.await()
                }
            } catch (t: Throwable) {
                Result.failure(IllegalStateException(t.message ?: "Offline conversion timed out or failed", t))
            } finally {
                val binder = remoteBinder
                val recipient = deathRecipient
                if (binder != null && recipient != null) {
                    runCatching { binder.unlinkToDeath(recipient, 0) }
                }
                runCatching { appContext.unbindService(connection) }
            }
        }
    }
}

