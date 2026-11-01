package org.fossify.phone.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import org.fossify.phone.helpers.Config

class GradientRingImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    
    private var rotationAngle = 0f
    private var isAnimating = false
    private val config = Config(context)
    
    // Dynamic gradient colors based on theme
    private val gradientColors = if (config.isDarkThemeEnabled || org.fossify.phone.helpers.ThemeManager.isDarkThemeEnabled(context)) {
        intArrayOf(
            Color.parseColor("#d1c8b6"),  // Beige
            Color.parseColor("#00b2b6"),  // Teal
            Color.parseColor("#2E8B57"),  // Seagreen
            Color.parseColor("#d1c8b6")   // Back to Beige for smooth loop
        )
    } else {
        intArrayOf(
            Color.parseColor("#FFD700"),  // Golden
            Color.parseColor("#FFA500"),  // Orange
            Color.parseColor("#FF6347"),  // Sunrise Orange/Tomato
            Color.parseColor("#FFD700")   // Back to Golden for smooth loop
        )
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }

    private fun updateGradient() {
        if (width > 0 && height > 0) {
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = minOf(width, height) / 2f - ringPaint.strokeWidth / 2
            
            // Create sweep gradient for rotating effect
            val gradient = SweepGradient(
                centerX,
                centerY,
                gradientColors,
                null
            )
            
            val matrix = Matrix()
            matrix.postRotate(rotationAngle, centerX, centerY)
            gradient.setLocalMatrix(matrix)
            
            ringPaint.shader = gradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (width > 0 && height > 0) {
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = minOf(width, height) / 2f - ringPaint.strokeWidth / 2
            
            // Draw the gradient ring
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
        }
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animateRotation()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(animationRunnable)
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                rotationAngle += 1f  // Slow rotation: 1 degree per frame
                if (rotationAngle >= 360f) {
                    rotationAngle = 0f
                }
                updateGradient()
                invalidate()
                postDelayed(this, 16)  // ~60fps
            }
        }
    }

    private fun animateRotation() {
        post(animationRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
