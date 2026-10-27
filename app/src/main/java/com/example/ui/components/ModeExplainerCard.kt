package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppPreferences
import com.example.ui.theme.AdGuardGreen
import com.example.ui.theme.CyberCyan

/**
 * Beginner-friendly guide explaining the two operating modes in plain, simple English:
 * 1. Automatic (Zero-Command Local VPN) - 1 tap, no PC, safe on-device filtering
 * 2. Manual / System Private DNS - Android native TLS, requires 1-time PC/ADB setup
 */
@Composable
fun ModeExplainerCard(
    currentEngineMode: String,
    hasSecurePermission: Boolean,
    onSelectEngineMode: (String) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedKeyFaq by remember { mutableStateOf(false) }
    var expandedComparison by remember { mutableStateOf(false) }

    val isAutomaticActive = currentEngineMode == AppPreferences.ENGINE_LOCAL_DNS && !hasSecurePermission
    val isSystemActive = currentEngineMode == AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS || hasSecurePermission

    FrostedGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mode_explainer_container"),
        shape = RoundedCornerShape(26.dp),
        backgroundColor = Color(0xFF0F1A2E).copy(alpha = 0.85f),
        accentGlow = CyberCyan
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WHICH MODE SHOULD I USE?",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = CyberCyan
                        )
                        Text(
                            text = "Beginner\'s Quick Guide",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isAutomaticActive) "Auto Mode Active" else "Native Mode Active",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isAutomaticActive) AdGuardGreen else CyberCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode 1: Automatic (Zero-Command) Card
            ModeCardItem(
                title = "1. Automatic Mode (Zero-Command)",
                badge = "RECOMMENDED FOR FRIENDS & BEGINNERS",
                badgeColor = AdGuardGreen,
                isSelected = isAutomaticActive,
                icon = Icons.Default.Bolt,
                iconTint = AdGuardGreen,
                summary = "Instant 1-tap activation. No computer, cables, or technical setup needed.",
                whenToUse = "Best for: Anyone installing the app directly on their phone without a PC.",
                howItWorks = "Creates a 100% private loopback directly on your phone. Your DNS requests are encrypted on-device. No external VPN servers ever see or log your data.",
                buttonText = if (isAutomaticActive) "Active (Tap to Re-apply)" else "Switch to Automatic Mode",
                onClickSelect = { onSelectEngineMode(AppPreferences.ENGINE_LOCAL_DNS) },
                testTag = "select_automatic_mode_card"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mode 2: Manual (System Private DNS) Card
            ModeCardItem(
                title = "2. Manual System Mode (Private DNS)",
                badge = if (hasSecurePermission) "READY & GRANTED" else "REQUIRES 1-TIME PC SETUP",
                badgeColor = if (hasSecurePermission) AdGuardGreen else Color(0xFFF59E0B),
                isSelected = isSystemActive,
                icon = Icons.Default.Settings,
                iconTint = CyberCyan,
                summary = "Uses Android\'s native system Private DNS setting with zero battery impact and no key icon.",
                whenToUse = "Best for: Advanced users who have a PC (ADB) or use Shizuku.",
                howItWorks = "Directly commands Android\'s Settings.Global system resolver over encrypted TLS (DoT). Requires one-time ADB permission.",
                buttonText = if (isSystemActive) "Active (Native DoT)" else "Switch to System Mode",
                onClickSelect = { onSelectEngineMode(AppPreferences.ENGINE_SYSTEM_PRIVATE_DNS) },
                testTag = "select_system_mode_card"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Collapsible: "Why does Automatic Mode show a Key 🔑 icon?"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expandedKeyFaq = !expandedKeyFaq },
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Why do I see a key icon (🔑) in status bar?",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = if (expandedKeyFaq) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    AnimatedVisibility(visible = expandedKeyFaq) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "Don\'t worry! The key icon is standard Android behavior whenever an app encrypts or filters DNS requests on-device.\n\n" +
                                        "• 100% On-Device: All DNS filtering happens entirely inside your phone.\n" +
                                        "• Zero Data Sent to Us: There is no third-party VPN server.\n" +
                                        "• Battery Friendly: Uses less than 0.5% battery throughout the day.",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Collapsible: Side-by-side comparison matrix
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expandedComparison = !expandedComparison },
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Side-by-Side Comparison Table",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = if (expandedComparison) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    AnimatedVisibility(visible = expandedComparison) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            ComparisonRow(
                                feature = "Setup Required",
                                automatic = "1-Tap on Screen",
                                manual = "Computer (ADB command)"
                            )
                            ComparisonRow(
                                feature = "Status Bar Key (🔑)",
                                automatic = "Shown by Android",
                                manual = "None (Invisible)"
                            )
                            ComparisonRow(
                                feature = "Battery Overhead",
                                automatic = "< 0.5% (Very Low)",
                                manual = "0% (Built-in OS)"
                            )
                            ComparisonRow(
                                feature = "Works on any phone?",
                                automatic = "Yes (Android 7+)",
                                manual = "Needs ADB permission"
                            )
                            ComparisonRow(
                                feature = "Ideal For",
                                automatic = "Friends, Family, Anyone",
                                manual = "Tech enthusiasts"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCardItem(
    title: String,
    badge: String,
    badgeColor: Color,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    summary: String,
    whenToUse: String,
    howItWorks: String,
    buttonText: String,
    onClickSelect: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) iconTint.copy(alpha = 0.12f) else Color(0x10FFFFFF),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) iconTint.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = iconTint
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = whenToUse,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF93C5FD)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = howItWorks,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClickSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) iconTint else Color.White.copy(alpha = 0.12f),
                    contentColor = if (isSelected) Color.Black else Color.White
                )
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    feature: String,
    automatic: String,
    manual: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = CyberCyan
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "• Auto: $automatic",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "• Manual: $manual",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
