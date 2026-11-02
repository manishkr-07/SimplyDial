package org.fossify.phone.dialogs

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.phone.R
import org.fossify.phone.databinding.DialogCustomizeInterfaceBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.Config
import org.fossify.phone.helpers.INTERFACE_MODE_MODERN
import org.fossify.phone.helpers.INTERFACE_MODE_SIMPLE

class CustomizeInterfaceBottomSheetDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: DialogCustomizeInterfaceBinding
    private var selectedTabMode = INTERFACE_MODE_MODERN
    private lateinit var simplerFeatures: Config.ModeFeatures
    private lateinit var modernFeatures: Config.ModeFeatures
    var onSaved: (() -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = DialogCustomizeInterfaceBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)

        val config = requireActivity().config
        selectedTabMode = config.interfaceMode
        simplerFeatures = config.getModeFeatures(INTERFACE_MODE_SIMPLE)
        modernFeatures = config.getModeFeatures(INTERFACE_MODE_MODERN)

        setupTabs()
        loadStateToSwitches(selectedTabMode)
        setupListeners()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColors()
        updateTabStyles()
    }

    private fun setupColors() {
        val activity = activity ?: return
        val properTextColor = activity.getProperTextColor()
        val properPrimaryColor = activity.getProperPrimaryColor()
        val secondaryTextColor = properTextColor.adjustAlpha(0.7f)

        activity.updateTextColors(binding.root)

        binding.apply {
            dialogCustomizeTitle.setTextColor(properTextColor)
            dialogCustomizeSubtitle.setTextColor(secondaryTextColor)
            tvModeHelper.setTextColor(secondaryTextColor)

            headerCallingActions.setTextColor(properPrimaryColor)
            headerIncomingIncall.setTextColor(properPrimaryColor)
            headerDialpadKeypad.setTextColor(properPrimaryColor)
            headerRecentsMessaging.setTextColor(properPrimaryColor)

            val trackColor = ColorStateList.valueOf(properPrimaryColor)
            switchDirectOneTap.trackTintList = trackColor
            switchCallConfirmation.trackTintList = trackColor
            switchQuickActions.trackTintList = trackColor
            switchProfilePictureClick.trackTintList = trackColor
            switchLargeAnswer.trackTintList = trackColor
            switchFullscreenCall.trackTintList = trackColor
            switchDialpadVibration.trackTintList = trackColor
            switchDialpadBeeps.trackTintList = trackColor
            switchOpenDialpadAtLaunch.trackTintList = trackColor
            switchHideDialpadNumbers.trackTintList = trackColor
            switchMessagesJoystick.trackTintList = trackColor
            switchGroupSubsequentCalls.trackTintList = trackColor
            switchAnnounceCallerName.trackTintList = trackColor
            switchShowEmergencySosTile.trackTintList = trackColor
            switchSmartDualSim.trackTintList = trackColor

            btnApplySave.backgroundTintList = ColorStateList.valueOf(properPrimaryColor)
            btnApplySave.setTextColor(properPrimaryColor.getContrastColor())
        }
    }

    private fun setupTabs() {
        binding.apply {
            tabModeSimple.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (selectedTabMode != INTERFACE_MODE_SIMPLE) {
                    saveCurrentTabToState()
                    selectedTabMode = INTERFACE_MODE_SIMPLE
                    updateTabStyles()
                    loadStateToSwitches(selectedTabMode)
                }
            }

            tabModeModern.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (selectedTabMode != INTERFACE_MODE_MODERN) {
                    saveCurrentTabToState()
                    selectedTabMode = INTERFACE_MODE_MODERN
                    updateTabStyles()
                    loadStateToSwitches(selectedTabMode)
                }
            }
        }
    }

    private fun updateTabStyles() {
        val activity = activity ?: return
        val config = activity.config
        val properPrimaryColor = activity.getProperPrimaryColor()
        val properTextColor = activity.getProperTextColor()
        val secondaryTextColor = properTextColor.adjustAlpha(0.65f)
        val density = resources.displayMetrics.density

        val radius = 12 * density
        val cardBgColor = activity.getColor(R.color.card_background)
        val dividerColor = activity.getColor(R.color.divider_color)

        val selectedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(cardBgColor)
            setStroke((2 * density).toInt(), properPrimaryColor)
        }

        val unselectedBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(cardBgColor)
            setStroke((1 * density).toInt(), dividerColor)
        }

        binding.apply {
            val isCurrentAppSimple = config.interfaceMode == INTERFACE_MODE_SIMPLE
            badgeActiveSimple.beVisibleIf(isCurrentAppSimple)
            badgeActiveModern.beVisibleIf(!isCurrentAppSimple)

            if (selectedTabMode == INTERFACE_MODE_SIMPLE) {
                tabModeSimple.background = selectedBg
                tabIconSimple.imageTintList = ColorStateList.valueOf(properPrimaryColor)
                tabTextSimple.setTextColor(properTextColor)

                tabModeModern.background = unselectedBg
                tabIconModern.imageTintList = ColorStateList.valueOf(secondaryTextColor)
                tabTextModern.setTextColor(secondaryTextColor)

                tvModeHelper.text = getString(R.string.customizing_mode_helper, getString(R.string.mode_tab_simple))
            } else {
                tabModeModern.background = selectedBg
                tabIconModern.imageTintList = ColorStateList.valueOf(properPrimaryColor)
                tabTextModern.setTextColor(properTextColor)

                tabModeSimple.background = unselectedBg
                tabIconSimple.imageTintList = ColorStateList.valueOf(secondaryTextColor)
                tabTextSimple.setTextColor(secondaryTextColor)

                tvModeHelper.text = getString(R.string.customizing_mode_helper, getString(R.string.mode_tab_modern))
            }
        }
    }

    private fun loadStateToSwitches(mode: Int) {
        val features = if (mode == INTERFACE_MODE_SIMPLE) simplerFeatures else modernFeatures
        binding.apply {
            switchDirectOneTap.isChecked = features.directOneTapCalling
            switchCallConfirmation.isChecked = features.showCallConfirmation
            switchQuickActions.isChecked = features.showQuickActionsSheet
            switchProfilePictureClick.isChecked = features.enableProfilePictureClick
            switchLargeAnswer.isChecked = features.disableSwipeToAnswer
            switchFullscreenCall.isChecked = features.alwaysShowFullscreen
            switchDialpadVibration.isChecked = features.dialpadVibration
            switchDialpadBeeps.isChecked = features.dialpadBeeps
            switchOpenDialpadAtLaunch.isChecked = features.openDialPadAtLaunch
            switchHideDialpadNumbers.isChecked = features.hideDialpadNumbers
            switchMessagesJoystick.isChecked = features.enableMessagesJoystick
            switchGroupSubsequentCalls.isChecked = features.groupSubsequentCalls
            switchAnnounceCallerName.isChecked = features.announceCallerName
            switchShowEmergencySosTile.isChecked = features.showEmergencySOSTile
            switchSmartDualSim.isChecked = features.smartDualSIM
            switchPostCallSummary.isChecked = features.showPostCallSummary
        }
    }

    private fun saveCurrentTabToState() {
        val features = if (selectedTabMode == INTERFACE_MODE_SIMPLE) simplerFeatures else modernFeatures
        binding.apply {
            features.directOneTapCalling = switchDirectOneTap.isChecked
            features.showCallConfirmation = switchCallConfirmation.isChecked
            features.showQuickActionsSheet = switchQuickActions.isChecked
            features.enableProfilePictureClick = switchProfilePictureClick.isChecked
            features.disableSwipeToAnswer = switchLargeAnswer.isChecked
            features.alwaysShowFullscreen = switchFullscreenCall.isChecked
            features.dialpadVibration = switchDialpadVibration.isChecked
            features.dialpadBeeps = switchDialpadBeeps.isChecked
            features.openDialPadAtLaunch = switchOpenDialpadAtLaunch.isChecked
            features.hideDialpadNumbers = switchHideDialpadNumbers.isChecked
            features.enableMessagesJoystick = switchMessagesJoystick.isChecked
            features.groupSubsequentCalls = switchGroupSubsequentCalls.isChecked
            features.announceCallerName = switchAnnounceCallerName.isChecked
            features.showEmergencySOSTile = switchShowEmergencySosTile.isChecked
            features.smartDualSIM = switchSmartDualSim.isChecked
            features.showPostCallSummary = switchPostCallSummary.isChecked
        }
    }

    private fun setupListeners() {
        binding.apply {
            holderDirectOneTap.setOnClickListener { switchDirectOneTap.toggle() }
            holderCallConfirmation.setOnClickListener { switchCallConfirmation.toggle() }
            holderQuickActions.setOnClickListener { switchQuickActions.toggle() }
            holderProfilePictureClick.setOnClickListener { switchProfilePictureClick.toggle() }
            holderLargeAnswer.setOnClickListener { switchLargeAnswer.toggle() }
            holderFullscreenCall.setOnClickListener { switchFullscreenCall.toggle() }
            holderDialpadVibration.setOnClickListener { switchDialpadVibration.toggle() }
            holderDialpadBeeps.setOnClickListener { switchDialpadBeeps.toggle() }
            holderOpenDialpadAtLaunch.setOnClickListener { switchOpenDialpadAtLaunch.toggle() }
            holderHideDialpadNumbers.setOnClickListener { switchHideDialpadNumbers.toggle() }
            holderMessagesJoystick.setOnClickListener { switchMessagesJoystick.toggle() }
            holderGroupSubsequentCalls.setOnClickListener { switchGroupSubsequentCalls.toggle() }
            holderAnnounceCallerName.setOnClickListener { switchAnnounceCallerName.toggle() }
            holderShowEmergencySosTile.setOnClickListener { switchShowEmergencySosTile.toggle() }
            holderSmartDualSim.setOnClickListener { switchSmartDualSim.toggle() }
            holderPostCallSummary.setOnClickListener { switchPostCallSummary.toggle() }

            btnResetDefaults.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val config = requireActivity().config
                val defaults = config.getDefaultModeFeatures(selectedTabMode)
                if (selectedTabMode == INTERFACE_MODE_SIMPLE) {
                    simplerFeatures = defaults
                } else {
                    modernFeatures = defaults
                }
                loadStateToSwitches(selectedTabMode)

                val modeName = if (selectedTabMode == INTERFACE_MODE_SIMPLE) {
                    getString(R.string.mode_tab_simple)
                } else {
                    getString(R.string.mode_tab_modern)
                }
                requireActivity().toast(getString(R.string.defaults_restored_msg, modeName))
            }

            btnApplySave.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                saveCurrentTabToState()

                val activity = requireActivity()
                val config = activity.config

                config.saveModeFeatures(INTERFACE_MODE_SIMPLE, simplerFeatures)
                config.saveModeFeatures(INTERFACE_MODE_MODERN, modernFeatures)
                config.syncModePreferences()

                activity.toast(getString(R.string.settings_updated))
                onSaved?.invoke()
                dismissAllowingStateLoss()
            }
        }
    }

    companion object {
        private const val TAG = "CustomizeInterfaceBottomSheetDialog"

        fun show(
            fragmentManager: FragmentManager,
            onSaved: (() -> Unit)? = null
        ): CustomizeInterfaceBottomSheetDialog {
            return CustomizeInterfaceBottomSheetDialog().apply {
                this.onSaved = onSaved
                show(fragmentManager, TAG)
            }
        }
    }
}
