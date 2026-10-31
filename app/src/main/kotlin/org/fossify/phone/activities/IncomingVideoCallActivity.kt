package org.fossify.phone.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityIncomingVideoCallBinding
import org.fossify.phone.helpers.AppUserRegistry

/**
 * Activity for incoming app-to-app video calls
 * Shows accept/decline interface when another app user initiates a direct call
 */
class IncomingVideoCallActivity : SimpleActivity() {
    
    private val binding by viewBinding(ActivityIncomingVideoCallBinding::inflate)
    private lateinit var appUserRegistry: AppUserRegistry
    
    companion object {
        private const val EXTRA_CALLER_NAME = "caller_name"
        private const val EXTRA_CALLER_NUMBER = "caller_number"
        private const val EXTRA_MEETING_URL = "meeting_url"
        
        fun getStartIntent(
            context: Context, 
            callerName: String, 
            callerNumber: String, 
            meetingUrl: String
        ): Intent {
            return Intent(context, IncomingVideoCallActivity::class.java).apply {
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_MEETING_URL, meetingUrl)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
    
    private lateinit var callerName: String
    private lateinit var callerNumber: String
    private lateinit var meetingUrl: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show when locked
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        
        setContentView(binding.root)
        
        appUserRegistry = AppUserRegistry(this)
        
        // Get caller information
        callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
        callerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: ""
        meetingUrl = intent.getStringExtra(EXTRA_MEETING_URL) ?: run {
            finish()
            return
        }
        
        // Mark this number as an app user
        if (callerNumber.isNotEmpty()) {
            appUserRegistry.markContactAsAppUser(callerNumber)
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.apply {
            callerName.text = this@IncomingVideoCallActivity.callerName
            callerNumber.text = this@IncomingVideoCallActivity.callerNumber
            
            // Accept button - join the video call
            acceptButton.setOnClickListener {
                acceptCall()
            }
            
            // Decline button - reject the call
            declineButton.setOnClickListener {
                declineCall()
            }
        }
    }
    
    private fun acceptCall() {
        // Start video call activity with the meeting URL
        val videoCallIntent = VideoCallActivity.getStartIntent(
            context = this,
            meetingUrl = meetingUrl,
            contactName = callerName
        )
        startActivity(videoCallIntent)
        finish()
    }
    
    private fun declineCall() {
        // Simply close the activity
        finish()
    }
    
    override fun onBackPressed() {
        // Prevent back button from dismissing call
        // User must explicitly accept or decline
    }
}
