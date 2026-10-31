package org.fossify.phone.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.viewBinding
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityVideoCallBinding
import org.fossify.phone.helpers.VideoCallManager
import org.fossify.phone.dialogs.VideoCallRejoinDialog

/**
 * Activity for in-app video calling using Jitsi Meet WebView
 * Opens Jitsi Meet within the app instead of external browser
 */
class VideoCallActivity : SimpleActivity() {
    
    private val binding by viewBinding(ActivityVideoCallBinding::inflate)
    private var isInForeground = true
    private var hasLeftCall = false
    
    companion object {
        private const val EXTRA_MEETING_URL = "meeting_url"
        private const val EXTRA_CONTACT_NAME = "contact_name"
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val MICROPHONE_PERMISSION_REQUEST = 101
        
        fun getStartIntent(context: Context, meetingUrl: String, contactName: String): Intent {
            return Intent(context, VideoCallActivity::class.java).apply {
                putExtra(EXTRA_MEETING_URL, meetingUrl)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
    
    private lateinit var meetingUrl: String
    private lateinit var contactName: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        // Get meeting URL and contact name from intent
        meetingUrl = intent.getStringExtra(EXTRA_MEETING_URL) ?: run {
            Toast.makeText(this, "Invalid meeting URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Contact"
        
        // Register this video call with the manager
        VideoCallManager.startVideoCall(this, meetingUrl, contactName)
        
        // Request camera and microphone permissions
        if (!hasRequiredPermissions()) {
            requestVideoCallPermissions()
        } else {
            setupWebView()
        }
    }
    
    /**
     * Setup WebView with Jitsi Meet configuration
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.videoCallWebView.apply {
            // Enable hardware acceleration for better video performance
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // Add JavaScript interface for app communication
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onCallEnded() {
                    runOnUiThread {
                        // End the video call session and close activity immediately
                        VideoCallManager.endVideoCall(this@VideoCallActivity)
                        // Clear WebView to prevent any further navigation
                        binding.videoCallWebView.stopLoading()
                        binding.videoCallWebView.loadUrl("about:blank")
                        // Close the activity
                        finish()
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun onCallStarted() {
                    runOnUiThread {
                        binding.loadingOverlay.visibility = View.GONE
                    }
                }
            }, "AndroidApp")
            
            settings.apply {
                // Core JavaScript settings
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // Media settings - crucial for video calls
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                
                // Enable video and audio
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                
                // Camera and microphone permissions
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                
                // Iframe-like embedding settings
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                
                // Cache for better performance
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                
                // Mixed content for HTTPS/HTTP
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // Desktop mode for better Jitsi compatibility
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                
                // Text settings
                textZoom = 100
                minimumFontSize = 8
                defaultFontSize = 16
                
                // Security settings
                setGeolocationEnabled(true)
                
                // Layout settings
                layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            }
            
            // WebView client for page navigation
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.let { url ->
                        val urlString = url.toString()
                        
                        // Allow all Jitsi meeting URLs to load
                        if (urlString.contains("meet.jit.si") || 
                            urlString.contains("beta.meet.jit.si") ||
                            urlString.contains("8x8.vc") ||
                            urlString.contains("meet.ffmuc.net") ||
                            urlString.contains("meet.evolix.org") ||
                            urlString.contains("jitsi")) {
                            
                            // But block post-call pages from these domains
                            if (urlString.contains("/static/close") ||
                                urlString.contains("/close.html") ||
                                urlString.contains("/static/feedback") ||
                                urlString.contains("/feedback.html") ||
                                urlString.contains("/thankyou") ||
                                urlString.contains("/static/thankYou")) {
                                
                                // Post-call page detected - close immediately
                                view?.stopLoading()
                                VideoCallManager.endVideoCall(this@VideoCallActivity)
                                finish()
                                return true
                            }
                            
                            // Allow normal Jitsi URLs
                            return false
                        }
                        
                        // Block non-Jitsi external URLs
                        if (!urlString.startsWith("about:") && !urlString.startsWith("data:")) {
                            // External URL detected - likely post-call redirect
                            view?.stopLoading()
                            VideoCallManager.endVideoCall(this@VideoCallActivity)
                            finish()
                            return true
                        }
                    }
                    return false
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    
                    // Check if navigating to a post-call page - immediately stop and exit
                    url?.let {
                        if (it.contains("/static/close") || 
                            it.contains("/close.html") ||
                            it.contains("/static/feedback") || 
                            it.contains("/feedback.html") ||
                            it.contains("/thankyou") ||
                            it.contains("/static/thankYou")) {
                            // Stop loading immediately and close
                            view?.stopLoading()
                            VideoCallManager.endVideoCall(this@VideoCallActivity)
                            finish()
                            return
                        }
                    }
                    
                    // Show loading overlay for normal pages
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // Check if this is a post-call page - immediately stop and exit
                    url?.let {
                        if (it.contains("/static/close") || 
                            it.contains("/close.html") ||
                            it.contains("/static/feedback") || 
                            it.contains("/feedback.html") ||
                            it.contains("/thankyou") ||
                            it.contains("/static/thankYou")) {
                            // Stop any further loading and close immediately
                            view?.stopLoading()
                            VideoCallManager.endVideoCall(this@VideoCallActivity)
                            finish()
                            return
                        }
                    }
                    
                    // Hide loading overlay
                    binding.loadingOverlay.visibility = View.GONE
                    
                    // Inject CSS and JavaScript to clean up UI and detect call end
                    val hideElements = """
                        javascript:(function() {
                            // Hide Jitsi header/footer for cleaner embedding
                            var style = document.createElement('style');
                            style.innerHTML = `
                                .watermark { display: none !important; }
                                body { overflow: hidden !important; }
                                #largeVideoContainer { height: 100vh !important; }
                            `;
                            document.head.appendChild(style);
                            
                            // Monitor for call end - check for hangup button clicks
                            function monitorCallEnd() {
                                // Listen for hangup button clicks
                                var hangupButton = document.querySelector('[aria-label*="Hang up"]') || 
                                                  document.querySelector('[aria-label*="hangup"]') ||
                                                  document.querySelector('[data-testid*="hangup"]') ||
                                                  document.querySelector('.hangup-button');
                                
                                if (hangupButton && !hangupButton.hasAttribute('data-monitored')) {
                                    hangupButton.setAttribute('data-monitored', 'true');
                                    hangupButton.addEventListener('click', function() {
                                        // Call ended by user, notify Android
                                        if (window.AndroidApp) {
                                            window.AndroidApp.onCallEnded();
                                        }
                                    });
                                }
                                
                                // Also monitor if we're redirected to a post-call page
                                if (window.location.href.includes('/static/close') || 
                                    window.location.href.includes('/close.html') ||
                                    window.location.href.includes('/static/feedback') ||
                                    window.location.href.includes('/feedback.html') ||
                                    window.location.href.includes('/thankyou') ||
                                    window.location.href.includes('/static/thankYou')) {
                                    // Call ended, notify Android
                                    if (window.AndroidApp) {
                                        window.AndroidApp.onCallEnded();
                                    }
                                }
                            }
                            
                            // Auto-click join button if present
                            setTimeout(function() {
                                // Look for join button and auto-click it
                                var joinButton = document.querySelector('[data-testid="prejoin.joinMeeting"]');
                                if (!joinButton) {
                                    joinButton = document.querySelector('button[aria-label*="Join"]');
                                }
                                if (!joinButton) {
                                    joinButton = document.querySelector('.prejoin-preview-dropdown-container button');
                                }
                                if (joinButton) {
                                    joinButton.click();
                                }
                                
                                // Ensure full screen video
                                var video = document.querySelector('video');
                                if (video) {
                                    video.style.width = '100%';
                                    video.style.height = '100%';
                                    video.style.objectFit = 'cover';
                                }
                                
                                // Start monitoring for call end
                                monitorCallEnd();
                            }, 1000);
                            
                            // Keep monitoring for hangup button
                            setInterval(monitorCallEnd, 1000);
                        })()
                    """.trimIndent()
                    
                    view?.loadUrl(hideElements)
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    
                    // Log the error for debugging
                    val errorMsg = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        "Error: ${error?.description} (Code: ${error?.errorCode})"
                    } else {
                        "Error loading video call"
                    }
                    
                    android.util.Log.e("VideoCallActivity", "WebView error: $errorMsg, URL: ${request?.url}")
                    
                    // Only show error for main frame errors
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(
                            this@VideoCallActivity,
                            "Failed to load video call. Please check internet connection.",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // If main page fails to load, close the activity
                        Handler().postDelayed({
                            finish()
                        }, 2000)
                    }
                }
            }
            
            // Chrome client for permissions and features
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.let {
                        // Grant camera and microphone permissions to WebView
                        if (hasRequiredPermissions()) {
                            it.grant(it.resources)
                        } else {
                            it.deny()
                            Toast.makeText(
                                this@VideoCallActivity,
                                "Camera and microphone permissions required",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    // Log console messages for debugging
                    consoleMessage?.let {
                        android.util.Log.d(
                            "VideoCallWebView",
                            "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                        )
                    }
                    return true
                }
            }
            
            // Load Jitsi Meet URL
            android.util.Log.d("VideoCallActivity", "Loading Jitsi Meet URL: $meetingUrl")
            loadUrl(meetingUrl)
        }
    }
    
    /**
     * Check if camera and microphone permissions are granted
     */
    private fun hasRequiredPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val micGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        return cameraGranted && micGranted
    }
    
    /**
     * Request camera and microphone permissions
     */
    private fun requestVideoCallPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                CAMERA_PERMISSION_REQUEST
            )
        } else {
            setupWebView()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, setup WebView
                    setupWebView()
                } else {
                    // Permissions denied
                    Toast.makeText(
                        this,
                        "Camera and microphone permissions are required for video calls",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        isInForeground = false
        
        // If video call is still active and user left the screen, mark it
        if (VideoCallManager.hasActiveVideoCall() && !hasLeftCall) {
            hasLeftCall = true
            
            // Show rejoin dialog after a short delay if user navigated within app
            Handler().postDelayed({
                if (!isInForeground && VideoCallManager.hasActiveVideoCall()) {
                    // Check if app is still in foreground (not minimized)
                    if (!isFinishing) {
                        VideoCallRejoinDialog.showIfNeeded(this)
                    }
                }
            }, 500)
        }
    }
    
    override fun onResume() {
        super.onResume()
        isInForeground = true
        hasLeftCall = false
        
        // Dismiss any showing dialog since we're back in the call
        VideoCallRejoinDialog.dismissCurrent()
    }
    
    override fun onBackPressed() {
        if (binding.videoCallWebView.canGoBack()) {
            binding.videoCallWebView.goBack()
        } else {
            // Show rejoin dialog when leaving the call
            if (VideoCallManager.hasActiveVideoCall()) {
                showRejoinDialog()
            } else {
                super.onBackPressed()
            }
        }
    }
    
    override fun onDestroy() {
        // Clean up WebView
        binding.videoCallWebView.apply {
            loadUrl("about:blank")
            clearHistory()
            clearCache(true)
            destroy()
        }
        
        // End video call session if this was the active call
        if (!hasLeftCall) {
            VideoCallManager.endVideoCall(this)
        }
        
        super.onDestroy()
    }
    
    /**
     * Show rejoin dialog when user tries to leave the video call
     */
    private fun showRejoinDialog() {
        VideoCallRejoinDialog(
            activity = this,
            contactName = contactName,
            onRejoin = {
                // Stay in current activity, no action needed
            },
            onDisconnect = {
                // End video call and close activity
                VideoCallManager.endVideoCall(this)
                finish()
            }
        ).show()
    }
}
