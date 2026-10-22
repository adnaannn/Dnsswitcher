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
    const val HOSTNAME_CLOUDFLARE_IP = "1.1.1.1"
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
                        cleanSpecifier == "one.one.one.one" ||
                        cleanSpecifier == "1.1.1.1" ||
                        cleanSpecifier.contains("cloudflare") -> "Cloudflare DNS"
                        cleanSpecifier.contains("quad9") -> "Quad9 DNS"
                        cleanSpecifier.isNotEmpty() -> cleanSpecifier
                        else -> "Automatic"
                    }
                    else -> "Automatic"
                }
            }
    }

    /**
     * Normalizes and sanitizes a Private DNS hostname.
     * Strips URL schemes, ports, and resolves aliases (e.g. "1.1.1.1" -> "one.one.one.one").
     */
    fun normalizeHostname(raw: String): String {
        var clean = raw.trim().lowercase()
        // Strip URL schemes if user pasted a DoH/DoT URL
        clean = clean.removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("tls://")
            .removePrefix("dot://")
            .removePrefix("dns://")

        // Strip path or port suffixes
        if (clean.contains("/")) {
            clean = clean.substringBefore("/")
        }
        if (clean.contains(":") && !clean.contains("::")) {
            clean = clean.substringBefore(":")
        }

        // Map known aliases or IP literals to official TLS SNI hostnames
        return when {
            clean == "1.1.1.1" || clean == "1.0.0.1" || clean == "cloudflare" || clean == "1dot1dot1dot1.cloudflare-dns.com" -> HOSTNAME_CLOUDFLARE
            clean == "adguard" || clean == "dns.adguard-dns.com" || clean == "dns.adguard.com" -> HOSTNAME_ADGUARD
            clean == "quad9" || clean == "9.9.9.9" || clean == "149.112.112.112" -> HOSTNAME_QUAD9
            else -> clean
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
     * Actively instructs Android's ConnectivityService / NetworkMonitor to re-probe
     * and validate the active network(s). This forces netd to initiate DoT TLS handshake
     * and unblocks network traffic on Wi-Fi and Mobile Data immediately.
     */
    fun triggerNetworkRevalidation(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                cm.reportNetworkConnectivity(activeNetwork, true)
            }
            cm.allNetworks.forEach { network ->
                try {
                    cm.reportNetworkConnectivity(network, true)
                } catch (ignored: Exception) {}
            }
            Log.d(TAG, "Triggered network revalidation on active networks")
        } catch (e: Exception) {
            Log.w(TAG, "Could not report network connectivity revalidation", e)
        }
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
            val targetSpecifier = if (targetMode == MODE_PROVIDER_HOSTNAME) {
                normalizeHostname(hostname)
            } else {
                ""
            }

            val modeSuccess: Boolean
            val specifierSuccess: Boolean

            // CRITICAL ORDER OF OPERATIONS:
            // When setting strict hostname mode, Android's netd triggers TLS certificate validation
            // immediately when PRIVATE_DNS_MODE changes to "hostname".
            // Therefore, PRIVATE_DNS_SPECIFIER MUST be written FIRST so netd never validates against
            // an empty or stale hostname.
            // Conversely, when turning off or switching to opportunistic, write mode first, then clear specifier.
            if (targetMode == MODE_PROVIDER_HOSTNAME) {
                specifierSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_SPECIFIER, targetSpecifier)
                modeSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_MODE, targetMode)
            } else {
                modeSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_MODE, targetMode)
                specifierSuccess = Settings.Global.putString(resolver, PRIVATE_DNS_SPECIFIER, "")
            }

            if (!modeSuccess || !specifierSuccess) {
                return Result.failure(IllegalStateException("Settings.Global returned false when persisting values"))
            }

            // Immediately read back and verify from system
            val verifiedConfig = getCurrentConfig(context)
            val isModeOk = verifiedConfig.mode.equals(targetMode, ignoreCase = true)
            val isSpecifierOk = when (targetMode) {
                MODE_PROVIDER_HOSTNAME -> verifiedConfig.specifier.equals(targetSpecifier, ignoreCase = true)
                else -> true
            }

            if (!isModeOk || !isSpecifierOk) {
                val errorMsg = "System verification failed. Read back: mode=${verifiedConfig.mode}, specifier=${verifiedConfig.specifier}"
                Log.w(TAG, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // Actively trigger network connectivity revalidation so Android ConnectivityService
            // immediately validates the new DoT server and restores internet on Wi-Fi and Mobile Data
            triggerNetworkRevalidation(context)

            Log.i(TAG, "Private DNS successfully updated to: mode=$targetMode, specifier=$targetSpecifier")
            return Result.success(verifiedConfig)

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
     * Convenience method to set Private DNS to AdGuard DNS (dns.adguard.com).
     */
    fun setAdGuard(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_ADGUARD)
    }

    /**
     * Convenience method to set Private DNS to Cloudflare DNS (one.one.one.one / 1.1.1.1).
     */
    fun setCloudflare(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_CLOUDFLARE)
    }

    /**
     * Convenience method to set Private DNS using Cloudflare IP "1.1.1.1" (automatically normalized).
     */
    fun setCloudflareIp(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_CLOUDFLARE_IP)
    }

    /**
     * Convenience method to set Private DNS to Quad9 DNS (dns.quad9.net).
     */
    fun setQuad9(context: Context): Result<PrivateDnsConfig> {
        return setHostname(context, HOSTNAME_QUAD9)
    }
}
