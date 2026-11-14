package com.yomi.reader.util.conversion

import com.yomi.reader.util.ApiConfig
import com.yomi.reader.util.ILovePdfConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ConversionQuotaHelperTest {

    @Test
    fun isQuotaExhaustedHttpError_detectsPaymentRequiredAndRateLimits() {
        assertTrue(ConversionQuotaHelper.isQuotaExhaustedHttpError(402, ""))
        assertTrue(ConversionQuotaHelper.isQuotaExhaustedHttpError(429, ""))
        assertTrue(
            ConversionQuotaHelper.isQuotaExhaustedHttpError(
                401,
                """{"error":{"message":"Remaining credits exhausted for this project"}}"""
            )
        )
        assertTrue(
            ConversionQuotaHelper.isQuotaExhaustedHttpError(
                403,
                """{"code":"CREDITS_EXCEEDED","message":"Account quota reached"}"""
            )
        )
        assertTrue(
            ConversionQuotaHelper.isQuotaExhaustedHttpError(
                400,
                "Insufficient credit balance"
            )
        )
    }

    @Test
    fun isQuotaExhaustedHttpError_ignoresTransientOrNonQuotaErrors() {
        assertFalse(ConversionQuotaHelper.isQuotaExhaustedHttpError(500, "credits exhausted"))
        assertFalse(ConversionQuotaHelper.isQuotaExhaustedHttpError(404, "task not found"))
        assertFalse(ConversionQuotaHelper.isQuotaExhaustedHttpError(401, "invalid token signature"))
    }

    @Test
    fun cooldownWindow_tracksAvailabilityAcrossTime() {
        val now = 1_700_000_000_000L
        val until = ConversionQuotaHelper.computeCooldownUntil(now)

        assertTrue(ConversionQuotaHelper.isSlotAvailable(0L, now))
        assertFalse(ConversionQuotaHelper.isSlotAvailable(until, now + 1_000L))
        assertTrue(ConversionQuotaHelper.isSlotAvailable(until, until))
        assertTrue(ConversionQuotaHelper.isSlotAvailable(until, until + 5_000L))
    }

    @Test
    fun computeCooldownUntil_enforcesMinimumOneMinuteFloor() {
        val now = 1_000_000L
        assertEquals(now + 60_000L, ConversionQuotaHelper.computeCooldownUntil(now, cooldownMs = 500L))
    }

    @Test
    fun slotKey_isDeterministicAndScopedByProvider() {
        val k1 = ConversionQuotaHelper.slotKey("ilove", "project_public_abc123")
        val k2 = ConversionQuotaHelper.slotKey("ilove", "  project_public_abc123 ")
        val k3 = ConversionQuotaHelper.slotKey("cc", "project_public_abc123")

        assertEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    @Test
    fun maskKey_hidesSensitiveMiddleCharacters() {
        assertEquals("", ConversionQuotaHelper.maskKey("   "))
        assertEquals("••••", ConversionQuotaHelper.maskKey("short"))
        assertEquals("projec…6789", ConversionQuotaHelper.maskKey("project_public_123456789"))
    }

    @Test
    fun collectOrderedKeys_splitsDeduplicatesAndPreservesOrder() {
        val keys = ApiConfig.collectOrderedKeys(
            "key_primary, key_backup_1;key_backup_2\n",
            null,
            "   ",
            "key_backup_1, key_user_custom"
        )
        assertEquals(
            listOf("key_primary", "key_backup_1", "key_backup_2", "key_user_custom"),
            keys
        )
    }

    @Test
    fun dailyQuota_enforcesFivePerDayLimit() {
        assertEquals(5, ConversionQuotaHelper.DAILY_CLOUD_CONVERSION_LIMIT)
        assertEquals(5, ConversionQuotaHelper.remainingDailyQuota(0))
        assertTrue(ConversionQuotaHelper.hasDailyQuotaRemaining(0))
        assertEquals(1, ConversionQuotaHelper.remainingDailyQuota(4))
        assertTrue(ConversionQuotaHelper.hasDailyQuotaRemaining(4))
        assertEquals(0, ConversionQuotaHelper.remainingDailyQuota(5))
        assertFalse(ConversionQuotaHelper.hasDailyQuotaRemaining(5))
        assertEquals(0, ConversionQuotaHelper.remainingDailyQuota(9))
    }

    @Test
    fun resolveUserScope_normalizesEmailAndFallsBackToGuest() {
        assertEquals("guest", ConversionQuotaHelper.resolveUserScope(null))
        assertEquals("guest", ConversionQuotaHelper.resolveUserScope("   "))
        assertEquals(
            ConversionQuotaHelper.resolveUserScope("User@Example.COM "),
            ConversionQuotaHelper.resolveUserScope("user@example.com")
        )
        assertNotEquals(
            ConversionQuotaHelper.resolveUserScope("user1@example.com"),
            ConversionQuotaHelper.resolveUserScope("user2@example.com")
        )
    }

    @Test
    fun currentDayKey_formatsUtcCalendarDateDeterministically() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2026-11-14T00:00:00Z = 1794614400000L
        assertEquals("2026-11-14", ConversionQuotaHelper.currentDayKey(1794614400000L, utc))
        assertEquals("2026-11-13", ConversionQuotaHelper.currentDayKey(1794614399000L, utc))
    }

    @Test
    fun buildContentCacheKey_distinguishesModifiedContentUnderSameUri() {
        val uri = "content://com.android.providers.downloads.documents/document/42"
        val keyA = ILovePdfConverter.buildContentCacheKey(
            prefix = "converted",
            fileName = "Thesis Draft.docx",
            uriString = uri,
            fileBytes = "Version 1 content".encodeToByteArray()
        )
        val keyB = ILovePdfConverter.buildContentCacheKey(
            prefix = "converted",
            fileName = "Thesis Draft.docx",
            uriString = uri,
            fileBytes = "Version 2 updated content".encodeToByteArray()
        )
        val keyA2 = ILovePdfConverter.buildContentCacheKey(
            prefix = "converted",
            fileName = "Thesis Draft.docx",
            uriString = uri,
            fileBytes = "Version 1 content".encodeToByteArray()
        )
        assertEquals(keyA, keyA2)
        assertTrue(keyA != keyB)
        assertTrue(keyA.endsWith(".pdf"))
    }

    @Test
    fun proxyUrlHelpers_validateAndNormalizeConvertEndpoint() {
        assertFalse(ConversionQuotaHelper.isValidProxyUrl(null))
        assertFalse(ConversionQuotaHelper.isValidProxyUrl(""))
        assertFalse(ConversionQuotaHelper.isValidProxyUrl("not-a-url"))
        assertTrue(ConversionQuotaHelper.isValidProxyUrl("https://yomi-conversion-proxy.example.workers.dev"))
        assertEquals(
            "https://yomi-conversion-proxy.example.workers.dev/convert",
            ConversionQuotaHelper.normalizeProxyConvertUrl("https://yomi-conversion-proxy.example.workers.dev/")
        )
        assertEquals(
            "https://yomi-conversion-proxy.example.workers.dev/convert",
            ConversionQuotaHelper.normalizeProxyConvertUrl("https://yomi-conversion-proxy.example.workers.dev/convert")
        )
    }
}

