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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.kj7ppk.birdfeeder.MainActivity
import com.kj7ppk.birdfeeder.R
import com.pedro.common.ConnectChecker
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.AudioEncoder
import com.pedro.encoder.audio.GetAudioData
import com.pedro.rtspserver.server.RtspServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Foreground service that captures raw PCM audio from selected microphone(s),
 * downmixes to mono, applies gain, and broadcasts via RTSP.
 * Refactored to use AudioEncoder and RtspServer for audio-only support.
 */
class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    
    private var rtspServer: RtspServer? = null
    private var audioEncoder: AudioEncoder? = null
    
    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null
    
    @Volatile
    private var gain: Float = 1.0f

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _audioLevels = MutableStateFlow(emptyList<Float>())
    val audioLevels: StateFlow<List<Float>> = _audioLevels.asStateFlow()

    private val _rtspUrl = MutableStateFlow("")
    val rtspUrl: StateFlow<String> = _rtspUrl.asStateFlow()

    private val _audioSourceMode = MutableStateFlow("Scanning...")
    val audioSourceMode: StateFlow<String> = _audioSourceMode.asStateFlow()

    private val _foundMicIds = MutableStateFlow(emptyList<Int>())
    val foundMicIds: StateFlow<List<Int>> = _foundMicIds.asStateFlow()

    private var preferredDevice: AudioDeviceInfo? = null
    private var streamingPort: Int = PORT

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

        fun start(context: Context, port: Int = PORT, device: AudioDeviceInfo? = null) {
            val intent = Intent(context, AudioStreamingService::class.java).apply {
                putExtra("PORT", port)
                device?.let { putExtra("DEVICE_ID", it.id) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioStreamingService::class.java)
            context.stopService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingService = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        updateEngineStatus()
    }

    private fun updateEngineStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        _audioSourceMode.value = if (getBestAudioSource(audioManager) == MediaRecorder.AudioSource.UNPROCESSED) "UNPROCESSED" else "VOICE_RECOGNITION"
        
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val physicalMics = inputDevices.filter { 
            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC || 
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        _foundMicIds.value = physicalMics.map { it.id }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        streamingPort = intent?.getIntExtra("PORT", PORT) ?: PORT
        val deviceId = intent?.getIntExtra("DEVICE_ID", -1) ?: -1
        if (deviceId != -1) {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            preferredDevice = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).find { it.id == deviceId }
        } else {
            preferredDevice = null // All Available
        }
        
        updateEngineStatus()

        try {
            val notification = createNotification()
            val hasRecordAudio = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasRecordAudio) {
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

    private fun startStreaming() {
        if (isRunning.get()) return
        isRunning.set(true)
        _isStreaming.value = true
        
        val connectChecker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) { Log.d(TAG, "RTSP Connection started: $url") }
            override fun onConnectionSuccess() { Log.d(TAG, "RTSP Connection success") }
            override fun onConnectionFailed(reason: String) { Log.e(TAG, "RTSP Connection failed: $reason") }
            override fun onNewBitrate(bitrate: Long) {}
            override fun onDisconnect() { Log.d(TAG, "RTSP Disconnected") }
            override fun onAuthError() { Log.e(TAG, "RTSP Auth error") }
            override fun onAuthSuccess() { Log.d(TAG, "RTSP Auth success") }
        }

        // Initialize RTSP Server
        rtspServer = RtspServer(connectChecker, streamingPort)
        rtspServer?.setOnlyAudio(true)
        rtspServer?.setAudioInfo(SAMPLE_RATE, false) // 48kHz, mono
        
        // Start RTSP Server FIRST before sending codec config
        rtspServer?.startServer()
        
        // Pre-populate AAC AudioSpecificConfig for 48kHz mono AAC-LC (0x11, 0x88) AFTER startServer()
        val aacConfig = byteArrayOf(0x11.toByte(), 0x88.toByte())
        val configBuffer = ByteBuffer.wrap(aacConfig)
        val configInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = aacConfig.size
            presentationTimeUs = 0
            flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
        }
        rtspServer?.sendAudio(configBuffer, configInfo)
        
        // Initialize Audio Encoder
        audioEncoder = AudioEncoder(object : GetAudioData {
            override fun getAudioData(audioBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
                try {
                    audioBuffer.position(info.offset)
                    audioBuffer.limit(info.offset + info.size)
                    rtspServer?.sendAudio(audioBuffer, info)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sendAudio", e)
                }
            }

            override fun onAudioFormat(mediaFormat: MediaFormat) {
                val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                rtspServer?.setAudioInfo(sampleRate, channelCount > 1)
                
                try {
                    val csd0 = mediaFormat.getByteBuffer("csd-0")
                    if (csd0 != null) {
                        val csdInfo = MediaCodec.BufferInfo().apply {
                            offset = 0
                            size = csd0.remaining()
                            presentationTimeUs = 0
                            flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        }
                        rtspServer?.sendAudio(csd0, csdInfo)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to extract csd-0 from media format", e)
                }
            }
        })
        
        // Use 64kbps bitrate for Mono AAC (128kbps can fail on some device encoders for mono)
        var prepared = audioEncoder?.prepareAudioEncoder(64 * 1024, SAMPLE_RATE, false) ?: false
        if (!prepared) {
            Log.w(TAG, "Failed 64kbps, trying 96kbps AudioEncoder preparation...")
            prepared = audioEncoder?.prepareAudioEncoder(96 * 1024, SAMPLE_RATE, false) ?: false
        }

        if (prepared) {
            audioEncoder?.start(true)
            _rtspUrl.value = getRtspUrl()
            
            audioThread = Thread({ captureAndStream() }, "AudioCaptureThread")
            audioThread?.start()
            
            Log.i(TAG, "Audio-only RTSP Server started on port $streamingPort (${getRtspUrl()})")
        } else {
            Log.e(TAG, "Failed to prepare AudioEncoder")
            isRunning.set(false)
            _isStreaming.value = false
            _audioLevels.value = emptyList()
            rtspServer?.stopServer()
        }
    }

    private fun getRtspUrl(): String {
        val ip = rtspServer?.serverIp ?: getLocalIpAddress() ?: "0.0.0.0"
        return "rtsp://$ip:$streamingPort/live"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && ip.indexOf(':') < 0) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun captureAndStream() {
        val records = mutableListOf<AudioRecord>()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        val sourcesToTry = listOf(
            getBestAudioSource(audioManager),
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        ).distinct()

        for (source in sourcesToTry) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) break
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val record = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
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

        // 1024 PCM samples per AAC frame (16-bit mono = 2048 bytes)
        val frameSamples = 1024
        val shortBuffer = ShortArray(frameSamples)
        val byteBuffer = ByteArray(frameSamples * 2)

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
                
                maxAmplitude = max(maxAmplitude, abs(clampedSample.toFloat()))
                
                byteBuffer[j * 2] = (clampedSample.toInt() and 0xFF).toByte()
                byteBuffer[j * 2 + 1] = ((clampedSample.toInt() shr 8) and 0xFF).toByte()
            }
            
            // Update visualizer StateFlow
            val normalizedLevel = min(1.0f, maxAmplitude / Short.MAX_VALUE.toFloat())
            updateAudioLevels(normalizedLevel)
            
            // Feed to AudioEncoder (2048 bytes per AAC frame)
            try {
                audioEncoder?.let { encoder ->
                    val timeStamp = System.nanoTime() / 1000 // Microseconds
                    encoder.inputPCMData(Frame(byteBuffer, 0, readCount * 2, timeStamp))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error feeding encoder", e)
            }
        }
        
        for (record in records) {
            try {
                record.stop()
                record.release()
            } catch (_: Exception) {}
        }
    }

    private val levelHistory = mutableListOf<Float>()
    
    private fun getBestAudioSource(audioManager: AudioManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        ) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Audio Streaming Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
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
        isRunning.set(false)
        audioEncoder?.stop()
        rtspServer?.stopServer()
        instance = null
        _isStreaming.value = false
        _audioLevels.value = emptyList() // Clear visualizer
        super.onDestroy()
    }
}
