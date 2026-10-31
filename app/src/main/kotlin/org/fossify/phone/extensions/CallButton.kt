package org.fossify.phone.extensions

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import org.fossify.phone.R

/**
 * Adds an animated rotating gradient border to call action buttons
 * Colors: Red → Baby Pink with slow rotation
 * @param cornerRadius Array of 8 floats for corner radii [topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft]
 */
fun ImageView.addRotatingBorder(cornerRadius: FloatArray = floatArrayOf(24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f)) {
    post {
        val size = maxOf(width, height)
        if (size > 0) {
            // Create gradient ring drawable
            val ringDrawable = object : android.graphics.drawable.Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                
                private var rotationAngle = 0f
                private val gradientColors = intArrayOf(
                    Color.parseColor("#FF0000"),  // Red
                    Color.parseColor("#FFB6C1"),  // Baby Pink  
                    Color.parseColor("#FF0000")   // Back to Red
                )
                
                override fun draw(canvas: Canvas) {
                    val bounds = bounds
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()
                    val rect = RectF(
                        bounds.left + paint.strokeWidth / 2,
                        bounds.top + paint.strokeWidth / 2,
                        bounds.right - paint.strokeWidth / 2,
                        bounds.bottom - paint.strokeWidth / 2
                    )
                    
                    // Create sweep gradient
                    val gradient = SweepGradient(centerX, centerY, gradientColors, null)
                    val matrix = Matrix()
                    matrix.postRotate(rotationAngle, centerX, centerY)
                    gradient.setLocalMatrix(matrix)
                    paint.shader = gradient
                    
                    // Draw rounded rectangle ring
                    val path = Path()
                    path.addRoundRect(rect, cornerRadius, Path.Direction.CW)
                    canvas.drawPath(path, paint)
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
                duration = 4000  // 4 seconds for full rotation
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val angle = animation.animatedValue as Float
                    ringDrawable.updateRotation(angle)
                }
            }
            animator.start()
            
            // Store animator in tag
            setTag(R.id.call_button_animator, this)
        }
    }
}

/**
 * Button position enum for selective corner rounding
 */
enum class ButtonPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * Updates call button appearance based on selected state and position
 */
fun ImageView.updateCallButtonState(isSelected: Boolean, position: ButtonPosition = ButtonPosition.MIDDLE_CENTER) {
    val backgroundRes = when {
        isSelected -> when (position) {
            ButtonPosition.TOP_LEFT -> R.drawable.call_button_rect_selected_top_left
            ButtonPosition.TOP_RIGHT -> R.drawable.call_button_rect_selected_top_right
            ButtonPosition.BOTTOM_LEFT -> R.drawable.call_button_rect_selected_bottom_left
            ButtonPosition.BOTTOM_RIGHT -> R.drawable.call_button_rect_selected_bottom_right
            else -> R.drawable.call_button_rect_selected_center
        }
        else -> when (position) {
            ButtonPosition.TOP_LEFT -> R.drawable.call_button_rect_top_left
            ButtonPosition.TOP_RIGHT -> R.drawable.call_button_rect_top_right
            ButtonPosition.BOTTOM_LEFT -> R.drawable.call_button_rect_bottom_left
            ButtonPosition.BOTTOM_RIGHT -> R.drawable.call_button_rect_bottom_right
            else -> R.drawable.call_button_rect_center
        }
    }
    setBackgroundResource(backgroundRes)
}

/**
 * Updates call button appearance based on selected state (backward compatibility)
 */
fun ImageView.updateCallButtonState(isSelected: Boolean) {
    if (isSelected) {
        // Selected: Gradient background + rotating border
        setBackgroundResource(R.drawable.call_button_selected_gradient)
    } else {
        // Unselected: Transparent + rotating border
        setBackgroundResource(R.drawable.call_button_unselected_border)
    }
}

/**
 * Get corner radius array for button position
 * All buttons now have fully rounded corners (24dp on all sides)
 */
fun getCornerRadiusForPosition(position: ButtonPosition): FloatArray {
    val dp24 = 24f * Resources.getSystem().displayMetrics.density
    
    // All buttons have rounded corners on all sides
    return floatArrayOf(dp24, dp24, dp24, dp24, dp24, dp24, dp24, dp24)
}

/**
 * Removes the rotating border animation
 */
fun ImageView.removeRotatingBorder() {
    val animator = getTag(R.id.call_button_animator) as? ValueAnimator
    animator?.cancel()
    foreground = null
}
