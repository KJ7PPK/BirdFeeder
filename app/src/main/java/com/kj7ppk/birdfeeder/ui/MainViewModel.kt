package com.kj7ppk.birdfeeder.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kj7ppk.birdfeeder.audio.AudioStreamingService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _audioLevels = MutableStateFlow(emptyList<Float>())
    val audioLevels: StateFlow<List<Float>> = _audioLevels.asStateFlow()

    private val _rtspUrl = MutableStateFlow("")
    val rtspUrl: StateFlow<String> = _rtspUrl.asStateFlow()

    private val _audioSourceMode = MutableStateFlow("")
    val audioSourceMode: StateFlow<String> = _audioSourceMode.asStateFlow()

    private val _foundMicIds = MutableStateFlow(emptyList<Int>())
    val foundMicIds: StateFlow<List<Int>> = _foundMicIds.asStateFlow()

    private val _gain = MutableStateFlow(AudioStreamingService.currentGain)
    val gain: StateFlow<Float> = _gain.asStateFlow()

    private val _port = MutableStateFlow(8554)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _selectedMic = MutableStateFlow<AudioDeviceInfo?>(null)
    val selectedMic: StateFlow<AudioDeviceInfo?> = _selectedMic.asStateFlow()

    private val _availableMics = MutableStateFlow<List<AudioDeviceInfo?>>(listOf(null))
    val availableMics: StateFlow<List<AudioDeviceInfo?>> = _availableMics.asStateFlow()

    private var service: AudioStreamingService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioStreamingService.LocalBinder
            service = localBinder?.getService()
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    init {
        updateAvailableMics()
        bindService()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), AudioStreamingService::class.java)
        getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun observeService() {
        service?.let { s ->
            viewModelScope.launch {
                s.isStreaming.collect { _isStreaming.value = it }
            }
            viewModelScope.launch {
                s.audioLevels.collect { _audioLevels.value = it }
            }
            viewModelScope.launch {
                s.rtspUrl.collect { _rtspUrl.value = it }
            }
            viewModelScope.launch {
                s.audioSourceMode.collect { _audioSourceMode.value = it }
            }
            viewModelScope.launch {
                s.foundMicIds.collect { _foundMicIds.value = it }
            }
        }
    }

    fun updateAvailableMics() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val physicalMics = inputs.filter { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || 
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        _availableMics.value = listOf(null) + physicalMics
    }

    fun toggleStreaming(enabled: Boolean) {
        if (enabled) {
            AudioStreamingService.start(getApplication(), _port.value, _selectedMic.value)
        } else {
            AudioStreamingService.stop(getApplication())
        }
    }

    fun setGain(value: Float) {
        _gain.value = value
        AudioStreamingService.currentGain = value
    }

    fun setPort(value: Int) {
        _port.value = value
    }

    fun setSelectedMic(device: AudioDeviceInfo?) {
        _selectedMic.value = device
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unbindService(connection)
    }
}
