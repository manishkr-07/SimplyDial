package org.fossify.phone.helpers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

/**
 * Singleton manager to track and control video call state across the app
 * Handles voice call muting, video call state tracking, and lifecycle management
 */
object VideoCallManager {
    private const val TAG = "VideoCallManager"
    
    // Video call state
    var isVideoCallActive: Boolean = false
        private set
    
    var currentMeetingUrl: String? = null
        private set
        
    var currentContactName: String? = null
        private set
    
    // Voice call state before video call started
    private var wasVoiceCallMuted: Boolean = false
    private var audioManager: AudioManager? = null
    
    /**
     * Start a new video call session
     */
    fun startVideoCall(context: Context, meetingUrl: String, contactName: String) {
        Log.d(TAG, "Starting video call for $contactName at $meetingUrl")
        
        isVideoCallActive = true
        currentMeetingUrl = meetingUrl
        currentContactName = contactName
        
        // Auto-mute ongoing voice call if any
        muteVoiceCallIfActive(context)
    }
    
    /**
     * End the current video call session
     */
    fun endVideoCall(context: Context) {
        Log.d(TAG, "Ending video call")
        
        isVideoCallActive = false
        currentMeetingUrl = null
        currentContactName = null
        
        // Restore voice call state if it was changed
        restoreVoiceCallState(context)
    }
    
    /**
     * Check if a video call is currently active
     */
    fun hasActiveVideoCall(): Boolean {
        return isVideoCallActive && currentMeetingUrl != null
    }
    
    /**
     * Mute ongoing voice call when video call starts
     */
    private fun muteVoiceCallIfActive(context: Context) {
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Check if there's an ongoing call
            if (audioManager?.mode == AudioManager.MODE_IN_CALL || 
                audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION) {
                
                // Save current mute state
                wasVoiceCallMuted = audioManager?.isMicrophoneMute ?: false
                
                // Mute the microphone if not already muted
                if (!wasVoiceCallMuted) {
                    audioManager?.isMicrophoneMute = true
                    Log.d(TAG, "Voice call muted for video call")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error muting voice call", e)
        }
    }
    
    /**
     * Restore voice call state after video call ends
     */
    private fun restoreVoiceCallState(context: Context) {
        try {
            // Only restore if we previously muted the call
            if (!wasVoiceCallMuted && audioManager != null) {
                audioManager?.isMicrophoneMute = false
                Log.d(TAG, "Voice call unmuted after video call")
            }
            
            audioManager = null
            wasVoiceCallMuted = false
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring voice call state", e)
        }
    }
    
    /**
     * Get intent to rejoin the current video call
     */
    fun getRejoinIntent(context: Context): Intent? {
        return if (hasActiveVideoCall()) {
            org.fossify.phone.activities.VideoCallActivity.getStartIntent(
                context = context,
                meetingUrl = currentMeetingUrl!!,
                contactName = currentContactName ?: "Contact"
            )
        } else {
            null
        }
    }
    
    /**
     * Reset all states (used for cleanup)
     */
    fun reset(context: Context) {
        endVideoCall(context)
    }
}
