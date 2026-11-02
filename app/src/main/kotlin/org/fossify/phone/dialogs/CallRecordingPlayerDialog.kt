package org.fossify.phone.dialogs

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogCallRecordingPlayerBinding
import org.fossify.phone.managers.CallRecordingManager
import org.fossify.phone.models.CallRecording
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingPlayerDialog : BottomSheetDialogFragment() {

    private var _binding: DialogCallRecordingPlayerBinding? = null
    private val binding get() = _binding!!

    private var filePath: String = ""
    private var fileName: String = ""
    private var timestamp: Long = 0L
    private var fileSize: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserTracking = false
    private var currentSpeedIndex = 0
    private val speedOptions = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = arrayOf("1.0x", "1.25x", "1.5x", "2.0x")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePath = arguments?.getString(ARG_FILE_PATH) ?: ""
        fileName = arguments?.getString(ARG_FILE_NAME) ?: ""
        timestamp = arguments?.getLong(ARG_TIMESTAMP) ?: 0L
        fileSize = arguments?.getLong(ARG_FILE_SIZE) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCallRecordingPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.playerRecordingTitle.text = fileName
        val dateStr = if (timestamp > 0) {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))
        } else {
            ""
        }
        val mbStr = "%.1f MB".format(fileSize / (1024f * 1024f))
        binding.playerRecordingSubtitle.text = "$dateStr • $mbStr"

        setupPlayer()
        setupListeners()
    }

    private fun setupPlayer() {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                requireContext().toast(R.string.no_recordings_found)
                dismiss()
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                val totalDurationSec = duration / 1000
                binding.playerTotalTime.text = totalDurationSec.getFormattedDuration()
                binding.playerSeekbar.max = duration

                setOnCompletionListener {
                    binding.playerPlayPauseFab.setImageResource(R.drawable.ic_play_vector)
                    binding.playerSeekbar.progress = 0
                    binding.playerCurrentTime.text = 0.getFormattedDuration()
                    handler.removeCallbacks(updateProgressTask)
                }

                setOnErrorListener { _, _, _ ->
                    requireContext().toast(R.string.playback_error)
                    dismiss()
                    true
                }
            }

            startPlayback()
        } catch (_: Exception) {
            requireContext().toast(R.string.playback_error)
            dismiss()
        }
    }

    private fun startPlayback() {
        mediaPlayer?.let { player ->
            player.start()
            binding.playerPlayPauseFab.setImageResource(R.drawable.ic_pause_vector)
            handler.post(updateProgressTask)
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            binding.playerPlayPauseFab.setImageResource(R.drawable.ic_play_vector)
            handler.removeCallbacks(updateProgressTask)
        }
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }
    }

    private val updateProgressTask = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (!isUserTracking && player.isPlaying) {
                    val currentPos = player.currentPosition
                    binding.playerSeekbar.progress = currentPos
                    binding.playerCurrentTime.text = (currentPos / 1000).getFormattedDuration()
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    private fun setupListeners() {
        binding.playerPlayPauseFab.setOnClickListener {
            togglePlayPause()
        }

        binding.playerSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.playerCurrentTime.text = (progress / 1000).getFormattedDuration()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = false
                mediaPlayer?.seekTo(seekBar?.progress ?: 0)
            }
        })

        // Rewind 5s
        binding.playerRewindBtn.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPos = (player.currentPosition - 5000).coerceAtLeast(0)
                player.seekTo(newPos)
                binding.playerSeekbar.progress = newPos
                binding.playerCurrentTime.text = (newPos / 1000).getFormattedDuration()
            }
        }

        // Forward 5s
        binding.playerForwardBtn.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPos = (player.currentPosition + 5000).coerceAtMost(player.duration)
                player.seekTo(newPos)
                binding.playerSeekbar.progress = newPos
                binding.playerCurrentTime.text = (newPos / 1000).getFormattedDuration()
            }
        }

        // Playback Speed
        binding.playerSpeedBtn.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
            val newSpeed = speedOptions[currentSpeedIndex]
            binding.playerSpeedBtn.text = speedLabels[currentSpeedIndex]
            try {
                mediaPlayer?.let { player ->
                    player.playbackParams = player.playbackParams.setSpeed(newSpeed)
                }
            } catch (_: Exception) {}
        }

        // Share Audio File
        binding.playerShareBtn.setOnClickListener {
            try {
                val file = File(filePath)
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Call Recording"))
            } catch (_: Exception) {
                requireContext().toast("Could not share file")
            }
        }

        // Delete Audio File
        binding.playerDeleteBtn.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete)
                .setMessage("Delete recording $fileName?")
                .setPositiveButton(R.string.delete) { _, _ ->
                val manager = CallRecordingManager(requireContext())
                val recording = CallRecording(filePath, fileName, timestamp, 0, fileSize, "UNKNOWN")
                manager.deleteRecording(recording)
                requireContext().toast("Recording deleted")
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        handler.removeCallbacks(updateProgressTask)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        handler.removeCallbacks(updateProgressTask)
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FILE_PATH = "arg_file_path"
        private const val ARG_FILE_NAME = "arg_file_name"
        private const val ARG_TIMESTAMP = "arg_timestamp"
        private const val ARG_FILE_SIZE = "arg_file_size"

        fun show(fragmentManager: FragmentManager, recording: CallRecording) {
            CallRecordingPlayerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, recording.filePath)
                    putString(ARG_FILE_NAME, recording.fileName)
                    putLong(ARG_TIMESTAMP, recording.timestamp)
                    putLong(ARG_FILE_SIZE, recording.fileSize)
                }
                show(fragmentManager, "CallRecordingPlayerDialog")
            }
        }
    }
}

