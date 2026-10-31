package org.fossify.phone.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper class to manage Jitsi Meet video calls
 * Uses public Jitsi server (https://meet.jit.si) for free video calling via browser
 * No SDK required - lightweight WebView approach
 */
class JitsiMeetHelper(private val context: Context) {

    companion object {
        // Public Jitsi server URL - using beta.meet.jit.si (Jitsi beta server)
        // Alternative servers: https://meet.jit.si, https://8x8.vc, https://meet.ffmuc.net, https://meet.evolix.org
        private const val JITSI_SERVER_URL = "https://beta.meet.jit.si"
        
        // Prefix for room names to avoid conflicts
        private const val ROOM_PREFIX = "FossifyPhone"
        
        // SMS permission request code
        const val SMS_PERMISSION_REQUEST_CODE = 1001
    }

    /**
     * Start a video call with the given contact
     * Opens Jitsi Meet in the app's VideoCallActivity (in-app WebView)
     * @param contactName Name of the contact (used for display)
     * @param contactNumber Phone number (used to generate unique room ID)
     * @param userName Current user's name for display in meeting
     * @param useTimestampRoom If true, uses timestamp-based room (for direct calls)
     */
    fun startVideoCall(
        contactName: String,
        contactNumber: String,
        userName: String = "User",
        useTimestampRoom: Boolean = false
    ) {
        try {
            // Generate a unique room name based on the contact number
            val roomName = generateRoomName(contactNumber, useTimestampRoom)
            
            // Build very simple meeting URL - no parameters to avoid authentication issues
            // Just the server URL and room name
            val meetingUrl = "$JITSI_SERVER_URL/$roomName"
            
            android.util.Log.d("JitsiMeetHelper", "startVideoCall: roomName=$roomName, useTimestamp=$useTimestampRoom, url=$meetingUrl")
            
            // Open Jitsi Meet in-app using VideoCallActivity
            val videoCallIntent = org.fossify.phone.activities.VideoCallActivity.getStartIntent(
                context = context,
                meetingUrl = meetingUrl,
                contactName = contactName
            )
            context.startActivity(videoCallIntent)
            
            // Show toast with meeting info
            Toast.makeText(
                context,
                "Starting video call with $contactName",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Error starting video call: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Start a video call with a specific meeting URL
     * Used when joining from a received invitation
     * @param meetingUrl The exact URL to join
     * @param contactName Name of the contact for display
     */
    fun startVideoCallWithUrl(
        meetingUrl: String,
        contactName: String
    ) {
        try {
            // Open Jitsi Meet in-app using VideoCallActivity with the specific URL
            val videoCallIntent = org.fossify.phone.activities.VideoCallActivity.getStartIntent(
                context = context,
                meetingUrl = meetingUrl,
                contactName = contactName
            )
            context.startActivity(videoCallIntent)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Error starting video call: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Generate a unique room name based on contact number
     * This ensures both parties can join the same room
     * Using a simple format that works with public Jitsi servers
     */
    private fun generateRoomName(contactNumber: String, useTimestamp: Boolean = true): String {
        // Remove all non-alphanumeric characters from phone number
        val cleanedNumber = contactNumber.replace(Regex("[^a-zA-Z0-9]"), "")
        
        // Create a simple room name using last 10 digits
        val shortNumber = if (cleanedNumber.length > 10) {
            cleanedNumber.takeLast(10)
        } else {
            cleanedNumber
        }
        
        android.util.Log.d("JitsiMeetHelper", "generateRoomName: cleanedNumber=$cleanedNumber, shortNumber=$shortNumber")
        
        // For direct calls, use timestamp-based room name (consistent for 1 minute)
        // For other calls, add random suffix
        val suffix = if (useTimestamp) {
            // Use timestamp rounded to minute for consistency between caller and receiver
            val currentTimeMillis = System.currentTimeMillis()
            val timestamp = currentTimeMillis / 60000 // Round to minute
            val timestampSuffix = timestamp.toString().takeLast(6) // Use last 6 digits
            
            android.util.Log.d("JitsiMeetHelper", "Timestamp mode: currentMillis=$currentTimeMillis, timestamp=$timestamp, suffix=$timestampSuffix")
            timestampSuffix
        } else {
            // Random suffix for non-direct calls
            val randomSuffix = (1..4).map { ('a'..'z').random() }.joinToString("")
            android.util.Log.d("JitsiMeetHelper", "Random mode: suffix=$randomSuffix")
            randomSuffix
        }
        
        // Simple room name format: Call + last digits + suffix
        // Avoids special prefixes that might require authentication
        val roomName = "Call${shortNumber}${suffix}"
        android.util.Log.d("JitsiMeetHelper", "Final room name: $roomName")
        return roomName
    }
    
    /**
     * Get the shareable meeting URL for a contact
     * This can be shared via SMS or other messaging apps
     */
    fun getMeetingUrl(contactNumber: String): String {
        val roomName = generateRoomName(contactNumber, useTimestamp = false)
        return "$JITSI_SERVER_URL/$roomName"
    }
    
    /**
     * Get the meeting URL for direct app-to-app calls
     * Uses timestamp-based room name for consistency between caller and receiver
     */
    fun getMeetingUrlForDirectCall(contactNumber: String): String {
        val roomName = generateRoomName(contactNumber, useTimestamp = true)
        return "$JITSI_SERVER_URL/$roomName"
    }
    
    /**
     * Share the meeting URL via system share dialog
     * Allows the user to send the link via SMS, WhatsApp, etc.
     */
    fun shareMeetingUrl(
        activity: Activity,
        contactName: String,
        contactNumber: String
    ) {
        val meetingUrl = getMeetingUrl(contactNumber)
        val shareText = """
            Join my video call:
            $meetingUrl
            
            Meeting with: $contactName
            
            You can join directly from your browser - no app installation required!
        """.trimIndent()
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Video Call Invitation")
        }
        
        activity.startActivity(
            Intent.createChooser(shareIntent, "Share meeting link via")
        )
    }
    
    /**
     * Open meeting in app
     * Opens Jitsi Meet in the in-app VideoCallActivity
     */
    fun openMeetingInApp(contactNumber: String, contactName: String = "Contact") {
        val meetingUrl = getMeetingUrl(contactNumber)
        val videoCallIntent = org.fossify.phone.activities.VideoCallActivity.getStartIntent(
            context = context,
            meetingUrl = meetingUrl,
            contactName = contactName
        )
        context.startActivity(videoCallIntent)
    }
    
    /**
     * Check if Jitsi Meet is properly configured and ready
     */
    fun isVideoCallAvailable(): Boolean {
        // Video calling is always available with public Jitsi server
        // No API key or configuration required
        return true
    }
    
    /**
     * Send video call link via SMS
     * Requests SMS permission if needed and sends the Jitsi Meet link to the contact
     * @param activity The calling activity for permission handling
     * @param contactName Name of the contact
     * @param contactNumber Phone number to send SMS to
     * @param onPermissionDenied Callback when permission is denied
     */
    fun sendVideoCallLinkViaSMS(
        activity: Activity,
        contactName: String,
        contactNumber: String,
        onPermissionDenied: (() -> Unit)? = null
    ) {
        // Check if we have SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            // Request SMS permission
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST_CODE
            )
            onPermissionDenied?.invoke()
            return
        }
        
        // Generate the meeting URL
        val meetingUrl = getMeetingUrl(contactNumber)
        
        // Create the SMS message
        val smsMessage = """
            Hi $contactName! Join my video call:
            $meetingUrl
            
            Click the link to join directly from your browser - no app needed!
        """.trimIndent()
        
        try {
            // Send SMS using SmsManager
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            // Check if message is too long and needs to be split
            val parts = smsManager.divideMessage(smsMessage)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    contactNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    contactNumber,
                    null,
                    smsMessage,
                    null,
                    null
                )
            }
            
            // Show success toast
            Toast.makeText(
                context,
                "Video call link sent to $contactName",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Failed to send video call link: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Send video call link using system SMS app
     * Opens the default SMS app with pre-filled message
     * @param contactNumber Phone number to send SMS to
     * @param contactName Name of the contact
     */
    fun sendVideoCallLinkViaIntent(
        contactNumber: String,
        contactName: String
    ) {
        // Generate the meeting URL
        val meetingUrl = getMeetingUrl(contactNumber)
        
        // Create the SMS message
        val smsMessage = """
            Hi $contactName! Join my video call:
            $meetingUrl
            
            Click the link to join directly from your browser - no app needed!
        """.trimIndent()
        
        // Create SMS intent
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$contactNumber")
            putExtra("sms_body", smsMessage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(smsIntent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No SMS app found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Check if SMS permission is granted
     */
    fun hasSMSPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Send direct app-to-app video call request
     * Sends a special SMS that the app recognizes and shows incoming call screen
     * @param activity The calling activity
     * @param contactName Name of the contact being called
     * @param contactNumber Phone number to send request to
     * @param callerName Name of the person calling
     */
    fun sendDirectVideoCallRequest(
        activity: Activity,
        contactName: String,
        contactNumber: String,
        callerName: String = "User"
    ) {
        // Check if we have SMS permission
        if (!hasSMSPermission()) {
            Toast.makeText(
                context,
                "SMS permission required to send video call request",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Generate the meeting URL for direct call (timestamp-based for consistency)
        val meetingUrl = getMeetingUrlForDirectCall(contactNumber)
        
        android.util.Log.d("JitsiMeetHelper", "Direct call URL generated: $meetingUrl for contact: $contactNumber")
        
        // Create special SMS message that app will recognize
        // Format: SIMPLYDIAL_VIDEO_CALL|CallerName|MeetingURL
        val smsMessage = "SIMPLYDIAL_VIDEO_CALL|$callerName|$meetingUrl"
        
        android.util.Log.d("JitsiMeetHelper", "Sending SMS: $smsMessage")
        
        try {
            // Send SMS using SmsManager
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            smsManager.sendTextMessage(
                contactNumber,
                null,
                smsMessage,
                null,
                null
            )
            
            // Show success message
            Toast.makeText(
                context,
                "Video call request sent to $contactName",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Failed to send video call request: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
