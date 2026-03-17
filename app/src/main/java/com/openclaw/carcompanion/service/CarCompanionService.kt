package com.openclaw.carcompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.openclaw.carcompanion.*
import com.openclaw.carcompanion.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

/**
 * 車機伴侶服務 - 前台服務
 */
class CarCompanionService : Service() {

    companion object {
        private const val TAG = "CarCompanionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "car_companion_channel"
        
        const val ACTION_START = "com.openclaw.carcompanion.START"
        const val ACTION_STOP = "com.openclaw.carcompanion.STOP"
        
        const val EXTRA_GATEWAY_HOST = "gateway_host"
        const val EXTRA_GATEWAY_PORT = "gateway_port"
        
        // 預設 Gateway
        const val DEFAULT_HOST = "43.134.169.83"
        const val DEFAULT_PORT = 18789
        
        // GPS 回傳間隔 (毫秒)
        const val LOCATION_REPORT_INTERVAL = 60000L // 60 秒
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Managers
    private var webSocketManager: WebSocketManager? = null
    private var ttsManager: TtsManager? = null
    private var locationManager: LocationManagerHelper? = null
    private var screenCaptureManager: ScreenCaptureManager? = null

    private val gson = Gson()

    // 狀態
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _gpsEnabled = MutableStateFlow(false)
    val gpsEnabled: StateFlow<Boolean> = _gpsEnabled

    private var gatewayHost: String = DEFAULT_HOST
    private var gatewayPort: Int = DEFAULT_PORT
    private var locationReportJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): CarCompanionService = this@CarCompanionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        initManagers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                // 獲取 Gateway 配置
                gatewayHost = intent?.getStringExtra(EXTRA_GATEWAY_HOST) 
                    ?: getSystemProperty("openclaw.gateway.host", DEFAULT_HOST)
                gatewayPort = intent?.getIntExtra(EXTRA_GATEWAY_PORT, DEFAULT_PORT) 
                    ?: getSystemProperty("openclaw.gateway.port", DEFAULT_PORT.toString()).toIntOrNull() 
                    ?: DEFAULT_PORT

                Log.d(TAG, "Gateway: $gatewayHost:$gatewayPort")

                // 啟動前台服務
                startForegroundService()
                
                // 連線
                connect()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        disconnect()
        stopLocationTracking()
        ttsManager?.shutdown()
        screenCaptureManager?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenClaw Car Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "車機伴侶服務通知"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = createNotification("連線中...", false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(content: String, connected: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CarCompanionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenClaw Car Companion")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String, connected: Boolean) {
        val notification = createNotification(content, connected)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun initManagers() {
        // TTS Manager
        ttsManager = TtsManager(this).apply {
            onSpeakDone = { success, text ->
                Log.d(TAG, "TTS done: $success, $text")
                // 通知 Gateway TTS 完成
                webSocketManager?.sendMessage(TtsDoneMessage(success = success, text = text))
            }
        }

        // Location Manager
        locationManager = LocationManagerHelper(this).apply {
            onStatusChanged = { enabled ->
                _gpsEnabled.value = enabled
                Log.d(TAG, "GPS status: $enabled")
            }
        }

        // Screen Capture Manager
        screenCaptureManager = ScreenCaptureManager(this)
    }

    private fun connect() {
        _connectionState.value = ConnectionState.Connecting

        webSocketManager = WebSocketManager(gatewayHost, gatewayPort, serviceScope).apply {
            // 監聽連線狀態
            serviceScope.launch {
                connectionState.collect { state ->
                    when (state) {
                        is WebSocketManager.ConnectionState.Connected -> {
                            _connectionState.value = ConnectionState.Connected
                            updateNotification("已連線", true)
                            startLocationTracking()
                        }
                        is WebSocketManager.ConnectionState.Disconnected -> {
                            _connectionState.value = ConnectionState.Disconnected
                            updateNotification("已斷線", false)
                            stopLocationTracking()
                        }
                        is WebSocketManager.ConnectionState.Error -> {
                            _connectionState.value = ConnectionState.Error(state.message)
                            updateNotification("錯誤: ${state.message}", false)
                        }
                        is WebSocketManager.ConnectionState.Connecting -> {
                            _connectionState.value = ConnectionState.Connecting
                        }
                    }
                }
            }

            // 監聽訊息
            serviceScope.launch {
                messages.collect { message ->
                    handleMessage(message)
                }
            }

            connect()
        }
    }

    private fun disconnect() {
        webSocketManager?.disconnect()
        webSocketManager = null
    }

    private suspend fun handleMessage(message: WebSocketMessage) {
        when (message) {
            is TtsRequest -> {
                Log.d(TAG, "TTS request: ${message.text}")
                ttsManager?.speak(message.text, message.language)
            }
            is ScreenshotRequest -> {
                Log.d(TAG, "Screenshot request")
                captureAndSendScreenshot()
            }
            else -> {
                Log.d(TAG, "Unknown message type: ${message.javaClass}")
            }
        }
    }

    private fun startLocationTracking() {
        locationManager?.startTracking()
        
        // 定時回傳 GPS
        locationReportJob?.cancel()
        locationReportJob = serviceScope.launch {
            while (isActive) {
                delay(LOCATION_REPORT_INTERVAL)
                reportLocation()
            }
        }
    }

    private fun stopLocationTracking() {
        locationManager?.stopTracking()
        locationReportJob?.cancel()
    }

    private fun reportLocation() {
        val location = locationManager?.getLastKnownLocation()
        location?.let {
            val message = LocationMessage(
                lat = it.latitude,
                lng = it.longitude,
                accuracy = it.accuracy,
                timestamp = System.currentTimeMillis() / 1000
            )
            webSocketManager?.sendMessage(message)
            Log.d(TAG, "Location reported: ${it.latitude}, ${it.longitude}")
        }
    }

    private fun captureAndSendScreenshot() {
        // 這裡需要先請求 MediaProjection 權限
        // 簡化版本：使用 WindowManager 擷取
        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            display.getRealSize(size)

            // 由於需要 Activity Result API 來獲取權限，這裡提供框架
            // 實際實現需要在 Activity 中處理
            Log.d(TAG, "Screenshot requested but requires permission flow")
            
            // 發送錯誤回覆
            webSocketManager?.sendMessage(
                ScreenshotMessage(
                    data = "",
                    width = 0,
                    height = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot error: ${e.message}")
        }
    }

    /**
     * 停止 TTS
     */
    fun stopTts() {
        ttsManager?.stop()
    }

    /**
     * 檢查連線狀態
     */
    fun isConnected(): Boolean = webSocketManager?.isConnected() ?: false

    /**
     * 獲取系統屬性
     */
    private fun getSystemProperty(key: String, default: String): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop $key")
            val result = process.inputStream.bufferedReader().readText().trim()
            if (result.isNotEmpty()) result else default
        } catch (e: Exception) {
            default
        }
    }

    enum class ConnectionState {
        Connecting,
        Connected,
        Disconnected,
        Error
    }
}
