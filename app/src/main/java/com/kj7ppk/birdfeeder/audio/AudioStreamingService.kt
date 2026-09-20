package com.kj7ppk.birdfeeder.audio

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.kj7ppk.birdfeeder.MainActivity
import com.kj7ppk.birdfeeder.R
import com.kj7ppk.birdfeeder.data.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Foreground service that captures raw PCM audio from selected microphone(s),
 * downmixes to mono, applies gain, and broadcasts via RTSP.
 * Supports hardware AAC-LC encoding as well as uncompressed L16 PCM.
 */
class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    
    private var rtspServer: SimpleRtspServer? = null
    
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    
    @Volatile
    private var gain: Float = 1.0f

    private val _isStreaming = MutableStateFlow(value = false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _audioLevels = MutableStateFlow(emptyList<Float>())
    val audioLevels: StateFlow<List<Float>> = _audioLevels.asStateFlow()

    private val _rtspUrl = MutableStateFlow("")
    val rtspUrl: StateFlow<String> = _rtspUrl.asStateFlow()

    private val _audioSourceMode = MutableStateFlow("Scanning...")
    private val _foundMicIds = MutableStateFlow(emptyList<Int>())

    private var preferredDevice: AudioDeviceInfo? = null
    private var streamingPort: Int = PORT
    private var selectedAudioSource: Int = MediaRecorder.AudioSource.MIC
    private var selectedAudioCodec: String = SettingsManager.CODEC_AAC_128

    private var mediaCodec: MediaCodec? = null
    private var totalSampleCount = 0L
    private val levelHistory = mutableListOf<Float>()

    companion object {
        private const val TAG = "AudioStreamingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_streaming_channel"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val PORT = 8554
        
        /**
         * Expose current gain to be controlled via UI.
         */
        var currentGain: Float = 1.0f
            set(value) {
                field = value
                instance?.gain = value
            }
            
        private var instance: AudioStreamingService? = null

        fun start(
            context: Context,
            port: Int = PORT,
            device: AudioDeviceInfo? = null,
            audioSource: Int = MediaRecorder.AudioSource.MIC,
            audioCodec: String = SettingsManager.CODEC_AAC_128,
        ) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                action = "ACTION_START"
                putExtra("PORT", port)
                putExtra("AUDIO_SOURCE", audioSource)
                putExtra("AUDIO_CODEC", audioCodec)
                device?.let { putExtra("DEVICE_ID", it.id) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                action = "ACTION_STOP"
            }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingService = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        try {
            if (networkCallback != null) return
            connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val ip = getLocalIpAddress()
                    if (ip != null) {
                        updateRtspUrl()
                        if (!isRunning.get() && SettingsManager(this@AudioStreamingService).isStreamingEnabled.value) {
                            startStreaming()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val ip = getLocalIpAddress()
                    if (ip == null) {
                        pauseStreamingForNoNetwork()
                    } else {
                        updateRtspUrl()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ip = getLocalIpAddress()
                    if (ip == null) {
                        if (isRunning.get()) {
                            pauseStreamingForNoNetwork()
                        }
                    } else {
                        updateRtspUrl()
                        if (!isRunning.get() && SettingsManager(this@AudioStreamingService).isStreamingEnabled.value) {
                            startStreaming()
                        }
                    }
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Error registering network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    fun updateRtspUrl() {
        _rtspUrl.value = getRtspUrl()
    }

    private fun pauseStreamingForNoNetwork() {
        synchronized(this) {
            if (!isRunning.get()) return
            isRunning.set(false)
            audioThread?.interrupt()
            try { audioThread?.join(500) } catch (_: Exception) {}
            audioThread = null
            stopAacCodec()
            rtspServer?.stop()
            rtspServer = null
            _isStreaming.value = false
            _audioLevels.value = emptyList()
            _rtspUrl.value = ""
            Log.w(TAG, "Streaming paused: Network IP disconnected")
        }
    }

    fun stopStreaming() {
        synchronized(this) {
            if (!isRunning.get()) return
            isRunning.set(false)
            audioThread?.interrupt()
            try { audioThread?.join(500) } catch (_: Exception) {}
            audioThread = null
            stopAacCodec()
            rtspServer?.stop()
            rtspServer = null
            _isStreaming.value = false
            _audioLevels.value = emptyList()
            unregisterNetworkCallback()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        updateEngineStatus()
    }

    private fun updateEngineStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        _audioSourceMode.value = when (selectedAudioSource) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED (Raw)"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
            else -> if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) && (selectedAudioSource == MediaRecorder.AudioSource.VOICE_PERFORMANCE)) {
                "VOICE_PERFORMANCE"
            } else {
                "Basic AGC"
            }
        }
        
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val physicalMics = inputDevices.filter { 
            (it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) || 
            (it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_DEVICE) ||
            (it.type == AudioDeviceInfo.TYPE_USB_HEADSET) ||
            (it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) ||
            (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        }
        _foundMicIds.value = physicalMics.map { it.id }
    }

    private fun restartStreamPipeline(newSource: Int, newCodec: String, newDevice: AudioDeviceInfo?) {
        synchronized(this) {
            // Stop capture thread
            isRunning.set(false)
            audioThread?.interrupt()
            try { audioThread?.join(500) } catch (_: Exception) {}
            audioThread = null

            // Stop AAC codec
            stopAacCodec()

            // Update parameters
            selectedAudioSource = newSource
            selectedAudioCodec = newCodec
            preferredDevice = newDevice
            updateEngineStatus()

            // Restart RTSP server
            rtspServer?.stop()
            rtspServer = SimpleRtspServer(streamingPort, selectedAudioCodec)
            rtspServer?.start()

            // Re-init AAC codec if AAC
            if (selectedAudioCodec != SettingsManager.CODEC_PCM_768) {
                initAacCodec(selectedAudioCodec)
            }

            // Start capture thread
            isRunning.set(true)
            _isStreaming.value = true
            updateRtspUrl()
            audioThread = Thread({ captureAndStream() }, "AudioCaptureThread")
            audioThread?.start()
            Log.i(TAG, "Stream pipeline restarted smoothly ($selectedAudioCodec, Source: $selectedAudioSource)")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP") {
            stopStreaming()
            return START_NOT_STICKY
        }

        streamingPort = intent?.getIntExtra("PORT", PORT) ?: PORT
        val newAudioSource = intent?.getIntExtra("AUDIO_SOURCE", MediaRecorder.AudioSource.MIC) ?: MediaRecorder.AudioSource.MIC
        val newAudioCodec = intent?.getStringExtra("AUDIO_CODEC") ?: SettingsManager.CODEC_AAC_128

        val deviceId = intent?.getIntExtra("DEVICE_ID", -1) ?: -1
        val newDevice = if (deviceId != -1) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).find { it.id == deviceId }
        } else {
            null // Combined Mics
        }

        if (isRunning.get()) {
            restartStreamPipeline(newAudioSource, newAudioCodec, newDevice)
            return START_STICKY
        }

        selectedAudioSource = newAudioSource
        selectedAudioCodec = newAudioCodec
        preferredDevice = newDevice
        
        updateEngineStatus()

        try {
            val notification = createNotification()
            val hasRecordAudio = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && hasRecordAudio) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startStreaming()
        } else {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            stopSelf()
        }
        
        return START_STICKY
    }

    private fun initAacCodec(codecKey: String): Boolean {
        val bitrate = if (codecKey == SettingsManager.CODEC_AAC_96) 96000 else 128000
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            mediaCodec = codec
            totalSampleCount = 0L
            Log.i(TAG, "Initialized MediaCodec AAC encoder ($codecKey @ $bitrate bps)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaCodec AAC encoder", e)
            false
        }
    }

    private fun stopAacCodec() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null
    }

    private fun encodeAndSendAac(shortBuffer: ShortArray, readCount: Int, server: SimpleRtspServer) {
        val codec = mediaCodec ?: return
        if (!isRunning.get()) return

        try {
            val pcmBytes = ByteArray(readCount * 2)
            ByteBuffer.wrap(pcmBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .put(shortBuffer, 0, readCount)

            val inputIndex = codec.dequeueInputBuffer(10000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(pcmBytes)
                    val pts = (totalSampleCount * 1_000_000L) / SAMPLE_RATE
                    codec.queueInputBuffer(inputIndex, 0, pcmBytes.size, pts, 0)
                }
            }

            totalSampleCount += readCount

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while ((outputIndex >= 0) && isRunning.get()) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if ((outputBuffer != null) && ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) && (bufferInfo.size > 0)) {
                    val aacData = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(aacData)
                    server.sendAacAudio(aacData, bufferInfo.size, readCount)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during AAC encoding", e)
        }
    }

    private fun startStreaming() {
        val currentIp = getLocalIpAddress()
        if (currentIp == null) {
            Log.w(TAG, "Cannot start streaming: No valid network IP address found")
            _isStreaming.value = false
            _rtspUrl.value = ""
            registerNetworkCallback()
            return
        }

        if (isRunning.get()) return
        isRunning.set(true)
        _isStreaming.value = true

        registerNetworkCallback()

        if (selectedAudioCodec != SettingsManager.CODEC_PCM_768) {
            initAacCodec(selectedAudioCodec)
        }

        // Initialize Simple RTSP Server
        rtspServer = SimpleRtspServer(streamingPort, selectedAudioCodec)
        rtspServer?.start()

        updateRtspUrl()

        audioThread = Thread({ captureAndStream() }, "AudioCaptureThread")
        audioThread?.start()

        Log.i(TAG, "RTSP Server started on port $streamingPort ($selectedAudioCodec - ${getRtspUrl()})")
    }

    private fun getRtspUrl(): String {
        val ip = getLocalIpAddress() ?: return ""
        return "rtsp://$ip:$streamingPort/live"
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

    private fun captureAndStream() {
        val records = mutableListOf<AudioRecord>()
        
        val sourcesToTry = listOf(
            selectedAudioSource,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        ).distinct()

        for (source in sourcesToTry) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) break
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minBufferSize * 2, 8192))
                    .build()
                
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    if (preferredDevice != null) {
                        record.preferredDevice = preferredDevice
                    }
                    record.startRecording()
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        disableAudioEffects(record.audioSessionId)
                        records.add(record)
                        Log.d(TAG, "Started recording with source $source on device: ${preferredDevice?.productName ?: "Default"}")
                        break
                    } else {
                        record.release()
                    }
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start AudioRecord with source $source", e)
            }
        }
        
        if (records.isEmpty()) {
            Log.e(TAG, "No active AudioRecord initialized")
            isRunning.set(false)
            _isStreaming.value = false
            stopSelf()
            return
        }

        // 1024 PCM samples per frame
        val frameSamples = 1024
        val shortBuffer = ShortArray(frameSamples)

        while (isRunning.get()) {
            val record = records[0]
            val readCount = record.read(shortBuffer, 0, frameSamples)
            if (readCount <= 0) {
                Log.w(TAG, "AudioRecord read returned $readCount")
                try { Thread.sleep(5) } catch (_: InterruptedException) {}
                continue
            }
            
            var maxAmplitude = 0f
            for (j in 0 until readCount) {
                val sampleWithGain = shortBuffer[j] * gain
                val clampedSample = max(Short.MIN_VALUE.toFloat(), min(Short.MAX_VALUE.toFloat(), sampleWithGain)).toInt().toShort()
                shortBuffer[j] = clampedSample
                maxAmplitude = max(maxAmplitude, abs(clampedSample.toFloat()))
            }
            
            // Update visualizer StateFlow
            val normalizedLevel = min(1.0f, maxAmplitude / Short.MAX_VALUE.toFloat())
            updateAudioLevels(normalizedLevel)
            
            val server = rtspServer ?: continue
            if (selectedAudioCodec == SettingsManager.CODEC_PCM_768) {
                server.sendPcmAudio(shortBuffer, readCount)
            } else {
                encodeAndSendAac(shortBuffer, readCount, server)
            }
        }
        
        for (record in records) {
            try {
                record.stop()
                record.release()
            } catch (_: Exception) {}
        }

        stopAacCodec()
    }

    private fun disableAudioEffects(audioSessionId: Int) {
        try {
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = false
                    release()
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = false
                    release()
                }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = false
                    release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable audio effects", e)
        }
    }

    private fun updateAudioLevels(level: Float) {
        synchronized(levelHistory) {
            levelHistory.add(level)
            if (levelHistory.size > 60) { // Slightly longer history for smoother visualizer
                levelHistory.removeAt(0)
            }
            _audioLevels.value = levelHistory.toList()
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Audio Streaming Service Channel",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BirdFeeder Audio Streaming")
            .setContentText("Broadcasting audio on port $streamingPort")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        stopStreaming()
        instance = null
        super.onDestroy()
    }
}
