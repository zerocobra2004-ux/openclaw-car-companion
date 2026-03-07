package com.openclaw.carcompanion

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

/**
 * TTS 語音朗讀管理器
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false

    // 朗讀完成回調
    var onSpeakDone: ((Boolean, String) -> Unit)? = null

    // 當前朗讀的文字
    private var currentText: String = ""

    init {
        Log.d(TAG, "Initializing TTS")
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Chinese not supported, falling back to default")
                tts?.setLanguage(Locale.getDefault())
            }

            // 設置語速
            tts?.setSpeechRate(1.0f)

            // 設置音調
            tts?.setPitch(1.0f)

            // 設置監聽器
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.d(TAG, "TTS started")
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    Log.d(TAG, "TTS done")
                    onSpeakDone?.invoke(true, currentText)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    Log.e(TAG, "TTS error")
                    onSpeakDone?.invoke(false, currentText)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking = false
                    Log.e(TAG, "TTS error: $errorCode")
                    onSpeakDone?.invoke(false, currentText)
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed: $status")
        }
    }

    /**
     * 朗讀文字
     * @param text 要朗讀的文字
     * @param language 語言代碼 (可選)
     */
    fun speak(text: String, language: String? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        // 如果正在說話，先停止
        if (isSpeaking) {
            stop()
        }

        currentText = text

        // 設置語言
        language?.let {
            val locale = when (it.lowercase()) {
                "zh", "zh-tw", "zh-hant" -> Locale.TRADITIONAL_CHINESE
                "zh-cn", "zh-hans" -> Locale.SIMPLIFIED_CHINESE
                "en" -> Locale.ENGLISH
                "ja" -> Locale.JAPANESE
                "ko" -> Locale.KOREAN
                else -> Locale.getDefault()
            }
            tts?.setLanguage(locale)
        }

        // 生成唯一的utterance ID
        val utteranceId = "tts_${System.currentTimeMillis()}"

        // 朗讀
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.SUCCESS) {
            Log.d(TAG, "Speaking: $text")
        } else {
            Log.e(TAG, "Failed to speak: $result")
            onSpeakDone?.invoke(false, text)
        }
    }

    /**
     * 停止朗讀
     */
    fun stop() {
        tts?.stop()
        isSpeaking = false
        Log.d(TAG, "TTS stopped")
    }

    /**
     * 檢查是否正在朗讀
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * 檢查 TTS 是否已初始化
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 釋放資源
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TTS shutdown")
    }
}
