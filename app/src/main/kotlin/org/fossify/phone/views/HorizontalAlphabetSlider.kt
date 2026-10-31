package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getFirstLetter
import org.fossify.phone.extensions.startsWithEnglishLetter
import org.fossify.phone.extensions.startsWithHindiLetter
import org.fossify.phone.helpers.LANGUAGE_HINDI
import kotlin.math.max
import kotlin.math.min

class HorizontalAlphabetSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 6f, 0x60000000)  // Deeper shadow for depth
    }

    private val popupRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val popupInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        isFakeBoldText = true  // Make text bolder for visibility
    }

    private var contacts: List<Contact> = emptyList()
    private var recyclerView: RecyclerView? = null
    
    // Full alphabet lists (reference for all possible letters)
    private val allEnglishLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toList()
    private val allHindiLetters = "अआइईउऊएओकखगघचछजझटठडढणतथदधनपफबभमयरलवशषसह#".toList()
    
    // Active alphabet (dynamically filtered based on contacts)
    private var alphabet = emptyList<Char>()
    
    private var dotPosition = 0f // 0.0 to 1.0
    private var isDragging = false
    private var showPopup = false
    private val dotRadius = 32f
    private val lineWidth = 8f
    private val popupSize = 120f  // Smaller for better visibility
    private val popupOffset = 50f  // More space from line

    private var hidePopupRunnable: Runnable? = null
    private var popupAnimator: ValueAnimator? = null
    private var popupAlpha = 0f
    
    // Zipper animation variables
    private var zipperAnimator: ValueAnimator? = null
    private var zipperSplitAmount = 0f  // 0.0 to 1.0, how much the line splits
    private val maxZipperSplit = 40f  // Maximum pixels the line can split horizontally

    private var textColor: Int = 0
    private var primaryColor: Int = 0
    
    // Custom color scheme
    private val roseGoldColor = 0xFFF4C2C2.toInt()  // Light rose gold for popup
    private val copperColor = 0xFFB87333.toInt()     // Copper for knob
    private val darkCopperColor = 0xFF8B5A2B.toInt() // Darker copper for accents

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateColors()
    }

    fun updateColors() {
        textColor = context.getProperTextColor()
        primaryColor = ContextCompat.getColor(context, R.color.color_primary)

        linePaint.color = textColor.adjustAlpha(0.3f)
        
        // Copper color scheme for knob
        dotPaint.color = copperColor
        dotStrokePaint.color = darkCopperColor.adjustAlpha(0.3f)
        
        // Rose gold color scheme for popup
        popupPaint.color = roseGoldColor
        popupTextPaint.color = darkCopperColor  // Dark copper text on rose gold
        popupRingPaint.color = copperColor.adjustAlpha(0.6f)  // Copper ring
        popupInnerRingPaint.color = copperColor.adjustAlpha(0.4f)  // Light copper inner ring

        invalidate()
    }

    fun setupWithContacts(recyclerView: RecyclerView, contacts: List<Contact>) {
        this.recyclerView = recyclerView
        this.contacts = contacts

        isDragging = false
        showPopup = false
        popupAlpha = 0f
        zipperSplitAmount = 0f
        removeHidePopupCallback()
        popupAnimator?.cancel()
        zipperAnimator?.cancel()
        updateAlphabetForLanguage()
        invalidate()
    }
    
    private fun updateAlphabetForLanguage() {
        val isHindiMode = context.config.appLanguage == LANGUAGE_HINDI
        
        // Get unique first letters from contacts based on current language mode
        val contactLetters = contacts
            .mapNotNull { contact ->
                val firstLetter = contact.getFirstLetter()
                if (firstLetter.isEmpty()) return@mapNotNull null
                
                // Filter based on language mode
                when {
                    firstLetter == "#" -> "#"
                    isHindiMode && contact.startsWithHindiLetter() -> firstLetter
                    !isHindiMode && contact.startsWithEnglishLetter() -> firstLetter
                    else -> null
                }
            }
            .toSet()
        
        // Filter the full alphabet to only include letters present in contacts
        val baseAlphabet = if (isHindiMode) allHindiLetters else allEnglishLetters
        
        alphabet = baseAlphabet
            .filter { letter ->
                val letterStr = letter.toString()
                contactLetters.contains(letterStr) || letterStr == "#"
            }
            .sortedBy { letter ->
                // Maintain original alphabet order
                baseAlphabet.indexOf(letter)
            }
        
        // Ensure we always have at least # if there are contacts
        if (alphabet.isEmpty() && contacts.isNotEmpty()) {
            alphabet = listOf('#')
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (dotRadius * 2 + paddingStart + paddingEnd + 40).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startY = paddingTop.toFloat() + dotRadius * 2
        val endY = height - paddingBottom.toFloat() - dotRadius * 2
        val centerX = width / 2f

        // Calculate dot position
        val dotY = startY + (endY - startY) * dotPosition
        
        // Draw the zipper chain animation - split line toward popup
        drawZipperLine(canvas, centerX, startY, endY, dotY)

        // Draw dot shadow/glow
        dotStrokePaint.alpha = if (isDragging) 255 else 128
        canvas.drawCircle(centerX, dotY, dotRadius + 8f, dotStrokePaint)

        // Draw the dot (🔘) - Copper color
        dotPaint.alpha = 255
        canvas.drawCircle(centerX, dotY, dotRadius, dotPaint)
        
        // Draw inner circle for better visibility - Rose gold accent
        val innerDotColor = roseGoldColor
        dotPaint.color = innerDotColor
        canvas.drawCircle(centerX, dotY, dotRadius * 0.3f, dotPaint)
        dotPaint.color = copperColor

        // Draw popup if visible (speedometer-style circular design above knob)
        if (showPopup || popupAlpha > 0f) {
            val letter = getCurrentLetter()
            
            // Position above the draggable knob, aligned with vertical line
            val popupX = centerX  // Centered on vertical line
            var popupY = dotY - popupSize - 20f   // Above the knob with gap
            val radius = popupSize / 2
            
            // Keep popup within top bounds
            if (popupY - radius < paddingTop) {
                popupY = paddingTop + radius
            }

            // Save alpha values
            val bgOldAlpha = popupPaint.alpha
            val ringOldAlpha = popupRingPaint.alpha
            val innerRingOldAlpha = popupInnerRingPaint.alpha
            val textOldAlpha = popupTextPaint.alpha
            
            // Apply fade animation alpha
            popupPaint.alpha = (bgOldAlpha * popupAlpha).toInt()
            popupRingPaint.alpha = (ringOldAlpha * popupAlpha).toInt()
            popupInnerRingPaint.alpha = (innerRingOldAlpha * popupAlpha).toInt()
            popupTextPaint.alpha = (255 * popupAlpha).toInt()

            // Draw main circle (speedometer face)
            canvas.drawCircle(popupX, popupY, radius, popupPaint)
            
            // Draw outer ring (speedometer bezel)
            canvas.drawCircle(popupX, popupY, radius - 3f, popupRingPaint)
            
            // Draw inner decorative ring
            canvas.drawCircle(popupX, popupY, radius - 12f, popupInnerRingPaint)

            // Draw letter in center (speedometer reading)
            val textY = popupY + (popupTextPaint.textSize - popupTextPaint.descent() - popupTextPaint.ascent()) / 2
            canvas.drawText(letter, popupX, textY, popupTextPaint)
            
            // Restore alpha values
            popupPaint.alpha = bgOldAlpha
            popupRingPaint.alpha = ringOldAlpha
            popupInnerRingPaint.alpha = innerRingOldAlpha
            popupTextPaint.alpha = textOldAlpha
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                showPopup = true
                animatePopupAlpha(1f)
                animateZipperOpen()  // Open the zipper chain
                updateDotPosition(event.y)
                removeHidePopupCallback()
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateDotPosition(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    animateZipperClose()  // Close the zipper chain
                    scheduleHidePopup()
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateDotPosition(touchY: Float) {
        val startY = paddingTop.toFloat() + dotRadius * 2
        val endY = height - paddingBottom.toFloat() - dotRadius * 2
        val range = endY - startY

        if (range <= 0) return

        dotPosition = ((touchY - startY) / range).coerceIn(0f, 1f)
        scrollToLetter()
        invalidate()
    }

    private fun getCurrentLetter(): String {
        if (alphabet.isEmpty()) {
            return "#"
        }
        val index = (dotPosition * (alphabet.size - 1)).toInt().coerceIn(0, alphabet.size - 1)
        return alphabet[index].toString()
    }

    private fun scrollToLetter() {
        val letter = getCurrentLetter()
        val position = findPositionForLetter(letter)
        
        if (position >= 0) {
            recyclerView?.let { rv ->
                val layoutManager = rv.layoutManager as? LinearLayoutManager
                layoutManager?.scrollToPositionWithOffset(position, 0)
            }
        }
    }

    private fun findPositionForLetter(letter: String): Int {
        if (contacts.isEmpty()) return -1

        val isHindiMode = context.config.appLanguage == LANGUAGE_HINDI
        
        for (i in contacts.indices) {
            val contact = contacts[i]
            val firstLetter = contact.getFirstLetter()
            
            if (letter == "#") {
                // For '#', find contacts that don't start with alphabet letters
                if (firstLetter == "#") {
                    return i
                }
            } else {
                // In Hindi mode, only match Hindi letters; in English mode, only match English letters
                val shouldMatch = if (isHindiMode) {
                    contact.startsWithHindiLetter() && firstLetter.startsWith(letter, ignoreCase = false)
                } else {
                    contact.startsWithEnglishLetter() && firstLetter.startsWith(letter, ignoreCase = true)
                }
                
                if (shouldMatch) {
                    return i
                }
            }
        }

        // If exact match not found, find the closest position
        return findClosestPosition(letter)
    }

    private fun findClosestPosition(letter: String): Int {
        if (contacts.isEmpty()) return -1
        if (letter == "#") return contacts.size - 1

        val targetChar = letter.firstOrNull() ?: return 0
        
        for (i in contacts.indices) {
            val firstLetter = contacts[i].getFirstLetter()
            if (firstLetter.isNotEmpty()) {
                val contactChar = firstLetter.first().uppercaseChar()
                if (contactChar >= targetChar) {
                    return max(0, i)
                }
            }
        }

        return contacts.size - 1
    }

    private fun animatePopupAlpha(targetAlpha: Float) {
        popupAnimator?.cancel()
        popupAnimator = ValueAnimator.ofFloat(popupAlpha, targetAlpha).apply {
            duration = 150
            addUpdateListener { animator ->
                popupAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun scheduleHidePopup() {
        removeHidePopupCallback()
        hidePopupRunnable = Runnable {
            showPopup = false
            animatePopupAlpha(0f)
        }
        postDelayed(hidePopupRunnable, 800)
    }

    private fun removeHidePopupCallback() {
        hidePopupRunnable?.let {
            removeCallbacks(it)
            hidePopupRunnable = null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeHidePopupCallback()
        popupAnimator?.cancel()
        zipperAnimator?.cancel()
    }
    
    private fun drawZipperLine(canvas: Canvas, centerX: Float, startY: Float, endY: Float, dotY: Float) {
        val splitOffset = maxZipperSplit * zipperSplitAmount
        val popupX = centerX - popupSize - popupOffset
        
        val path = Path()
        
        // Upper section - straight line from top to zipper split start
        val splitZoneStart = dotY - 80f
        val splitZoneEnd = dotY + 80f
        
        if (zipperSplitAmount > 0f) {
            // Draw upper straight line
            if (splitZoneStart > startY) {
                canvas.drawLine(centerX, startY, centerX, splitZoneStart, linePaint)
            }
            
            // Draw the zipper curve - splits toward popup like opening chain
            path.reset()
            path.moveTo(centerX, splitZoneStart)
            
            // Bezier curve bending toward popup (left)
            val controlX1 = centerX - (splitOffset * 0.5f)
            val controlY1 = dotY - 40f
            val curveX = centerX - splitOffset  // Curves toward popup
            val curveY = dotY
            
            path.cubicTo(
                controlX1, controlY1,  // First control point
                curveX, controlY1,      // Second control point
                curveX, curveY          // End point at dot level
            )
            
            // Curve back to center
            val controlX2 = centerX - (splitOffset * 0.5f)
            val controlY2 = dotY + 40f
            
            path.cubicTo(
                curveX, controlY2,      // Control point
                controlX2, controlY2,   // Control point
                centerX, splitZoneEnd   // Back to center
            )
            
            canvas.drawPath(path, linePaint)
            
            // Draw lower straight line
            if (splitZoneEnd < endY) {
                canvas.drawLine(centerX, splitZoneEnd, centerX, endY, linePaint)
            }
            
            // Draw chain links effect - small circles along the curve
            if (zipperSplitAmount > 0.3f) {
                val chainPaint = Paint(linePaint).apply {
                    style = Paint.Style.FILL
                    alpha = (zipperSplitAmount * 255).toInt()
                }
                
                // Draw chain links at key points
                val linkRadius = 3f
                canvas.drawCircle(controlX1, controlY1, linkRadius, chainPaint)
                canvas.drawCircle(curveX, curveY, linkRadius, chainPaint)
                canvas.drawCircle(controlX2, controlY2, linkRadius, chainPaint)
            }
        } else {
            // No split - draw straight line
            canvas.drawLine(centerX, startY, centerX, endY, linePaint)
        }
    }
    
    private fun animateZipperOpen() {
        zipperAnimator?.cancel()
        zipperAnimator = ValueAnimator.ofFloat(zipperSplitAmount, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                zipperSplitAmount = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    private fun animateZipperClose() {
        zipperAnimator?.cancel()
        zipperAnimator = ValueAnimator.ofFloat(zipperSplitAmount, 0f).apply {
            duration = 250
            addUpdateListener { animator ->
                zipperSplitAmount = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
