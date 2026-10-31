package org.fossify.phone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import org.fossify.phone.activities.IncomingVideoCallActivity
import org.fossify.phone.helpers.AppUserRegistry

/**
 * Receiver for incoming SMS messages
 * Detects special video call request messages and shows incoming call screen
 */
class VideoCallSmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val VIDEO_CALL_PREFIX_PRIMARY = "SIMPLYDIAL_VIDEO_CALL|"
        private const val VIDEO_CALL_PREFIX_LEGACY = "FOSSIFY_VIDEO_CALL|"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }
        
        for (smsMessage in messages) {
            val messageBody = smsMessage.messageBody ?: continue
            val sender = smsMessage.originatingAddress ?: continue
            
            // Check if this is a video call request (support both SimplyDial and legacy formats)
            if (messageBody.startsWith(VIDEO_CALL_PREFIX_PRIMARY) || messageBody.startsWith(VIDEO_CALL_PREFIX_LEGACY)) {
                handleVideoCallRequest(context, sender, messageBody)
                
                // Abort broadcast to prevent SMS app from showing this message
                abortBroadcast()
                break
            }
        }
    }
    
    private fun handleVideoCallRequest(context: Context, sender: String, messageBody: String) {
        try {
            // Log the received message for debugging
            android.util.Log.d("VideoCallSmsReceiver", "Received message: $messageBody from $sender")
            
            // Parse the message
            // Format: FOSSIFY_VIDEO_CALL|CallerName|MeetingURL
            val parts = messageBody.split("|")
            if (parts.size != 3) {
                android.util.Log.e("VideoCallSmsReceiver", "Invalid message format: expected 3 parts, got ${parts.size}")
                return
            }
            
            val callerName = parts[1]
            val meetingUrl = parts[2]
            
            android.util.Log.d("VideoCallSmsReceiver", "Parsed: callerName=$callerName, meetingUrl=$meetingUrl")
            
            // Mark sender as app user
            val appUserRegistry = AppUserRegistry(context)
            appUserRegistry.markContactAsAppUser(sender)
            
            // Show incoming video call activity
            val incomingCallIntent = IncomingVideoCallActivity.getStartIntent(
                context = context,
                callerName = callerName,
                callerNumber = sender,
                meetingUrl = meetingUrl
            )
            context.startActivity(incomingCallIntent)
            
            android.util.Log.d("VideoCallSmsReceiver", "Started IncomingVideoCallActivity")
            
        } catch (e: Exception) {
            android.util.Log.e("VideoCallSmsReceiver", "Error handling video call request", e)
            e.printStackTrace()
            // If parsing fails, let the SMS show normally
        }
    }
}
