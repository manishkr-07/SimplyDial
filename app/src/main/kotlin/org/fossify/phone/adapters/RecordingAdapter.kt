package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.phone.R
import org.fossify.phone.databinding.ItemRecordingBinding
import org.fossify.phone.models.CallRecording

/**
 * Adapter for displaying call recordings list
 */
class RecordingAdapter(
    private val onPlay: (CallRecording) -> Unit,
    private val onStop: () -> Unit,
    private val onShare: (CallRecording) -> Unit,
    private val onDelete: (CallRecording) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder>() {
    
    var recordings = listOf<CallRecording>()
        private set
    
    private var playingItemPath: String? = null
    
    fun updateRecordings(newRecordings: List<CallRecording>) {
        recordings = newRecordings
        notifyDataSetChanged()
    }
    
    fun setPlayingItem(path: String?) {
        val oldPath = playingItemPath
        playingItemPath = path
        
        // Notify only the affected items
        recordings.indexOfFirst { it.filePath == oldPath }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
        recordings.indexOfFirst { it.filePath == path }.takeIf { it >= 0 }?.let {
            notifyItemChanged(it)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecordingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(recordings[position])
    }
    
    override fun getItemCount() = recordings.size
    
    inner class RecordingViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(recording: CallRecording) {
            binding.apply {
                // Recording file name
                recordingFileName.text = recording.fileName
                
                // Date and time
                recordingDate.text = recording.getFormattedDate()
                
                // File size and duration
                recordingDetails.text = buildString {
                    append(recording.getFormattedFileSize())
                    if (recording.duration > 0) {
                        append(" • ")
                        append(recording.getFormattedDuration())
                    }
                }
                
                // Check if this recording is currently playing
                val isPlaying = playingItemPath == recording.filePath
                
                // Update play button icon and text
                if (isPlaying) {
                    recordingPlayButton.setImageResource(R.drawable.ic_pause_vector)
                    recordingPlayButton.contentDescription = root.context.getString(R.string.stop_playback)
                } else {
                    recordingPlayButton.setImageResource(R.drawable.ic_play_vector)
                    recordingPlayButton.contentDescription = root.context.getString(R.string.play_recording)
                }
                
                // Play/Stop button
                recordingPlayButton.setOnClickListener {
                    if (isPlaying) {
                        onStop()
                    } else {
                        onPlay(recording)
                    }
                }
                
                // Share button
                recordingShareButton.setOnClickListener {
                    onShare(recording)
                }
                
                // Delete button
                recordingDeleteButton.setOnClickListener {
                    onDelete(recording)
                }
            }
        }
    }
}
