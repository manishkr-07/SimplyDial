package org.fossify.phone.models

import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for a call recording
 */
data class CallRecording(
    val filePath: String,
    val fileName: String,
    val timestamp: Long,
    val duration: Long,  // in milliseconds
    val fileSize: Long,  // in bytes
    val audioSource: String
) {
    
    /**
     * Get formatted date and time
     */
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Get formatted date (shorter version for list)
     */
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Get formatted duration
     */
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        val hours = minutes / 60
        val mins = minutes % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
    
    /**
     * Get formatted file size
     */
    fun getFormattedFileSize(): String {
        val kb = fileSize / 1024.0
        val mb = kb / 1024.0
        
        return if (mb >= 1.0) {
            String.format("%.2f MB", mb)
        } else {
            String.format("%.2f KB", kb)
        }
    }
    
    /**
     * Parse phone number from filename
     */
    fun getPhoneNumber(): String? {
        return try {
            val parts = fileName.split("_")
            if (parts.size >= 3) {
                parts[2] // Phone number part
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if incoming or outgoing
     */
    fun isIncoming(): Boolean {
        return fileName.contains("_IN_")
    }
    
    fun getCallDirection(): String {
        return if (isIncoming()) "Incoming" else "Outgoing"
    }
}
