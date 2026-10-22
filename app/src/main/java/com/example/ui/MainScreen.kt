package com.example.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
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

    // Launcher for standard on-screen Android VPN permission dialog
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

    val activeAccentColor by animateColorAsState(
        targetValue = when (currentProvider) {
            DnsProvider.ADGUARD -> AdGuardGreen
            DnsProvider.CLOUDFLARE -> CloudflareOrange
            DnsProvider.QUAD9 -> Quad9Purple
            DnsProvider.AUTOMATIC -> AutomaticBlue
            DnsProvider.OFF -> DnsOffGray
        },
        animationSpec = tween(400),
        label = "activeAccentColor"
    )

    // Liquid Glass Root Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060911),
                        Color(0xFF0A1020),
                        Color(0xFF04060C)
                    )
                )
            )
    ) {
        // Ambient Liquid Glow Meshes
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-40).dp, y = (-20).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            activeAccentColor.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = 140.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CyberCyan.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                activeAccentColor.copy(alpha = 0.35f),
                                                Color(0x15FFFFFF)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = activeAccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "DNS Switcher",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshState() },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh Status",
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0x60070B14)
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

                // Operating Mode Selector (Liquid Glass Pills)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engine_mode_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F172A).copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0x35FFFFFF),
                                    Color(0x10FFFFFF),
                                    CyberCyan.copy(alpha = 0.25f)
                                )
                            )
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "OPERATING ENGINE",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = CyberCyan
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasPermission) AdGuardGreen.copy(alpha = 0.18f) else Color(0x2038BDF8),
                                    border = BorderStroke(
                                        0.5.dp,
                                        if (hasPermission) AdGuardGreen.copy(alpha = 0.4f) else Color(0x4038BDF8)
                                    )
                                ) {
                                    Text(
                                        text = if (hasPermission) "Native DoT Active" else "Setup Available",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (hasPermission) AdGuardGreen else CyberCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Option 1: Native System Private DNS (Priority when granted)
                                val isSystemSelected = (engineMode == AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) || hasPermission
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSystemSelected) CyberCyan.copy(alpha = 0.15f) else Color(0x20FFFFFF),
                                    border = BorderStroke(
                                        if (isSystemSelected) 1.5.dp else 1.dp,
                                        if (isSystemSelected) CyberCyan else Color(0x1FFFFFFF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.setEngineMode(AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) }
                                        .testTag("mode_system_dns_button")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "⚙️ System DoT",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSystemSelected) CyberCyan else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Android native TLS (Zero lag)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                // Option 2: Local Zero-Command Mode
                                val isLocalSelected = !hasPermission && (engineMode == AppPreferences.ENGINE_LOCAL_DNS)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isLocalSelected) AdGuardGreen.copy(alpha = 0.15f) else Color(0x20FFFFFF),
                                    border = BorderStroke(
                                        if (isLocalSelected) 1.5.dp else 1.dp,
                                        if (isLocalSelected) AdGuardGreen else Color(0x1FFFFFFF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.setEngineMode(AppPreferences.ENGINE_LOCAL_DNS) }
                                        .testTag("mode_no_command_button")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "⚡ Zero-Command",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isLocalSelected) AdGuardGreen else Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "100% on phone (No PC)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Hero Liquid Glass Status Card with Glowing Orb & Toggle
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_status_card"),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF0F1A2E).copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0x55FFFFFF),
                                    activeAccentColor.copy(alpha = 0.7f),
                                    Color(0x15FFFFFF)
                                )
                            )
                        )
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
                                    // Liquid Glowing Orb Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(
                                                        activeAccentColor.copy(alpha = 0.35f),
                                                        Color(0x1AFFFFFF)
                                                    )
                                                )
                                            )
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = currentProvider.iconRes),
                                            contentDescription = currentProvider.displayName,
                                            tint = activeAccentColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = "ACTIVE DNS PROVIDER",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = currentProvider.displayName,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }

                                // Status Pill
                                val isOff = (currentProvider == DnsProvider.OFF)
                                Surface(
                                    shape = CircleShape,
                                    color = if (isOff) DnsOffGray.copy(alpha = 0.25f) else activeAccentColor.copy(alpha = 0.2f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isOff) DnsOffGray.copy(alpha = 0.5f) else activeAccentColor.copy(alpha = 0.8f)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (isOff) DnsOffGray else activeAccentColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (currentProvider) {
                                                DnsProvider.ADGUARD -> "BLOCKING ADS"
                                                DnsProvider.CLOUDFLARE -> "ZERO LOGS"
                                                DnsProvider.QUAD9 -> "THREAT SHIELD"
                                                DnsProvider.AUTOMATIC -> "ISP TLS"
                                                DnsProvider.OFF -> "DISABLED"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isOff) Color(0xFF94A3B8) else activeAccentColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Liquid Details Strip
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x20FFFFFF),
                                border = BorderStroke(0.5.dp, Color(0x25FFFFFF)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TLS Hostname:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = if (currentProvider.hostname.isNotEmpty()) currentProvider.hostname else "Standard DNS (Port 53)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeAccentColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Liquid Glass Primary Toggle Button
                            Button(
                                onClick = {
                                    if (!hasPermission && engineMode == AppPreferences.ENGINE_LOCAL_DNS && !isVpnConsentGranted) {
                                        requestVpnConsent()
                                    } else {
                                        viewModel.toggleNextProvider()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("toggle_dns_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = activeAccentColor,
                                    contentColor = Color(0xFF030816)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Switch to ${nextProvider.displayName}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Permission or Local Consent Card
                if (!hasPermission && engineMode == AppPreferences.ENGINE_LOCAL_DNS) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_vpn_consent_card"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isVpnConsentGranted) Color(0xFF0D251C).copy(alpha = 0.75f) else Color(0xFF101C30).copy(alpha = 0.75f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isVpnConsentGranted) AdGuardGreen.copy(alpha = 0.45f) else CyberCyan.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isVpnConsentGranted) Icons.Default.CheckCircle else Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = if (isVpnConsentGranted) AdGuardGreen else CyberCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isVpnConsentGranted) "Zero-Command Mode Active" else "One-Tap Setup (No PC Required)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (isVpnConsentGranted) "Ready to switch DNS on device" else "Tap below to allow on-screen permission",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                if (!isVpnConsentGranted) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Android displays an on-screen connection prompt. Tap 'OK' to enable instant DNS switching without any computer or commands.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = requestVpnConsent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("grant_vpn_permission_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberCyan,
                                            contentColor = Color(0xFF030816)
                                        )
                                    ) {
                                        Text("Grant On-Screen Permission", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
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

                // Providers Section Header
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
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CyberCyan
                        )
                        Text(
                            text = "Tap to activate directly",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Individual Provider Cards (Liquid Glass cards)
                items(
                    count = DnsProvider.values().size,
                    key = { DnsProvider.values()[it].id }
                ) { index ->
                    val provider = DnsProvider.values()[index]
                    ProviderCard(
                        provider = provider,
                        isActive = provider == currentProvider,
                        onActivateClick = {
                            if (!hasPermission && engineMode == AppPreferences.ENGINE_LOCAL_DNS && !isVpnConsentGranted) {
                                requestVpnConsent()
                            } else {
                                viewModel.selectProvider(provider)
                            }
                        }
                    )
                }

                // Diagnostics Telemetry Card (tests latency and verifies ad blocking)
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
