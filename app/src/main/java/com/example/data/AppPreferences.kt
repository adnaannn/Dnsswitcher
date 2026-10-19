package com.example.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var engineMode: String
        get() = prefs.getString(KEY_ENGINE_MODE, ENGINE_LOCAL_DNS) ?: ENGINE_LOCAL_DNS
        set(value) = prefs.edit().putString(KEY_ENGINE_MODE, value).apply()

    var cycleMode: String
        get() = prefs.getString(KEY_CYCLE_MODE, CYCLE_FIVE_STATE) ?: CYCLE_FIVE_STATE
        set(value) = prefs.edit().putString(KEY_CYCLE_MODE, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "DARK") ?: "DARK"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var lastSwitchProviderName: String
        get() = prefs.getString(KEY_LAST_PROVIDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_PROVIDER, value).apply()

    var lastSwitchStatus: String
        get() = prefs.getString(KEY_LAST_STATUS, "Ready") ?: "Ready"
        set(value) = prefs.edit().putString(KEY_LAST_STATUS, value).apply()

    var lastSwitchTimestamp: Long
        get() = prefs.getLong(KEY_LAST_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_TIMESTAMP, value).apply()

    var lastSwitchError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    var activeLocalProviderId: String
        get() = prefs.getString(KEY_ACTIVE_LOCAL_PROVIDER_ID, DnsProvider.ADGUARD.id) ?: DnsProvider.ADGUARD.id
        set(value) = prefs.edit().putString(KEY_ACTIVE_LOCAL_PROVIDER_ID, value).apply()

    fun recordSwitchSuccess(provider: DnsProvider) {
        prefs.edit()
            .putString(KEY_LAST_PROVIDER, provider.displayName)
            .putString(KEY_LAST_STATUS, "Success")
            .putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun recordSwitchFailure(errorMessage: String) {
        prefs.edit()
            .putString(KEY_LAST_STATUS, "Failed")
            .putLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_LAST_ERROR, errorMessage)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dns_switcher_prefs"
        const val KEY_ENGINE_MODE = "key_engine_mode"
        const val KEY_CYCLE_MODE = "key_cycle_mode"
        const val KEY_THEME_MODE = "key_theme_mode"
        const val KEY_LAST_PROVIDER = "key_last_provider"
        const val KEY_LAST_STATUS = "key_last_status"
        const val KEY_LAST_TIMESTAMP = "key_last_timestamp"
        const val KEY_LAST_ERROR = "key_last_error"
        const val KEY_ACTIVE_LOCAL_PROVIDER_ID = "key_active_local_provider_id"

        // Engine modes:
        // ENGINE_LOCAL_DNS: Zero command execution, uses native Android VPN permission (1-click on screen)
        // ENGINE_SYSTEM_PRIVATE_DNS: Modifies Android Settings.Global Private DNS (requires ADB)
        const val ENGINE_LOCAL_DNS = "local_dns"
        const val ENGINE_SYSTEM_PRIVATE_DNS = "system_private_dns"

        const val CYCLE_FIVE_STATE = "five_state" // Off -> AdGuard -> Cloudflare -> Quad9 -> Automatic
        const val CYCLE_TWO_STATE = "two_state"   // AdGuard <-> Cloudflare
        const val CYCLE_THREE_STATE = "three_state" // Auto -> AdGuard -> Cloudflare

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context).also { INSTANCE = it }
            }
        }
    }
}
