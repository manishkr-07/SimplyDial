package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import org.fossify.phone.helpers.Config

class BlurBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var blurProgress = 0f
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maxBlurRadius = 25f
    private val config = Config(context)
    
    // Dynamic color based on theme
    private val blurColor: Int
        get() = if (config.isDarkThemeEnabled || org.fossify.phone.helpers.ThemeManager.isDarkThemeEnabled(context)) {
            Color.parseColor("#2E8B57") // Seagreen/blue dark shade for dark mode
        } else {
            Color.parseColor("#E0F2FE") // Soft modern sky-blue tint for light mode
        }
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (blurProgress <= 0f) return
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Create radial gradient from center outward (yellowish to transparent)
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = Math.sqrt((width * width + height * height).toDouble()).toFloat() / 2f
        
        val gradient = RadialGradient(
            centerX,
            centerY,
            maxRadius,
            intArrayOf(
                adjustAlpha(blurColor, (220 * blurProgress).toInt()),  // Theme color in center
                adjustAlpha(blurColor, (150 * blurProgress).toInt()),  // Medium theme color
                adjustAlpha(blurColor, (80 * blurProgress).toInt()),   // Light theme color
                adjustAlpha(blurColor, 0)                              // Transparent at edges
            ),
            floatArrayOf(0.0f, 0.3f, 0.6f, 1.0f),
            Shader.TileMode.CLAMP
        )
        
        blurPaint.shader = gradient
        
        // Apply blur effect
        val currentBlur = maxBlurRadius * blurProgress
        if (currentBlur > 0.5f) {
            blurPaint.maskFilter = BlurMaskFilter(currentBlur, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Draw the blurred background
        canvas.drawRect(0f, 0f, width, height, blurPaint)
    }
    
    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
    
    fun animateIn(duration: Long = 300) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                blurProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun animateOut(duration: Long = 200) {
        ValueAnimator.ofFloat(blurProgress, 0f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                blurProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
