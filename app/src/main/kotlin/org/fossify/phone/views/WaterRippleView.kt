package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import org.fossify.phone.R

class WaterRippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Ripple(
        var radius: Float = 0f,
        var alpha: Int = 255,
        var startTime: Long = 0L
    )

    private val ripples = mutableListOf<Ripple>()
    private val maxRipples = 4
    private val rippleDuration = 2000L // 2 seconds per ripple
    private var animator: ValueAnimator? = null
    
    // Theme-aware colors for water wave effect
    private val rippleColors = listOf(
        ContextCompat.getColor(context, R.color.primary_color),
        ContextCompat.getColor(context, R.color.primary_variant_color),
        ContextCompat.getColor(context, R.color.secondary_color)
    )
    
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private var centerX = 0f
    private var centerY = 0f
    private var maxRadius = 0f
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        maxRadius = minOf(w, h) / 2f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentTime = System.currentTimeMillis()
        
        // Draw all ripples
        ripples.forEachIndexed { index, ripple ->
            val elapsed = currentTime - ripple.startTime
            val progress = elapsed.toFloat() / rippleDuration
            
            if (progress <= 1f) {
                // Calculate ripple properties
                ripple.radius = maxRadius * progress
                ripple.alpha = ((1f - progress) * 180).toInt() // Fade out as it expands
                
                // Use different colors for variety
                val colorIndex = index % rippleColors.size
                ripplePaint.color = rippleColors[colorIndex]
                ripplePaint.alpha = ripple.alpha
                ripplePaint.strokeWidth = 4f - (progress * 2f) // Thinner as it expands
                
                // Draw the ripple ring
                canvas.drawCircle(centerX, centerY, ripple.radius, ripplePaint)
            }
        }
        
        // Remove completed ripples
        ripples.removeAll { currentTime - it.startTime > rippleDuration }
    }
    
    fun startRipples() {
        if (animator?.isRunning == true) return
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = rippleDuration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            
            addUpdateListener {
                // Add new ripple periodically
                val currentTime = System.currentTimeMillis()
                if (ripples.isEmpty() || currentTime - ripples.last().startTime > rippleDuration / maxRipples) {
                    if (ripples.size < maxRipples) {
                        ripples.add(Ripple(startTime = currentTime))
                    }
                }
                invalidate()
            }
            
            start()
        }
    }
    
    fun stopRipples() {
        animator?.cancel()
        animator = null
        ripples.clear()
        invalidate()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRipples()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRipples()
    }
}
