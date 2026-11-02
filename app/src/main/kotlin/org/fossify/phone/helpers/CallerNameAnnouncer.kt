package org.fossify.phone.helpers

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.fossify.phone.extensions.config
import java.util.Locale

object CallerNameAnnouncer : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingAnnouncement: String? = null
    private var isAnnouncing = false
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var repeatRunnable: Runnable? = null

    fun announce(context: Context, callerName: String, isUnknown: Boolean = false) {
        val config = context.config
        if (!config.announceCallerName) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            // Respect silent and vibrate modes
            return
        }

        appContext = context.applicationContext

        val textToSpeak = if (isUnknown || callerName.isBlank()) {
            if (config.appLanguage == LANGUAGE_HINDI) "अज्ञात नंबर से कॉल आ रही है" else "Incoming call from unknown caller"
        } else {
            if (config.appLanguage == LANGUAGE_HINDI) "$callerName की कॉल आ रही है" else "Incoming call from $callerName"
        }

        pendingAnnouncement = textToSpeak
        isAnnouncing = true

        if (tts == null) {
            tts = TextToSpeech(appContext, this)
        } else if (isInitialized) {
            startRepeatLoop(textToSpeak)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.let { engine ->
                val lang = if (appContext?.config?.appLanguage == LANGUAGE_HINDI) Locale("hi", "IN") else Locale.getDefault()
                val result = engine.setLanguage(lang)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                engine.setSpeechRate(0.95f)
                engine.setPitch(1.0f)
            }
            pendingAnnouncement?.let { text ->
                if (isAnnouncing) {
                    startRepeatLoop(text)
                }
            }
        } else {
            isInitialized = false
        }
    }

    private fun startRepeatLoop(text: String) {
        stopLoop()
        speak(text)
        repeatRunnable = object : Runnable {
            override fun run() {
                if (isAnnouncing) {
                    speak(text)
                    handler.postDelayed(this, 3500L)
                }
            }
        }
        handler.postDelayed(repeatRunnable!!, 3500L)
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CallerAnnouncementUtterance")
        } catch (_: Exception) {}
    }

    private fun stopLoop() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    fun stop() {
        isAnnouncing = false
        pendingAnnouncement = null
        stopLoop()
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}

