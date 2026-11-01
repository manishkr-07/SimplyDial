package org.fossify.phone.dialogs

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import kotlin.system.exitProcess

class InterfaceModeBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogInterfaceModeSelectionBinding
    var onModeSelected: ((Int) -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogInterfaceModeSelectionBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
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
        val currentMode = requireActivity().config.interfaceMode

        binding.apply {
            radioModeSimple.isChecked = currentMode == INTERFACE_MODE_SIMPLE
            radioModeModern.isChecked = currentMode == INTERFACE_MODE_MODERN

            optionModeSimple.setOnClickListener {
                if (currentMode != INTERFACE_MODE_SIMPLE) {
                    radioModeSimple.isChecked = true
                    radioModeModern.isChecked = false
                    confirmModeSwitch(INTERFACE_MODE_SIMPLE, getString(R.string.interface_mode_simple_title))
                } else {
                    dismissAllowingStateLoss()
                }
            }

            optionModeModern.setOnClickListener {
                if (currentMode != INTERFACE_MODE_MODERN) {
                    radioModeModern.isChecked = true
                    radioModeSimple.isChecked = false
                    confirmModeSwitch(INTERFACE_MODE_MODERN, getString(R.string.interface_mode_modern_title))
                } else {
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    private fun confirmModeSwitch(targetMode: Int, targetModeTitle: String) {
        val activity = requireActivity()

        AlertDialog.Builder(activity)
            .setTitle(R.string.restart_app_to_apply_mode_title)
            .setMessage(getString(R.string.restart_app_to_apply_mode_msg, targetModeTitle))
            .setPositiveButton(R.string.restart_now) { _, _ ->
                applyModeAndRestart(targetMode)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Revert visual radio selection
                val currentMode = activity.config.interfaceMode
                binding.radioModeSimple.isChecked = currentMode == INTERFACE_MODE_SIMPLE
                binding.radioModeModern.isChecked = currentMode == INTERFACE_MODE_MODERN
            }
            .setCancelable(false)
            .show()
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
        activity.finishAffinity()
        activity.startActivity(intent)
        exitProcess(0)
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

