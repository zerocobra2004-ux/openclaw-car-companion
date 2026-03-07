package com.openclaw.carcompanion.models

import com.google.gson.annotations.SerializedName

/**
 * WebSocket 訊息格式
 */
sealed class WebSocketMessage {
    
    companion object {
        const val TYPE_LOCATION = "location"
        const val TYPE_TTS = "tts"
        const val TYPE_TTS_DONE = "tts_done"
        const val TYPE_SCREENSHOT_REQUEST = "screenshot_request"
        const val TYPE_SCREENSHOT = "screenshot"
        const val TYPE_STATUS = "status"
        const val TYPE_PING = "ping"
        const val TYPE_PONG = "pong"
    }
}

/**
 * GPS 位置訊息 (發送給 Gateway)
 */
data class LocationMessage(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_LOCATION,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
    @SerializedName("accuracy") val accuracy: Float = 0f,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis() / 1000
) : WebSocketMessage()

/**
 * TTS 朗讀請求 (從 Gateway 接收)
 */
data class TtsRequest(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_TTS,
    @SerializedName("text") val text: String,
    @SerializedName("language") val language: String? = null
) : WebSocketMessage()

/**
 * TTS 完成通知 (發送給 Gateway)
 */
data class TtsDoneMessage(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_TTS_DONE,
    @SerializedName("success") val success: Boolean,
    @SerializedName("text") val text: String
) : WebSocketMessage()

/**
 * 螢幕擷取請求 (從 Gateway 接收)
 */
data class ScreenshotRequest(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_SCREENSHOT_REQUEST
) : WebSocketMessage()

/**
 * 螢幕擷取回覆 (發送給 Gateway)
 */
data class ScreenshotMessage(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_SCREENSHOT,
    @SerializedName("data") val data: String, // Base64 encoded image
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int
) : WebSocketMessage()

/**
 * 狀態訊息
 */
data class StatusMessage(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_STATUS,
    @SerializedName("connected") val connected: Boolean,
    @SerializedName("gps_enabled") val gpsEnabled: Boolean,
    @SerializedName("tts_ready") val ttsReady: Boolean
) : WebSocketMessage()

/**
 * 心跳 Pong 回覆
 */
data class PongMessage(
    @SerializedName("type") val type: String = WebSocketMessage.TYPE_PONG,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis() / 1000
) : WebSocketMessage()

/**
 * 通用訊息解析結果
 */
data class MessageWrapper(
    val type: String,
    val rawJson: String
)
