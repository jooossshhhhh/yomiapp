package com.yomi.reader.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import java.io.FileOutputStream

object PdfPrintHelper {

    fun printOrExportPdf(context: Context, uri: Uri, documentTitle: String) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
                return
            }

            val jobName = documentTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "Yomi_Document" }
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("Invalid destination descriptor")
                        return
                    }

                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        } ?: run {
                            callback?.onWriteFailed("Cannot open document input stream")
                            return
                        }

                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                        } else {
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        }
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage ?: "Failed to write PDF for printing")
                    }
                }
            }

            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(context, "Error printing document: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
