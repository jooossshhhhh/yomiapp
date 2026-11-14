package com.yomi.reader

import android.app.Application
import com.yomi.reader.data.worker.StorageScannerWorker
import com.yomi.reader.di.AppContainer

class YomiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Schedule periodic background storage indexing only when auto-scan is enabled
        if (container.preferencesManager.autoScanOnLaunch.value) {
            StorageScannerWorker.enqueuePeriodicScan(this)
        } else {
            StorageScannerWorker.cancelPeriodicScan(this)
        }
    }
}
