package com.example.manager

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to handle reading, writing, and verifying Android Private DNS settings
 * via the system Settings.Global API.
 *
 * Android Framework Reference (android.provider.Settings.Global):
 * - PRIVATE_DNS_MODE: "private_dns_mode"
 * - PRIVATE_DNS_SPECIFIER: "private_dns_specifier"
 * - PRIVATE_DNS_DEFAULT_MODE: "private_dns_default_mode"
 *
 * Supported Modes:
 * - MODE_OFF ("off"): Private DNS is turned off.
 * - MODE_OPPORTUNISTIC ("opportunistic"): Automatic opportunistic DNS-over-TLS.
 * - MODE_PROVIDER_HOSTNAME ("hostname"): Strict mode requiring a validated TLS provider hostname.
 */
object PrivateDnsHelper {

    private const val TAG = "PrivateDnsHelper"

    // Settings.Global private DNS keys
    const val PRIVATE_DNS_MODE = "private_dns_mode"
    const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    const val PRIVATE_DNS_DEFAULT_MODE = "private_dns_default_mode"

    // Standard mode string values
    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_PROVIDER_HOSTNAME = "hostname"

    // Known provider hostnames
    const val HOSTNAME_ADGUARD = "dns.adguard.com"
    const val HOSTNAME_CLOUDFLARE = "one.one.one.one"
    const val HOSTNAME_QUAD9 = "dns.quad9.net"

    /**
     * Data snapshot of the system's Private DNS configuration.
     */
    data class PrivateDnsConfig(
        val mode: String?,
        val specifier: String?
    ) {
        val isOff: Boolean
            get() = mode.equals(MODE_OFF, ignoreCase = true)

        val isOpportunistic: Boolean
            get() = mode.equals(MODE_OPPORTUNISTIC, ignoreCase = true)

        val isHostnameMode: Boolean
            get() = mode.equals(MODE_PROVIDER_HOSTNAME, ignoreCase = true)

        val providerName: String
            get() {
                val cleanSpecifier = specifier?.trim()?.lowercase() ?: ""
                return when {
                    isOff -> "DNS Off"
                    isHostnameMode -> when {
                        cleanSpecifier.contains("adguard") -> "AdGuard DNS"
                        cleanSpecifier == "one.one.one.one" || cleanSpecifier.contains("cloudflare") -> "Cloudflare DNS"
                        cleanSpecifier.contains("quad9") -> "Quad9 DNS"
                        cleanSpecifier.isNotEmpty() -> cleanSpecifier
                        else -> "Automatic"
                    }
                    else -> "Automatic"
                }
            }
    }

    /**
     * Checks if the app has been granted the required WRITE_SECURE_SETTINGS permission.
     */
    fun canWriteSecureSettings(context: Context): Boolean {
        return try {
            val status = context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
            status == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WRITE_SECURE_SETTINGS permission", e)
            false
        }
    }

    /**
     * Generates the exact ADB command required to grant WRITE_SECURE_SETTINGS for this package.
     */
    fun getAdbGrantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Reads current Private DNS mode from Settings.Global.
     * Returns "off", "opportunistic", "hostname", or null if not configured.
     */
    fun getMode(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading $PRIVATE_DNS_MODE", e)
            null
        }
    }

    /**
     * Reads current Private DNS hostname specifier from Settings.Global.
     */
    fun getSpecifier(context: Context): String? {
        return try {
            Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading $PRIVATE_DNS_SPECIFIER", e)
            null
        }
    }

    /**
     * Reads both mode and specifier in a single call.
     */
    fun getCurrentConfig(context: Context): PrivateDnsConfig {
        val mode = getMode(context)
        val specifier = getSpecifier(context)
        return PrivateDnsConfig(mode, specifier)
    }

    /**
     * Applies a new Private DNS configuration to Settings.Global and immediately verifies
     * that Android accepted and committed the change.
     *
     * @param context Application or Activity context
     * @param mode Desired mode: MODE_OFF, MODE_OPPORTUNISTIC, or MODE_PROVIDER_HOSTNAME
     * @param hostname TLS provider hostname (required when mode is MODE_PROVIDER_HOSTNAME)
     * @return Result containing verified PrivateDnsConfig on success, or an Exception on failure
     */
    @Synchronized
    fun setPrivateDns(
        context: Context,
        mode: String,
        hostname: String = ""
    ): Result<PrivateDnsConfig> {
        val resolver = context.contentResolver

        if (!canWriteSecureSettings(context)) {
            val adbCmd = getAdbGrantCommand(context)
            return Result.failure(
                SecurityException("WRITE_SECURE_SETTINGS permission required. Run: $adbCmd")
            )
        }

        try {
            val targetMode = mode.trim().lowercase()
            val targetSpecifier = hostname.trim()

            // 1. Write the mode (off, opportunistic, hostname)
            val modeSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_MODE, targetMode)

            // 2. Write the specifier hostname
            val specifierSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_SPECIFIER, targetSpecifier)

            if (!modeSuccess || !specifierSuccess) {
                return Result.failure(IllegalStateException("Settings.Global returned false when persisting values"))
            }

            // 3. Immediately read back and verify from system
            val verifiedConfig = getCurrentConfig(context)
            val isModeOk = verifiedConfig.mode.equals(targetMode, ignoreCase = true)
            val isSpecifierOk = when (targetMode) {
                MODE_PROVIDER_HOSTNAME -> verifiedConfig.specifier.equals(targetSpecifier, ignoreCase = true)
                else -> true
            }

            return if (isModeOk && isSpecifierOk) {
                Log.i(TAG, "Private DNS successfully updated to: mode=$targetMode, specifier=$targetSpecifier")
                Result.success(verifiedConfig)
            } else {
                val errorMsg = "System verification failed. Read back: mode=${verifiedConfig.mode}, specifier=${verifiedConfig.specifier}"
                Log.w(TAG, errorMsg)
                Result.failure(IllegalStateException(errorMsg))
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException changing Private DNS", e)
            return Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error changing Private DNS", e)
            return Result.failure(e)
        }
    }

    /**
     * Convenience method to disable Private DNS (Mode = "off").
     */
    fun setOff(context: Context): Result<PrivateDnsConfig> {
        return setPrivateDns(context, MODE_OFF, "")
    }

    /**
     * Convenience method to set Private DNS to Automatic (Mode = "opportunistic").
     */
    fun setAutomatic(context: Context): Result<PrivateDnsConfig> {
        return setPrivateDns(context, MODE_OPPORTUNISTIC, "")
    }

    /**
     * Convenience method to set Private DNS to a custom TLS hostname (Mode = "hostname").
     */
    fun setHostname(context: Context, hostname: String): Result<PrivateDnsConfig> {
        return setPrivateDns(context, MODE_PROVIDER_HOSTNAME, hostname)
    }

    /**
     * Convenience method to set Private DNS to AdGuard DNS (dns.adguard-dns.com).
     */
    fun setAdGuard(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_ADGUARD)
    }

    /**
     * Convenience method to set Private DNS to Cloudflare DNS (one.one.one.one).
     */
    fun setCloudflare(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_CLOUDFLARE)
    }

    /**
     * Convenience method to set Private DNS to Quad9 DNS (dns.quad9.net).
     */
    fun setQuad9(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_QUAD9)
    }
}
