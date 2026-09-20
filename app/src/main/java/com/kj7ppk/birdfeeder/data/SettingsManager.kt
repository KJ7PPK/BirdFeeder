package com.kj7ppk.birdfeeder.data

import android.content.Context
import android.media.MediaRecorder
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

    companion object {
        const val CODEC_AAC_128 = "AAC_128"
        const val CODEC_AAC_96 = "AAC_96"
        const val CODEC_PCM_768 = "PCM_768"
    }
}
