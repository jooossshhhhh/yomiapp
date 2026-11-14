package com.yomi.reader.domain.model

enum class DocumentType(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    PDF("pdf", "application/pdf", "PDF Document"),
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Spreadsheet"),
    POWERPOINT("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint Presentation"),
    UNKNOWN("", "*/*", "Document");

    companion object {
        fun fromExtension(ext: String?): DocumentType {
            return when (ext?.lowercase()?.trim()) {
                "pdf" -> PDF
                "docx", "doc" -> WORD
                "xlsx", "xls" -> EXCEL
                "pptx", "ppt" -> POWERPOINT
                else -> UNKNOWN
            }
        }

        fun fromMimeType(mime: String?): DocumentType {
            return when {
                mime == null -> UNKNOWN
                mime.contains("pdf", ignoreCase = true) -> PDF
                mime.contains("word", ignoreCase = true) || mime.contains("msword", ignoreCase = true) -> WORD
                mime.contains("spreadsheet", ignoreCase = true) || mime.contains("excel", ignoreCase = true) -> EXCEL
                mime.contains("presentation", ignoreCase = true) || mime.contains("powerpoint", ignoreCase = true) -> POWERPOINT
                else -> UNKNOWN
            }
        }
    }
}
