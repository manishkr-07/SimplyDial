package org.fossify.phone.dialogs

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogEmergencySosBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.startCallWithConfirmationCheck

class EmergencySOSDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogEmergencySosBinding

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogEmergencySosBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)

        setupButtons()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = activity ?: return
        val textColor = activity.getProperTextColor()

        binding.apply {
            dialogSosTitle.setTextColor(textColor)
            btnSetupEmergencyContact.setTextColor(textColor)
        }
    }

    private fun setupButtons() {
        val activity = activity as? SimpleActivity ?: return
        val config = activity.config

        val emergencyNumber = config.emergencySOSNumber
        val trustedContact = config.emergencySOSContact

        binding.apply {
            tvEmergencyServicesLabel.text = "${getString(R.string.call_emergency_services)} ($emergencyNumber)"

            val hasTrusted = trustedContact.isNotBlank()
            btnCallTrustedContact.beVisibleIf(hasTrusted)
            if (hasTrusted) {
                tvTrustedContactLabel.text = "${getString(R.string.call_trusted_contact)}: $trustedContact"
                btnCallTrustedContact.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    dismissAllowingStateLoss()
                    activity.startCallWithConfirmationCheck(trustedContact, trustedContact)
                }
            }

            btnCallEmergencyServices.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                dismissAllowingStateLoss()
                activity.startCallWithConfirmationCheck(emergencyNumber, getString(R.string.emergency_services))
            }

            btnSetupEmergencyContact.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                showConfigureDialog()
            }
        }
    }

    private fun showConfigureDialog() {
        val activity = activity ?: return
        val config = activity.config

        val input = EditText(activity).apply {
            hint = "e.g. 112 or Family Phone Number"
            setText(config.emergencySOSContact.ifEmpty { config.emergencySOSNumber })
            setSelection(text.length)
        }

        activity.getAlertDialogBuilder()
            .setTitle(R.string.setup_emergency_contact)
            .setView(input)
            .setPositiveButton(R.string.apply) { _, _ ->
                val newNumber = input.text.toString().trim()
                if (newNumber.isNotEmpty()) {
                    config.emergencySOSContact = newNumber
                    activity.toast(R.string.settings_updated)
                    setupButtons()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(input, this)
            }
    }

    companion object {
        private const val TAG = "EmergencySOSDialog"

        fun show(fragmentManager: FragmentManager): EmergencySOSDialog {
            return EmergencySOSDialog().apply {
                show(fragmentManager, TAG)
            }
        }
    }
}

