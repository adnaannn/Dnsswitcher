package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun BatteryOptimizationCard(
    isIgnored: Boolean,
    onRequestWhitelist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_optimization_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isIgnored) Color(0xFF0F261E).copy(alpha = 0.70f) else Color(0xFF241C0F).copy(alpha = 0.70f)
        ),
        border = BorderStroke(
            1.dp,
            if (isIgnored) AdGuardGreen.copy(alpha = 0.40f) else Color(0xFFF59E0B).copy(alpha = 0.40f)
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isIgnored) AdGuardGreen.copy(alpha = 0.20f) else Color(0xFFF59E0B).copy(alpha = 0.20f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isIgnored) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isIgnored) AdGuardGreen else Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isIgnored) "BATTERY: UNRESTRICTED" else "BATTERY OPTIMIZATION ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            ),
                            color = if (isIgnored) AdGuardGreen else Color(0xFFFBBF24)
                        )
                        Text(
                            text = if (isIgnored) "Tile & VPN Run Reliably" else "Quick Settings tile may lag",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isIgnored) AdGuardGreen.copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isIgnored) "Optimal" else "Action Needed",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isIgnored) AdGuardGreen else Color(0xFFFBBF24),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!isIgnored) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Android may pause background network routing when screen is locked. Whitelist app for instant Quick Settings responses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestWhitelist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whitelist_battery_card_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Whitelist App from Battery Optimization", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
