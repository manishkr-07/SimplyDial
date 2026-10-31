package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import org.fossify.phone.R
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Custom View for swipeable Accept/Decline call buttons
 * Inside a glassmorphic yellowish container
 */
class SwipeCallButtonsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Callbacks
    var onAcceptCall: (() -> Unit)? = null
    var onDeclineCall: (() -> Unit)? = null

    // Paint objects
    private val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val acceptPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val declinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Dimensions
    private var containerWidth = 0f
    private var containerHeight = 0f
    private var containerRadius = 0f
    private var buttonRadius = 0f
    private var centerX = 0f
    private var centerY = 0f
    
    // Button positions
    private var acceptButtonX = 0f
    private var declineButtonX = 0f
    private val buttonY get() = centerY
    
    // Touch handling
    private var isDraggingAccept = false
    private var isDraggingDecline = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentAcceptX = 0f
    private var currentDeclineX = 0f
    private var hasReachedThreshold = false
    private var hasMoved = false // Track if user moved finger significantly
    
    // Thresholds
    private val swipeThreshold = 0.6f // 60% of distance to center
    private val minMovementThreshold = 30f // Minimum pixels to consider as intentional movement
    private val returnToOriginalThreshold = 40f // If button is within 40px of original, consider it "returned"
    
    // Long press handling
    private var longPressTimer: ValueAnimator? = null
    private val longPressDelay = 1500L // 1.5 seconds
    private var pressStartTime = 0L
    private var longPressProgress = 0f // 0.0 to 1.0
    private var longPressCompleted = false // Track if long press completed
    
    // Animations
    private var glowAlpha = 0.3f
    private var glowAnimator: ValueAnimator? = null

    init {
        setupPaints()
        startGlowAnimation()
    }

    private fun setupPaints() {
        // Container with glassmorphic effect
        val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
        val surfaceColor = ContextCompat.getColor(context, R.color.surface_color)
        val acceptColor = ContextCompat.getColor(context, R.color.call_accept)
        val rejectColor = ContextCompat.getColor(context, R.color.call_reject)
        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
        
        containerPaint.apply {
            color = Color.argb(64, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor))
            style = Paint.Style.FILL
        }
        
        // Border
        borderPaint.apply {
            color = Color.argb(96, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor))
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        
        // Glow effect
        glowPaint.apply {
            color = Color.argb(64, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
            style = Paint.Style.FILL
            maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Accept button (green gradient)
        acceptPaint.apply {
            shader = RadialGradient(
                0f, 0f, 100f,
                intArrayOf(
                    acceptColor,
                    Color.argb(255, 0, 200, 0),
                    Color.argb(255, 0, 150, 0)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        
        // Decline button (red gradient)
        declinePaint.apply {
            shader = RadialGradient(
                0f, 0f, 100f,
                intArrayOf(
                    rejectColor,
                    Color.argb(255, 220, 0, 0),
                    Color.argb(255, 180, 0, 0)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        
        // Icon paint
        iconPaint.apply {
            color = onPrimaryColor
            style = Paint.Style.FILL
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        
        // Text paint
        textPaint.apply {
            color = onPrimaryColor
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        centerX = w / 2f
        centerY = h / 2f
        
        // Container dimensions
        containerWidth = w * 0.85f
        containerHeight = h * 0.35f
        containerRadius = containerHeight / 2f
        
        // Button size
        buttonRadius = containerHeight * 0.35f
        
        // Initial button positions
        val containerLeft = (w - containerWidth) / 2f
        val containerRight = containerLeft + containerWidth
        
        declineButtonX = containerLeft + buttonRadius + 40f
        acceptButtonX = containerRight - buttonRadius - 40f
        
        // Reset current positions
        currentDeclineX = declineButtonX
        currentAcceptX = acceptButtonX
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw glow effect
        drawGlow(canvas)
        
        // Draw glassmorphic container
        drawContainer(canvas)
        
        // Draw swipe hints (arrows)
        drawSwipeHints(canvas)
        
        // Draw decline button (red)
        drawDeclineButton(canvas, currentDeclineX, buttonY)
        
        // Draw accept button (green)
        drawAcceptButton(canvas, currentAcceptX, buttonY)
        
        // Draw text labels
        drawLabels(canvas)
    }

    private fun drawGlow(canvas: Canvas) {
        val containerLeft = (width - containerWidth) / 2f
        val containerTop = centerY - containerHeight / 2f
        
        glowPaint.alpha = (glowAlpha * 255).toInt()
        canvas.drawRoundRect(
            containerLeft - 10f,
            containerTop - 10f,
            containerLeft + containerWidth + 10f,
            containerTop + containerHeight + 10f,
            containerRadius + 10f,
            containerRadius + 10f,
            glowPaint
        )
    }

    private fun drawContainer(canvas: Canvas) {
        val containerLeft = (width - containerWidth) / 2f
        val containerTop = centerY - containerHeight / 2f
        
        // Main container background
        canvas.drawRoundRect(
            containerLeft,
            containerTop,
            containerLeft + containerWidth,
            containerTop + containerHeight,
            containerRadius,
            containerRadius,
            containerPaint
        )
        
        // Gradient overlay for depth
        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
        val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
        val secondaryColor = ContextCompat.getColor(context, R.color.secondary_color)
        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                containerLeft, containerTop,
                containerLeft + containerWidth, containerTop + containerHeight,
                intArrayOf(
                    Color.argb(32, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor)),
                    Color.argb(16, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                    Color.argb(8, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor))
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(
            containerLeft,
            containerTop,
            containerLeft + containerWidth,
            containerTop + containerHeight,
            containerRadius,
            containerRadius,
            gradientPaint
        )
        
        // Border
        canvas.drawRoundRect(
            containerLeft,
            containerTop,
            containerLeft + containerWidth,
            containerTop + containerHeight,
            containerRadius,
            containerRadius,
            borderPaint
        )
    }

    private fun drawSwipeHints(canvas: Canvas) {
        val rejectColor = ContextCompat.getColor(context, R.color.call_reject)
        val acceptColor = ContextCompat.getColor(context, R.color.call_accept)
        
        // Left arrows (for decline)
        val leftArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, Color.red(rejectColor), Color.green(rejectColor), Color.blue(rejectColor))
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        // Right arrows (for accept)
        val rightArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, Color.red(acceptColor), Color.green(acceptColor), Color.blue(acceptColor))
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        
        // Draw decline arrows (pointing right towards center)
        for (i in 0..2) {
            val arrowX = currentDeclineX - buttonRadius - 60f - (i * 40f)
            drawChevronRight(canvas, arrowX, centerY, leftArrowPaint)
        }
        
        // Draw accept arrows (pointing left towards center)
        for (i in 0..2) {
            val arrowX = currentAcceptX + buttonRadius + 60f + (i * 40f)
            drawChevronLeft(canvas, arrowX, centerY, rightArrowPaint)
        }
    }

    private fun drawChevronRight(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x - 15f, y - 20f)
            lineTo(x + 5f, y)
            lineTo(x - 15f, y + 20f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawChevronLeft(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x + 15f, y - 20f)
            lineTo(x - 5f, y)
            lineTo(x + 15f, y + 20f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawDeclineButton(canvas: Canvas, x: Float, y: Float) {
        // Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 0, 0, 0)
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(x, y + 6f, buttonRadius, shadowPaint)
        
        // Main button
        canvas.drawCircle(x, y, buttonRadius, declinePaint)
        
        // Glossy highlight
        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x - buttonRadius * 0.3f,
                y - buttonRadius * 0.3f,
                buttonRadius * 0.6f,
                Color.argb(96, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(x, y, buttonRadius, highlightPaint)
        
        // Draw long press progress ring
        if (isDraggingDecline && longPressProgress > 0f) {
            drawLongPressProgress(canvas, x, y, longPressProgress, false)
        }
        
        // Phone hangup icon
        drawPhoneDownIcon(canvas, x, y)
    }

    private fun drawAcceptButton(canvas: Canvas, x: Float, y: Float) {
        // Shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 0, 0, 0)
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(x, y + 6f, buttonRadius, shadowPaint)
        
        // Main button
        canvas.drawCircle(x, y, buttonRadius, acceptPaint)
        
        // Glossy highlight
        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x - buttonRadius * 0.3f,
                y - buttonRadius * 0.3f,
                buttonRadius * 0.6f,
                Color.argb(96, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(x, y, buttonRadius, highlightPaint)
        
        // Draw long press progress ring
        if (isDraggingAccept && longPressProgress > 0f) {
            drawLongPressProgress(canvas, x, y, longPressProgress, true)
        }
        
        // Phone pickup icon
        drawPhoneIcon(canvas, x, y)
    }
    
    private fun drawLongPressProgress(canvas: Canvas, x: Float, y: Float, progress: Float, isAccept: Boolean) {
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            color = ContextCompat.getColor(context, R.color.fab_icon)
            alpha = 200
        }
        
        // Draw outer glow ring
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            color = if (isAccept) ContextCompat.getColor(context, R.color.call_accept) else ContextCompat.getColor(context, R.color.call_reject)
            alpha = (150 * progress).toInt()
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        
        val sweepAngle = 360f * progress
        val startAngle = -90f // Start from top
        
        // Draw glow ring
        canvas.drawArc(
            x - buttonRadius - 12f,
            y - buttonRadius - 12f,
            x + buttonRadius + 12f,
            y + buttonRadius + 12f,
            startAngle,
            sweepAngle,
            false,
            glowPaint
        )
        
        // Draw progress ring
        canvas.drawArc(
            x - buttonRadius - 8f,
            y - buttonRadius - 8f,
            x + buttonRadius + 8f,
            y + buttonRadius + 8f,
            startAngle,
            sweepAngle,
            false,
            progressPaint
        )
    }

    private fun drawPhoneDownIcon(canvas: Canvas, x: Float, y: Float) {
        val iconSize = buttonRadius * 0.55f
        
        // Professional phone handset icon (Material Design style)
        val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 0, 0, 0)
            style = Paint.Style.FILL
        }
        
        canvas.save()
        canvas.rotate(135f, x, y) // Rotated for "hang up" position
        
        // Create classic curved phone receiver shape
        val receiverPath = Path().apply {
            // Left earpiece (rounded rectangle)
            moveTo(x - iconSize * 0.7f, y - iconSize * 0.15f)
            cubicTo(
                x - iconSize * 0.75f, y - iconSize * 0.25f,
                x - iconSize * 0.65f, y - iconSize * 0.35f,
                x - iconSize * 0.5f, y - iconSize * 0.35f
            )
            lineTo(x - iconSize * 0.35f, y - iconSize * 0.35f)
            cubicTo(
                x - iconSize * 0.25f, y - iconSize * 0.35f,
                x - iconSize * 0.2f, y - iconSize * 0.25f,
                x - iconSize * 0.2f, y - iconSize * 0.15f
            )
            
            // Curved middle section
            cubicTo(
                x - iconSize * 0.2f, y,
                x - iconSize * 0.1f, y + iconSize * 0.1f,
                x, y + iconSize * 0.15f
            )
            cubicTo(
                x + iconSize * 0.1f, y + iconSize * 0.1f,
                x + iconSize * 0.2f, y,
                x + iconSize * 0.2f, y - iconSize * 0.15f
            )
            
            // Right earpiece (rounded rectangle)
            cubicTo(
                x + iconSize * 0.2f, y - iconSize * 0.25f,
                x + iconSize * 0.25f, y - iconSize * 0.35f,
                x + iconSize * 0.35f, y - iconSize * 0.35f
            )
            lineTo(x + iconSize * 0.5f, y - iconSize * 0.35f)
            cubicTo(
                x + iconSize * 0.65f, y - iconSize * 0.35f,
                x + iconSize * 0.75f, y - iconSize * 0.25f,
                x + iconSize * 0.7f, y - iconSize * 0.15f
            )
            
            // Bottom curve
            cubicTo(
                x + iconSize * 0.7f, y + iconSize * 0.05f,
                x + iconSize * 0.6f, y + iconSize * 0.15f,
                x + iconSize * 0.5f, y + iconSize * 0.15f
            )
            lineTo(x - iconSize * 0.5f, y + iconSize * 0.15f)
            cubicTo(
                x - iconSize * 0.6f, y + iconSize * 0.15f,
                x - iconSize * 0.7f, y + iconSize * 0.05f,
                x - iconSize * 0.7f, y - iconSize * 0.15f
            )
            close()
        }
        
        // Draw shadow
        canvas.translate(2f, 2f)
        canvas.drawPath(receiverPath, shadowPaint)
        canvas.translate(-2f, -2f)
        
        // Draw main icon
        canvas.drawPath(receiverPath, phonePaint)
        
        canvas.restore()
    }

    private fun drawPhoneIcon(canvas: Canvas, x: Float, y: Float) {
        val iconSize = buttonRadius * 0.55f
        
        // Professional phone handset icon (Material Design style)
        val phonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 0, 0, 0)
            style = Paint.Style.FILL
        }
        
        canvas.save()
        canvas.rotate(-45f, x, y) // Rotated for "answer" position
        
        // Create classic curved phone receiver shape
        val receiverPath = Path().apply {
            // Left earpiece (rounded rectangle)
            moveTo(x - iconSize * 0.7f, y - iconSize * 0.15f)
            cubicTo(
                x - iconSize * 0.75f, y - iconSize * 0.25f,
                x - iconSize * 0.65f, y - iconSize * 0.35f,
                x - iconSize * 0.5f, y - iconSize * 0.35f
            )
            lineTo(x - iconSize * 0.35f, y - iconSize * 0.35f)
            cubicTo(
                x - iconSize * 0.25f, y - iconSize * 0.35f,
                x - iconSize * 0.2f, y - iconSize * 0.25f,
                x - iconSize * 0.2f, y - iconSize * 0.15f
            )
            
            // Curved middle section
            cubicTo(
                x - iconSize * 0.2f, y,
                x - iconSize * 0.1f, y + iconSize * 0.1f,
                x, y + iconSize * 0.15f
            )
            cubicTo(
                x + iconSize * 0.1f, y + iconSize * 0.1f,
                x + iconSize * 0.2f, y,
                x + iconSize * 0.2f, y - iconSize * 0.15f
            )
            
            // Right earpiece (rounded rectangle)
            cubicTo(
                x + iconSize * 0.2f, y - iconSize * 0.25f,
                x + iconSize * 0.25f, y - iconSize * 0.35f,
                x + iconSize * 0.35f, y - iconSize * 0.35f
            )
            lineTo(x + iconSize * 0.5f, y - iconSize * 0.35f)
            cubicTo(
                x + iconSize * 0.65f, y - iconSize * 0.35f,
                x + iconSize * 0.75f, y - iconSize * 0.25f,
                x + iconSize * 0.7f, y - iconSize * 0.15f
            )
            
            // Bottom curve
            cubicTo(
                x + iconSize * 0.7f, y + iconSize * 0.05f,
                x + iconSize * 0.6f, y + iconSize * 0.15f,
                x + iconSize * 0.5f, y + iconSize * 0.15f
            )
            lineTo(x - iconSize * 0.5f, y + iconSize * 0.15f)
            cubicTo(
                x - iconSize * 0.6f, y + iconSize * 0.15f,
                x - iconSize * 0.7f, y + iconSize * 0.05f,
                x - iconSize * 0.7f, y - iconSize * 0.15f
            )
            close()
        }
        
        // Draw shadow
        canvas.translate(2f, 2f)
        canvas.drawPath(receiverPath, shadowPaint)
        canvas.translate(-2f, -2f)
        
        // Draw main icon
        canvas.drawPath(receiverPath, phonePaint)
        
        canvas.restore()
    }

    private fun drawLabels(canvas: Canvas) {
        // "Decline" label
        canvas.drawText(
            "Decline",
            currentDeclineX,
            buttonY + buttonRadius + 60f,
            textPaint
        )
        
        // "Accept" label
        canvas.drawText(
            "Accept",
            currentAcceptX,
            buttonY + buttonRadius + 60f,
            textPaint
        )
        
        // Center hint
        if (!isDraggingAccept && !isDraggingDecline) {
            val hintPaint = Paint(textPaint).apply {
                textSize = 32f
                alpha = 150
            }
            canvas.drawText(
                "Swipe to answer or decline",
                centerX,
                centerY,
                hintPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                hasReachedThreshold = false
                longPressCompleted = false
                hasMoved = false
                pressStartTime = System.currentTimeMillis()
                
                // Check if touching accept button
                if (isPointInCircle(event.x, event.y, currentAcceptX, buttonY, buttonRadius)) {
                    isDraggingAccept = true
                    startLongPressTimer(true)
                    return true
                }
                
                // Check if touching decline button
                if (isPointInCircle(event.x, event.y, currentDeclineX, buttonY, buttonRadius)) {
                    isDraggingDecline = true
                    startLongPressTimer(false)
                    return true
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Check if user has moved finger significantly from initial position
                val deltaX = abs(event.x - initialTouchX)
                val deltaY = abs(event.y - initialTouchY)
                val totalMovement = sqrt(deltaX * deltaX + deltaY * deltaY)
                
                if (totalMovement > minMovementThreshold) {
                    hasMoved = true
                }
                
                if (isDraggingAccept) {
                    // Drag accept button towards center (left)
                    val newX = acceptButtonX + (event.x - initialTouchX)
                    currentAcceptX = newX.coerceIn(centerX, acceptButtonX)
                    invalidate()
                    
                    // Check if reached threshold (but don't trigger yet)
                    val dragDistance = acceptButtonX - currentAcceptX
                    val maxDistance = acceptButtonX - centerX
                    hasReachedThreshold = dragDistance > maxDistance * swipeThreshold
                    
                    // Cancel long press if user starts swiping
                    if (deltaX > 20f) {
                        cancelLongPressTimer()
                    }
                }
                
                if (isDraggingDecline) {
                    // Drag decline button towards center (right)
                    val newX = declineButtonX + (event.x - initialTouchX)
                    currentDeclineX = newX.coerceIn(declineButtonX, centerX)
                    invalidate()
                    
                    // Check if reached threshold (but don't trigger yet)
                    val dragDistance = currentDeclineX - declineButtonX
                    val maxDistance = centerX - declineButtonX
                    hasReachedThreshold = dragDistance > maxDistance * swipeThreshold
                    
                    // Cancel long press if user starts swiping
                    if (deltaX > 20f) {
                        cancelLongPressTimer()
                    }
                }
            }
            
            MotionEvent.ACTION_UP -> {
                cancelLongPressTimer()
                
                // SAFETY CHECK #1: Check if button was brought back to original position
                val acceptReturnedToOriginal = abs(currentAcceptX - acceptButtonX) < returnToOriginalThreshold
                val declineReturnedToOriginal = abs(currentDeclineX - declineButtonX) < returnToOriginalThreshold
                
                // SAFETY CHECK #2: Only trigger if user either:
                // 1. Swiped to threshold (hasMoved + hasReachedThreshold)
                // 2. Held for 1.5 seconds (longPressCompleted)
                // 3. AND button is NOT back at original position
                // If user just taps in same place (no movement, no hold), do nothing
                // If user brought button back to original, do nothing
                
                val shouldTriggerAccept = ((hasReachedThreshold && hasMoved) || longPressCompleted) && !acceptReturnedToOriginal
                val shouldTriggerDecline = ((hasReachedThreshold && hasMoved) || longPressCompleted) && !declineReturnedToOriginal
                
                if (isDraggingAccept && shouldTriggerAccept) {
                    triggerAccept()
                    isDraggingAccept = false
                    hasReachedThreshold = false
                    longPressCompleted = false
                    hasMoved = false
                    return true
                }
                
                if (isDraggingDecline && shouldTriggerDecline) {
                    triggerDecline()
                    isDraggingDecline = false
                    hasReachedThreshold = false
                    longPressCompleted = false
                    hasMoved = false
                    return true
                }
                
                // Animate back to original position if conditions not met
                if (isDraggingAccept) {
                    animateButtonBack(true)
                    isDraggingAccept = false
                }
                
                if (isDraggingDecline) {
                    animateButtonBack(false)
                    isDraggingDecline = false
                }
                
                hasReachedThreshold = false
                longPressCompleted = false
                hasMoved = false
            }
            
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPressTimer()
                
                // Always animate back on cancel
                if (isDraggingAccept) {
                    animateButtonBack(true)
                    isDraggingAccept = false
                }
                
                if (isDraggingDecline) {
                    animateButtonBack(false)
                    isDraggingDecline = false
                }
                
                hasReachedThreshold = false
                longPressCompleted = false
                hasMoved = false
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    private fun startLongPressTimer(isAccept: Boolean) {
        cancelLongPressTimer()
        longPressProgress = 0f
        longPressCompleted = false
        
        longPressTimer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = longPressDelay
            addUpdateListener {
                // Update progress for visual feedback
                longPressProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    longPressProgress = 0f
                    longPressCompleted = false
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Mark long press as completed, but wait for finger lift (ACTION_UP)
                    longPressCompleted = true
                    // Keep the progress ring visible at 100%
                    longPressProgress = 1f
                    invalidate()
                }
            })
            start()
        }
    }
    
    private fun cancelLongPressTimer() {
        longPressTimer?.cancel()
        longPressTimer = null
        longPressProgress = 0f
        // Don't reset longPressCompleted here - let ACTION_UP handle it
    }

    private fun isPointInCircle(px: Float, py: Float, cx: Float, cy: Float, radius: Float): Boolean {
        val dx = px - cx
        val dy = py - cy
        return sqrt(dx * dx + dy * dy) <= radius
    }

    private fun triggerAccept() {
        isDraggingAccept = false
        // Animate button to center with expansion
        ValueAnimator.ofFloat(currentAcceptX, centerX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentAcceptX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        postDelayed({
            onAcceptCall?.invoke()
        }, 200)
    }

    private fun triggerDecline() {
        isDraggingDecline = false
        // Animate button to center with expansion
        ValueAnimator.ofFloat(currentDeclineX, centerX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentDeclineX = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        postDelayed({
            onDeclineCall?.invoke()
        }, 200)
    }

    private fun animateButtonBack(isAccept: Boolean) {
        if (isAccept) {
            ValueAnimator.ofFloat(currentAcceptX, acceptButtonX).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    currentAcceptX = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            ValueAnimator.ofFloat(currentDeclineX, declineButtonX).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    currentDeclineX = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private fun startGlowAnimation() {
        glowAnimator = ValueAnimator.ofFloat(0.2f, 0.5f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                glowAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator?.cancel()
        cancelLongPressTimer()
    }
}
