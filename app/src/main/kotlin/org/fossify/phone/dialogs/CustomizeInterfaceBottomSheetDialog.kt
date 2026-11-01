package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogCustomizeInterfaceBinding
import org.fossify.phone.extensions.config

class CustomizeInterfaceBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogCustomizeInterfaceBinding
    var onSaved: (() -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogCustomizeInterfaceBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        setupSwitches()
        setupListeners()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColors()
    }

    private fun setupColors() {
        val activity = requireActivity()
        val properTextColor = activity.getProperTextColor()
        val properPrimaryColor = activity.getProperPrimaryColor()

        activity.updateTextColors(binding.root)

        binding.apply {
            dialogCustomizeTitle.setTextColor(properTextColor)
            dialogCustomizeSubtitle.setTextColor(properTextColor)

            val trackColor = ColorStateList.valueOf(properPrimaryColor)
            switchDirectOneTap.trackTintList = trackColor
            switchQuickActions.trackTintList = trackColor
            switchLargeAnswer.trackTintList = trackColor
            switchFullscreenCall.trackTintList = trackColor
            switchDialpadVibration.trackTintList = trackColor
            switchDialpadBeeps.trackTintList = trackColor
        }
    }

    private fun setupSwitches() {
        val config = requireActivity().config

        binding.apply {
            switchDirectOneTap.isChecked = config.directOneTapCalling
            switchQuickActions.isChecked = config.showQuickActionsSheet
            switchLargeAnswer.isChecked = config.disableSwipeToAnswer
            switchFullscreenCall.isChecked = config.alwaysShowFullscreen
            switchDialpadVibration.isChecked = config.dialpadVibration
            switchDialpadBeeps.isChecked = config.dialpadBeeps
        }
    }

    private fun setupListeners() {
        val activity = requireActivity()
        val config = activity.config

        binding.apply {
            holderDirectOneTap.setOnClickListener { switchDirectOneTap.toggle() }
            holderQuickActions.setOnClickListener { switchQuickActions.toggle() }
            holderLargeAnswer.setOnClickListener { switchLargeAnswer.toggle() }
            holderFullscreenCall.setOnClickListener { switchFullscreenCall.toggle() }
            holderDialpadVibration.setOnClickListener { switchDialpadVibration.toggle() }
            holderDialpadBeeps.setOnClickListener { switchDialpadBeeps.toggle() }

            btnResetDefaults.setOnClickListener {
                if (config.isSimpleMode) {
                    switchDirectOneTap.isChecked = true
                    switchQuickActions.isChecked = false
                    switchLargeAnswer.isChecked = true
                    switchFullscreenCall.isChecked = true
                    switchDialpadVibration.isChecked = true
                    switchDialpadBeeps.isChecked = true
                } else {
                    switchDirectOneTap.isChecked = false
                    switchQuickActions.isChecked = true
                    switchLargeAnswer.isChecked = false
                    switchFullscreenCall.isChecked = false
                    switchDialpadVibration.isChecked = false
                    switchDialpadBeeps.isChecked = false
                }
            }

            btnApplySave.setOnClickListener {
                config.directOneTapCalling = switchDirectOneTap.isChecked
                config.showQuickActionsSheet = switchQuickActions.isChecked
                config.disableSwipeToAnswer = switchLargeAnswer.isChecked
                config.alwaysShowFullscreen = switchFullscreenCall.isChecked
                config.dialpadVibration = switchDialpadVibration.isChecked
                config.dialpadBeeps = switchDialpadBeeps.isChecked

                activity.toast(activity.getString(R.string.settings_updated))
                onSaved?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {
        private const val TAG = "CustomizeInterfaceBottomSheetDialog"

        fun show(
            fragmentManager: FragmentManager,
            onSaved: (() -> Unit)? = null
        ): CustomizeInterfaceBottomSheetDialog {
            return CustomizeInterfaceBottomSheetDialog().apply {
                this.onSaved = onSaved
                show(fragmentManager, TAG)
            }
        }
    }
}

