package org.fossify.phone.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.fossify.commons.extensions.toast
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogQuickDeclineSmsBinding

class QuickDeclineSmsDialog : BottomSheetDialogFragment() {

    private var _binding: DialogQuickDeclineSmsBinding? = null
    private val binding get() = _binding!!

    private var callerInfo: String = ""
    private var onMessageSelected: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callerInfo = arguments?.getString(ARG_CALLER_INFO) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQuickDeclineSmsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.quickDeclineCaller.text = callerInfo

        val onPresetClick: (String) -> Unit = { msg ->
            dismissAllowingStateLoss()
            onMessageSelected?.invoke(msg)
        }

        binding.quickReplyPreset1.setOnClickListener {
            onPresetClick(binding.quickReplyPreset1.text.toString())
        }

        binding.quickReplyPreset2.setOnClickListener {
            onPresetClick(binding.quickReplyPreset2.text.toString())
        }

        binding.quickReplyPreset3.setOnClickListener {
            onPresetClick(binding.quickReplyPreset3.text.toString())
        }

        binding.quickReplyPreset4.setOnClickListener {
            onPresetClick(binding.quickReplyPreset4.text.toString())
        }

        binding.quickReplySendCustomBtn.setOnClickListener {
            val customMsg = binding.quickReplyCustomInput.text.toString().trim()
            if (customMsg.isNotEmpty()) {
                dismissAllowingStateLoss()
                onMessageSelected?.invoke(customMsg)
            } else {
                context?.toast(R.string.quick_reply_custom)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "QuickDeclineSmsDialog"
        private const val ARG_CALLER_INFO = "caller_info"

        fun show(
            fragmentManager: FragmentManager,
            callerInfo: String,
            onMessageSelected: (String) -> Unit
        ): QuickDeclineSmsDialog {
            return QuickDeclineSmsDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CALLER_INFO, callerInfo)
                }
                this.onMessageSelected = onMessageSelected
                show(fragmentManager, TAG)
            }
        }
    }
}

