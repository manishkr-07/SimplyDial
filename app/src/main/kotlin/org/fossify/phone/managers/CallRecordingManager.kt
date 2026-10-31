package org.fossify.phone.managers

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.util.Log
import org.fossify.phone.models.CallRecording
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Professional Call Recording Manager
 * Handles call recording with proper error handling and device compatibility
 */
class CallRecordingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CallRecordingManager"
        private const val RECORDINGS_DIRECTORY = "CallRecordings"
        private const val AUDIO_FORMAT = ".m4a"
        
        // Beep tone configuration for consent notification
        private const val BEEP_FREQUENCY_HZ = 900.0  // 800-1000 Hz range
        private const val BEEP_DURATION_MS = 300     // 200-400ms range
        private const val SAMPLE_RATE = 44100        // Standard sample rate
        
        // Audio source fallback hierarchy for compatibility
        // MIC is prioritized as it's most reliable when speakerphone is on
        private val AUDIO_SOURCE_PRIORITY = listOf(
            MediaRecorder.AudioSource.MIC,                  // Best with speakerphone (captures both sides)
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Good for voice calls
            MediaRecorder.AudioSource.VOICE_CALL,           // Traditional call recording
            MediaRecorder.AudioSource.VOICE_RECOGNITION,    // Alternative for voice
            MediaRecorder.AudioSource.DEFAULT               // System default
        )
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var currentAudioSource: Int = MediaRecorder.AudioSource.VOICE_CALL
    private var isRecording = false
    private var recordingStartTime: Long = 0
    
    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onRecordingError: ((String) -> Unit)? = null
    var onSpeakerphoneRequired: ((Boolean) -> Unit)? = null
    
    /**
     * Get the recordings directory in Downloads folder
     * Creates the directory if it doesn't exist
     */
    private fun getRecordingsDirectory(): File {
        // Use public Downloads directory - visible in file manager
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        
        // Ensure Downloads directory exists first
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
            Log.i(TAG, "Created Downloads directory: ${downloadsDir.absolutePath}")
        }
        
        val recordingsDir = File(downloadsDir, RECORDINGS_DIRECTORY)
        if (!recordingsDir.exists()) {
            val created = recordingsDir.mkdirs()
            if (created) {
                Log.i(TAG, "Successfully created CallRecordings folder: ${recordingsDir.absolutePath}")
            } else {
                Log.e(TAG, "Failed to create CallRecordings folder: ${recordingsDir.absolutePath}")
            }
        } else {
            Log.i(TAG, "CallRecordings folder already exists: ${recordingsDir.absolutePath}")
        }
        
        return recordingsDir
    }
    
    /**
     * Generate unique filename with timestamp
     */
    private fun generateFilename(phoneNumber: String?, isIncoming: Boolean): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val direction = if (isIncoming) "IN" else "OUT"
        val number = phoneNumber?.replace("+", "")?.take(10) ?: "Unknown"
        return "CALL_${direction}_${number}_${timestamp}${AUDIO_FORMAT}"
    }
    
    /**
     * Play beep tone to notify remote party of recording (consent indicator)
     * This is MANDATORY for Google Play compliance and legal requirements
     */
    private fun playBeepTone() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sampleRate = SAMPLE_RATE
                val numSamples = (BEEP_DURATION_MS * sampleRate / 1000)
                val samples = ShortArray(numSamples)
                
                // Generate sine wave beep at specified frequency
                for (i in samples.indices) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / BEEP_FREQUENCY_HZ)
                    samples[i] = (sin(angle) * Short.MAX_VALUE * 0.5).toInt().toShort()
                }
                
                // Play beep through VOICE_CALL stream (uplink - remote party hears it)
                val audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numSamples * 2,
                    AudioTrack.MODE_STATIC
                )
                
                audioTrack.write(samples, 0, numSamples)
                audioTrack.setNotificationMarkerPosition(numSamples)
                audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        track?.stop()
                        track?.release()
                    }
                    
                    override fun onPeriodicNotification(track: AudioTrack?) {}
                })
                
                audioTrack.play()
                
                Log.i(TAG, "Beep tone played for recording consent notification")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing beep tone", e)
            }
        }
    }
    
    /**
     * Start recording with automatic audio source fallback
     * Plays mandatory beep tone to notify remote party
     */
    fun startRecording(phoneNumber: String?, isIncoming: Boolean): Boolean {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress")
            return false
        }
        
        try {
            // Get recordings directory and ensure it exists
            val recordingsDir = getRecordingsDirectory()
            if (!recordingsDir.exists()) {
                Log.e(TAG, "Failed to create recordings directory")
                onRecordingError?.invoke("Cannot create recordings folder")
                return false
            }
            
            // Check if directory is writable
            if (!recordingsDir.canWrite()) {
                Log.e(TAG, "Recordings directory is not writable: ${recordingsDir.absolutePath}")
                onRecordingError?.invoke("Cannot write to recordings folder")
                return false
            }
            
            // Create recording file
            val filename = generateFilename(phoneNumber, isIncoming)
            currentRecordingFile = File(recordingsDir, filename)
            
            Log.i(TAG, "Attempting to create recording file: ${currentRecordingFile?.absolutePath}")
            
            // Create empty file to reserve the name
            currentRecordingFile?.createNewFile()
            
            if (currentRecordingFile?.exists() != true) {
                Log.e(TAG, "Failed to create recording file")
                onRecordingError?.invoke("Cannot create recording file")
                return false
            }
            
            // Try audio sources in priority order
            var success = false
            for (audioSource in AUDIO_SOURCE_PRIORITY) {
                if (tryStartRecording(audioSource)) {
                    currentAudioSource = audioSource
                    success = true
                    Log.i(TAG, "Recording started successfully:")
                    Log.i(TAG, "  Audio Source: ${getAudioSourceName(audioSource)}")
                    Log.i(TAG, "  File: ${currentRecordingFile?.name}")
                    Log.i(TAG, "  Path: ${currentRecordingFile?.absolutePath}")
                    
                    // Enable speakerphone for MIC audio source to capture both sides
                    if (audioSource == MediaRecorder.AudioSource.MIC || 
                        audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION) {
                        Log.i(TAG, "  Requesting speakerphone for better audio capture")
                        onSpeakerphoneRequired?.invoke(true)
                    }
                    
                    break
                }
            }
            
            if (!success) {
                val error = "Failed to start recording with any audio source"
                Log.e(TAG, error)
                onRecordingError?.invoke(error)
                cleanup()
                return false
            }
            
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            // MANDATORY: Play beep to notify remote party (Google Play compliance)
            playBeepTone()
            
            onRecordingStateChanged?.invoke(true)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onRecordingError?.invoke("Recording error: ${e.message}")
            cleanup()
            return false
        }
    }
    
    /**
     * Try to start recording with specific audio source
     */
    private fun tryStartRecording(audioSource: Int): Boolean {
        return try {
            releaseMediaRecorder()
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(256000)  // Increased for better quality
                setAudioSamplingRate(48000)      // Higher sample rate
                setAudioChannels(2)              // Stereo for better capture
                setOutputFile(currentRecordingFile?.absolutePath)
                
                prepare()
                start()
            }
            
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed with audio source $audioSource: ${e.message}")
            releaseMediaRecorder()
            false
        }
    }
    
    /**
     * Stop recording and save file
     */
    fun stopRecording(): CallRecording? {
        if (!isRecording) {
            Log.w(TAG, "No recording in progress")
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            
            val duration = System.currentTimeMillis() - recordingStartTime
            val recording = currentRecordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    // Make file visible in file managers immediately
                    scanMediaFile(file.absolutePath)
                    
                    Log.i(TAG, "Recording saved successfully:")
                    Log.i(TAG, "  Path: ${file.absolutePath}")
                    Log.i(TAG, "  Size: ${file.length()} bytes")
                    Log.i(TAG, "  Duration: ${duration}ms")
                    
                    CallRecording(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        timestamp = recordingStartTime,
                        duration = duration,
                        fileSize = file.length(),
                        audioSource = getAudioSourceName(currentAudioSource)
                    )
                } else {
                    Log.w(TAG, "Recording file is empty or doesn't exist: ${file.absolutePath}")
                    null
                }
            }
            
            if (recording == null) {
                Log.e(TAG, "Recording failed - deleting empty file")
                currentRecordingFile?.delete()
            }
            
            isRecording = false
            
            // MANDATORY: Play beep to notify recording stopped
            playBeepTone()
            
            onRecordingStateChanged?.invoke(false)
            cleanup()
            
            recording
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onRecordingError?.invoke("Error stopping recording: ${e.message}")
            cleanup()
            null
        }
    }
    
    /**
     * Scan media file to make it visible in file managers
     */
    private fun scanMediaFile(filePath: String) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                arrayOf("audio/m4a")
            ) { path, uri ->
                Log.i(TAG, "Media scan completed: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media file", e)
        }
    }
    
    /**
     * Get human-readable audio source name
     */
    private fun getAudioSourceName(audioSource: Int): String {
        return when (audioSource) {
            MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.MIC -> "MICROPHONE"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Release media recorder resources
     */
    private fun releaseMediaRecorder() {
        mediaRecorder?.apply {
            try {
                reset()
                release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaRecorder", e)
            }
        }
        mediaRecorder = null
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        releaseMediaRecorder()
        currentRecordingFile = null
    }
    
    /**
     * Get all recordings
     */
    fun getAllRecordings(): List<CallRecording> {
        val recordings = mutableListOf<CallRecording>()
        val dir = getRecordingsDirectory()
        
        dir.listFiles()?.filter { it.extension == "m4a" }?.forEach { file ->
            try {
                val recording = CallRecording(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    duration = 0, // Duration not stored in filename
                    fileSize = file.length(),
                    audioSource = "UNKNOWN"
                )
                recordings.add(recording)
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing recording file: ${file.name}", e)
            }
        }
        
        return recordings.sortedByDescending { it.timestamp }
    }
    
    /**
     * Delete a recording
     */
    fun deleteRecording(recording: CallRecording): Boolean {
        return try {
            val file = File(recording.filePath)
            val deleted = file.delete()
            if (deleted) {
                Log.i(TAG, "Recording deleted: ${recording.fileName}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting recording", e)
            false
        }
    }
    
    /**
     * Check if currently recording
     */
    fun isCurrentlyRecording(): Boolean = isRecording
    
    /**
     * Get current recording duration
     */
    fun getCurrentRecordingDuration(): Long {
        return if (isRecording) {
            System.currentTimeMillis() - recordingStartTime
        } else {
            0
        }
    }
    
    /**
     * Release all resources
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        cleanup()
        onRecordingStateChanged = null
        onRecordingError = null
    }
}
