package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DnsProfile

@Composable
fun AddEditProfileDialog(
    initialProfile: DnsProfile? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, dohUrl: String, protocol: String) -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var urlOrHostname by remember { mutableStateOf(initialProfile?.dohUrl ?: "") }
    var protocol by remember { mutableStateOf(initialProfile?.protocol ?: "DOH") }
    var isNameError by remember { mutableStateOf(false) }
    var isUrlError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialProfile == null) "Add Custom DNS Profile" else "Edit DNS Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isNameError = false
                    },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. NextDNS Home") },
                    singleLine = true,
                    isError = isNameError,
                    supportingText = if (isNameError) { { Text("Name cannot be empty") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_name"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = urlOrHostname,
                    onValueChange = {
                        urlOrHostname = it
                        isUrlError = false
                    },
                    label = { Text("DoH URL or Hostname") },
                    placeholder = { Text("e.g. https://dns.nextdns.io/xyz") },
                    singleLine = true,
                    isError = isUrlError,
                    supportingText = if (isUrlError) { { Text("URL cannot be empty") } } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_url"),
                    shape = RoundedCornerShape(12.dp)
                )

                Column {
                    Text(
                        text = "Encrypted Protocol:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            RadioButton(
                                selected = protocol == "DOH",
                                onClick = { protocol = "DOH" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DNS-over-HTTPS (DoH)")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isNameError = true
                        return@Button
                    }
                    if (urlOrHostname.isBlank()) {
                        isUrlError = true
                        return@Button
                    }
                    onSave(name, urlOrHostname, protocol)
                },
                modifier = Modifier.testTag("dialog_save_profile_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
