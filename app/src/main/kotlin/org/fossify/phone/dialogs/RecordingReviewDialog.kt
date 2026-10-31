package org.fossify.phone.dialogs

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import org.fossify.phone.R
import org.fossify.phone.models.CallRecording
import java.io.File

/**
 * Professional dialog for reviewing call recordings with playback controls
 */
class RecordingReviewDialog(
    private val context: Context,
    private val recording: CallRecording,
    private val onSave: () -> Unit,
    private val onDiscard: () -> Unit
) {
    
    private var dialog: AlertDialog? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    
    private lateinit var btnPlayPause: ImageView
    private lateinit var playbackTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var phoneNumber: TextView
    private lateinit var duration: TextView
    private lateinit var fileSize: TextView
    private lateinit var fileName: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDiscard: MaterialButton
    
    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_recording_review, null)
        
        initViews(view)
        setupViews()
        setupListeners()
        prepareMediaPlayer()
        
        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
        
        dialog?.window?.setBackgroundDrawableResource(android.R.drawable.dialog_holo_light_frame)
        dialog?.show()
    }
    
    private fun initViews(view: View) {
        btnPlayPause = view.findViewById(R.id.btn_play_pause)
        playbackTime = view.findViewById(R.id.playback_time)
        seekBar = view.findViewById(R.id.playback_seekbar)
        phoneNumber = view.findViewById(R.id.phone_number)
        duration = view.findViewById(R.id.duration)
        fileSize = view.findViewById(R.id.file_size)
        fileName = view.findViewById(R.id.file_name)
        btnSave = view.findViewById(R.id.btn_save)
        btnDiscard = view.findViewById(R.id.btn_discard)
    }
    
    private fun setupViews() {
        // Set recording details
        phoneNumber.text = recording.getPhoneNumber() ?: "Unknown Number"
        duration.text = "Duration: ${recording.getFormattedDuration()}"
        fileSize.text = "Size: ${recording.getFormattedFileSize()}"
        fileName.text = recording.fileName
    }
    
    private fun setupListeners() {
        // Play/Pause button
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
        
        // SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mediaPlayer != null) {
                    val position = (progress * mediaPlayer!!.duration) / 100
                    mediaPlayer?.seekTo(position)
                    updatePlaybackTime(position)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Pause while seeking
                if (isPlaying) {
                    mediaPlayer?.pause()
                }
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Resume if was playing
                if (isPlaying) {
                    mediaPlayer?.start()
                }
            }
        })
        
        // Save button
        btnSave.setOnClickListener {
            cleanup()
            dialog?.dismiss()
            onSave()
        }
        
        // Discard button
        btnDiscard.setOnClickListener {
            showDiscardConfirmation()
        }
    }
    
    private fun prepareMediaPlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
            }
            
            mediaPlayer?.setOnCompletionListener {
                // Reset to beginning when playback completes
                isPlaying = false
                seekBar.progress = 0
                playbackTime.text = "00:00"
                btnPlayPause.setImageResource(R.drawable.ic_play_vector)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecordingReviewDialog", "Error preparing media player", e)
        }
    }
    
    private fun startPlayback() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            btnPlayPause.setImageResource(R.drawable.ic_pause_vector)
            updatePlaybackProgress()
        } catch (e: Exception) {
            android.util.Log.e("RecordingReviewDialog", "Error starting playback", e)
        }
    }
    
    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            btnPlayPause.setImageResource(R.drawable.ic_play_vector)
        } catch (e: Exception) {
            android.util.Log.e("RecordingReviewDialog", "Error pausing playback", e)
        }
    }
    
    private fun updatePlaybackProgress() {
        if (isPlaying && mediaPlayer != null) {
            try {
                val currentPosition = mediaPlayer!!.currentPosition
                val duration = mediaPlayer!!.duration
                
                // Update seekbar
                val progress = if (duration > 0) {
                    (currentPosition * 100) / duration
                } else {
                    0
                }
                seekBar.progress = progress
                
                // Update time
                updatePlaybackTime(currentPosition)
                
                // Schedule next update
                handler.postDelayed({ updatePlaybackProgress() }, 100)
            } catch (e: Exception) {
                android.util.Log.e("RecordingReviewDialog", "Error updating progress", e)
            }
        }
    }
    
    private fun updatePlaybackTime(milliseconds: Int) {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        playbackTime.text = String.format("%02d:%02d", minutes, secs)
    }
    
    private fun showDiscardConfirmation() {
        AlertDialog.Builder(context)
            .setTitle("Discard Recording?")
            .setMessage("This recording will be permanently deleted. This action cannot be undone.")
            .setPositiveButton("Discard") { _, _ ->
                cleanup()
                
                // Delete the file
                try {
                    File(recording.filePath).delete()
                } catch (e: Exception) {
                    android.util.Log.e("RecordingReviewDialog", "Error deleting file", e)
                }
                
                dialog?.dismiss()
                onDiscard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun cleanup() {
        // Stop playback
        if (isPlaying) {
            pausePlayback()
        }
        
        // Release media player
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            android.util.Log.e("RecordingReviewDialog", "Error releasing media player", e)
        }
        
        // Remove callbacks
        handler.removeCallbacksAndMessages(null)
    }
    
    fun dismiss() {
        cleanup()
        dialog?.dismiss()
    }
}
