package org.fossify.phone.extensions

import android.animation.ValueAnimator
import android.graphics.*
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import org.fossify.phone.helpers.Config

/**
 * Adds an animated gradient ring around the ImageView (for profile photos)
 * Colors: Golden -> Sunrise Orange with slow rotation (Light mode)
 * Colors: Cyan -> Teal with slow rotation (Dark mode)
 */
fun ImageView.addAnimatedGradientRing() {
    // Create a custom drawable that wraps the existing drawable with a gradient ring
    val originalDrawable = drawable
    val config = Config(context)
    
    post {
        val size = maxOf(width, height)
        if (size > 0) {
            // Create gradient ring drawable
            val ringDrawable = object : android.graphics.drawable.Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                }
                
                private var rotationAngle = 0f
                private val gradientColors = if (config.isDarkThemeEnabled) {
                    intArrayOf(
                        Color.parseColor("#d1c8b6"),  // Beige
                        Color.parseColor("#00b2b6"),  // Teal
                        Color.parseColor("#2E8B57"),  // Seagreen
                        Color.parseColor("#d1c8b6")   // Back to Beige
                    )
                } else {
                    intArrayOf(
                        Color.parseColor("#FFD700"),  // Golden
                        Color.parseColor("#FFA500"),  // Orange
                        Color.parseColor("#FF6347"),  // Sunrise Orange
                        Color.parseColor("#FFD700")   // Back to Golden
                    )
                }
                
                override fun draw(canvas: Canvas) {
                    val bounds = bounds
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()
                    val radius = (minOf(bounds.width(), bounds.height()) / 2f) - paint.strokeWidth / 2
                    
                    // Create sweep gradient
                    val gradient = SweepGradient(centerX, centerY, gradientColors, null)
                    val matrix = Matrix()
                    matrix.postRotate(rotationAngle, centerX, centerY)
                    gradient.setLocalMatrix(matrix)
                    paint.shader = gradient
                    
                    // Draw ring
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                
                override fun setAlpha(alpha: Int) {
                    paint.alpha = alpha
                }
                
                override fun setColorFilter(colorFilter: ColorFilter?) {
                    paint.colorFilter = colorFilter
                }
                
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                
                fun updateRotation(angle: Float) {
                    rotationAngle = angle
                    invalidateSelf()
                }
            }
            
            // Set as foreground drawable
            foreground = ringDrawable
            
            // Animate rotation
            val animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 6000  // 6 seconds for full rotation
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val angle = animation.animatedValue as Float
                    ringDrawable.updateRotation(angle)
                }
            }
            animator.start()
            
            // Store animator in tag to stop it later if needed
            setTag(org.fossify.phone.R.id.gradient_ring_animator, animator)
        }
    }
}

/**
 * Removes the animated gradient ring
 */
fun ImageView.removeAnimatedGradientRing() {
    val animator = getTag(org.fossify.phone.R.id.gradient_ring_animator) as? ValueAnimator
    animator?.cancel()
    foreground = null
}
