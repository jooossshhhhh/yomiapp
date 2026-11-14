package com.yomi.reader.di

import android.content.Context
import com.yomi.reader.data.local.PdfAnnotationsRepository
import com.yomi.reader.data.local.YomiDatabase
import com.yomi.reader.data.repository.DocumentRepositoryImpl
import com.yomi.reader.domain.repository.DocumentRepository

class AppContainer(context: Context) {
    val database: YomiDatabase by lazy {
        YomiDatabase.getInstance(context)
    }

    val preferencesManager: com.yomi.reader.data.local.PreferencesManager by lazy {
        com.yomi.reader.data.local.PreferencesManager(context.applicationContext)
    }

    val googleAuthManager: com.yomi.reader.auth.GoogleAuthManager by lazy {
        com.yomi.reader.auth.GoogleAuthManager(context.applicationContext)
    }

    val pdfAnnotationsRepository: PdfAnnotationsRepository by lazy {
        PdfAnnotationsRepository(context.applicationContext)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepositoryImpl(
            context = context.applicationContext,
            dao = database.documentDao(),
            preferencesManager = preferencesManager
        )
    }
}
