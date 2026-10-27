package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AdGuardGreen
import com.example.ui.theme.CyberCyan
import com.example.updater.UpdateInfo
import com.example.updater.UpdateStatus
import java.io.File

/**
 * An on-screen pop-up dialog that alerts the user when an update is available on GitHub
 * or the remote server, allowing seamless 1-tap download and installation without uninstalling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateStatus: UpdateStatus,
    currentVersionName: String,
    onDownloadClick: (UpdateInfo) -> Unit,
    onInstallClick: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val isVisible = updateStatus is UpdateStatus.Available ||
            updateStatus is UpdateStatus.Downloading ||
            updateStatus is UpdateStatus.ReadyToInstall

    if (!isVisible) return

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("update_popup_dialog"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A2E)),
            border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = CyberCyan.copy(alpha = 0.18f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "UPDATE AVAILABLE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = CyberCyan
                            )
                            Text(
                                text = "Seamless In-App Update",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (updateStatus) {
                    is UpdateStatus.Available -> {
                        val info = updateStatus.updateInfo

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current: v$currentVersionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "→  New: v${info.versionName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AdGuardGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "What\'s New:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = Color(0xFFCBD5E1)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Later", color = Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onDownloadClick(info) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is UpdateStatus.Downloading -> {
                        Text(
                            text = "Downloading update package...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (updateStatus.progressPercent >= 0) {
                            LinearProgressIndicator(
                                progress = { updateStatus.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = CyberCyan,
                                trackColor = Color(0x33FFFFFF)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = CyberCyan,
                                trackColor = Color(0x33FFFFFF)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val downloadedMb = updateStatus.downloadedBytes / (1024f * 1024f)
                        val totalMb = updateStatus.totalBytes / (1024f * 1024f)
                        val progressLabel = if (updateStatus.totalBytes > 0) {
                            String.format("%.1f MB / %.1f MB (%d%%)", downloadedMb, totalMb, updateStatus.progressPercent)
                        } else {
                            String.format("%.1f MB downloaded", downloadedMb)
                        }

                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    is UpdateStatus.ReadyToInstall -> {
                        Text(
                            text = "Update downloaded and verified!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = AdGuardGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap below to install directly over the current app. No data or preferences will be lost.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onInstallClick(updateStatus.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdGuardGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}
