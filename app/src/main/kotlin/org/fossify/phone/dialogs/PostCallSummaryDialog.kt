package org.fossify.phone.dialogs

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogPostCallSummaryBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallIntent
import org.fossify.phone.helpers.CommunicationHelper

class PostCallSummaryDialog : BottomSheetDialogFragment() {

    private var _binding: DialogPostCallSummaryBinding? = null
    private val binding get() = _binding!!

    private var callerName: String = ""
    private var phoneNumber: String = ""
    private var durationSeconds: Int = 0
    private var countDownTimer: CountDownTimer? = null
    private var onDismissCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callerName = arguments?.getString(ARG_NAME) ?: ""
        phoneNumber = arguments?.getString(ARG_NUMBER) ?: ""
        durationSeconds = arguments?.getInt(ARG_DURATION) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPostCallSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.postCallName.text = callerName.ifEmpty { phoneNumber }
        binding.postCallNumber.text = phoneNumber
        binding.postCallDuration.text = durationSeconds.getFormattedDuration()

        // Existing note
        val existingNote = requireContext().config.getContactNote(phoneNumber)
        if (existingNote.isNotEmpty()) {
            binding.postCallNoteInput.setText(existingNote)
        }

        binding.postCallSaveNoteBtn.setOnClickListener {
            val newNote = binding.postCallNoteInput.text.toString().trim()
            requireContext().config.setContactNote(phoneNumber, newNote)
            requireContext().toast(R.string.settings_updated)
        }

        // WhatsApp
        binding.postCallWhatsappBtn.setOnClickListener {
            cancelCountdown()
            activity?.let { act ->
                CommunicationHelper.openWhatsApp(act, phoneNumber)
            }
        }

        // Telegram
        binding.postCallTelegramBtn.setOnClickListener {
            cancelCountdown()
            activity?.let { act ->
                CommunicationHelper.openTelegram(act, phoneNumber)
            }
        }

        // Redial
        binding.postCallRedialBtn.setOnClickListener {
            cancelCountdown()
            dismiss()
            (activity as? org.fossify.phone.activities.SimpleActivity)?.startCallIntent(phoneNumber)
        }

        // Done button
        binding.postCallDoneBtn.setOnClickListener {
            val newNote = binding.postCallNoteInput.text.toString().trim()
            if (newNote.isNotEmpty() && newNote != existingNote) {
                requireContext().config.setContactNote(phoneNumber, newNote)
            }
            dismiss()
        }

        // Auto-dismiss in 8 seconds if untouched
        startCountdown()
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(8000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                if (_binding != null) {
                    val secondsLeft = (millisUntilFinished / 1000) + 1
                    binding.postCallTimer.text = "Closing in ${secondsLeft}s"
                }
            }

            override fun onFinish() {
                dismissAllowingStateLoss()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        if (_binding != null) {
            binding.postCallTimer.visibility = View.GONE
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cancelCountdown()
        onDismissCallback?.invoke()
    }

    override fun onDestroyView() {
        cancelCountdown()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_NAME = "arg_name"
        private const val ARG_NUMBER = "arg_number"
        private const val ARG_DURATION = "arg_duration"

        fun show(
            fragmentManager: FragmentManager,
            name: String,
            number: String,
            duration: Int,
            onDismiss: (() -> Unit)? = null
        ) {
            PostCallSummaryDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_NUMBER, number)
                    putInt(ARG_DURATION, duration)
                }
                onDismissCallback = onDismiss
                show(fragmentManager, "PostCallSummaryDialog")
            }
        }
    }
}

