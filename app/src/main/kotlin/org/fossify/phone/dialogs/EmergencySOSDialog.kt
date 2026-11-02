package org.fossify.phone.dialogs

import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
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


            btnSendSosSmsLocation.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val targetContact = trustedContact.ifBlank { emergencyNumber }
                if (targetContact.isBlank()) {
                    activity.toast(R.string.sos_no_contact)
                    showConfigureDialog()
                    return@setOnClickListener
                }
                sendSosLocationSms(activity, targetContact)
                dismissAllowingStateLoss()
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

    private fun sendSosLocationSms(activity: SimpleActivity, recipientNumber: String) {
        val batteryManager = activity.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val batteryStr = if (batteryLevel >= 0) "$batteryLevel%" else "Unknown"

        var locationUrl: String? = null
        try {
            val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val hasFine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        locationUrl = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                    }
                }
            }
        } catch (_: Exception) {
            // gracefully fallback
        }

        val message = if (locationUrl != null) {
            "EMERGENCY SOS! I need immediate help. Battery: $batteryStr. My location: $locationUrl"
        } else {
            "EMERGENCY SOS! I need immediate help. Battery: $batteryStr. (Location unavailable)"
        }

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(recipientNumber, null, message, null, null)
            activity.toast(R.string.sos_sent_success)
        } catch (e: Exception) {
            activity.toast("Failed to send SOS SMS: ${e.message}")
        }
    }

}
