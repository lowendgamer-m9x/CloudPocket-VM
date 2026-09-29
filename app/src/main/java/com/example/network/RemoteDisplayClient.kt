package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.example.model.RemoteInputEvent
import com.example.model.StreamQuality
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface RemoteDisplayListener {
    fun onFrameReceived(bitmap: Bitmap, latencyMs: Long, frameBytes: Int)
    fun onStateChanged(isConnected: Boolean, isReconnecting: Boolean, error: String?)
    fun onAdaptiveQualityRequested(suggestedQuality: StreamQuality)
}

class RemoteDisplayClient(
    private val listener: RemoteDisplayListener
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websockets
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var shouldReconnect = true
    private var reconnectAttempts = 0
    private var connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Memory-saving bitmap reuse buffer
    private var reusableBitmap: Bitmap? = null
    private val bitmapOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565 // 16-bit color: 50% less RAM than ARGB_8888!
        inMutable = true
    }

    private var lastPingSendTime = 0L
    private var currentLatencyMs = 28L
    private var lastFrameTime = 0L
    private var frameIntervalAverage = 66L // ~15 fps
    private var currentQuality = StreamQuality.LOW

    fun connect(wsUrl: String, quality: StreamQuality) {
        currentQuality = quality
        shouldReconnect = true
        reconnectAttempts = 0
        doConnect(wsUrl)
    }

    private fun doConnect(wsUrl: String) {
        val request = Request.Builder()
            .url(wsUrl)
            .header("X-Quality-Preset", currentQuality.name)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                reconnectAttempts = 0
                listener.onStateChanged(isConnected = true, isReconnecting = false, error = null)
                // Send initial quality negotiation
                sendInput(RemoteInputEvent.QualityChange(currentQuality.name))
                // Send initial ping
                sendPing()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryFrame(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                if (shouldReconnect) {
                    scheduleReconnect(wsUrl)
                } else {
                    listener.onStateChanged(isConnected = false, isReconnecting = false, error = null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                if (shouldReconnect) {
                    listener.onStateChanged(
                        isConnected = false,
                        isReconnecting = true,
                        error = "Reconnecting... (${t.localizedMessage ?: "Network interrupted"})"
                    )
                    scheduleReconnect(wsUrl)
                } else {
                    listener.onStateChanged(
                        isConnected = false,
                        isReconnecting = false,
                        error = t.localizedMessage ?: "Connection failed"
                    )
                }
            }
        })
    }

    private fun handleBinaryFrame(data: ByteArray) {
        if (data.isEmpty()) return

        val now = SystemClock.uptimeMillis()
        if (lastFrameTime > 0) {
            val interval = now - lastFrameTime
            frameIntervalAverage = (frameIntervalAverage * 4 + interval) / 5
            // Dynamic adaptive quality reduction on slow network
            if (interval > 350 && currentQuality != StreamQuality.LOW) {
                listener.onAdaptiveQualityRequested(StreamQuality.LOW)
            }
        }
        lastFrameTime = now

        try {
            // Low-RAM decode with RGB_565 configuration
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, bitmapOptions)
            if (bitmap != null) {
                listener.onFrameReceived(bitmap, currentLatencyMs, data.size)
            }
        } catch (_: OutOfMemoryError) {
            // Aggressive fallback for 512MB RAM constraints
            System.gc()
            bitmapOptions.inSampleSize = 2
            try {
                val fallbackBitmap = BitmapFactory.decodeByteArray(data, 0, data.size, bitmapOptions)
                if (fallbackBitmap != null) {
                    listener.onFrameReceived(fallbackBitmap, currentLatencyMs, data.size)
                }
            } catch (_: Throwable) {}
            bitmapOptions.inSampleSize = 1
        } catch (_: Exception) {}
    }

    private fun handleTextMessage(text: String) {
        if (text.contains("\"type\":\"pong\"")) {
            if (lastPingSendTime > 0) {
                currentLatencyMs = SystemClock.uptimeMillis() - lastPingSendTime
            }
        } else if (text.contains("\"vm_terminated\":true") || text.contains("VM_NO_LONGER_AVAILABLE")) {
            shouldReconnect = false
            listener.onStateChanged(
                isConnected = false,
                isReconnecting = false,
                error = "Cloud computer is no longer available."
            )
            disconnect()
        }
    }

    fun sendPing() {
        lastPingSendTime = SystemClock.uptimeMillis()
        sendInput(RemoteInputEvent.Heartbeat(lastPingSendTime))
    }

    fun sendInput(event: RemoteInputEvent) {
        val ws = webSocket ?: return
        if (isConnected) {
            try {
                ws.send(event.toJson())
            } catch (_: Exception) {}
        }
    }

    private fun scheduleReconnect(wsUrl: String) {
        if (!shouldReconnect) return
        reconnectAttempts++
        val delayMs = (1000L * reconnectAttempts).coerceAtMost(8000L)

        connectionScope.launch {
            delay(delayMs)
            if (shouldReconnect) {
                doConnect(wsUrl)
            }
        }
    }

    fun setQuality(quality: StreamQuality) {
        currentQuality = quality
        sendInput(RemoteInputEvent.QualityChange(quality.name))
    }

    fun disconnect() {
        shouldReconnect = false
        isConnected = false
        connectionScope.cancel()
        connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        listener.onStateChanged(isConnected = false, isReconnecting = false, error = null)
    }
}
