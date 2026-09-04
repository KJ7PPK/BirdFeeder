package com.kj7ppk.birdfeeder.ui.components

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
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
    autoStreamOnLaunch: Boolean,
    onAutoStreamChange: (Boolean) -> Unit,
    selectedAudioSource: Int,
    onAudioSourceChange: (Int) -> Unit,
    micSource: AudioDeviceInfo?,
    availableMics: List<AudioDeviceInfo?>,
    onMicChange: (AudioDeviceInfo?) -> Unit,
    port: Int,
    onPortChange: (Int) -> Unit,
    gain: Float,
    onGainChange: (Float) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier,
    audioSourceMode: String = ""
) {
    var micExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Stream Audio Switch & Auto-Stream Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Stream Audio", style = MaterialTheme.typography.titleMedium)
            }
            Switch(checked = isStreaming, onCheckedChange = onStreamingChange)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Auto-Stream on Boot / Launch", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = autoStreamOnLaunch, onCheckedChange = onAutoStreamChange)
        }

        // Hardware Boosted (MIC) vs Unprocessed Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedAudioSource == MediaRecorder.AudioSource.MIC,
                onClick = { onAudioSourceChange(MediaRecorder.AudioSource.MIC) },
                label = { Text("Hardware Boosted") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedAudioSource == MediaRecorder.AudioSource.UNPROCESSED,
                onClick = { onAudioSourceChange(MediaRecorder.AudioSource.UNPROCESSED) },
                label = { Text("Raw (Unprocessed)") },
                modifier = Modifier.weight(1f)
            )
        }

        // Action Buttons: Launcher & Battery Exemption
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onOpenHomeSettings,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Set Home Launcher", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onRequestBatteryExemption,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Never Kill (Battery Limit)", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Gain Adjuster
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Gain: ${(gain * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(90.dp)
            )
            Slider(
                value = gain,
                onValueChange = onGainChange,
                valueRange = 0.5f..20f,
                modifier = Modifier.weight(1f)
            )
        }

        // Mic Source & Port Compact Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ExposedDropdownMenuBox(
                expanded = micExpanded,
                onExpandedChange = { micExpanded = !micExpanded },
                modifier = Modifier.weight(1.5f)
            ) {
                OutlinedTextField(
                    value = micSource?.productName?.toString() ?: "All Available",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mic") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = micExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = micExpanded,
                    onDismissRequest = { micExpanded = false }
                ) {
                    availableMics.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device?.productName?.toString() ?: "All Available (Merged)") },
                            onClick = {
                                onMicChange(device)
                                micExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = port.toString(),
                onValueChange = { onPortChange(it.toIntOrNull() ?: port) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
