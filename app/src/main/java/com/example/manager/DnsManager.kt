package com.example.manager

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.data.AppPreferences
import com.example.data.DnsProvider
import com.example.data.DnsSwitchResult
import com.example.service.DnsTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object DnsManager {
    private const val TAG = "DnsManager"

    // Forward constant aliases from PrivateDnsHelper
    const val KEY_PRIVATE_DNS_MODE = PrivateDnsHelper.PRIVATE_DNS_MODE
    const val KEY_PRIVATE_DNS_SPECIFIER = PrivateDnsHelper.PRIVATE_DNS_SPECIFIER

    private val isSwitching = AtomicBoolean(false)

    private val _currentProviderFlow = MutableStateFlow(DnsProvider.AUTOMATIC)
    val currentProviderFlow: StateFlow<DnsProvider> = _currentProviderFlow.asStateFlow()

    private val _lastSwitchResultFlow = MutableStateFlow<DnsSwitchResult?>(null)
    val lastSwitchResultFlow: StateFlow<DnsSwitchResult?> = _lastSwitchResultFlow.asStateFlow()

    fun hasSecureSettingsPermission(context: Context): Boolean {
        return PrivateDnsHelper.canWriteSecureSettings(context)
    }

    fun getAdbCommand(context: Context): String {
        return PrivateDnsHelper.getAdbGrantCommand(context)
    }

    fun getRawSystemSettings(context: Context): Pair<String?, String?> {
        val config = PrivateDnsHelper.getCurrentConfig(context)
        return Pair(config.mode, config.specifier)
    }

    fun getCurrentProvider(context: Context): DnsProvider {
        val (mode, specifier) = getRawSystemSettings(context)
        val provider = DnsProvider.fromSpecifier(mode, specifier)
        _currentProviderFlow.value = provider
        return provider
    }

    fun getNextProviderInCycle(context: Context, current: DnsProvider): DnsProvider {
        val prefs = AppPreferences.getInstance(context)
        return when (prefs.cycleMode) {
            AppPreferences.CYCLE_TWO_STATE -> {
                when (current) {
                    DnsProvider.ADGUARD -> DnsProvider.CLOUDFLARE
                    DnsProvider.CLOUDFLARE -> DnsProvider.ADGUARD
                    else -> DnsProvider.ADGUARD
                }
            }
            AppPreferences.CYCLE_THREE_STATE -> {
                when (current) {
                    DnsProvider.AUTOMATIC -> DnsProvider.ADGUARD
                    DnsProvider.ADGUARD -> DnsProvider.CLOUDFLARE
                    DnsProvider.CLOUDFLARE -> DnsProvider.AUTOMATIC
                    else -> DnsProvider.ADGUARD
                }
            }
            else -> {
                // Default: 5-state cycle (Off -> AdGuard -> Cloudflare -> Quad9 -> Automatic -> Off)
                when (current) {
                    DnsProvider.OFF -> DnsProvider.ADGUARD
                    DnsProvider.ADGUARD -> DnsProvider.CLOUDFLARE
                    DnsProvider.CLOUDFLARE -> DnsProvider.QUAD9
                    DnsProvider.QUAD9 -> DnsProvider.AUTOMATIC
                    DnsProvider.AUTOMATIC -> DnsProvider.OFF
                }
            }
        }
    }

    @Synchronized
    fun applyProvider(context: Context, target: DnsProvider): DnsSwitchResult {
        if (!isSwitching.compareAndSet(false, true)) {
            Log.w(TAG, "Operation already in progress, ignoring rapid tap")
            return DnsSwitchResult.Error("Another DNS switch is already in progress.")
        }

        try {
            if (!hasSecureSettingsPermission(context)) {
                val adbCmd = getAdbCommand(context)
                val result = DnsSwitchResult.PermissionDenied(
                    requiredCommand = adbCmd,
                    reason = "WRITE_SECURE_SETTINGS permission is required to change Android Private DNS."
                )
                AppPreferences.getInstance(context).recordSwitchFailure("Missing WRITE_SECURE_SETTINGS permission.")
                _lastSwitchResultFlow.value = result
                return result
            }

            val helperResult = PrivateDnsHelper.setPrivateDns(
                context = context,
                mode = target.mode,
                hostname = target.hostname
            )

            val result: DnsSwitchResult = if (helperResult.isSuccess) {
                val config = helperResult.getOrThrow()
                _currentProviderFlow.value = target
                AppPreferences.getInstance(context).recordSwitchSuccess(target)
                DnsTileService.requestTileUpdate(context)
                DnsSwitchResult.Success(
                    provider = target,
                    verifiedHostname = config.specifier ?: target.hostname
                )
            } else {
                val exception = helperResult.exceptionOrNull()
                val errorMsg = exception?.localizedMessage ?: "Verification failed."
                AppPreferences.getInstance(context).recordSwitchFailure(errorMsg)
                val (actualMode, actualSpecifier) = getRawSystemSettings(context)
                DnsSwitchResult.VerificationFailed(
                    expectedProvider = target,
                    actualMode = actualMode,
                    actualSpecifier = actualSpecifier
                )
            }

            _lastSwitchResultFlow.value = result
            return result

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while changing Private DNS", e)
            val adbCmd = getAdbCommand(context)
            val result = DnsSwitchResult.PermissionDenied(
                requiredCommand = adbCmd,
                reason = e.localizedMessage ?: "SecurityException: WRITE_SECURE_SETTINGS not granted"
            )
            AppPreferences.getInstance(context).recordSwitchFailure(e.localizedMessage ?: "Permission denied")
            _lastSwitchResultFlow.value = result
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while changing Private DNS", e)
            val result = DnsSwitchResult.Error(e.localizedMessage ?: "Unknown error writing DNS settings")
            AppPreferences.getInstance(context).recordSwitchFailure(e.localizedMessage ?: "Error writing settings")
            _lastSwitchResultFlow.value = result
            return result
        } finally {
            isSwitching.set(false)
        }
    }

    fun openSystemPrivateDnsSettings(context: Context) {
        val intents = listOf(
            Intent("android.settings.NETWORK_PROVIDER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (ignored: Exception) {
                // Try next intent
            }
        }
    }
}
