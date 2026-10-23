package com.example.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.example.MainActivity
import com.example.data.AppPreferences
import com.example.data.DnsProvider
import com.example.data.DnsSwitchResult
import com.example.data.ProfileRepository
import com.example.manager.DnsManager
import com.example.manager.PrivateDnsHelper
import com.example.vpn.DnsVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class DnsTileService : TileService() {

    companion object {
        private const val TAG = "DnsTileService"
        private val isSwitching = AtomicBoolean(false)

        fun requestTileUpdate(context: Context) {
            try {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, DnsTileService::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed requesting tile update", e)
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleTileTap() }
        } else {
            handleTileTap()
        }
    }

    private fun handleTileTap() {
        // Prevent concurrent overlapping taps
        if (!isSwitching.compareAndSet(false, true)) {
            Log.d(TAG, "Tap ignored, switch in progress")
            return
        }

        val prefs = AppPreferences.getInstance(this)
        val hasSecurePerm = DnsManager.hasSecureSettingsPermission(this)
        val isLocalDnsMode = (prefs.engineMode == AppPreferences.ENGINE_LOCAL_DNS) || !hasSecurePerm

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isLocalDnsMode) {
                    handleLocalDnsTileTap(prefs)
                } else {
                    DnsVpnService.stopVpn(this@DnsTileService)
                    handleSystemDnsTileTap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling tile tap", e)
                postToast("DNS switch failed: ${e.localizedMessage}")
                refreshTile()
            } finally {
                isSwitching.set(false)
            }
        }
    }

    /**
     * Zero-command execution flow.
     * Uses Android's native VpnService permission prompt on device screen (no ADB or PC needed).
     */
    private fun handleLocalDnsTileTap(prefs: AppPreferences) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            postToast("Tap 'Grant Permission' on screen (No PC/ADB needed)")
            openMainActivity()
            refreshTile()
            return
        }

        val isRunning = DnsVpnService.isRunningFlow.value
        val currentProvider = if (isRunning) {
            when (DnsVpnService.currentProfileFlow.value?.id) {
                "adguard" -> DnsProvider.ADGUARD
                "cloudflare" -> DnsProvider.CLOUDFLARE
                "quad9" -> DnsProvider.QUAD9
                else -> DnsProvider.ADGUARD
            }
        } else {
            if (prefs.activeLocalProviderId == "automatic") DnsProvider.AUTOMATIC else DnsProvider.OFF
        }

        val nextProvider = DnsManager.getNextProviderInCycle(this, currentProvider)

        prefs.activeLocalProviderId = nextProvider.id
        prefs.recordSwitchSuccess(nextProvider)

        when (nextProvider) {
            DnsProvider.OFF -> {
                DnsVpnService.stopVpn(this)
                updateTileAppearance(qsTile, DnsProvider.OFF)
                PrivateDnsHelper.triggerNetworkRevalidation(this)
                postToast("DNS: Off")
            }
            DnsProvider.AUTOMATIC -> {
                DnsVpnService.stopVpn(this)
                updateTileAppearance(qsTile, DnsProvider.AUTOMATIC)
                PrivateDnsHelper.triggerNetworkRevalidation(this)
                postToast("DNS: Automatic (System Default)")
            }
            DnsProvider.ADGUARD, DnsProvider.CLOUDFLARE, DnsProvider.QUAD9 -> {
                val profileId = nextProvider.id
                if (!isRunning) {
                    DnsVpnService.startVpn(this, profileId)
                } else {
                    DnsVpnService.switchProfile(this, profileId)
                }
                updateTileAppearance(qsTile, nextProvider)
                PrivateDnsHelper.triggerNetworkRevalidation(this)
                postToast("DNS: ${nextProvider.displayName}")
            }
        }
    }

    /**
     * System Private DNS flow (uses Settings.Global, requires WRITE_SECURE_SETTINGS via ADB).
     */
    private fun handleSystemDnsTileTap() {
        if (!DnsManager.hasSecureSettingsPermission(applicationContext)) {
            postToast("WRITE_SECURE_SETTINGS required or switch to No-Command Mode")
            openMainActivity()
            refreshTile()
            return
        }

        val currentProvider = DnsManager.getCurrentProvider(applicationContext)
        val nextProvider = DnsManager.getNextProviderInCycle(applicationContext, currentProvider)

        when (val result = DnsManager.applyProvider(applicationContext, nextProvider)) {
            is DnsSwitchResult.Success -> {
                updateTileAppearance(qsTile, result.provider)
                postToast("DNS switched to ${result.provider.displayName}")
            }
            is DnsSwitchResult.VerificationFailed -> {
                postToast("Verification failed. System kept current DNS.")
                refreshTile()
            }
            is DnsSwitchResult.PermissionDenied -> {
                postToast("Permission required: Open app to setup ADB or use No-Command Mode")
                openMainActivity()
                refreshTile()
            }
            is DnsSwitchResult.Error -> {
                postToast("DNS switch error: ${result.message}")
                refreshTile()
            }
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val prefs = AppPreferences.getInstance(this)
        val hasSecurePerm = DnsManager.hasSecureSettingsPermission(this)
        val isLocalDnsMode = (prefs.engineMode == AppPreferences.ENGINE_LOCAL_DNS) || !hasSecurePerm

        if (isLocalDnsMode) {
            val isRunning = DnsVpnService.isRunningFlow.value
            val provider = if (isRunning) {
                when (DnsVpnService.currentProfileFlow.value?.id) {
                    "adguard" -> DnsProvider.ADGUARD
                    "cloudflare" -> DnsProvider.CLOUDFLARE
                    "quad9" -> DnsProvider.QUAD9
                    else -> DnsProvider.ADGUARD
                }
            } else {
                if (prefs.activeLocalProviderId == "automatic") DnsProvider.AUTOMATIC else DnsProvider.OFF
            }
            updateTileAppearance(tile, provider)
        } else {
            try {
                val currentProvider = DnsManager.getCurrentProvider(this)
                updateTileAppearance(tile, currentProvider)
            } catch (e: Exception) {
                Log.w(TAG, "Failed refreshing tile", e)
            }
        }
    }

    private fun updateTileAppearance(tile: Tile?, provider: DnsProvider) {
        if (tile == null) return

        tile.label = provider.displayName

        val isOff = (provider == DnsProvider.OFF)
        tile.state = if (isOff) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (provider) {
                DnsProvider.OFF -> "Off"
                DnsProvider.AUTOMATIC -> "System Default"
                else -> provider.hostname.ifEmpty { "Active" }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = "${provider.displayName} ${if (isOff) "Disabled" else "Active"}"
        }

        tile.contentDescription = "DNS Switcher: ${provider.displayName}"
        try {
            tile.icon = Icon.createWithResource(this, provider.iconRes)
        } catch (e: Exception) {
            tile.icon = Icon.createWithResource(this, com.example.R.drawable.ic_tile_dns)
        }
        tile.updateTile()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun postToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
