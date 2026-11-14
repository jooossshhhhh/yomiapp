package com.yomi.reader.util.conversion

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists per-key quota cooldown timestamps, optional user-configured backup API keys, and
 * last active conversion engine name in Yomi's shared preferences store.
 */
object ConversionQuotaStore {

    private const val PREFS_NAME = "yomi_app_prefs"
    private const val AUTH_PREFS_NAME = "yomi_auth_prefs"
    private const val KEY_BACKUP_ILOVEPDF = "backup_ilovepdf_public_key"
    private const val KEY_BACKUP_CLOUDCONVERT = "backup_cloudconvert_api_key"
    private const val KEY_LAST_ENGINE = "last_conversion_engine"
    private const val KEY_LAST_ENGINE_AT = "last_conversion_engine_at"
    private const val KEY_CLOUD_HIGH_FIDELITY_MODE = "cloud_high_fidelity_mode"
    private const val PREFIX_DAILY_DAY = "daily_cloud_day_"
    private const val PREFIX_DAILY_COUNT = "daily_cloud_count_"
    private const val PREFIX_COOLDOWN_UNTIL = "conv_cooldown_until_"
    private const val PREFIX_COOLDOWN_REASON = "conv_cooldown_reason_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSlotAvailable(context: Context, providerPrefix: String, apiKey: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val slot = ConversionQuotaHelper.slotKey(providerPrefix, apiKey)
        val until = prefs(context).getLong("$PREFIX_COOLDOWN_UNTIL$slot", 0L)
        return ConversionQuotaHelper.isSlotAvailable(until, nowMs)
    }

    fun getSlotCooldownUntil(context: Context, providerPrefix: String, apiKey: String): Long {
        val slot = ConversionQuotaHelper.slotKey(providerPrefix, apiKey)
        return prefs(context).getLong("$PREFIX_COOLDOWN_UNTIL$slot", 0L)
    }

    fun getSlotCooldownReason(context: Context, providerPrefix: String, apiKey: String): String? {
        val slot = ConversionQuotaHelper.slotKey(providerPrefix, apiKey)
        return prefs(context).getString("$PREFIX_COOLDOWN_REASON$slot", null)
    }

    fun markSlotQuotaExhausted(
        context: Context,
        providerPrefix: String,
        apiKey: String,
        reason: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val slot = ConversionQuotaHelper.slotKey(providerPrefix, apiKey)
        val until = ConversionQuotaHelper.computeCooldownUntil(nowMs)
        prefs(context).edit()
            .putLong("$PREFIX_COOLDOWN_UNTIL$slot", until)
            .putString("$PREFIX_COOLDOWN_REASON$slot", reason)
            .apply()
    }

    fun clearAllCooldowns(context: Context) {
        val sp = prefs(context)
        val editor = sp.edit()
        sp.all.keys
            .filter { it.startsWith(PREFIX_COOLDOWN_UNTIL) || it.startsWith(PREFIX_COOLDOWN_REASON) }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun getBackupIlovePdfKey(context: Context): String =
        prefs(context).getString(KEY_BACKUP_ILOVEPDF, "") ?: ""

    fun setBackupIlovePdfKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_BACKUP_ILOVEPDF, key.trim()).apply()
        clearAllCooldowns(context)
    }

    fun getBackupCloudConvertKey(context: Context): String =
        prefs(context).getString(KEY_BACKUP_CLOUDCONVERT, "") ?: ""

    fun setBackupCloudConvertKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_BACKUP_CLOUDCONVERT, key.trim()).apply()
        clearAllCooldowns(context)
    }

    fun recordLastConversionEngine(context: Context, engineLabel: String) {
        prefs(context).edit()
            .putString(KEY_LAST_ENGINE, engineLabel)
            .putLong(KEY_LAST_ENGINE_AT, System.currentTimeMillis())
            .apply()
    }

    fun getLastConversionEngine(context: Context): String? =
        prefs(context).getString(KEY_LAST_ENGINE, null)

    /**
     * Option 2 default: false = Offline-First (uses 0 credits; cloud APIs act as backup on failure).
     * When true = tries Cloud APIs first up to the per-user daily allowance, then falls back to Offline.
     */
    fun isCloudHighFidelityModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLOUD_HIGH_FIDELITY_MODE, false)

    fun setCloudHighFidelityModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLOUD_HIGH_FIDELITY_MODE, enabled).apply()
    }

    fun resolveActiveUserEmail(context: Context): String? {
        val authPrefs = context.applicationContext.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        if (!authPrefs.getBoolean("is_signed_in", false)) return null
        return authPrefs.getString("user_email", null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getDailyCloudUsageCount(
        context: Context,
        userIdentifier: String? = resolveActiveUserEmail(context),
        dayKey: String = ConversionQuotaHelper.currentDayKey()
    ): Int {
        val scope = ConversionQuotaHelper.resolveUserScope(userIdentifier)
        val sp = prefs(context)
        val storedDay = sp.getString("$PREFIX_DAILY_DAY$scope", null)
        return if (storedDay == dayKey) sp.getInt("$PREFIX_DAILY_COUNT$scope", 0).coerceAtLeast(0) else 0
    }

    fun getRemainingDailyCloudConversions(
        context: Context,
        userIdentifier: String? = resolveActiveUserEmail(context),
        dayKey: String = ConversionQuotaHelper.currentDayKey()
    ): Int = ConversionQuotaHelper.remainingDailyQuota(
        usedToday = getDailyCloudUsageCount(context, userIdentifier, dayKey)
    )

    fun hasDailyCloudQuotaRemaining(
        context: Context,
        userIdentifier: String? = resolveActiveUserEmail(context),
        dayKey: String = ConversionQuotaHelper.currentDayKey()
    ): Boolean = ConversionQuotaHelper.hasDailyQuotaRemaining(
        usedToday = getDailyCloudUsageCount(context, userIdentifier, dayKey)
    )

    fun recordSuccessfulCloudConversion(
        context: Context,
        userIdentifier: String? = resolveActiveUserEmail(context),
        dayKey: String = ConversionQuotaHelper.currentDayKey()
    ): Int {
        val scope = ConversionQuotaHelper.resolveUserScope(userIdentifier)
        val current = getDailyCloudUsageCount(context, userIdentifier, dayKey)
        val updated = current + 1
        prefs(context).edit()
            .putString("$PREFIX_DAILY_DAY$scope", dayKey)
            .putInt("$PREFIX_DAILY_COUNT$scope", updated)
            .apply()
        return updated
    }
}

