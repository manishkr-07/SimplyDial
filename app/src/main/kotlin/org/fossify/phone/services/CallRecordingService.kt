package org.fossify.phone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.managers.CallRecordingManager

/**
 * Foreground service for call recording
 * Ensures recording continues even when app is in background
 */
class CallRecordingService : Service() {
    
    companion object {
        private const val TAG = "CallRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "call_recording_channel"
        private const val CHANNEL_NAME = "Call Recording"
        
        const val ACTION_START_RECORDING = "action_start_recording"
        const val ACTION_STOP_RECORDING = "action_stop_recording"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"
        
        /**
         * Start recording service
         */
        fun startRecording(context: Context, phoneNumber: String?, isIncoming: Boolean) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stop recording service
         */
        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }
    
    private lateinit var recordingManager: CallRecordingManager
    private var isServiceRunning = false
    
    override fun onCreate() {
        super.onCreate()
        recordingManager = CallRecordingManager(this)
        setupCallbacks()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
                startRecording(phoneNumber, isIncoming)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun setupCallbacks() {
        recordingManager.onRecordingStateChanged = { isRecording ->
            if (isRecording) {
                updateNotification("Recording in progress...")
            }
        }
        
        recordingManager.onRecordingError = { error ->
            Log.e(TAG, "Recording error: $error")
            stopSelf()
        }
    }
    
    private fun startRecording(phoneNumber: String?, isIncoming: Boolean) {
        if (isServiceRunning) {
            Log.w(TAG, "Recording already in progress")
            return
        }
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification("Starting recording..."))
        
        val success = recordingManager.startRecording(phoneNumber, isIncoming)
        if (success) {
            isServiceRunning = true
            updateNotification("Recording call...")
        } else {
            Log.e(TAG, "Failed to start recording")
            stopSelf()
        }
    }
    
    private fun stopRecording() {
        if (!isServiceRunning) {
            Log.w(TAG, "No recording in progress to stop")
            stopSelf()
            return
        }
        
        val recording = recordingManager.stopRecording()
        if (recording != null) {
            Log.i(TAG, "Recording saved: ${recording.fileName}")
            updateNotification("Recording saved")
        } else {
            Log.w(TAG, "Recording was not saved")
        }
        
        isServiceRunning = false
        
        // Stop foreground and remove notification after a short delay
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for call recording status"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Recording")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_microphone_vector)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        recordingManager.release()
        isServiceRunning = false
    }
}
