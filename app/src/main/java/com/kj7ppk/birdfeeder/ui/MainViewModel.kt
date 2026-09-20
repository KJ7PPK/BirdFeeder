package com.kj7ppk.birdfeeder.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kj7ppk.birdfeeder.audio.AudioStreamingService
import com.kj7ppk.birdfeeder.data.SettingsManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val settings = SettingsManager(application)

    val selectedAudioSource: StateFlow<Int> = settings.audioSource
    val selectedAudioCodec: StateFlow<String> = settings.audioCodec

    private val _isStreaming = MutableStateFlow(value = false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _audioLevels = MutableStateFlow(emptyList<Float>())
    val audioLevels: StateFlow<List<Float>> = _audioLevels.asStateFlow()

    private val _rtspUrl = MutableStateFlow("")
    val rtspUrl: StateFlow<String> = _rtspUrl.asStateFlow()

    private val _gain = MutableStateFlow(AudioStreamingService.currentGain)
    val gain: StateFlow<Float> = _gain.asStateFlow()

    private val _selectedMic = MutableStateFlow<AudioDeviceInfo?>(null)
    val selectedMic: StateFlow<AudioDeviceInfo?> = _selectedMic.asStateFlow()

    private val _availableMics = MutableStateFlow<List<AudioDeviceInfo?>>(listOf(null))
    val availableMics: StateFlow<List<AudioDeviceInfo?>> = _availableMics.asStateFlow()

    private val _isHomeLauncher = MutableStateFlow(value = false)
    val isHomeLauncher: StateFlow<Boolean> = _isHomeLauncher.asStateFlow()

    private val _isBatteryExempt = MutableStateFlow(value = false)
    val isBatteryExempt: StateFlow<Boolean> = _isBatteryExempt.asStateFlow()

    private var hasAutoStarted = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableMics()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateAvailableMics()
        }
    }

    @SuppressLint("StaticFieldLeak")
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
        updateSystemSettingsStatus(getApplication())
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (_: Exception) {}
        bindService()
        
        viewModelScope.launch {
            if (settings.isStreamingEnabled.value && !hasAutoStarted) {
                hasAutoStarted = true
                toggleStreaming(enabled = true)
            }
        }
    }

    fun updateSystemSettingsStatus(context: Context) {
        _isHomeLauncher.value = isDefaultLauncher(context)
        _isBatteryExempt.value = isIgnoringBatteryOptimizations(context)
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Exception) {
            false
        }
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
        }
    }

    fun updateAvailableMics() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val physicalMics = inputs.filter { 
            (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) || 
            (it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_DEVICE) ||
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) ||
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        }
        _availableMics.value = listOf(null) + physicalMics

        val currentSelected = _selectedMic.value
        if ((currentSelected != null) && physicalMics.none { it.id == currentSelected.id }) {
            setSelectedMic(null)
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if ((!addr.isLoopbackAddress) && (addr is InetAddress)) {
                        val ip = addr.hostAddress
                        if ((ip != null) && (ip.indexOf(':') < 0) && (ip != "0.0.0.0")) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun toggleStreaming(enabled: Boolean, context: Context? = null) {
        if (enabled) {
            val ip = getLocalIpAddress()
            if (ip == null) {
                settings.setStreamingEnabled(value = false)
                _isStreaming.value = false
                context?.let {
                    Toast.makeText(
                        it,
                        "No network IP address assigned. Connect to Wi-Fi or Ethernet to stream.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return
            }
            settings.setStreamingEnabled(value = true)
            AudioStreamingService.start(
                context = getApplication(),
                port = 8554,
                device = _selectedMic.value,
                audioSource = selectedAudioSource.value,
                audioCodec = selectedAudioCodec.value,
            )
        } else {
            settings.setStreamingEnabled(value = false)
            AudioStreamingService.stop(getApplication())
        }
    }

    fun setSelectedAudioSource(source: Int) {
        settings.setAudioSource(source)
        if (_isStreaming.value) {
            toggleStreaming(false)
            toggleStreaming(true)
        }
    }

    fun setSelectedAudioCodec(codec: String) {
        settings.setAudioCodec(codec)
        if (_isStreaming.value) {
            toggleStreaming(false)
            toggleStreaming(true)
        }
    }

    fun setGain(value: Float) {
        _gain.value = value
        AudioStreamingService.currentGain = value
    }

    fun setSelectedMic(device: AudioDeviceInfo?) {
        _selectedMic.value = device
        if (_isStreaming.value) {
            toggleStreaming(false)
            toggleStreaming(true)
        }
    }

    fun openHomeSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (_: Exception) {}
    }
}
