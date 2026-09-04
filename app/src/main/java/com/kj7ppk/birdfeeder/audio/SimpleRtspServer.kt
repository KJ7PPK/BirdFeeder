package com.kj7ppk.birdfeeder.audio

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class SimpleRtspServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<ClientSession>()
    
    private var sequenceNumber = 0
    private var sampleCount = 0L
    private val ssrc = 0x12345678

    companion object {
        private const val TAG = "SimpleRtspServer"
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        Thread({
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "SimpleRtspServer listening on port $port")
                while (isRunning.get()) {
                    val socket = serverSocket?.accept() ?: break
                    Log.i(TAG, "Client connected from ${socket.inetAddress.hostAddress}")
                    val session = ClientSession(socket)
                    clients.add(session)
                    Thread(session, "RtspClientThread").start()
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "Server socket error", e)
            }
        }, "SimpleRtspServerThread").start()
    }

    fun stop() {
        isRunning.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        for (client in clients) {
            client.close()
        }
        clients.clear()
    }

    fun sendPcmAudio(pcmShorts: ShortArray, readCount: Int) {
        if (!isRunning.get() || clients.isEmpty() || readCount <= 0) return

        val pcmBytesLength = readCount * 2
        val rtpPacketSize = 12 + pcmBytesLength
        val rtpPacket = ByteArray(rtpPacketSize)

        // RTP Header
        rtpPacket[0] = 0x80.toByte() // Version 2
        rtpPacket[1] = 11.toByte()   // Payload type 11 (L16 PCM Mono)
        
        // Sequence number (16-bit)
        sequenceNumber = (sequenceNumber + 1) and 0xFFFF
        rtpPacket[2] = ((sequenceNumber shr 8) and 0xFF).toByte()
        rtpPacket[3] = (sequenceNumber and 0xFF).toByte()

        // Timestamp (32-bit sample count @ 48kHz)
        val rtpTimestamp = (sampleCount and 0xFFFFFFFFL).toInt()
        sampleCount += readCount
        rtpPacket[4] = ((rtpTimestamp shr 24) and 0xFF).toByte()
        rtpPacket[5] = ((rtpTimestamp shr 16) and 0xFF).toByte()
        rtpPacket[6] = ((rtpTimestamp shr 8) and 0xFF).toByte()
        rtpPacket[7] = (rtpTimestamp and 0xFF).toByte()

        // SSRC
        rtpPacket[8] = ((ssrc shr 24) and 0xFF).toByte()
        rtpPacket[9] = ((ssrc shr 16) and 0xFF).toByte()
        rtpPacket[10] = ((ssrc shr 8) and 0xFF).toByte()
        rtpPacket[11] = (ssrc and 0xFF).toByte()

        // L16 requires Big-Endian 16-bit PCM bytes
        for (i in 0 until readCount) {
            val sample = pcmShorts[i].toInt()
            rtpPacket[12 + i * 2] = ((sample shr 8) and 0xFF).toByte()     // MSB
            rtpPacket[12 + i * 2 + 1] = (sample and 0xFF).toByte()         // LSB
        }

        // TCP Interleaved Frame Header ($ + Channel 0 + 16-bit length)
        val frameHeader = ByteArray(4)
        frameHeader[0] = 0x24.toByte() // '$'
        frameHeader[1] = 0x00.toByte() // Channel 0 (Audio RTP)
        frameHeader[2] = ((rtpPacketSize shr 8) and 0xFF).toByte()
        frameHeader[3] = (rtpPacketSize and 0xFF).toByte()

        for (client in clients) {
            if (client.isStreaming) {
                client.sendInterleavedFrame(frameHeader, rtpPacket)
            }
        }
    }

    private inner class ClientSession(private val socket: Socket) : Runnable {
        @Volatile var isStreaming = false
        private var output: OutputStream? = null

        override fun run() {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                output = socket.getOutputStream()

                while (isRunning.get() && !socket.isClosed) {
                    val requestLine = reader.readLine() ?: break
                    if (requestLine.isEmpty()) continue

                    var cSeq = "1"
                    val headers = mutableMapOf<String, String>()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val header = line ?: break
                        if (header.isEmpty()) break
                        val parts = header.split(":", limit = 2)
                        if (parts.size == 2) {
                            headers[parts[0].trim().lowercase()] = parts[1].trim()
                        }
                    }

                    headers["cseq"]?.let { cSeq = it }

                    val tokens = requestLine.split(" ")
                    if (tokens.size < 2) continue
                    val method = tokens[0].uppercase()

                    when (method) {
                        "OPTIONS" -> handleOptions(cSeq)
                        "DESCRIBE" -> handleDescribe(cSeq)
                        "SETUP" -> handleSetup(cSeq)
                        "PLAY" -> handlePlay(cSeq)
                        "TEARDOWN" -> {
                            handleTeardown(cSeq)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client disconnected: ${e.message}")
            } finally {
                close()
            }
        }

        private fun sendResponse(response: String) {
            try {
                output?.write(response.toByteArray(Charsets.UTF_8))
                output?.flush()
            } catch (e: Exception) {
                close()
            }
        }

        private fun handleOptions(cSeq: String) {
            val resp = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cSeq\r\n" +
                    "Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY\r\n\r\n"
            sendResponse(resp)
        }

        private fun handleDescribe(cSeq: String) {
            val ip = socket.localAddress?.hostAddress ?: "0.0.0.0"
            val sdp = "v=0\r\n" +
                    "o=- 0 0 IN IP4 $ip\r\n" +
                    "s=BirdFeeder Audio\r\n" +
                    "t=0 0\r\n" +
                    "a=recvonly\r\n" +
                    "m=audio 0 RTP/AVP 11\r\n" +
                    "a=rtpmap:11 L16/48000/1\r\n" +
                    "a=control:trackID=0\r\n"

            val resp = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cSeq\r\n" +
                    "Content-Type: application/sdp\r\n" +
                    "Content-Length: ${sdp.toByteArray(Charsets.UTF_8).size}\r\n\r\n" +
                    sdp
            sendResponse(resp)
        }

        private fun handleSetup(cSeq: String) {
            val resp = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cSeq\r\n" +
                    "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n" +
                    "Session: 12345678\r\n\r\n"
            sendResponse(resp)
        }

        private fun handlePlay(cSeq: String) {
            val resp = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cSeq\r\n" +
                    "Session: 12345678\r\n" +
                    "Range: ntp=0.000-\r\n\r\n"
            sendResponse(resp)
            isStreaming = true
            Log.i(TAG, "Client active and playing L16 PCM audio stream")
        }

        private fun handleTeardown(cSeq: String) {
            val resp = "RTSP/1.0 200 OK\r\n" +
                    "CSeq: $cSeq\r\n\r\n"
            sendResponse(resp)
            close()
        }

        fun sendInterleavedFrame(header: ByteArray, payload: ByteArray) {
            try {
                synchronized(this) {
                    output?.write(header)
                    output?.write(payload)
                    output?.flush()
                }
            } catch (e: Exception) {
                close()
            }
        }

        fun close() {
            isStreaming = false
            clients.remove(this)
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
