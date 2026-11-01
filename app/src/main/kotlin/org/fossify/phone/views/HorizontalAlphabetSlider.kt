package org.fossify.phone.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getFirstLetter
import org.fossify.phone.extensions.startsWithEnglishLetter
import org.fossify.phone.extensions.startsWithHindiLetter
import org.fossify.phone.helpers.LANGUAGE_HINDI
import kotlin.math.max

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
        strokeWidth = 4f
    }

    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(16f, 0f, 6f, 0x80000000.toInt())
    }

    private val popupStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        isFakeBoldText = true
    }

    private var contacts: List<Contact> = emptyList()
    private var recyclerView: RecyclerView? = null

    // Full alphabet lists
    private val allEnglishLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toList()
    private val allHindiLetters = "अआइईउऊएओकखगघचछजझटठडढणतथदधनपफबभमयरलवशषसह#".toList()

    // Active alphabet (dynamically filtered based on contacts)
    private var alphabet = emptyList<Char>()

    private var dotPosition = 0f // 0.0 to 1.0
    private var isDragging = false
    private var showPopup = false
    private val dotRadius = 24f
    private val popupRadius = 46f

    private var hidePopupRunnable: Runnable? = null
    private var popupAnimator: ValueAnimator? = null
    private var popupAlpha = 0f

    private var textColor: Int = 0
    private var primaryColor: Int = 0

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateColors()
    }

    fun updateColors() {
        textColor = context.getProperTextColor()
        primaryColor = ContextCompat.getColor(context, R.color.color_primary)

        linePaint.color = textColor.adjustAlpha(0.2f)

        // SimplyDial Electric Cyan Scrubber
        dotPaint.color = primaryColor
        dotStrokePaint.color = textColor.adjustAlpha(0.6f)

        // Sleek floating cyan bubble with dark contrast typography
        popupPaint.color = primaryColor
        popupStrokePaint.color = textColor.adjustAlpha(0.3f)
        popupTextPaint.color = 0xFF000000.toInt()

        invalidate()
    }

    fun setupWithContacts(recyclerView: RecyclerView, contacts: List<Contact>) {
        this.recyclerView = recyclerView
        this.contacts = contacts

        isDragging = false
        showPopup = false
        popupAlpha = 0f
        removeHidePopupCallback()
        popupAnimator?.cancel()
        updateAlphabetForLanguage()
        invalidate()
    }

    private fun updateAlphabetForLanguage() {
        val isHindiMode = context.config.appLanguage == LANGUAGE_HINDI

        val contactLetters = contacts
            .mapNotNull { contact ->
                val firstLetter = contact.getFirstLetter()
                if (firstLetter.isEmpty()) return@mapNotNull null

                when {
                    firstLetter == "#" -> "#"
                    isHindiMode && contact.startsWithHindiLetter() -> firstLetter
                    !isHindiMode && contact.startsWithEnglishLetter() -> firstLetter
                    else -> null
                }
            }
            .toSet()

        val baseAlphabet = if (isHindiMode) allHindiLetters else allEnglishLetters

        alphabet = baseAlphabet
            .filter { letter ->
                val letterStr = letter.toString()
                contactLetters.contains(letterStr) || letterStr == "#"
            }
            .sortedBy { letter ->
                baseAlphabet.indexOf(letter)
            }

        if (alphabet.isEmpty() && contacts.isNotEmpty()) {
            alphabet = listOf('#')
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (dotRadius * 2 + paddingStart + paddingEnd + 16).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startY = paddingTop.toFloat() + dotRadius * 2
        val endY = height - paddingBottom.toFloat() - dotRadius * 2
        val centerX = width / 2f

        if (endY <= startY) return

        // 1. Draw smooth vertical capsule scrubber line
        canvas.drawLine(centerX, startY, centerX, endY, linePaint)

        // 2. Calculate knob position
        val dotY = startY + (endY - startY) * dotPosition

        // 3. Draw knob with glow / outer ring
        dotStrokePaint.alpha = if (isDragging) 220 else 100
        canvas.drawCircle(centerX, dotY, dotRadius + 4f, dotStrokePaint)

        // Draw primary knob circle
        dotPaint.alpha = 255
        canvas.drawCircle(centerX, dotY, dotRadius, dotPaint)

        // Inner dark core for high-end look
        val oldColor = dotPaint.color
        dotPaint.color = 0xFF121212.toInt()
        canvas.drawCircle(centerX, dotY, dotRadius * 0.4f, dotPaint)
        dotPaint.color = oldColor

        // 4. Draw preview bubble when dragging (positioned to the left so thumb does NOT block it)
        if (showPopup || popupAlpha > 0f) {
            val letter = getCurrentLetter()

            // Position bubble cleanly to the left of the slider
            val popupX = centerX - popupRadius - 40f
            var popupY = dotY

            // Keep within top and bottom bounds
            if (popupY - popupRadius < paddingTop) {
                popupY = paddingTop + popupRadius
            } else if (popupY + popupRadius > height - paddingBottom) {
                popupY = height - paddingBottom - popupRadius
            }

            // Save alpha values
            val bgOldAlpha = popupPaint.alpha
            val textOldAlpha = popupTextPaint.alpha
            val strokeOldAlpha = popupStrokePaint.alpha

            // Apply fade animation
            popupPaint.alpha = (bgOldAlpha * popupAlpha).toInt()
            popupStrokePaint.alpha = (strokeOldAlpha * popupAlpha).toInt()
            popupTextPaint.alpha = (255 * popupAlpha).toInt()

            // Draw glowing cyan bubble
            canvas.drawCircle(popupX, popupY, popupRadius, popupPaint)
            canvas.drawCircle(popupX, popupY, popupRadius, popupStrokePaint)

            // Draw bold contrast letter
            val textY = popupY + (popupTextPaint.textSize - popupTextPaint.descent() - popupTextPaint.ascent()) / 2
            canvas.drawText(letter, popupX, textY, popupTextPaint)

            // Restore alpha
            popupPaint.alpha = bgOldAlpha
            popupStrokePaint.alpha = strokeOldAlpha
            popupTextPaint.alpha = textOldAlpha
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                showPopup = true
                animatePopupAlpha(1f)
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
                if (firstLetter == "#") {
                    return i
                }
            } else {
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
        postDelayed(hidePopupRunnable, 600)
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
    }
}
