package com.example.ui

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppPreferences
import com.example.data.DnsDiagnosticResult
import com.example.data.DnsProvider
import com.example.data.DnsSwitchResult
import com.example.manager.DnsDiagnosticRunner
import com.example.manager.DnsManager
import com.example.service.DnsTileService
import com.example.vpn.DnsVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences.getInstance(application)

    private val _engineMode = MutableStateFlow(prefs.engineMode)
    val engineMode: StateFlow<String> = _engineMode.asStateFlow()

    private val _currentProvider = MutableStateFlow(DnsProvider.ADGUARD)
    val currentProvider: StateFlow<DnsProvider> = _currentProvider.asStateFlow()

    private val _isVpnRunning = DnsVpnService.isRunningFlow
    val isVpnRunning: StateFlow<Boolean> = _isVpnRunning

    private val _isVpnConsentGranted = MutableStateFlow(false)
    val isVpnConsentGranted: StateFlow<Boolean> = _isVpnConsentGranted.asStateFlow()

    private val _hasSecureSettingsPermission = MutableStateFlow(false)
    val hasSecureSettingsPermission: StateFlow<Boolean> = _hasSecureSettingsPermission.asStateFlow()

    private val _rawMode = MutableStateFlow<String?>(null)
    val rawMode: StateFlow<String?> = _rawMode.asStateFlow()

    private val _rawSpecifier = MutableStateFlow<String?>(null)
    val rawSpecifier: StateFlow<String?> = _rawSpecifier.asStateFlow()

    private val _lastStatus = MutableStateFlow(prefs.lastSwitchStatus)
    val lastStatus: StateFlow<String> = _lastStatus.asStateFlow()

    private val _lastTimestamp = MutableStateFlow(prefs.lastSwitchTimestamp)
    val lastTimestamp: StateFlow<Long> = _lastTimestamp.asStateFlow()

    private val _lastError = MutableStateFlow(prefs.lastSwitchError)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _diagnosticResult = MutableStateFlow<DnsDiagnosticResult>(DnsDiagnosticResult.Idle)
    val diagnosticResult: StateFlow<DnsDiagnosticResult> = _diagnosticResult.asStateFlow()

    private val _cycleMode = MutableStateFlow(prefs.cycleMode)
    val cycleMode: StateFlow<String> = _cycleMode.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.themeMode)
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    val adbCommand: String
        get() = DnsManager.getAdbCommand(getApplication())

    val isAddTileSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    init {
        refreshState()
    }

    fun refreshState() {
        val context = getApplication<Application>()
        _hasSecureSettingsPermission.value = DnsManager.hasSecureSettingsPermission(context)
        val (mode, specifier) = DnsManager.getRawSystemSettings(context)
        _rawMode.value = mode
        _rawSpecifier.value = specifier

        _isVpnConsentGranted.value = (VpnService.prepare(context) == null)

        if (_engineMode.value == AppPreferences.ENGINE_LOCAL_DNS) {
            val isRunning = DnsVpnService.isRunningFlow.value
            _currentProvider.value = if (isRunning) {
                when (DnsVpnService.currentProfileFlow.value?.id) {
                    "adguard" -> DnsProvider.ADGUARD
                    "cloudflare" -> DnsProvider.CLOUDFLARE
                    "quad9" -> DnsProvider.QUAD9
                    else -> DnsProvider.ADGUARD
                }
            } else {
                if (prefs.activeLocalProviderId == "automatic") DnsProvider.AUTOMATIC else DnsProvider.OFF
            }
        } else {
            _currentProvider.value = DnsManager.getCurrentProvider(context)
        }

        _lastStatus.value = prefs.lastSwitchStatus
        _lastTimestamp.value = prefs.lastSwitchTimestamp
        _lastError.value = prefs.lastSwitchError
    }

    fun setEngineMode(mode: String) {
        prefs.engineMode = mode
        _engineMode.value = mode
        refreshState()
        DnsTileService.requestTileUpdate(getApplication())
    }

    fun toggleNextProvider() {
        val context = getApplication<Application>()
        val next = DnsManager.getNextProviderInCycle(context, _currentProvider.value)
        selectProvider(next)
    }

    fun selectProvider(provider: DnsProvider) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            if (_engineMode.value == AppPreferences.ENGINE_LOCAL_DNS) {
                // Local zero-command mode
                if (VpnService.prepare(context) != null) {
                    _isVpnConsentGranted.value = false
                    Toast.makeText(context, "Please grant VPN permission on screen", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                prefs.activeLocalProviderId = provider.id
                prefs.recordSwitchSuccess(provider)

                when (provider) {
                    DnsProvider.OFF -> {
                        DnsVpnService.stopVpn(context)
                        _currentProvider.value = DnsProvider.OFF
                        Toast.makeText(context, "DNS Off", Toast.LENGTH_SHORT).show()
                    }
                    DnsProvider.AUTOMATIC -> {
                        DnsVpnService.stopVpn(context)
                        _currentProvider.value = DnsProvider.AUTOMATIC
                        Toast.makeText(context, "DNS: Automatic (Network Default)", Toast.LENGTH_SHORT).show()
                    }
                    DnsProvider.ADGUARD, DnsProvider.CLOUDFLARE, DnsProvider.QUAD9 -> {
                        if (!DnsVpnService.isRunningFlow.value) {
                            DnsVpnService.startVpn(context, provider.id)
                        } else {
                            DnsVpnService.switchProfile(context, provider.id)
                        }
                        _currentProvider.value = provider
                        Toast.makeText(context, "Switched to ${provider.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
                DnsTileService.requestTileUpdate(context)
                refreshState()

            } else {
                // System Private DNS mode (requires WRITE_SECURE_SETTINGS)
                val result = DnsManager.applyProvider(context, provider)
                refreshState()
                when (result) {
                    is DnsSwitchResult.Success -> {
                        Toast.makeText(context, "Switched to ${provider.displayName}", Toast.LENGTH_SHORT).show()
                    }
                    is DnsSwitchResult.PermissionDenied -> {
                        Toast.makeText(context, "Permission missing. Switch to No-Command Mode or run ADB.", Toast.LENGTH_LONG).show()
                    }
                    is DnsSwitchResult.VerificationFailed -> {
                        Toast.makeText(context, "Verification failed. System did not accept change.", Toast.LENGTH_LONG).show()
                    }
                    is DnsSwitchResult.Error -> {
                        Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun runDnsDiagnostic() {
        viewModelScope.launch {
            _diagnosticResult.value = DnsDiagnosticResult.Testing
            val result = DnsDiagnosticRunner.testDnsResolution("one.one.one.one")
            _diagnosticResult.value = result
        }
    }

    fun setCycleMode(mode: String) {
        prefs.cycleMode = mode
        _cycleMode.value = mode
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _themeMode.value = mode
    }

    fun openSystemSettings(context: Context) {
        DnsManager.openSystemPrivateDnsSettings(context)
    }

    fun requestAddTile(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as? StatusBarManager
            val component = ComponentName(context, DnsTileService::class.java)
            val icon = Icon.createWithResource(context, R.drawable.ic_tile_dns)
            val executor = Executor { it.run() }

            statusBarManager?.requestAddTileService(
                component,
                context.getString(R.string.tile_dns_name),
                icon,
                executor
            ) { resultCode ->
                val msg = when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added to Quick Settings!"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile is already in Quick Settings."
                    else -> "Tile request dismissed."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
