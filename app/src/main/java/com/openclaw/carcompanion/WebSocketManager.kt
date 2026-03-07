package com.openclaw.carcompanion

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.openclaw.carcompanion.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * WebSocket 連線管理器
 */
class WebSocketManager(
    private val host: String,
    private val port: Int,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val PING_INTERVAL_MS = 30000L
    }

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var pingJob: Job? = null

    // 訊息通道
    private val _messages = MutableStateFlow<WebSocketMessage?>(null)
    val messages: Flow<WebSocketMessage> = kotlinx.coroutines.flow.flow {
        _messages.collect { msg ->
            msg?.let { emit(it) }
        }
    }

    // 連線狀態
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: Flow<ConnectionState> = _connectionState.asStateFlow()

    sealed class ConnectionState {
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            isConnected = true
            scope.launch {
                _connectionState.value = ConnectionState.Connected
            }
            startPingJob()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            try {
                val messageType = gson.fromJson(text, MessageWrapper::class.java)
                val message = when (messageType.type) {
                    WebSocketMessage.TYPE_TTS -> gson.fromJson(text, TtsRequest::class.java)
                    WebSocketMessage.TYPE_SCREENSHOT_REQUEST -> gson.fromJson(text, ScreenshotRequest::class.java)
                    WebSocketMessage.TYPE_PING -> {
                        // 收到 ping，回覆 pong
                        scope.launch {
                            webSocket.send(gson.toJson(PongMessage()))
                        }
                        null
                    }
                    else -> null
                }
                message?.let {
                    scope.launch {
                        _messages.value = it
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "Closed: $code $reason")
            isConnected = false
            stopPingJob()
            scope.launch {
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "Failure: ${t.message}")
            isConnected = false
            stopPingJob()
            scope.launch {
                _connectionState.value = ConnectionState.Error(t.message ?: "Unknown error")
            }
        }
    }

    fun connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }

        scope.launch {
            _connectionState.value = ConnectionState.Connecting
        }

        val url = "ws://$host:$port/ws/car"
        val request = Request.Builder()
            .url(url)
            .build()

        Log.d(TAG, "Connecting to: $url")
        webSocket = client.newWebSocket(request, webSocketListener)
    }

    fun disconnect() {
        stopPingJob()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
    }

    fun sendMessage(message: WebSocketMessage): Boolean {
        if (!isConnected) {
            Log.w(TAG, "Cannot send message: not connected")
            return false
        }
        val json = gson.toJson(message)
        Log.d(TAG, "Sending: $json")
        return webSocket?.send(json) ?: false
    }

    fun isConnected(): Boolean = isConnected

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && isConnected) {
                delay(PING_INTERVAL_MS)
                if (isConnected) {
                    // OkHttp 會自動處理 ping，這裡可以做應用層的心跳
                    Log.d(TAG, "Heartbeat check")
                }
            }
        }
    }

    private fun stopPingJob() {
        pingJob?.cancel()
        pingJob = null
    }
}
