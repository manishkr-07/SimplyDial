package org.fossify.phone.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogVideoCallRejoinBinding
import org.fossify.phone.helpers.VideoCallManager

/**
 * Dialog shown when user leaves video call screen or returns to app
 * Provides options to rejoin the ongoing call or disconnect
 */
class VideoCallRejoinDialog(
    private val activity: Activity,
    private val contactName: String? = null,
    private val onRejoin: () -> Unit,
    private val onDisconnect: () -> Unit
) {
    
    private var dialog: Dialog? = null
    private lateinit var binding: DialogVideoCallRejoinBinding
    
    fun show() {
        // Don't show multiple dialogs
        if (dialog?.isShowing == true) {
            return
        }
        
        // Create custom dialog with Material Design
        dialog = Dialog(activity, R.style.Theme_Fossify_Dialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            
            // Inflate the custom layout
            binding = DialogVideoCallRejoinBinding.inflate(LayoutInflater.from(activity))
            setContentView(binding.root)
            
            // Set rounded corners background
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            
            // Make dialog non-dismissible by outside touch
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            
            setupViews()
        }
        
        dialog?.show()
    }
    
    private fun setupViews() {
        binding.apply {
            // Update message with contact name if available
            val message = if (contactName != null) {
                "You have an ongoing video call with $contactName"
            } else {
                "You have an ongoing video call"
            }
            videoCallMessage.text = message
            
            // Rejoin button - return to video call
            rejoinButton.setOnClickListener {
                dismiss()
                onRejoin()
            }
            
            // Disconnect button - end video call
            disconnectButton.setOnClickListener {
                dismiss()
                onDisconnect()
            }
        }
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
    
    companion object {
        private var currentDialog: VideoCallRejoinDialog? = null
        
        /**
         * Show rejoin dialog if video call is active
         * Ensures only one dialog is shown at a time
         */
        fun showIfNeeded(activity: Activity) {
            // Check if video call is active
            if (!VideoCallManager.hasActiveVideoCall()) {
                return
            }
            
            // Don't show if already showing
            if (currentDialog?.dialog?.isShowing == true) {
                return
            }
            
            // Create and show new dialog
            currentDialog = VideoCallRejoinDialog(
                activity = activity,
                contactName = VideoCallManager.currentContactName,
                onRejoin = {
                    // Start video call activity
                    VideoCallManager.getRejoinIntent(activity)?.let { intent ->
                        activity.startActivity(intent)
                    }
                },
                onDisconnect = {
                    // End video call
                    VideoCallManager.endVideoCall(activity)
                }
            )
            
            currentDialog?.show()
        }
        
        /**
         * Dismiss any showing dialog
         */
        fun dismissCurrent() {
            currentDialog?.dismiss()
            currentDialog = null
        }
    }
}
