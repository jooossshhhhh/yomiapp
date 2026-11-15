package com.yomi.reader

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.yomi.reader.data.worker.StorageScannerWorker
import com.yomi.reader.di.AppContainer

class YomiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // The isolated :converter process only runs LibreOfficeKit; skip main-process DB/WorkManager init.
        if (currentProcessName().endsWith(":converter")) {
            return
        }

        container = AppContainer(this)

        // Schedule periodic background storage indexing only when auto-scan is enabled
        if (container.preferencesManager.autoScanOnLaunch.value) {
            StorageScannerWorker.enqueuePeriodicScan(this)
        } else {
            StorageScannerWorker.cancelPeriodicScan(this)
        }
    }

    private fun currentProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName()
        }
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
    }
}
