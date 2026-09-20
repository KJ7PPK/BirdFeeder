package com.kj7ppk.birdfeeder.ui.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.MicrophoneInfo
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kj7ppk.birdfeeder.data.SettingsManager
import kotlin.math.abs

data class GainOption(
    val multiplier: Float,
    val label: String,
)

val availableGainOptions = listOf(
    GainOption(0.25f, "25% (-12 dB)"),
    GainOption(0.5f, "50% (-6 dB)"),
    GainOption(0.75f, "75% (-2.5 dB)"),
    GainOption(1.0f, "100% (Baseline / 0 dB)"),
    GainOption(1.5f, "150% (+3.5 dB)"),
    GainOption(2.0f, "200% (+6 dB)"),
    GainOption(3.0f, "300% (+9.5 dB)"),
    GainOption(4.0f, "400% (+12 dB)"),
    GainOption(5.0f, "500% (+14 dB)"),
    GainOption(10.0f, "1000% (+20 dB)"),
    GainOption(15.0f, "1500% (+23.5 dB)"),
    GainOption(20.0f, "2000% (+26 dB)"),
)

data class CodecOption(
    val codecKey: String,
    val title: String,
    val description: String,
)

val availableCodecOptions = listOf(
    CodecOption(
        codecKey = SettingsManager.CODEC_AAC_128,
        title = "AAC-LC (128 kbps)",
        description = "Recommended default. Hardware-accelerated, high audio fidelity, low network usage, minimal heat.",
    ),
    CodecOption(
        codecKey = SettingsManager.CODEC_AAC_96,
        title = "AAC-LC (96 kbps)",
        description = "Hardware-accelerated, ultra-low bandwidth (~45 MB/hr), reduced phone heat.",
    ),
    CodecOption(
        codecKey = SettingsManager.CODEC_PCM_768,
        title = "Uncompressed PCM (768 kbps)",
        description = "Lossless 768 kbps raw audio. Higher network bandwidth (~355 MB/hr) and device heat.",
    ),
)

data class AudioSourceOption(
    val sourceId: Int,
    val title: String,
    val description: String,
    val frequencyRange: String,
)

private fun getAvailableAudioSources(): List<AudioSourceOption> {
    val options = mutableListOf(
        AudioSourceOption(
            sourceId = MediaRecorder.AudioSource.MIC,
            title = "Basic AGC",
            description = "Standard microphone input with hardware pre-amp boost and automatic gain control.",
            frequencyRange = "300 Hz – 8 kHz",
        ),
        AudioSourceOption(
            sourceId = MediaRecorder.AudioSource.UNPROCESSED,
            title = "Raw (Unprocessed)",
            description = "Direct ADC signal. Bypasses hardware gain boost, echo cancellation, and noise reduction.",
            frequencyRange = "10 Hz – 24 kHz",
        ),
        AudioSourceOption(
            sourceId = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            title = "Voice Recognition",
            description = "Tuned for speech & AI recognition models with minimal dynamic range compression.",
            frequencyRange = "100 Hz – 12 kHz",
        ),
        AudioSourceOption(
            sourceId = MediaRecorder.AudioSource.CAMCORDER,
            title = "Camcorder",
            description = "Tuned for video recording. Uses directional or rear microphones when available.",
            frequencyRange = "150 Hz – 10 kHz",
        ),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        options.add(
            AudioSourceOption(
                sourceId = MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                title = "Voice Performance",
                description = "Low-latency, high-dynamic-range capture for live acoustic sounds and instruments.",
                frequencyRange = "20 Hz – 20 kHz",
            ),
        )
    }
    return options
}

fun getMicDisplayName(device: AudioDeviceInfo?, audioManager: AudioManager? = null): String {
    if (device == null) return "Combined Mics"

    val typeName = when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5mm Headset/Mic"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Mic"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Mic"
        else -> "External Mic"
    }

    if (device.type != AudioDeviceInfo.TYPE_BUILTIN_MIC) {
        return typeName
    }

    val locationDetail = getMicLocationDetails(device, audioManager)
    return if (locationDetail.isNotBlank()) {
        "$typeName - $locationDetail"
    } else {
        typeName
    }
}

