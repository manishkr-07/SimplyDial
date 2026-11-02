package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogInterfaceModeSelectionBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.INTERFACE_MODE_MODERN
import org.fossify.phone.helpers.INTERFACE_MODE_SIMPLE

class InterfaceModeBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogInterfaceModeSelectionBinding
    private var selectedMode = INTERFACE_MODE_MODERN
    private var originalMode = INTERFACE_MODE_MODERN
    var onModeSelected: ((Int) -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogInterfaceModeSelectionBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        originalMode = requireActivity().config.interfaceMode
        selectedMode = originalMode
        setupInterfaceOptions()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColors()
        updateCardStates()
    }

    private fun setupColors() {
        val activity = activity ?: return
        val properTextColor = activity.getProperTextColor()
        val properPrimaryColor = activity.getProperPrimaryColor()
        val secondaryTextColor = properTextColor.adjustAlpha(0.7f)

        binding.apply {
            dialogInterfaceTitle.setTextColor(properTextColor)
            dialogInterfaceSubtitle.setTextColor(secondaryTextColor)

            textModeSimpleTitle.setTextColor(properTextColor)
            textModeModernTitle.setTextColor(properTextColor)

            textModeSimpleDesc.setTextColor(secondaryTextColor)
            textModeModernDesc.setTextColor(secondaryTextColor)

            bulletSimple1.setTextColor(secondaryTextColor)
            bulletSimple2.setTextColor(secondaryTextColor)
            bulletSimple3.setTextColor(secondaryTextColor)
            bulletSimple4.setTextColor(secondaryTextColor)

            bulletModern1.setTextColor(secondaryTextColor)
            bulletModern2.setTextColor(secondaryTextColor)
            bulletModern3.setTextColor(secondaryTextColor)
            bulletModern4.setTextColor(secondaryTextColor)
            bulletModern5.setTextColor(secondaryTextColor)

            badgeModeSimple.setTextColor(properPrimaryColor)
            badgeModeModern.setTextColor(properPrimaryColor)

            btnCustomizeMode.setTextColor(properPrimaryColor)

            radioModeSimple.buttonTintList = ColorStateList.valueOf(properPrimaryColor)
            radioModeModern.buttonTintList = ColorStateList.valueOf(properPrimaryColor)
        }
    }

    private fun setupInterfaceOptions() {
        // Touch micro-interactions
        val touchListener = android.view.View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
            false
        }

        binding.apply {
            optionModeSimple.setOnTouchListener(touchListener)
            optionModeModern.setOnTouchListener(touchListener)
            btnApplyMode.setOnTouchListener(touchListener)

            optionModeSimple.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedMode = INTERFACE_MODE_SIMPLE
                updateCardStates()
            }

            optionModeModern.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedMode = INTERFACE_MODE_MODERN
                updateCardStates()
            }

            btnApplyMode.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (selectedMode == originalMode) {
                    dismissAllowingStateLoss()
                } else {
                    applyMode(selectedMode)
                }
            }

            btnCustomizeMode.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dismissAllowingStateLoss()
                CustomizeInterfaceBottomSheetDialog.show(parentFragmentManager) {
                    onModeSelected?.invoke(selectedMode)
                }
            }
        }
    }

    private fun updateCardStates() {
        val activity = activity ?: return
        val properPrimaryColor = activity.getProperPrimaryColor()
        val properTextColor = activity.getProperTextColor()
        val density = resources.displayMetrics.density
        val radius = 16 * density
        val strokeWidthSelected = (2 * density).toInt()
        val strokeWidthUnselected = (1 * density).toInt()

        val dividerColor = activity.getColor(R.color.divider_color)
        val cardBgColor = activity.getColor(R.color.card_background)

        // Selected card drawable with subtle primary tint
        val selectedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(cardBgColor)
            setStroke(strokeWidthSelected, properPrimaryColor)
        }

        // Unselected card drawable
        val unselectedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(cardBgColor)
            setStroke(strokeWidthUnselected, dividerColor)
        }

        binding.apply {
            if (selectedMode == INTERFACE_MODE_SIMPLE) {
                optionModeSimple.background = selectedDrawable
                radioModeSimple.isChecked = true
                iconSimpleMode.backgroundTintList = ColorStateList.valueOf(properPrimaryColor.adjustAlpha(0.18f))
                iconSimpleMode.imageTintList = ColorStateList.valueOf(properPrimaryColor)

                optionModeModern.background = unselectedDrawable
                radioModeModern.isChecked = false
                iconModernMode.backgroundTintList = null
                iconModernMode.imageTintList = ColorStateList.valueOf(properTextColor.adjustAlpha(0.6f))
            } else {
                optionModeModern.background = selectedDrawable
                radioModeModern.isChecked = true
                iconModernMode.backgroundTintList = ColorStateList.valueOf(properPrimaryColor.adjustAlpha(0.18f))
                iconModernMode.imageTintList = ColorStateList.valueOf(properPrimaryColor)

                optionModeSimple.background = unselectedDrawable
                radioModeSimple.isChecked = false
                iconSimpleMode.backgroundTintList = null
                iconSimpleMode.imageTintList = ColorStateList.valueOf(properTextColor.adjustAlpha(0.6f))
            }

            // Apply Button Styling & Contrast
            val buttonBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(properPrimaryColor)
            }
            btnApplyMode.background = buttonBg
            btnApplyMode.setTextColor(properPrimaryColor.getContrastColor())

            if (selectedMode != originalMode) {
                val modeTitle = if (selectedMode == INTERFACE_MODE_SIMPLE) {
                    getString(R.string.interface_mode_simple_title)
                } else {
                    getString(R.string.interface_mode_modern_title)
                }
                btnApplyMode.text = "✓ Switch to $modeTitle"
                btnApplyMode.alpha = 1.0f
            } else {
                val modeTitle = if (selectedMode == INTERFACE_MODE_SIMPLE) {
                    getString(R.string.interface_mode_simple_title)
                } else {
                    getString(R.string.interface_mode_modern_title)
                }
                btnApplyMode.text = "Active: $modeTitle (Done)"
                btnApplyMode.alpha = 0.9f
            }
        }
    }

    private fun applyMode(newMode: Int) {
        val activity = activity ?: return
        val config = activity.config

        config.interfaceMode = newMode
        config.syncModePreferences(newMode)
        config.fontSize = if (newMode == INTERFACE_MODE_SIMPLE) FONT_SIZE_EXTRA_LARGE else FONT_SIZE_MEDIUM

        val modeTitle = if (newMode == INTERFACE_MODE_SIMPLE) {
            getString(R.string.interface_mode_simple_title)
        } else {
            getString(R.string.interface_mode_modern_title)
        }
        activity.toast("Interface mode switched to $modeTitle")

        onModeSelected?.invoke(newMode)
        dismissAllowingStateLoss()

        // Smooth recreate without process termination or home-screen ejection
        activity.recreate()
    }

    companion object {
        private const val TAG = "InterfaceModeBottomSheetDialog"

        fun show(
            fragmentManager: FragmentManager,
            onModeSelected: ((Int) -> Unit)? = null
        ): InterfaceModeBottomSheetDialog {
            return InterfaceModeBottomSheetDialog().apply {
                this.onModeSelected = onModeSelected
                show(fragmentManager, TAG)
            }
        }
    }
}
