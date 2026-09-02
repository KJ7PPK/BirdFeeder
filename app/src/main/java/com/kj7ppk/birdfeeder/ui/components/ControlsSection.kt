package com.kj7ppk.birdfeeder.ui.components

import android.media.AudioDeviceInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsSection(
    isStreaming: Boolean,
    onStreamingChange: (Boolean) -> Unit,
    micSource: AudioDeviceInfo?,
    availableMics: List<AudioDeviceInfo?>,
    onMicChange: (AudioDeviceInfo?) -> Unit,
    port: Int,
    onPortChange: (Int) -> Unit,
    gain: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    audioSourceMode: String = "",
    foundMicIds: List<Int> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = "Controls", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Stream Audio Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Stream Audio", modifier = Modifier.weight(1f))
            Switch(checked = isStreaming, onCheckedChange = onStreamingChange)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mic Source Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = micSource?.productName?.toString() ?: "All Available (Merged)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Mic Source") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableMics.forEach { device ->
                    if (device == null) {
                        DropdownMenuItem(
                            text = { Text("All Available (Merged)") },
                            onClick = {
                                onMicChange(null)
                                expanded = false
                            }
                        )
                    } else {
                        val typeString = when (device.type) {
                            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
                            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                            else -> "External/Other"
                        }
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(text = "ID ${device.id}: $typeString")
                                    Text(
                                        text = device.productName?.toString() ?: "Unknown Device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onMicChange(device)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Port Field
        OutlinedTextField(
            value = port.toString(),
            onValueChange = { onPortChange(it.toIntOrNull() ?: port) },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Gain Adjuster
        Text(text = "Gain Adjuster: ${(gain * 100).toInt()}%")
        Slider(
            value = gain,
            onValueChange = onGainChange,
            valueRange = 0f..5f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Reporting
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Mode: $audioSourceMode", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Active Physical Mics: ${foundMicIds.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
