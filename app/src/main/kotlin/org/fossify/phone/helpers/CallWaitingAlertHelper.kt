package org.fossify.phone.helpers

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class CallWaitingAlertHelper(private val context: Context) {
    private val logTag = "CallWaitingAlertHelper"
    private val handler = Handler(Looper.getMainLooper())
    private var isAlerting = false
    private var toneGenerator: ToneGenerator? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val alertRunnable = object : Runnable {
        override fun run() {
            if (!isAlerting) return
            playCallWaitingTone()
            playCallWaitingVibration()
            // Repeat tone every 5 seconds while incoming call is still ringing
            handler.postDelayed(this, 5000)
        }
    }

    @Synchronized
    fun startAlert() {
        if (isAlerting) return
        isAlerting = true
        Log.d(logTag, "Starting call waiting tone and vibration alert")
        handler.removeCallbacks(alertRunnable)
        handler.post(alertRunnable)
    }

    @Synchronized
    fun stopAlert() {
        if (!isAlerting) return
        isAlerting = false
        Log.d(logTag, "Stopping call waiting alert")
        handler.removeCallbacks(alertRunnable)
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.w(logTag, "Error releasing ToneGenerator", e)
        } finally {
            toneGenerator = null
        }
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(logTag, "Error cancelling vibration", e)
        }
    }

    private fun playCallWaitingTone() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 85)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 400)
        } catch (e: Exception) {
            Log.w(logTag, "Failed to play call waiting tone", e)
            try {
                ToneGenerator(AudioManager.STREAM_DTMF, 85).apply {
                    startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 400)
                    handler.postDelayed({ release() }, 500)
                }
            } catch (fallbackError: Exception) {
                Log.w(logTag, "Fallback tone also failed", fallbackError)
            }
        }
    }

    private fun playCallWaitingVibration() {
        try {
            val pattern = longArrayOf(0, 200, 200, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to vibrate for call waiting", e)
        }
    }
}
