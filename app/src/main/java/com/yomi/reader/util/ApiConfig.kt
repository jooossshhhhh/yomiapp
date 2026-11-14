package com.yomi.reader.util

import com.yomi.reader.BuildConfig

object ApiConfig {
    val CONVERSION_PROXY_URL: String
        get() = BuildConfig.CONVERSION_PROXY_URL

    val CONVERSION_PROXY_TOKEN: String
        get() = BuildConfig.CONVERSION_PROXY_TOKEN

    val CLOUDCONVERT_API_KEY: String
        get() = BuildConfig.CLOUDCONVERT_API_KEY

    val CLOUDCONVERT_BACKUP_API_KEY: String
        get() = BuildConfig.CLOUDCONVERT_BACKUP_API_KEY

    val ILOVEPDF_PUBLIC_KEY: String
        get() = BuildConfig.ILOVEPDF_PUBLIC_KEY

    val ILOVEPDF_BACKUP_PUBLIC_KEY: String
        get() = BuildConfig.ILOVEPDF_BACKUP_PUBLIC_KEY

    val ILOVEPDF_SECRET_KEY: String
        get() = BuildConfig.ILOVEPDF_SECRET_KEY

    /**
     * Resolves an ordered, deduplicated list of configured API keys (supports comma-separated keys
     * in BuildConfig as well as an optional user-supplied backup key).
     */
    fun collectOrderedKeys(vararg rawSources: String?): List<String> {
        val result = linkedSetOf<String>()
        for (source in rawSources) {
            if (source.isNullOrBlank()) continue
            source.split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { result.add(it) }
        }
        return result.toList()
    }
}
