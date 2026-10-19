package com.example.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppPreferences
import com.example.data.DnsProvider
import com.example.manager.DnsManager
import com.example.ui.components.DiagnosticCard
import com.example.ui.components.PermissionStatusCard
import com.example.ui.components.ProviderCard
import com.example.ui.components.QuickSettingsGuideCard
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.AdGuardGreen
import com.example.ui.theme.AutomaticBlue
import com.example.ui.theme.CloudflareOrange
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DnsOffGray
import com.example.ui.theme.Quad9Purple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engineMode by viewModel.engineMode.collectAsState()
    val currentProvider by viewModel.currentProvider.collectAsState()
    val isVpnConsentGranted by viewModel.isVpnConsentGranted.collectAsState()
    val isVpnRunning by viewModel.isVpnRunning.collectAsState()
    val hasPermission by viewModel.hasSecureSettingsPermission.collectAsState()
    val rawMode by viewModel.rawMode.collectAsState()
    val rawSpecifier by viewModel.rawSpecifier.collectAsState()
    val lastStatus by viewModel.lastStatus.collectAsState()
    val lastTimestamp by viewModel.lastTimestamp.collectAsState()
    val diagnosticResult by viewModel.diagnosticResult.collectAsState()
    val cycleMode by viewModel.cycleMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }

    // Launcher for standard on-screen Android VPN permission dialog (Zero ADB required)
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshState()
            viewModel.selectProvider(DnsProvider.ADGUARD)
        }
    }

    val requestVpnConsent: () -> Unit = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            viewModel.refreshState()
        }
    }

    val nextProvider = remember(currentProvider, cycleMode) {
        DnsManager.getNextProviderInCycle(context, currentProvider)
    }

    val activeAccentColor = when (currentProvider) {
        DnsProvider.ADGUARD -> AdGuardGreen
        DnsProvider.CLOUDFLARE -> CloudflareOrange
        DnsProvider.QUAD9 -> Quad9Purple
        DnsProvider.AUTOMATIC -> AutomaticBlue
        DnsProvider.OFF -> DnsOffGray
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(activeAccentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = activeAccentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DNS Switcher",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshState() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // Operating Mode Selector (No-Command Mode vs System Private DNS)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("engine_mode_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "OPERATING MODE",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) AdGuardGreen.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) "No ADB Needed" else "Requires ADB",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) AdGuardGreen else CyberCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Option 1: No-Command Mode (Local DNS)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) 1.5.dp else 1.dp,
                                    if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setEngineMode(AppPreferences.ENGINE_LOCAL_DNS) }
                                    .testTag("mode_no_command_button")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "⚡ No Commands",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "100% on phone. No PC/ADB.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Option 2: System Private DNS Mode (Requires ADB)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (engineMode == AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (engineMode == AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) 1.5.dp else 1.dp,
                                    if (engineMode == AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setEngineMode(AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) }
                                    .testTag("mode_system_dns_button")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "⚙️ System DNS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Changes Android settings.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hero Status & Large Toggle Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_status_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = activeAccentColor.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.5.dp, activeAccentColor.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(activeAccentColor.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = currentProvider.iconRes),
                                        contentDescription = currentProvider.displayName,
                                        tint = activeAccentColor,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "Active DNS Provider",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentProvider.displayName,
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = if (currentProvider == DnsProvider.OFF) DnsOffGray else activeAccentColor,
                                contentColor = Color.White
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (currentProvider == DnsProvider.OFF) "Disabled" else "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Details pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) "Encrypted DoH Routing:" else "Private DNS Mode:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (currentProvider.hostname.isNotEmpty()) currentProvider.hostname else currentProvider.mode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = activeAccentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Toggle Button
                        Button(
                            onClick = {
                                if (engineMode == AppPreferences.ENGINE_LOCAL_DNS && !isVpnConsentGranted) {
                                    requestVpnConsent()
                                } else {
                                    viewModel.toggleNextProvider()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("toggle_dns_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeAccentColor,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Switch to ${nextProvider.displayName}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Mode-Specific Setup / Permission Banner
            if (engineMode == AppPreferences.ENGINE_LOCAL_DNS) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("local_vpn_consent_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVpnConsentGranted) AdGuardGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isVpnConsentGranted) AdGuardGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isVpnConsentGranted) Icons.Default.CheckCircle else Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = if (isVpnConsentGranted) AdGuardGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isVpnConsentGranted) "No-Command Mode Ready" else "One-Tap Setup (No PC Required)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isVpnConsentGranted) "Active and ready to switch anytime" else "Tap below to allow on-screen permission",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!isVpnConsentGranted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Android displays a standard connection prompt right on your screen. Tap 'OK' to enable instant DNS switching without any command execution.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = requestVpnConsent,
                                    modifier = Modifier.fillMaxWidth().testTag("grant_vpn_permission_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Grant On-Screen Permission (No PC / ADB)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // System Private DNS mode (requires WRITE_SECURE_SETTINGS)
                item {
                    PermissionStatusCard(
                        isGranted = hasPermission,
                        adbCommand = viewModel.adbCommand,
                        onVerifyClick = { viewModel.refreshState() },
                        onOpenSystemSettings = { viewModel.openSystemSettings(context) }
                    )
                }
            }

            // Quick Settings Guide Card
            item {
                QuickSettingsGuideCard(
                    isAddTileSupported = viewModel.isAddTileSupported,
                    onAddTileClick = { viewModel.requestAddTile(context) }
                )
            }

            // Providers List Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DNS PROVIDERS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap any card to activate directly",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Individual Provider Cards (AdGuard, Cloudflare, Quad9, Automatic, Off)
            items(
                count = DnsProvider.values().size,
                key = { DnsProvider.values()[it].id }
            ) { index ->
                val provider = DnsProvider.values()[index]
                ProviderCard(
                    provider = provider,
                    isActive = provider == currentProvider,
                    onActivateClick = {
                        if (engineMode == AppPreferences.ENGINE_LOCAL_DNS && !isVpnConsentGranted) {
                            requestVpnConsent()
                        } else {
                            viewModel.selectProvider(provider)
                        }
                    }
                )
            }

            // Diagnostics telemetry card
            item {
                DiagnosticCard(
                    rawMode = rawMode,
                    rawSpecifier = rawSpecifier,
                    lastStatus = lastStatus,
                    lastTimestamp = lastTimestamp,
                    diagnosticResult = diagnosticResult,
                    onRunDiagnostic = { viewModel.runDnsDiagnostic() }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showSettingsSheet) {
        SettingsSheet(
            cycleMode = cycleMode,
            onCycleModeChange = { viewModel.setCycleMode(it) },
            themeMode = themeMode,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
