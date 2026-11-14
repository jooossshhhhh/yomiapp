package com.yomi.reader.util.conversion

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Thrown when a cloud conversion API rejects a request due to exhausted credits/quota or rate limits.
 */
class QuotaExhaustedException(
    val httpCode: Int,
    override val message: String
) : IllegalStateException(message)

/**
 * Pure-Kotlin quota/failover detection & cooldown helper for multi-key / multi-provider Office-to-PDF conversion.
 */
object ConversionQuotaHelper {

    const val DEFAULT_QUOTA_COOLDOWN_MS: Long = 12L * 60L * 60L * 1000L // 12 hours
    const val DAILY_CLOUD_CONVERSION_LIMIT: Int = 5

    private val quotaKeywords = listOf(
        "credit",
        "credits",
        "quota",
        "limit",
        "exceeded",
        "exhausted",
        "insufficient",
        "remaining",
        "balance",
        "credits_exhausted",
        "too many requests"
    )

    fun isQuotaExhaustedHttpError(httpCode: Int, errorBody: String): Boolean {
        if (httpCode == 402 || httpCode == 429) return true
        if (httpCode in setOf(400, 401, 403)) {
            val lower = errorBody.lowercase()
            return quotaKeywords.any { lower.contains(it) }
        }
        return false
    }

    fun computeCooldownUntil(
        nowMs: Long,
        cooldownMs: Long = DEFAULT_QUOTA_COOLDOWN_MS
    ): Long = nowMs + cooldownMs.coerceAtLeast(60_000L)

    fun isSlotAvailable(cooldownUntilMs: Long, nowMs: Long): Boolean {
        return cooldownUntilMs <= 0L || nowMs >= cooldownUntilMs
    }

    fun slotKey(providerPrefix: String, apiKey: String): String {
        val hash = apiKey.trim().hashCode().toUInt().toString(16)
        return "${providerPrefix}_$hash"
    }

    fun maskKey(apiKey: String): String {
        val clean = apiKey.trim()
        if (clean.length <= 8) return if (clean.isEmpty()) "" else "••••"
        return "${clean.take(6)}…${clean.takeLast(4)}"
    }

    fun currentDayKey(
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return fmt.format(Date(nowMs))
    }

    fun resolveUserScope(userIdentifier: String?): String {
        val normalized = userIdentifier?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return "guest"
        return normalized.hashCode().toUInt().toString(16)
    }

    fun remainingDailyQuota(
        usedToday: Int,
        limit: Int = DAILY_CLOUD_CONVERSION_LIMIT
    ): Int = (limit.coerceAtLeast(0) - usedToday.coerceAtLeast(0)).coerceAtLeast(0)

    fun hasDailyQuotaRemaining(
        usedToday: Int,
        limit: Int = DAILY_CLOUD_CONVERSION_LIMIT
    ): Boolean = remainingDailyQuota(usedToday, limit) > 0

    fun isValidProxyUrl(rawUrl: String?): Boolean {
        val clean = rawUrl?.trim().orEmpty()
        return clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)
    }

    fun normalizeProxyConvertUrl(rawUrl: String): String {
        val clean = rawUrl.trim().trimEnd('/')
        return if (clean.endsWith("/convert", ignoreCase = true)) {
            clean
        } else {
            "$clean/convert"
        }
    }
}

