package com.kj7ppk.birdfeeder.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("birdfeeder_settings", Context.MODE_PRIVATE)

    private val _isStreamingEnabled = MutableStateFlow(prefs.getBoolean("is_streaming_enabled", true))
    val isStreamingEnabled: StateFlow<Boolean> = _isStreamingEnabled.asStateFlow()

    private val _audioSource = MutableStateFlow(prefs.getInt("audio_source", MediaRecorder.AudioSource.MIC))
    val audioSource: StateFlow<Int> = _audioSource.asStateFlow()

    private val _audioCodec = MutableStateFlow(prefs.getString("audio_codec", CODEC_AAC_128) ?: CODEC_AAC_128)
    val audioCodec: StateFlow<String> = _audioCodec.asStateFlow()

    private val _audioGain = MutableStateFlow(prefs.getFloat("audio_gain", 1.0f))
    val audioGain: StateFlow<Float> = _audioGain.asStateFlow()

    private val _selectedMicType = MutableStateFlow(prefs.getInt("selected_mic_type", -1))
    private val _selectedMicAddress = MutableStateFlow(prefs.getString("selected_mic_address", "") ?: "")
    private val _selectedMicId = MutableStateFlow(prefs.getInt("selected_mic_id", -1))

    fun setStreamingEnabled(value: Boolean) {
        _isStreamingEnabled.value = value
        prefs.edit { putBoolean("is_streaming_enabled", value) }
    }

    fun setAudioSource(value: Int) {
        _audioSource.value = value
        prefs.edit { putInt("audio_source", value) }
    }

    fun setAudioCodec(value: String) {
        _audioCodec.value = value
        prefs.edit { putString("audio_codec", value) }
    }

    fun setAudioGain(value: Float) {
        _audioGain.value = value
        prefs.edit { putFloat("audio_gain", value) }
    }

    fun setSelectedMic(device: AudioDeviceInfo?) {
        if (device == null) {
            _selectedMicType.value = -1
            _selectedMicAddress.value = ""
            _selectedMicId.value = -1
            prefs.edit {
                putInt("selected_mic_type", -1)
                putString("selected_mic_address", "")
                putInt("selected_mic_id", -1)
            }
        } else {
            val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.address else ""
            _selectedMicType.value = device.type
            _selectedMicAddress.value = addr
            _selectedMicId.value = device.id
            prefs.edit {
                putInt("selected_mic_type", device.type)
                putString("selected_mic_address", addr)
                putInt("selected_mic_id", device.id)
            }
        }
    }

    fun findSavedMicrophone(audioManager: AudioManager): AudioDeviceInfo? {
        val savedType = _selectedMicType.value
        if (savedType == -1) return null

        val savedAddr = _selectedMicAddress.value
        val savedId = _selectedMicId.value

        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val physicalMics = inputs.filter { 
            (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) || 
            (it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_DEVICE) ||
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) ||
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        }

        // 1. Try matching type and address (API 28+)
        if (savedAddr.isNotEmpty() && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)) {
            physicalMics.find { (it.type == savedType) && (it.address == savedAddr) }?.let { return it }
        }

        // 2. Try matching runtime device ID
        if (savedId != -1) {
            physicalMics.find { it.id == savedId }?.let { return it }
        }

        // 3. Fallback: match by type if unique
        val sameTypeMics = physicalMics.filter { it.type == savedType }
        if (sameTypeMics.size == 1) {
            return sameTypeMics.first()
        }

        return null
    }

    companion object {
        const val CODEC_AAC_128 = "AAC_128"
        const val CODEC_AAC_96 = "AAC_96"
        const val CODEC_PCM_768 = "PCM_768"
    }
}
