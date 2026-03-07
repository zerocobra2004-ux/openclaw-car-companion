package com.openclaw.carcompanion

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * 螢幕擷取管理器
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var isCapturing = false

    /**
     * 初始化 MediaProjection
     */
    fun initialize(projection: MediaProjection) {
        this.mediaProjection = projection
        Log.d(TAG, "ScreenCaptureManager initialized")
    }

    /**
     * 擷取螢幕
     * @param width 寬度
     * @param height 高度
     * @param density 密度
     * @param callback 回調 (Base64 編碼的圖片)
     */
    @SuppressLint("WrongConstant")
    fun capture(
        width: Int,
        height: Int,
        density: Int,
        callback: (Result<Pair<String, Bitmap>>) -> Unit
    ) {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized")
            callback(Result.failure(IllegalStateException("MediaProjection not initialized")))
            return
        }

        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return
        }

        isCapturing = true

        try {
            // 建立 ImageReader
            imageReader = ImageReader.newInstance(
                width,
                height,
                android.graphics.ImageFormat.JPEG,
                2
            )

            // 建立虛擬顯示
            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                android.view.Display.FLAG_SECURE,
                imageReader?.surface,
                null,
                null
            )

            // 等待圖像
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        // 轉為 Bitmap
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size
                        )

                        if (bitmap != null) {
                            // 轉為 Base64
                            val base64 = bitmapToBase64(bitmap)
                            callback(Result.success(Pair(base64, bitmap)))
                        } else {
                            callback(Result.failure(Exception("Failed to decode bitmap")))
                        }

                        image.close()
                    } else {
                        callback(Result.failure(Exception("No image available")))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing: ${e.message}")
                    callback(Result.failure(e))
                } finally {
                    virtualDisplay?.release()
                    isCapturing = false
                }
            }, null)

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up capture: ${e.message}")
            isCapturing = false
            callback(Result.failure(e))
        }
    }

    /**
     * 快速擷取 (使用 WindowManager)
     */
    fun captureQuick(callback: (Result<String>) -> Unit) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            display.getRealSize(size)

            // 這個方法需要螢幕截圖權限，這裡提供基本框架
            // 實際使用需要 MediaProjection 權限
            Log.d(TAG, "Quick capture: ${size.x}x${size.y}")
            callback(Result.failure(Exception("Use capture() with MediaProjection")))
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }

    /**
     * 將 Bitmap 轉為 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 釋放資源
     */
    fun release() {
        try {
            imageReader?.close()
            imageReader = null
            mediaProjection = null
            isCapturing = false
            Log.d(TAG, "ScreenCaptureManager released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing: ${e.message}")
        }
    }

    /**
     * 是否正在擷取
     */
    fun isCapturing(): Boolean = isCapturing
}
