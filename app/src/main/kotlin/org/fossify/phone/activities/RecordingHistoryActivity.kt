package org.fossify.phone.activities

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.phone.R
import org.fossify.phone.adapters.RecordingAdapter
import org.fossify.phone.databinding.ActivityRecordingHistoryBinding
import org.fossify.phone.managers.CallRecordingManager
import org.fossify.phone.models.CallRecording
import java.io.File

/**
 * Recording History Activity
 * Displays all call recordings with Play, Share, and Delete options
 */
class RecordingHistoryActivity : SimpleActivity() {
    
    private val binding by viewBinding(ActivityRecordingHistoryBinding::inflate)
    private lateinit var recordingManager: CallRecordingManager
    private lateinit var adapter: RecordingAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        setupToolbar()
        recordingManager = CallRecordingManager(this)
        setupRecyclerView()
        loadRecordings()
    }
    
    override fun onResume() {
        super.onResume()
        setupToolbar(binding.recordingHistoryToolbar, NavigationIcon.Arrow)
        updateTextColors(binding.recordingHistoryCoordinator)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    private fun setupToolbar() {
        binding.recordingHistoryToolbar.title = getString(R.string.recording_history)
        setupToolbar(binding.recordingHistoryToolbar, NavigationIcon.Arrow)
    }
    
    private fun setupRecyclerView() {
        adapter = RecordingAdapter(
            onPlay = { recording -> playRecording(recording) },
            onStop = { stopPlayback() },
            onShare = { recording -> shareRecording(recording) },
            onDelete = { recording -> deleteRecording(recording) }
        )
        
        binding.recordingsList.apply {
            layoutManager = LinearLayoutManager(this@RecordingHistoryActivity)
            adapter = this@RecordingHistoryActivity.adapter
        }
    }
    
    private fun loadRecordings() {
        val recordings = recordingManager.getAllRecordings()
        
        if (recordings.isEmpty()) {
            binding.recordingsList.beGone()
            binding.recordingsPlaceholder.beVisible()
            binding.recordingsPlaceholder.text = getString(R.string.no_recordings_found)
        } else {
            binding.recordingsList.beVisible()
            binding.recordingsPlaceholder.beGone()
            adapter.updateRecordings(recordings)
        }
    }
    
    /**
     * Play a recording
     */
    private fun playRecording(recording: CallRecording) {
        try {
            // Stop any currently playing audio
            if (currentlyPlayingPath == recording.filePath) {
                // Same recording - toggle play/pause
                if (mediaPlayer?.isPlaying == true) {
                    stopPlayback()
                    return
                }
            } else {
                stopPlayback()
            }
            
            // Start playback
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    stopPlayback()
                }
                
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    toast(R.string.playback_error)
                    true
                }
            }
            
            currentlyPlayingPath = recording.filePath
            adapter.setPlayingItem(recording.filePath)
            
        } catch (e: Exception) {
            toast(R.string.playback_error)
        }
    }
    
    /**
     * Stop playback
     */
    private fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
        }
        currentlyPlayingPath = null
        adapter.setPlayingItem(null)
    }
    
    /**
     * Share a recording
     */
    private fun shareRecording(recording: CallRecording) {
        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                toast(R.string.file_not_found)
                return
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, recording.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_recording)))
            
        } catch (e: Exception) {
            toast(R.string.share_error)
        }
    }
    
    /**
     * Delete a recording with confirmation
     */
    private fun deleteRecording(recording: CallRecording) {
        val message = getString(R.string.delete_recording_confirmation, recording.fileName)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_recording)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ ->
                val success = recordingManager.deleteRecording(recording)
                if (success) {
                    toast(R.string.recording_deleted)
                    
                    // Stop playback if this recording was playing
                    if (currentlyPlayingPath == recording.filePath) {
                        stopPlayback()
                    }
                    
                    // Reload list
                    loadRecordings()
                } else {
                    toast(R.string.delete_error)
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