private fun getMicLocationDetails(device: AudioDeviceInfo, audioManager: AudioManager?): String {
    val details = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val addr = device.address.trim().lowercase()
        if (addr.isNotEmpty()) {
            when {
                addr.contains("back") || addr.contains("rear") -> details.add("Back")
                addr.contains("front") -> details.add("Front")
                addr.contains("top") || addr.contains("upper") -> details.add("Top")
                addr.contains("bottom") || addr.contains("lower") -> details.add("Bottom")
                else -> details.add(addr.replaceFirstChar { it.uppercase() })
            }
        }

        if (audioManager != null) {
            try {
                val mics = audioManager.microphones
                val matchingMic = mics.find { mic ->
                    (mic.id == device.id) || (mic.type == device.type && mic.address == device.address)
                }
                if (matchingMic != null) {
                    val micAddr = matchingMic.address.trim().lowercase()
                    if (details.isEmpty() && micAddr.isNotEmpty()) {
                        when {
                            micAddr.contains("back") || micAddr.contains("rear") -> details.add("Back")
                            micAddr.contains("front") -> details.add("Front")
                            micAddr.contains("top") || micAddr.contains("upper") -> details.add("Top")
                            micAddr.contains("bottom") || micAddr.contains("lower") -> details.add("Bottom")
                            else -> details.add(micAddr.replaceFirstChar { it.uppercase() })
                        }
                    }

                    val pos = matchingMic.position
                    if (pos != MicrophoneInfo.POSITION_UNKNOWN) {
                        if (details.none { it.equals("Top", ignoreCase = true) || it.equals("Bottom", ignoreCase = true) }) {
                            if (pos.y > 0.01f) details.add("Top")
                            else if (pos.y < -0.01f) details.add("Bottom")
                        }
                        if (details.none { it.equals("Front", ignoreCase = true) || it.equals("Back", ignoreCase = true) }) {
                            if (pos.z > 0.01f) details.add("Front")
                            else if (pos.z < -0.01f) details.add("Back")
                        }
                    }

                    if (matchingMic.location == MicrophoneInfo.LOCATION_PERIPHERAL) {
                        details.add("Peripheral")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    return details.distinct().joinToString("/")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsSection(
    isStreaming: Boolean,
    onStreamingChange: (Boolean) -> Unit,
    selectedAudioSource: Int,
    onAudioSourceChange: (Int) -> Unit,
    selectedAudioCodec: String,
    onAudioCodecChange: (String) -> Unit,
    micSource: AudioDeviceInfo?,
    availableMics: List<AudioDeviceInfo?>,
    onMicChange: (AudioDeviceInfo?) -> Unit,
    gain: Float,
    onGainChange: (Float) -> Unit,
    isHomeLauncher: Boolean,
    onOpenHomeSettings: () -> Unit,
    isBatteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var micExpanded by remember { mutableStateOf(false) }
    var audioSourceExpanded by remember { mutableStateOf(false) }
    var audioCodecExpanded by remember { mutableStateOf(false) }
    var gainExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    val availableAudioSources = remember { getAvailableAudioSources() }
    val currentAudioOption = remember(selectedAudioSource) {
        availableAudioSources.find { it.sourceId == selectedAudioSource } ?: availableAudioSources.first()
    }
    val currentCodecOption = remember(selectedAudioCodec) {
        availableCodecOptions.find { it.codecKey == selectedAudioCodec } ?: availableCodecOptions.first()
    }
    val currentGainOption = remember(gain) {
        availableGainOptions.find { abs(it.multiplier - gain) < 0.05f }
            ?: GainOption(gain, "${(gain * 100).toInt()}%")
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Stream Audio Switch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Stream Audio", style = MaterialTheme.typography.titleMedium)
            }
            Switch(checked = isStreaming, onCheckedChange = onStreamingChange)
        }

        // System Appliance Toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Set Home Launcher", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = isHomeLauncher,
                onCheckedChange = { onOpenHomeSettings() },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Protect from Battery Saver", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = isBatteryExempt,
                onCheckedChange = { onRequestBatteryExemption() },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Audio Compression Format Dropdown
        ExposedDropdownMenuBox(
            expanded = audioCodecExpanded,
            onExpandedChange = { audioCodecExpanded = !audioCodecExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = currentCodecOption.title,
                onValueChange = {},
                readOnly = true,
                label = { Text("Audio Compression Format") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audioCodecExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = audioCodecExpanded,
                onDismissRequest = { audioCodecExpanded = false },
            ) {
                availableCodecOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text = option.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onAudioCodecChange(option.codecKey)
                            audioCodecExpanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Audio Source Selector
        ExposedDropdownMenuBox(
            expanded = audioSourceExpanded,
            onExpandedChange = { audioSourceExpanded = !audioSourceExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = currentAudioOption.title,
                onValueChange = {},
                readOnly = true,
                label = { Text("Audio Processing Mode") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = audioSourceExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = audioSourceExpanded,
                onDismissRequest = { audioSourceExpanded = false },
            ) {
                availableAudioSources.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = option.frequencyRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onAudioSourceChange(option.sourceId)
                            audioSourceExpanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Digital Gain Dropdown
        ExposedDropdownMenuBox(
            expanded = gainExpanded,
            onExpandedChange = { gainExpanded = !gainExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = currentGainOption.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Digital Gain") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gainExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = gainExpanded,
                onDismissRequest = { gainExpanded = false },
            ) {
                availableGainOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onGainChange(option.multiplier)
                            gainExpanded = false
                        },
                    )
                }
            }
        }

        // Mic Selector (Full Width)
        ExposedDropdownMenuBox(
            expanded = micExpanded,
            onExpandedChange = { micExpanded = !micExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = getMicDisplayName(micSource, audioManager),
                onValueChange = {},
                readOnly = true,
                label = { Text("Microphone") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = micExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = micExpanded,
                onDismissRequest = { micExpanded = false },
            ) {
                availableMics.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(getMicDisplayName(device, audioManager)) },
                        onClick = {
                            onMicChange(device)
                            micExpanded = false
                        },
                    )
                }
            }
        }
    }
}
