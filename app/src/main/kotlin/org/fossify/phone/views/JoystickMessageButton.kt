package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import org.fossify.phone.R
import kotlin.math.*

class JoystickMessageButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Message options for the 8 segments with premium gradients
    data class MessageSegment(
        val id: Int,
        val message: String,
        val angle: Float,
        val gradientStart: Int,
        val gradientEnd: Int
    )

    private val segments: List<MessageSegment> by lazy {
        val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
        val primaryVariant = ContextCompat.getColor(context, R.color.primary_variant_color)
        val secondaryColor = ContextCompat.getColor(context, R.color.secondary_color)
        val surfaceColor = ContextCompat.getColor(context, R.color.surface_color)
        val backgroundColor = ContextCompat.getColor(context, R.color.background_color)
        
        listOf(
            MessageSegment(0, "I'm in a meeting right now.", 0f, 
                Color.argb(255, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)),
                Color.argb(255, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))),
            MessageSegment(1, "I'm busy, will call you shortly.", 45f,
                Color.argb(220, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)),
                Color.argb(220, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))),
            MessageSegment(2, "Can't talk, I'll text you instead.", 90f,
                Color.argb(200, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)),
                Color.argb(255, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))),
            MessageSegment(3, "I'm at work, will call later.", 135f,
                Color.argb(230, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor)),
                Color.argb(200, Color.red(secondaryColor), Color.green(secondaryColor), Color.blue(secondaryColor))),
            MessageSegment(4, "Driving right now, will call back.", 180f,
                Color.argb(255, Color.red(primaryVariant), Color.green(primaryVariant), Color.blue(primaryVariant)),
                Color.argb(200, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))),
            MessageSegment(5, "In class, can't talk.", 225f,
                Color.argb(240, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)),
                Color.argb(200, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))),
            MessageSegment(6, "Can't speak, is it urgent?", 270f,
                Color.argb(200, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)),
                Color.argb(180, Color.red(primaryVariant), Color.green(primaryVariant), Color.blue(primaryVariant))),
            MessageSegment(7, "Call you in a few minutes.", 315f,
                Color.argb(210, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)),
                Color.argb(180, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor)))
        )
    }

    private var selectedSegmentIndex = -1
    private var previousSelectedIndex = -1
    private var isExpanded = false
    private var expansionProgress = 0f
    private var joystickRotation = 0f
    private var pulseProgress = 0f
    private var segmentScaleProgress = 0f
    private var segmentScaleAnimator: ValueAnimator? = null
    
    // Center button properties - Premium BGMI style (BIGGER)
    private val centerButtonRadius = 90f
    private val expandedRingRadius = 420f  // Much bigger dial
    private val joystickRadius = 80f
    private val segmentScaleAmount = 1.35f  // 35% bigger when selected (very prominent)
    
    // Joystick position
    private var joystickX = 0f
    private var joystickY = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var isDragging = false
    
    // Premium Paint objects with gradients
    private val centerButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        style = Paint.Style.FILL
        setShadowLayer(20f, 0f, 8f, Color.argb(96, 0, 0, 0))
    }
    
    private val joystickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        style = Paint.Style.FILL
        setShadowLayer(25f, 0f, 10f, Color.argb(128, 0, 0, 0))
    }
    
    private val roseGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_color)
        style = Paint.Style.FILL
    }
    
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_color)
        style = Paint.Style.FILL
        alpha = 77  // 30% opacity
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val haloGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_color)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        alpha = 102  // 40% opacity
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 30f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 26f  // Bigger font
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)  // Bold by default
        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
        setShadowLayer(2f, 0f, 1f, Color.argb(64, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor)))  // Text shadow for visibility
    }
    
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_color)
        textSize = 72f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val navyBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_dark_color)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val segmentBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val segmentPath = Path()
    private val expansionAnimator = ValueAnimator()
    
    var onSegmentSelectedListener: ((String) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)  // Hardware layer - no blur artifacts, better performance
        clipToOutline = false

    // Note: clipChildren and clipToPadding only apply to ViewGroup, not View
        // We handle clipping via parent layout XML attributes
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        joystickX = centerX
        joystickY = centerY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw blurred background overlay when popup opens
        if (isExpanded || expansionProgress > 0f) {
            drawBlurredBackground(canvas)
            // Draw radial glow behind segments
            drawRadialGlow(canvas)
            // Draw premium gradient segments
            drawSegments(canvas)
        }
        
        // Draw center button circle
        if (!isExpanded && expansionProgress == 0f) {
            drawCenterButton(canvas)
        }
        
        // Draw joystick with rotation
        if (isExpanded || expansionProgress > 0f) {
            drawJoystick(canvas)
        }
        
        // Draw emoji icon 💬
        if (!isExpanded && expansionProgress == 0f) {
            drawEmojiIcon(canvas)
        }
    }

    private fun drawSegments(canvas: Canvas) {
        val segmentAngle = 360f / segments.size
        val baseRadius = centerButtonRadius + (expandedRingRadius - centerButtonRadius) * expansionProgress
        
        // Draw unselected segments first
        segments.forEachIndexed { index, segment ->
            if (selectedSegmentIndex != index) {
                drawSingleSegment(canvas, index, segment, segmentAngle, baseRadius)
            }
        }
        
        // Draw selected segment last so it appears on top
        if (selectedSegmentIndex >= 0 && selectedSegmentIndex < segments.size) {
            drawSingleSegment(canvas, selectedSegmentIndex, segments[selectedSegmentIndex], segmentAngle, baseRadius)
        }
        
        // Draw navy blue circle border only when no segment is selected
        if (expansionProgress > 0.5f && selectedSegmentIndex < 0) {
            val currentRadius = centerButtonRadius + (expandedRingRadius - centerButtonRadius) * expansionProgress
            navyBluePaint.alpha = (255 * expansionProgress).toInt()
            canvas.drawCircle(centerX, centerY, currentRadius, navyBluePaint)
        }
        
        backgroundPaint.shader = null
    }
    
    private fun drawSingleSegment(canvas: Canvas, index: Int, segment: MessageSegment, segmentAngle: Float, baseRadius: Float) {
            // No spacing between segments
            val gapAngle = 0f  // No gap
            val startAngle = segment.angle - segmentAngle / 2
            val endAngle = segment.angle + segmentAngle / 2
            val adjustedSegmentAngle = segmentAngle
            
            // Calculate scaled radius for selected segment
            val isSelected = selectedSegmentIndex == index
            val radiusScale = if (isSelected) {
                1f + (segmentScaleAmount - 1f) * segmentScaleProgress
            } else {
                1f
            }
            val currentRadius = baseRadius * radiusScale
            
            // Create triangular wedge with rounded corners
            segmentPath.reset()
            
            // Inner radius for the wedge (gap from center)
            val innerRadius = centerButtonRadius + 15f  // Increased gap for better spacing
            
            // Calculate corner points of the triangular wedge
            val startAngleRad = Math.toRadians(startAngle.toDouble()).toFloat()
            val endAngleRad = Math.toRadians(endAngle.toDouble()).toFloat()
            
            // Outer arc points
            val outerStartX = centerX + currentRadius * cos(startAngleRad)
            val outerStartY = centerY + currentRadius * sin(startAngleRad)
            val outerEndX = centerX + currentRadius * cos(endAngleRad)
            val outerEndY = centerY + currentRadius * sin(endAngleRad)
            
            // Inner arc points
            val innerStartX = centerX + innerRadius * cos(startAngleRad)
            val innerStartY = centerY + innerRadius * sin(startAngleRad)
            val innerEndX = centerX + innerRadius * cos(endAngleRad)
            val innerEndY = centerY + innerRadius * sin(endAngleRad)
            
            // Draw triangular wedge with enhanced rounded corners
            val cornerRadius = 25f * radiusScale  // Enhanced rounded corners that scale
            
            segmentPath.moveTo(innerStartX, innerStartY)
            
            // Add rounded corner at inner-left edge
            val innerLeftControlX = (innerStartX + outerStartX) / 2f
            val innerLeftControlY = (innerStartY + outerStartY) / 2f
            segmentPath.quadTo(innerLeftControlX, innerLeftControlY, outerStartX, outerStartY)
            
            // Outer arc (rounded outer edge)
            val outerRect = RectF(
                centerX - currentRadius,
                centerY - currentRadius,
                centerX + currentRadius,
                centerY + currentRadius
            )
            segmentPath.arcTo(outerRect, startAngle, adjustedSegmentAngle, false)
            
            // Add rounded corner at outer-right edge
            val outerRightControlX = (outerEndX + innerEndX) / 2f
            val outerRightControlY = (outerEndY + innerEndY) / 2f
            segmentPath.quadTo(outerRightControlX, outerRightControlY, innerEndX, innerEndY)
            
            // Inner arc (rounded inner edge)
            val innerRect = RectF(
                centerX - innerRadius,
                centerY - innerRadius,
                centerX + innerRadius,
                centerY + innerRadius
            )
            segmentPath.arcTo(innerRect, endAngle, -adjustedSegmentAngle, false)
            
            segmentPath.close()
            
            // Create radial gradient for each segment (premium 3D metallic effect)
            val gradientAngle = Math.toRadians(segment.angle.toDouble()).toFloat()
            val gradientEndX = centerX + currentRadius * 0.8f * cos(gradientAngle)
            val gradientEndY = centerY + currentRadius * 0.8f * sin(gradientAngle)
            
            val segmentGradient = RadialGradient(
                centerX, centerY,
                currentRadius,
                intArrayOf(segment.gradientStart, segment.gradientEnd),
                floatArrayOf(0.3f, 1.0f),
                Shader.TileMode.CLAMP
            )
            
            backgroundPaint.shader = segmentGradient
            
            // Apply opacity - ONLY selected segment is bright, others stay normal
            backgroundPaint.alpha = when {
                selectedSegmentIndex == index -> 255  // Selected: Full bright with glow
                else -> (200 * expansionProgress).toInt()  // Others: Normal visibility
            }
            
            canvas.drawPath(segmentPath, backgroundPaint)
            
            // Draw BRIGHT halo glow ONLY for selected segment with pulsing effect
            if (selectedSegmentIndex == index) {
                // Multiple layers of glow for professional effect
                
                // Layer 1: Outer glow (biggest, most subtle)
                val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
                val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primaryColor
                    style = Paint.Style.FILL
                    alpha = (100 * segmentScaleProgress).toInt()
                    maskFilter = BlurMaskFilter(45f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(segmentPath, outerGlowPaint)
                
                // Layer 2: Middle glow (brighter)
                val middleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primaryColor
                    style = Paint.Style.FILL
                    alpha = (150 * segmentScaleProgress).toInt()
                    maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(segmentPath, middleGlowPaint)
                
                // Layer 3: Inner glow (strongest, closest to segment)
                val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
                val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = onPrimaryColor
                    style = Paint.Style.FILL
                    alpha = (220 * segmentScaleProgress).toInt()
                    maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(segmentPath, innerGlowPaint)
                
                // Draw pulsing halo outline
                val pulsatingHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = onPrimaryColor
                    style = Paint.Style.STROKE
                    strokeWidth = 6f + (4f * segmentScaleProgress)  // Pulsing thickness
                    alpha = (200 * segmentScaleProgress).toInt()
                    maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
                }
                canvas.drawPath(segmentPath, pulsatingHaloPaint)
            }
            
            // Draw gradient border for each segment (transparent center to black at edge)
            val borderGradient = RadialGradient(
                centerX, centerY,
                currentRadius,
                intArrayOf(
                    Color.TRANSPARENT,  // Transparent at center
                    Color.argb(187, 0, 0, 0)   // Black at edge (75% opacity)
                ),
                floatArrayOf(0.4f, 1.0f),
                Shader.TileMode.CLAMP
            )
            segmentBorderPaint.shader = borderGradient
            segmentBorderPaint.alpha = (255 * expansionProgress).toInt()
            canvas.drawPath(segmentPath, segmentBorderPaint)
            segmentBorderPaint.shader = null
            
            // Draw prominent stroke between segments for clear separation
            val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(64, 0, 0, 0)  // Semi-transparent black
                style = Paint.Style.STROKE
                strokeWidth = 4f
                alpha = (255 * expansionProgress).toInt()
            }
            canvas.drawPath(segmentPath, separatorPaint)
            
            // Draw text label if expanded enough
            if (expansionProgress > 0.6f) {
                val textAngle = segment.angle
                // Use scaled radius for text positioning so text follows the expanded segment
                val textRadius = currentRadius * 0.65f  // Slightly closer to center for better fit
                val textX = centerX + textRadius * cos(Math.toRadians(textAngle.toDouble())).toFloat()
                val textY = centerY + textRadius * sin(Math.toRadians(textAngle.toDouble())).toFloat()
                
                // Draw shortened text
                val shortText = getShortText(segment.message)
                val lines = shortText.split("\n")
                
                canvas.save()
                // Disable clipping to ensure text is visible
                canvas.clipRect(
                    centerX - expandedRingRadius * 1.2f,
                    centerY - expandedRingRadius * 1.2f,
                    centerX + expandedRingRadius * 1.2f,
                    centerY + expandedRingRadius * 1.2f
                )
                
                lines.forEachIndexed { lineIndex, line ->
                    if (line.isNotEmpty()) {
                        val baseTextSize = if (selectedSegmentIndex == index) 30f else 26f
                        val yOffset = (lineIndex - lines.size / 2f) * baseTextSize * 1.2f
                    
                    // Create text paint for this segment
                    val textPaintForSegment = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)  // Always BOLD
                        
                        // Text appearance based on selection with animation
                        val onPrimaryColor = ContextCompat.getColor(context, R.color.fab_icon)
                        val textPrimaryColor = ContextCompat.getColor(context, R.color.text_primary)
                        if (selectedSegmentIndex == index) {
                            // SELECTED: White text with strong shadow, animated bigger size
                            color = onPrimaryColor
                            val baseSize = 30f
                            val scaleIncrease = 6f * segmentScaleProgress  // Grows with animation
                            textSize = baseSize + scaleIncrease
                            setShadowLayer(4f + (2f * segmentScaleProgress), 0f, 2f, Color.argb(128, 0, 0, 0))
                            alpha = 255  // Full opacity
                        } else {
                            // NOT SELECTED: Dark text, still visible, normal size
                            color = textPrimaryColor
                            textSize = 26f
                            setShadowLayer(2f, 0f, 1f, Color.argb(64, Color.red(onPrimaryColor), Color.green(onPrimaryColor), Color.blue(onPrimaryColor)))
                            alpha = (230 * expansionProgress).toInt()  // Always visible when expanded
                        }
                    }
                    
                        canvas.drawText(line, textX, textY + yOffset, textPaintForSegment)
                    }
                }
                
                canvas.restore()
            }
    }

    private fun getShortText(message: String): String {
        return when (message) {
            "I'm in a meeting right now." -> "In\nMeeting"
            "I'm busy, will call you shortly." -> "Will Call\nShortly"
            "Can't talk, I'll text you instead." -> "Will\nText"
            "I'm at work, will call later." -> "At\nWork"
            "Driving right now, will call back." -> "Driving"
            "In class, can't talk." -> "In\nClass"
            "Can't speak, is it urgent?" -> "Urgent?"
            "Call you in a few minutes." -> "Few\nMinutes"
            else -> message.take(10)
        }
    }

    private fun drawCenterButton(canvas: Canvas) {
        // Draw only center button circle (not full background)
        // Draw shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(64, 0, 0, 0)
            maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(centerX, centerY + 3f, centerButtonRadius, shadowPaint)
        
        // Draw center button with radial gradient
        val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
        val backgroundColor = ContextCompat.getColor(context, R.color.background_color)
        val buttonGradient = RadialGradient(
            centerX, centerY,
            centerButtonRadius,
            intArrayOf(
                primaryColor,  // Primary at center
                backgroundColor   // Background at edges
            ),
            floatArrayOf(0.3f, 1.0f),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = buttonGradient
        canvas.drawCircle(centerX, centerY, centerButtonRadius, backgroundPaint)
        backgroundPaint.shader = null
        
        // Draw border
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.primary_variant_color)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(centerX, centerY, centerButtonRadius - 2f, ringPaint)
    }
    
    private fun drawBlurredBackground(canvas: Canvas) {
        // Subtle transparent background - no visible borders/square
        val backgroundColor = ContextCompat.getColor(context, R.color.background_color)
        val subtleBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(16, Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor))  // Very subtle (6% opacity)
            style = Paint.Style.FILL
            alpha = (40 * expansionProgress).toInt()  // Very light fade
        }
        
        // Draw clean circle without blur mask (no square effect)
        val backgroundRadius = expandedRingRadius * 1.15f * expansionProgress
        canvas.drawCircle(centerX, centerY, backgroundRadius, subtleBackgroundPaint)
    }
    
    private fun drawRadialGlow(canvas: Canvas) {
        // Subtle pale yellow glow behind dial
        if (expansionProgress > 0.3f) {
            val glowRadius = expandedRingRadius * 1.1f * expansionProgress
            val radialGlow = RadialGradient(
                centerX, centerY,
                glowRadius,
                intArrayOf(
                    Color.argb(32, Color.red(ContextCompat.getColor(context, R.color.primary_color)), 
                        Color.green(ContextCompat.getColor(context, R.color.primary_color)), 
                        Color.blue(ContextCompat.getColor(context, R.color.primary_color))),  // Pale primary center (lighter)
                    Color.TRANSPARENT   // Transparent edge
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            backgroundPaint.shader = radialGlow
            backgroundPaint.alpha = (255 * expansionProgress).toInt()
            canvas.drawCircle(centerX, centerY, glowRadius, backgroundPaint)
            backgroundPaint.shader = null
            backgroundPaint.alpha = 255
        }
    }

    private fun drawJoystick(canvas: Canvas) {
        canvas.save()
        
        // Apply rotation to joystick for realism
        canvas.rotate(joystickRotation, joystickX, joystickY)
        
        // Draw enhanced shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(joystickX, joystickY + 8f, joystickRadius * 1.1f, shadowPaint)
        
        // Draw BRIGHT glow around joystick when dragging
        if (isDragging && selectedSegmentIndex >= 0) {
            val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
            val brightGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = Paint.Style.FILL
                alpha = 200
                maskFilter = BlurMaskFilter(35f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(joystickX, joystickY, joystickRadius * 1.3f, brightGlow)
        }
        
        // Draw matte black joystick base
        canvas.drawCircle(joystickX, joystickY, joystickRadius, joystickPaint)
        
        // Draw rose gold accent ring (brighter when dragging)
        val primaryColor = ContextCompat.getColor(context, R.color.primary_color)
        val primaryVariant = ContextCompat.getColor(context, R.color.primary_variant_color)
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDragging) primaryColor else primaryVariant
            style = Paint.Style.STROKE
            strokeWidth = if (isDragging) 5f else 4f
        }
        canvas.drawCircle(joystickX, joystickY, joystickRadius * 0.85f, accentPaint)
        
        // Draw glow when active
        if (isExpanded || expansionProgress > 0f) {
            canvas.drawCircle(joystickX, joystickY, joystickRadius * 0.6f, glowPaint)
            
            // Draw center dot indicator (bigger when selected)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.primary_color)
                style = Paint.Style.FILL
            }
            val dotSize = if (selectedSegmentIndex >= 0) 0.20f else 0.15f
            canvas.drawCircle(joystickX, joystickY, joystickRadius * dotSize, dotPaint)
        }
        
        canvas.restore()
    }

    private fun drawEmojiIcon(canvas: Canvas) {
        // Draw 💬 emoji in rose gold color
        canvas.drawText(
            "💬",
            centerX,
            centerY + emojiPaint.textSize / 3,  // Adjust for text baseline
            emojiPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                
                // Only intercept touch if it's on the center button
                if (distance <= centerButtonRadius * 1.5f) {
                    isDragging = true
                    expandMenu()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                // Let other touches pass through
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && isExpanded) {
                    updateJoystickPosition(event.x, event.y)
                    updateSelectedSegment(event.x, event.y)
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (selectedSegmentIndex >= 0) {
                        val selectedMessage = segments[selectedSegmentIndex].message
                        onSegmentSelectedListener?.invoke(selectedMessage)
                    }
                    collapseMenu()
                    return true
                }
            }
        }
        
        return false  // Let touches pass through if not handled
    }

    private fun updateJoystickPosition(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        
        // Calculate rotation angle for joystick realism
        if (distance > 5f) {  // Avoid rotation jitter when near center
            joystickRotation = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        
        // Limit joystick movement to within the expanded ring
        val maxDistance = expandedRingRadius * 0.7f
        
        if (distance <= maxDistance) {
            joystickX = x
            joystickY = y
        } else {
            val angle = atan2(dy, dx)
            joystickX = centerX + maxDistance * cos(angle)
            joystickY = centerY + maxDistance * sin(angle)
        }
    }

    private fun updateSelectedSegment(x: Float, y: Float) {
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        
        val newSelectedIndex = if (distance > centerButtonRadius) {
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angle < 0) angle += 360f
            
            val segmentAngle = 360f / segments.size
            ((angle + segmentAngle / 2) / segmentAngle).toInt() % segments.size
        } else {
            -1
        }
        
        // Trigger animation when selection changes
        if (newSelectedIndex != selectedSegmentIndex) {
            previousSelectedIndex = selectedSegmentIndex
            selectedSegmentIndex = newSelectedIndex
            
            if (selectedSegmentIndex >= 0) {
                animateSegmentSelection()
                // Haptic feedback for professional feel
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            } else {
                // Deselected - animate back to normal
                animateSegmentDeselection()
            }
        }
    }
    
    private fun animateSegmentSelection() {
        segmentScaleAnimator?.cancel()
        segmentScaleAnimator = ValueAnimator.ofFloat(segmentScaleProgress, 1f).apply {
            duration = 150  // Even faster, more responsive
            interpolator = OvershootInterpolator(2f)  // More pronounced bounce
            addUpdateListener { animator ->
                segmentScaleProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun animateSegmentDeselection() {
        segmentScaleAnimator?.cancel()
        segmentScaleAnimator = ValueAnimator.ofFloat(segmentScaleProgress, 0f).apply {
            duration = 150  // Quick return to normal
            addUpdateListener { animator ->
                segmentScaleProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun expandMenu() {
        isExpanded = true
        expansionAnimator.cancel()
        
        ValueAnimator.ofFloat(expansionProgress, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator()
            addUpdateListener { animator ->
                expansionProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun collapseMenu() {
        // Clean up segment scale animator
        segmentScaleAnimator?.cancel()
        segmentScaleProgress = 0f
        
        // Pulse animation for selected segment before collapse
        if (selectedSegmentIndex >= 0) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 150
                addUpdateListener { animator ->
                    pulseProgress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        
        // Delay collapse slightly for pulse effect
        postDelayed({
            isExpanded = false
            val prevSelectedIndex = selectedSegmentIndex
            selectedSegmentIndex = -1
            previousSelectedIndex = -1
            
            ValueAnimator.ofFloat(expansionProgress, 0f).apply {
                duration = 250
                addUpdateListener { animator ->
                    expansionProgress = animator.animatedValue as Float
                    
                    // Reset joystick position smoothly
                    val progress = 1 - (animator.animatedValue as Float)
                    joystickX = centerX + (joystickX - centerX) * progress
                    joystickY = centerY + (joystickY - centerY) * progress
                    joystickRotation = joystickRotation * progress  // Smoothly reset rotation
                    
                    invalidate()
                }
                start()
            }
        }, 100)
    }
}
