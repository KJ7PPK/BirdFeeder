package com.kj7ppk.birdfeeder.data

import android.content.Context
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("birdfeeder_settings", Context.MODE_PRIVATE)

    private val _autoStreamOnLaunch = MutableStateFlow(prefs.getBoolean("auto_stream", true))
    val autoStreamOnLaunch: StateFlow<Boolean> = _autoStreamOnLaunch.asStateFlow()

    private val _audioSource = MutableStateFlow(prefs.getInt("audio_source", MediaRecorder.AudioSource.MIC))
    val audioSource: StateFlow<Int> = _audioSource.asStateFlow()

    fun setAutoStreamOnLaunch(value: Boolean) {
        _autoStreamOnLaunch.value = value
        prefs.edit().putBoolean("auto_stream", value).apply()
    }

    fun setAudioSource(value: Int) {
        _audioSource.value = value
        prefs.edit().putInt("audio_source", value).apply()
    }
}
