package org.fossify.phone.dialogs

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.phone.R
import org.fossify.phone.views.BlurBackgroundView

class CustomCallConfirmationDialog(
    val activity: BaseSimpleActivity,
    private val callee: String,
    private val callback: () -> Unit
) {
    
    private var dialog: Dialog? = null
    private var blurView: BlurBackgroundView? = null
    
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_call_confirmation, null)
        
        // Set up views
        val messageTextView = view.findViewById<TextView>(R.id.confirmation_dialog_message)
        val callIcon = view.findViewById<ImageView>(R.id.call_icon)
        val callIconContainer = view.findViewById<android.view.View>(R.id.call_icon_container)
        val closeButton = view.findViewById<ImageView>(R.id.close_dialog_button)
        
        // Set the contact name
        messageTextView.text = callee
        
        // Animate the call icon with pulsing effect
        val scaleAnimator = ObjectAnimator.ofFloat(callIcon, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleAnimatorY = ObjectAnimator.ofFloat(callIcon, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scaleAnimator.start()
        scaleAnimatorY.start()
        
        // Tap the call icon to initiate call
        callIconContainer.setOnClickListener {
            callback()
            dialog?.dismiss()
        }
        
        // Close button (X) to dismiss dialog
        closeButton.setOnClickListener {
            dialog?.dismiss()
        }
        
        // Create container with blur background
        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // Add blur background view
            blurView = BlurBackgroundView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setOnClickListener {
                    // Dismiss dialog when clicking outside
                    dialog?.dismiss()
                }
            }
            addView(blurView)
            
            // Add dialog content centered with 85% width
            val dialogWidth = (activity.resources.displayMetrics.widthPixels * 0.85).toInt()
            val contentContainer = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dialogWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                addView(view)
            }
            addView(contentContainer)
        }
        
        // Create and show dialog
        dialog = Dialog(activity).apply {
            setContentView(container)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                // Add blur flag for better blur effect on supported devices
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            setCancelable(true)
            setOnDismissListener {
                scaleAnimator.cancel()
                scaleAnimatorY.cancel()
                blurView?.animateOut()
            }
            show()
            
            // Animate blur in
            blurView?.animateIn()
        }
    }
}
