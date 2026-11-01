package org.fossify.phone.dialogs

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.databinding.DialogInterfaceModeSelectionBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.INTERFACE_MODE_MODERN
import org.fossify.phone.helpers.INTERFACE_MODE_SIMPLE

class InterfaceModeBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogInterfaceModeSelectionBinding
    private var selectedMode = INTERFACE_MODE_MODERN
    private var originalMode = INTERFACE_MODE_MODERN
    var onModeSelected: ((Int) -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogInterfaceModeSelectionBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        originalMode = requireActivity().config.interfaceMode
        selectedMode = originalMode
        setupInterfaceOptions()
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
            dialogInterfaceTitle.setTextColor(properTextColor)
            dialogInterfaceSubtitle.setTextColor(properTextColor)
            textModeSimpleTitle.setTextColor(properTextColor)
            textModeModernTitle.setTextColor(properTextColor)

            radioModeSimple.buttonTintList = ColorStateList.valueOf(properPrimaryColor)
            radioModeModern.buttonTintList = ColorStateList.valueOf(properPrimaryColor)
        }
    }

    private fun setupInterfaceOptions() {
        updateCardStates()

        binding.apply {
            optionModeSimple.setOnClickListener {
                selectedMode = INTERFACE_MODE_SIMPLE
                updateCardStates()
            }

            optionModeModern.setOnClickListener {
                selectedMode = INTERFACE_MODE_MODERN
                updateCardStates()
            }

            btnApplyMode.setOnClickListener {
                if (selectedMode == originalMode) {
                    dismissAllowingStateLoss()
                } else {
                    applyModeAndRestart(selectedMode)
                }
            }
        }
    }

    private fun updateCardStates() {
        binding.apply {
            if (selectedMode == INTERFACE_MODE_SIMPLE) {
                optionModeSimple.setBackgroundResource(R.drawable.mode_card_selected_bg)
                radioModeSimple.isChecked = true
                optionModeModern.setBackgroundResource(R.drawable.mode_card_unselected_bg)
                radioModeModern.isChecked = false
            } else {
                optionModeModern.setBackgroundResource(R.drawable.mode_card_selected_bg)
                radioModeModern.isChecked = true
                optionModeSimple.setBackgroundResource(R.drawable.mode_card_unselected_bg)
                radioModeSimple.isChecked = false
            }

            if (selectedMode != originalMode) {
                val modeTitle = if (selectedMode == INTERFACE_MODE_SIMPLE) {
                    getString(R.string.interface_mode_simple_title)
                } else {
                    getString(R.string.interface_mode_modern_title)
                }
                btnApplyMode.text = "Apply $modeTitle & Restart"
                btnApplyMode.alpha = 1.0f
            } else {
                btnApplyMode.text = "Current Mode Active (Close)"
                btnApplyMode.alpha = 0.85f
            }
        }
    }

    private fun applyModeAndRestart(newMode: Int) {
        val activity = requireActivity()
        val config = activity.config

        config.interfaceMode = newMode

        if (newMode == INTERFACE_MODE_SIMPLE) {
            // Apply Senior-friendly defaults
            config.directOneTapCalling = true
            config.showQuickActionsSheet = false
            config.fontSize = FONT_SIZE_EXTRA_LARGE
            config.dialpadVibration = true
            config.dialpadBeeps = true
            config.alwaysShowFullscreen = true
            config.disableSwipeToAnswer = true
        } else {
            // Apply Modern & Advanced defaults
            config.directOneTapCalling = false
            config.showQuickActionsSheet = true
            config.fontSize = FONT_SIZE_MEDIUM
            config.alwaysShowFullscreen = false
            config.disableSwipeToAnswer = false
        }

        onModeSelected?.invoke(newMode)
        dismissAllowingStateLoss()

        // Seamless whole app restart: Clear backstack and launch MainActivity fresh
        val intent = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.startActivity(intent)
        activity.finishAffinity()
    }

    companion object {
        private const val TAG = "InterfaceModeBottomSheetDialog"

        fun show(
            fragmentManager: FragmentManager,
            onModeSelected: ((Int) -> Unit)? = null
        ): InterfaceModeBottomSheetDialog {
            return InterfaceModeBottomSheetDialog().apply {
                this.onModeSelected = onModeSelected
                show(fragmentManager, TAG)
            }
        }
    }
}
