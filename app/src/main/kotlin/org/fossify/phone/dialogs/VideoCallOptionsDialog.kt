package org.fossify.phone.dialogs

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.databinding.DialogVideoCallOptionsBinding
import org.fossify.phone.helpers.JitsiMeetHelper

/**
 * Dialog to choose video call method:
 * 1. Send SMS & Start Call - Auto-send SMS then start video call in app
 * 2. Generate & Share Link - Create link, allow sharing, then start call in app
 * 3. Direct App-to-App Call - For users with same app, instant video call
 */
class VideoCallOptionsDialog(
    val activity: SimpleActivity,
    val contactName: String,
    val contactNumber: String
) {
    private val binding = DialogVideoCallOptionsBinding.inflate(LayoutInflater.from(activity))
    private var dialog: AlertDialog? = null
    private val jitsiMeetHelper = JitsiMeetHelper(activity)

    init {
        setupViews()
        createDialog()
    }

    private fun setupViews() {
        binding.apply {
            dialogTitle.setTextColor(activity.getProperTextColor())

            // Option 1: Send SMS & Start Call
            optionSendSmsAndCall.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handleSendSmsAndCall()
            }

            // Option 2: Generate & Share Link
            optionGenerateAndShare.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handleGenerateAndShare()
            }

            // Option 3: Direct App-to-App Call
            optionDirectCall.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handleDirectCall()
            }

            // Close button
            closeDialogButton.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dialog?.dismiss()
            }
        }
    }

    private fun createDialog() {
        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.choose_video_call_method) { alertDialog ->
                    dialog = alertDialog
                    // Add fade-in animation
                    val fadeIn = android.view.animation.AnimationUtils.loadAnimation(
                        activity,
                        android.R.anim.fade_in
                    )
                    fadeIn.duration = 300
                    binding.root.startAnimation(fadeIn)
                }
            }
    }

    /**
     * Option 1: Send SMS & Start Call
     * Automatically sends SMS with video link, then starts call immediately in the app
     */
    private fun handleSendSmsAndCall() {
        // Check if we have SMS permission
        if (!jitsiMeetHelper.hasSMSPermission()) {
            // Request SMS permission
            jitsiMeetHelper.sendVideoCallLinkViaSMS(
                activity = activity as Activity,
                contactName = contactName,
                contactNumber = contactNumber,
                onPermissionDenied = {
                    Toast.makeText(
                        activity,
                        "SMS permission required to send video call link",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
            
            // Show message about permission
            Toast.makeText(
                activity,
                "Grant SMS permission to send video call link",
                Toast.LENGTH_LONG
            ).show()
            
            // Keep dialog open for permission request
            return
        }

        // Send SMS automatically
        jitsiMeetHelper.sendVideoCallLinkViaSMS(
            activity = activity as Activity,
            contactName = contactName,
            contactNumber = contactNumber
        )

        // Start video call immediately in the app (caller's side)
        // Using a slight delay to ensure SMS is sent first
        binding.root.postDelayed({
            startVideoCallInApp()
        }, 500)

        // Dismiss dialog
        dialog?.dismiss()
    }

    /**
     * Option 2: Generate & Share Link
     * Generates a link, allows user to share it anywhere, then starts call in app
     */
    private fun handleGenerateAndShare() {
        // Share the meeting link using system share dialog
        jitsiMeetHelper.shareMeetingUrl(
            activity = activity as Activity,
            contactName = contactName,
            contactNumber = contactNumber
        )

        // Start video call immediately in the app (caller's side)
        // Using a slight delay to allow share dialog to appear first
        binding.root.postDelayed({
            startVideoCallInApp()
        }, 500)

        // Dismiss dialog
        dialog?.dismiss()
    }

    /**
     * Option 3: Direct App-to-App Call
     * For contacts using the same app - instant video call without SMS or link
     * Checks if contact has the app, sends notification, and both join same room
     */
    private fun handleDirectCall() {
        // Check if contact is registered as app user
        val appUserRegistry = org.fossify.phone.helpers.AppUserRegistry(activity)
        
        // For testing: temporarily allow direct calls to any contact
        // Remove this after testing
        val isAppUser = appUserRegistry.isAppUser(contactNumber) || true // Always true for testing
        
        if (!isAppUser) {
            // Contact doesn't have the app
            Toast.makeText(
                activity,
                activity.getString(org.fossify.phone.R.string.contact_not_app_user),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Get caller name (you can customize this)
        val callerName = appUserRegistry.getMyPhoneNumber() ?: "User"
        
        Toast.makeText(
            activity,
            activity.getString(org.fossify.phone.R.string.sending_video_call_request),
            Toast.LENGTH_SHORT
        ).show()
        
        // Send direct video call request via SMS
        jitsiMeetHelper.sendDirectVideoCallRequest(
            activity = activity as android.app.Activity,
            contactName = contactName,  
            contactNumber = contactNumber,
            callerName = callerName
        )
        
        // Start video call in caller's app immediately with same timestamp-based room
        binding.root.postDelayed({
            startVideoCallInAppForDirectCall()
        }, 1000)
        
        // Dismiss dialog
        dialog?.dismiss()
    }

    /**
     * Start video call in the app (opens Jitsi Meet in WebView)
     * This is called after SMS is sent or link is shared
     */
    private fun startVideoCallInApp() {
        val userName = "Me" // You can customize this with actual user name from settings
        jitsiMeetHelper.startVideoCall(
            contactName = contactName,
            contactNumber = contactNumber,
            userName = userName,
            useTimestampRoom = false
        )
    }
    
    /**
     * Start video call for direct app-to-app calls
     * Uses timestamp-based room to ensure both parties join same room
     */
    private fun startVideoCallInAppForDirectCall() {
        val userName = "Me" // You can customize this with actual user name from settings
        jitsiMeetHelper.startVideoCall(
            contactName = contactName,
            contactNumber = contactNumber,
            userName = userName,
            useTimestampRoom = true // Use timestamp-based room for consistency
        )
    }
}
