package org.fossify.phone.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogCallRecordingConsentBinding

/**
 * MANDATORY Call Recording Consent Dialog
 * Required for Google Play Store compliance and legal requirements
 * 
 * This dialog MUST be shown before enabling call recording feature
 * User MUST explicitly consent to recording laws and beep notification
 */
class CallRecordingConsentDialog(
    val activity: Activity,
    val onConsent: () -> Unit,
    val onDecline: () -> Unit
) {
    
    init {
        val binding = DialogCallRecordingConsentBinding.inflate(LayoutInflater.from(activity))
        
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.i_understand_and_consent) { dialog, _ ->
                onConsent()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.decline) { dialog, _ ->
                onDecline()
                dialog.dismiss()
            }
            .setCancelable(false)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.call_recording_consent_title) {
                    // Dialog is non-dismissible until user explicitly accepts or declines
                    it.setCanceledOnTouchOutside(false)
                }
            }
    }
}
