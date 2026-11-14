package com.yomi.reader.domain.model

enum class DocumentCategory(val label: String) {
    ALL("All"),
    PDF("PDFs"),
    WORD("Word"),
    EXCEL("Excel"),
    PRESENTATION("Slides"),
    GOOGLE_DRIVE("Google Drive")
}
