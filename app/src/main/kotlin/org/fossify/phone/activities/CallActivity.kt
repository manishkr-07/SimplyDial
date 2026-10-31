package org.fossify.phone.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.postDelayed
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.SimpleListItem
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityCallBinding
import org.fossify.phone.dialogs.CallRecordingConsentDialog
import org.fossify.phone.dialogs.DynamicBottomSheetChooserDialog
import org.fossify.phone.dialogs.RecordingReviewDialog
import org.fossify.phone.dialogs.VideoCallOptionsDialog
import org.fossify.phone.extensions.*
import org.fossify.phone.extensions.addAnimatedGradientRing
import org.fossify.phone.extensions.addRotatingBorder
import org.fossify.phone.extensions.updateCallButtonState
import org.fossify.phone.helpers.*
import org.fossify.phone.managers.CallRecordingManager
import org.fossify.phone.models.AudioRoute
import org.fossify.phone.models.CallContact
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Timer
import java.util.TimerTask
import kotlin.math.max
import kotlin.math.min
import org.fossify.phone.helpers.JitsiMeetHelper

class CallActivity : SimpleActivity() {
    companion object {
        fun getStartIntent(context: Context): Intent {
            val openAppIntent = Intent(context, CallActivity::class.java)
            openAppIntent.flags = Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            return openAppIntent
        }
    }

    private val binding by viewBinding(ActivityCallBinding::inflate)

    private var isSpeakerOn = false
    private var isMicrophoneOff = false
    private var isCallEnded = false
    private var callContact: CallContact? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var screenOnWakeLock: PowerManager.WakeLock? = null
    private var callDuration = 0
    private val callDurationHandler = Handler(Looper.getMainLooper())
    private var dragDownX = 0f
    private var stopAnimation = false
    private var viewsUnderDialpad = arrayListOf<Pair<View, Float>>()
    private var dialpadHeight = 0f

    private var audioRouteChooserDialog: DynamicBottomSheetChooserDialog? = null
    
    // Call recording
    private lateinit var recordingManager: CallRecordingManager
    private var isRecording = false
    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private val STORAGE_PERMISSION_CODE = 102
    private var shouldStartRecordingAfterPermission = false
    private var recordingTimer: Timer? = null
    private lateinit var jitsiMeetHelper: JitsiMeetHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (CallManager.getPhoneState() == NoCall) {
            finish()
            return
        }

        handleNotificationPermission { }

        // Initialize recording manager
        recordingManager = CallRecordingManager(this)
        setupRecordingCallbacks()
        
        // Initialize Jitsi Meet helper for video calling
        jitsiMeetHelper = JitsiMeetHelper(this)
        
        // Register for EventBus
        EventBus.getDefault().register(this)

        // Force light theme for call screen
        binding.callHolder.setBackgroundColor(0xFFFFFFFF.toInt())
        updateTextColors(binding.callHolder)
        initButtons()
        audioManager.mode = AudioManager.MODE_IN_CALL
        addLockScreenFlags()
        CallManager.addListener(callCallback)
        updateCallContactInfo(CallManager.getPrimaryCall())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val ringingCall = CallManager.getRingingCall()
        if (ringingCall != null) {
            updateCallContactInfo(ringingCall)
        } else {
            updateCallContactInfo(CallManager.getPrimaryCall())
        }
        updateState()
    }

    override fun onResume() {
        super.onResume()
        val ringingCall = CallManager.getRingingCall()
        if (ringingCall != null) {
            updateCallContactInfo(ringingCall)
        } else {
            updateCallContactInfo(CallManager.getPrimaryCall())
        }
        updateState()
        // Force light navigation and status bar
        updateNavigationBarColor(0xFFFFFFFF.toInt())
        updateStatusbarColor(0xFFFFFFFF.toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.removeListener(callCallback)
        disableProximitySensor()

        if (screenOnWakeLock?.isHeld == true) {
            screenOnWakeLock!!.release()
        }
        
        // Cleanup recording
        if (isRecording) {
            stopRecording()
        }
        stopRecordingTimer()
        recordingManager.release()
        EventBus.getDefault().unregister(this)
    }

    override fun onBackPressed() {
        if (binding.dialpadWrapper.isVisible()) {
            hideDialpad()
            return
        } else {
            super.onBackPressed()
        }

        val callState = CallManager.getState()
        if (callState == Call.STATE_CONNECTING || callState == Call.STATE_DIALING) {
            toast(R.string.call_is_being_connected)
        }
    }

    private fun initButtons() = binding.apply {
        // Initialize joystick message button
        joystickMessageButton.onSegmentSelectedListener = { message ->
            sendQuickReplyMessage(message)
        }
        joystickMessageButton.beVisibleIf(config.enableMessagesJoystick)

        // Initialize new glassmorphic swipeable call buttons
        swipeCallButtons.onAcceptCall = {
            acceptCall()
        }
        
        swipeCallButtons.onDeclineCall = {
            endCall()
        }

        if (config.disableSwipeToAnswer) {
            // User wants CLICKING - hide new swipeable buttons, show old click buttons
            swipeCallButtons.beGone()
            
            callDraggable.beGone()
            callDraggableBackground.beGone()
            callLeftArrow.beGone()
            callRightArrow.beGone()

            callDecline.beVisible()
            callDeclineLabel.beVisible()
            callAccept.beVisible()
            callAcceptLabel.beVisible()
            
            // Position message button below status text and above click buttons
            val messageButtonParams = joystickMessageButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            messageButtonParams.topToBottom = callStatusLabel.id
            messageButtonParams.bottomToTop = callDecline.id
            messageButtonParams.topMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            messageButtonParams.bottomMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            messageButtonParams.verticalBias = 0.5f  // Centered in available space
            joystickMessageButton.layoutParams = messageButtonParams
            
            callDecline.setOnClickListener {
                endCall()
            }

            callAccept.setOnClickListener {
                acceptCall()
            }
        } else {
            // User wants SWIPING - show new glassmorphic swipeable buttons
            swipeCallButtons.beVisible()
            
            // Hide old swipe mechanism and click buttons
            callDraggable.beGone()
            callDraggableBackground.beGone()
            callLeftArrow.beGone()
            callRightArrow.beGone()
            callDecline.beGone()
            callDeclineLabel.beGone()
            callAccept.beGone()
            callAcceptLabel.beGone()
            
            // Position message button below status text and above swipe buttons
            val messageButtonParams = joystickMessageButton.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            messageButtonParams.topToBottom = callStatusLabel.id
            messageButtonParams.bottomToTop = swipeCallButtons.id
            messageButtonParams.topMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            messageButtonParams.bottomMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            messageButtonParams.verticalBias = 0.5f  // Centered in available space
            joystickMessageButton.layoutParams = messageButtonParams
            
            // Old swipe logic no longer needed, using new swipeable buttons
            // handleSwipe()
        }

        callToggleMicrophone.setOnClickListener {
            toggleMicrophone()
        }

        callToggleSpeaker.setOnClickListener {
            changeCallAudioRoute()
        }

        callDialpad.setOnClickListener {
            toggleDialpadVisibility()
        }

        dialpadClose.setOnClickListener {
            hideDialpad()
        }

        callToggleHold.setOnClickListener {
            toggleHold()
        }

        callRecord.setOnClickListener {
            toggleCallRecording()
        }

        callAdd.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                startActivity(this)
            }
        }

        callSwap.setOnClickListener {
            CallManager.swap()
        }

        callMerge.setOnClickListener {
            CallManager.merge()
        }

        callManage.setOnClickListener {
            startActivity(Intent(this@CallActivity, ConferenceActivity::class.java))
        }
        
        callVideoCall.setOnClickListener {
            initVideoCallFromOngoingCall()
        }

        callEnd.setOnClickListener {
            endCall()
        }

        dialpadInclude.apply {
            dialpad0Holder.setOnClickListener { dialpadPressed('0') }
            dialpad1Holder.setOnClickListener { dialpadPressed('1') }
            dialpad2Holder.setOnClickListener { dialpadPressed('2') }
            dialpad3Holder.setOnClickListener { dialpadPressed('3') }
            dialpad4Holder.setOnClickListener { dialpadPressed('4') }
            dialpad5Holder.setOnClickListener { dialpadPressed('5') }
            dialpad6Holder.setOnClickListener { dialpadPressed('6') }
            dialpad7Holder.setOnClickListener { dialpadPressed('7') }
            dialpad8Holder.setOnClickListener { dialpadPressed('8') }
            dialpad9Holder.setOnClickListener { dialpadPressed('9') }

            arrayOf(
                dialpad0Holder,
                dialpad1Holder,
                dialpad2Holder,
                dialpad3Holder,
                dialpad4Holder,
                dialpad5Holder,
                dialpad6Holder,
                dialpad7Holder,
                dialpad8Holder,
                dialpad9Holder,
                dialpadPlusHolder,
                dialpadAsteriskHolder,
                dialpadHashtagHolder
            ).forEach {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.pill_background, theme)
                it.background?.alpha = LOWER_ALPHA_INT
            }

            dialpad0Holder.setOnLongClickListener { dialpadPressed('+'); true }
            dialpadAsteriskHolder.setOnClickListener { dialpadPressed('*') }
            dialpadHashtagHolder.setOnClickListener { dialpadPressed('#') }
            dialpadClearChar.setOnClickListener { clearChar(it) }
            dialpadClearChar.setOnLongClickListener { clearInput() }
        }

        dialpadWrapper.setBackgroundColor(
            if (isSystemInDarkMode()) {
                getProperBackgroundColor().lightenColor(2)
            } else {
                getProperBackgroundColor()
            }
        )

        arrayOf(dialpadClose, callSimImage, dialpadClearChar).forEach {
            it.applyColorFilter(getProperTextColor())
        }

        val bgColor = getProperBackgroundColor()
        val inactiveColor = getInactiveButtonColor()
        arrayOf(
            callToggleMicrophone, callToggleSpeaker, callDialpad,
            callToggleHold, callAdd, callSwap, callMerge, callManage, callVideoCall
        ).forEach {
            it.applyColorFilter(bgColor.getContrastColor())
            it.background.applyColorFilter(inactiveColor)
        }

        arrayOf(
            callToggleMicrophone, callToggleSpeaker, callDialpad,
            callToggleHold, callAdd, callSwap, callMerge, callManage, callVideoCall
        ).forEach { imageView ->
            imageView.setOnLongClickListener {
                if (!imageView.contentDescription.isNullOrEmpty()) {
                    toast(imageView.contentDescription.toString())
                }
                true
            }
        }

        callSimId.setTextColor(getProperTextColor().getContrastColor())
        dialpadInput.disableKeyboard()

        dialpadWrapper.onGlobalLayout {
            dialpadHeight = dialpadWrapper.height.toFloat()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleSwipe() = binding.apply {
        var minDragX = 0f
        var maxDragX = 0f
        var initialDraggableX = 0f
        var initialLeftArrowX = 0f
        var initialRightArrowX = 0f
        var initialLeftArrowScaleX = 0f
        var initialLeftArrowScaleY = 0f
        var initialRightArrowScaleX = 0f
        var initialRightArrowScaleY = 0f
        var leftArrowTranslation = 0f
        var rightArrowTranslation = 0f

        val isRtl = isRTLLayout
        callAccept.onGlobalLayout {
            minDragX = if (isRtl) {
                callAccept.left.toFloat()
            } else {
                callDecline.left.toFloat()
            }

            maxDragX = if (isRtl) {
                callDecline.left.toFloat()
            } else {
                callAccept.left.toFloat()
            }

            initialDraggableX = callDraggable.left.toFloat()
            initialLeftArrowX = callLeftArrow.x
            initialRightArrowX = callRightArrow.x
            initialLeftArrowScaleX = callLeftArrow.scaleX
            initialLeftArrowScaleY = callLeftArrow.scaleY
            initialRightArrowScaleX = callRightArrow.scaleX
            initialRightArrowScaleY = callRightArrow.scaleY
            leftArrowTranslation = if (isRtl) {
                callAccept.x
            } else {
                -callDecline.x
            }

            rightArrowTranslation = if (isRtl) {
                -callAccept.x
            } else {
                callDecline.x
            }

            if (isRtl) {
                callLeftArrow.setImageResource(R.drawable.ic_chevron_right_vector)
                callRightArrow.setImageResource(R.drawable.ic_chevron_left_vector)
            }

            callLeftArrow.applyColorFilter(getColor(R.color.md_red_400))
            callRightArrow.applyColorFilter(getColor(R.color.md_green_400))

            startArrowAnimation(callLeftArrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
            startArrowAnimation(callRightArrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
        }

        callDraggable.drawable.mutate().setTint(getProperTextColor())
        callDraggableBackground.drawable.mutate().setTint(getProperTextColor())

        var lock = false
        callDraggable.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownX = event.x
                    callDraggableBackground.animate().alpha(0f)
                    stopAnimation = true
                    callLeftArrow.animate().alpha(0f)
                    callRightArrow.animate().alpha(0f)
                    lock = false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragDownX = 0f
                    callDraggable.animate().x(initialDraggableX).withEndAction {
                        callDraggableBackground.animate().alpha(0.2f)
                    }
                    callDraggable.setImageDrawable(getDrawable(R.drawable.ic_phone_down_vector))
                    callDraggable.drawable.mutate().setTint(getProperTextColor())
                    callLeftArrow.animate().alpha(1f)
                    callRightArrow.animate().alpha(1f)
                    stopAnimation = false
                    startArrowAnimation(callLeftArrow, initialLeftArrowX, initialLeftArrowScaleX, initialLeftArrowScaleY, leftArrowTranslation)
                    startArrowAnimation(callRightArrow, initialRightArrowX, initialRightArrowScaleX, initialRightArrowScaleY, rightArrowTranslation)
                }

                MotionEvent.ACTION_MOVE -> {
                    callDraggable.x = min(maxDragX, max(minDragX, event.rawX - dragDownX))
                    when {
                        callDraggable.x >= maxDragX - 50f -> {
                            if (!lock) {
                                lock = true
                                callDraggable.performHapticFeedback()
                                if (isRtl) {
                                    endCall()
                                } else {
                                    acceptCall()
                                }
                            }
                        }

                        callDraggable.x <= minDragX + 50f -> {
                            if (!lock) {
                                lock = true
                                callDraggable.performHapticFeedback()
                                if (isRtl) {
                                    acceptCall()
                                } else {
                                    endCall()
                                }
                            }
                        }

                        callDraggable.x > initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_down_red_vector
                            } else {
                                R.drawable.ic_phone_green_vector
                            }
                            callDraggable.setImageDrawable(getDrawable(drawableRes))
                        }

                        callDraggable.x <= initialDraggableX -> {
                            lock = false
                            val drawableRes = if (isRtl) {
                                R.drawable.ic_phone_green_vector
                            } else {
                                R.drawable.ic_phone_down_red_vector
                            }
                            callDraggable.setImageDrawable(getDrawable(drawableRes))
                        }
                    }
                }
            }
            true
        }
    }

    private fun startArrowAnimation(arrow: ImageView, initialX: Float, initialScaleX: Float, initialScaleY: Float, translation: Float) {
        arrow.apply {
            alpha = 1f
            x = initialX
            scaleX = initialScaleX
            scaleY = initialScaleY
            animate()
                .alpha(0f)
                .translationX(translation)
                .scaleXBy(-0.5f)
                .scaleYBy(-0.5f)
                .setDuration(1000)
                .withEndAction {
                    if (!stopAnimation) {
                        startArrowAnimation(this, initialX, initialScaleX, initialScaleY, translation)
                    }
                }
        }
    }

    private fun dialpadPressed(char: Char) {
        CallManager.keypad(char)
        binding.dialpadInput.addCharacter(char)
    }

    private fun changeCallAudioRoute() {
        val supportAudioRoutes = CallManager.getSupportedAudioRoutes()
        if (supportAudioRoutes.contains(AudioRoute.BLUETOOTH)) {
            createOrUpdateAudioRouteChooser(supportAudioRoutes)
        } else {
            val isSpeakerOn = !isSpeakerOn
            val newRoute = if (isSpeakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            CallManager.setAudioRoute(newRoute)
        }
    }

    private fun createOrUpdateAudioRouteChooser(routes: Array<AudioRoute>, create: Boolean = true) {
        val callAudioRoute = CallManager.getCallAudioRoute()
        val items = routes
            .sortedByDescending { it.route }
            .map {
                SimpleListItem(id = it.route, textRes = it.stringRes, imageRes = it.iconRes, selected = it == callAudioRoute)
            }
            .toTypedArray()

        if (audioRouteChooserDialog?.isVisible == true) {
            audioRouteChooserDialog?.updateChooserItems(items)
        } else if (create) {
            audioRouteChooserDialog = DynamicBottomSheetChooserDialog.createChooser(
                fragmentManager = supportFragmentManager,
                title = R.string.choose_audio_route,
                items = items
            ) {
                audioRouteChooserDialog = null
                CallManager.setAudioRoute(it.id)
            }
        }
    }

    private fun updateCallAudioState(route: AudioRoute?) {
        if (route != null) {
            isMicrophoneOff = audioManager.isMicrophoneMute
            updateMicrophoneButton()

            isSpeakerOn = route == AudioRoute.SPEAKER
            val supportedAudioRoutes = CallManager.getSupportedAudioRoutes()
            binding.callToggleSpeaker.apply {
                val bluetoothConnected = supportedAudioRoutes.contains(AudioRoute.BLUETOOTH)
                contentDescription = if (bluetoothConnected) {
                    getString(R.string.choose_audio_route)
                } else {
                    getString(if (isSpeakerOn) R.string.turn_speaker_off else R.string.turn_speaker_on)
                }
                // show speaker icon when a headset is connected, a headset icon maybe confusing to some
                if (route == AudioRoute.WIRED_HEADSET) {
                    setImageResource(R.drawable.ic_volume_down_vector)
                } else {
                    setImageResource(route.iconRes)
                }
            }
            toggleButtonColor(binding.callToggleSpeaker, enabled = route != AudioRoute.EARPIECE && route != AudioRoute.WIRED_HEADSET)
            createOrUpdateAudioRouteChooser(supportedAudioRoutes, create = false)

            if (isSpeakerOn) {
                disableProximitySensor()
            } else {
                enableProximitySensor()
            }
        }
    }

    private fun toggleMicrophone() {
        isMicrophoneOff = !isMicrophoneOff
        audioManager.isMicrophoneMute = isMicrophoneOff
        CallManager.inCallService?.setMuted(isMicrophoneOff)
        updateMicrophoneButton()
    }

    private fun updateMicrophoneButton() {
        toggleButtonColor(binding.callToggleMicrophone, isMicrophoneOff)
        binding.callToggleMicrophone.contentDescription = getString(if (isMicrophoneOff) R.string.turn_microphone_on else R.string.turn_microphone_off)
    }

    private fun toggleDialpadVisibility() {
        if (binding.dialpadWrapper.isVisible()) hideDialpad() else showDialpad()
    }

    private fun findVisibleViewsUnderDialpad(): Sequence<Pair<View, Float>> {
        return binding.ongoingCallHolder.children
            .filter { it is ImageView && it.isVisible() }
            .map { view -> Pair(view, view.alpha) }
    }

    private fun showDialpad() {
        binding.dialpadWrapper.apply {
            updatePadding(
                bottom = binding.root.bottom - binding.callEnd.top + resources.getDimensionPixelSize(R.dimen.activity_margin)
            )

            translationY = dialpadHeight
            alpha = 0f
            animate()
                .withStartAction { beVisible() }
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(200L)
                .alpha(1f)
                .translationY(0f)
                .start()
        }

        viewsUnderDialpad.clear()
        viewsUnderDialpad.addAll(findVisibleViewsUnderDialpad())
        viewsUnderDialpad.forEach { (view, _) ->
            view.run {
                animate().scaleX(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
                animate().scaleY(0f).alpha(0f).withEndAction { beGone() }.duration = 250L
            }
        }
    }

    private fun hideDialpad() {
        binding.dialpadWrapper.animate()
            .withEndAction { binding.dialpadWrapper.beGone() }
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(200L)
            .alpha(0f)
            .translationY(dialpadHeight)
            .start()

        viewsUnderDialpad.forEach { (view, alpha) ->
            view.run {
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleX(1f).alpha(alpha).duration = 250L
                animate().withStartAction { beVisible() }.setInterpolator(OvershootInterpolator()).scaleY(1f).alpha(alpha).duration = 250L
            }
        }
    }

    private fun toggleHold() {
        val isOnHold = CallManager.toggleHold()
        toggleButtonColor(binding.callToggleHold, isOnHold)
        binding.callToggleHold.contentDescription = getString(if (isOnHold) R.string.resume_call else R.string.hold_call)
        binding.holdStatusLabel.beInvisibleIf(!isOnHold)
    }

    private fun updateOtherPersonsInfo(avatarUri: String?) {
        if (callContact == null) {
            return
        }

        binding.apply {
            val (name, _, number, numberLabel) = callContact!!
            callerNameLabel.text = name.ifEmpty { getString(R.string.unknown_caller) }
            if (number.isNotEmpty() && number != name) {
                callerNumber.text = number

                if (numberLabel.isNotEmpty()) {
                    callerNumber.text = "$number - $numberLabel"
                }
            } else {
                callerNumber.beGone()
            }

            callerAvatar.apply {
                if (avatarUri.isNullOrEmpty()) {
                    val bgColor = getProperPrimaryColor()
                    setBackgroundResource(R.drawable.circle_background)
                    setImageResource(R.drawable.ic_person_vector)
                    setPadding(resources.getDimensionPixelSize(R.dimen.activity_margin))
                    applyColorFilter(bgColor.getContrastColor())
                    background.applyColorFilter(bgColor)
                } else {
                    if (!isFinishing && !isDestroyed) {
                        Glide.with(this)
                            .load(avatarUri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(this)
                    }
                }
                // Add animated gradient ring around caller avatar
                addAnimatedGradientRing()
            }
        }
    }

    private fun getContactNameOrNumber(contact: CallContact): String {
        return contact.name.ifEmpty {
            contact.number.ifEmpty {
                getString(R.string.unknown_caller)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkCalledSIMCard() {
        try {
            val simLabels = getAvailableSIMCardLabels()
            if (simLabels.size > 1) {
                simLabels.forEachIndexed { index, sim ->
                    if (sim.handle == CallManager.getPrimaryCall()?.details?.accountHandle) {
                        binding.apply {
                            callSimId.text = sim.id.toString()
                            callSimId.beVisible()
                            callSimImage.beVisible()
                            val simColor = sim.color.adjustForContrast(getProperBackgroundColor())
                            callSimId.setTextColor(simColor.getContrastColor())
                            callSimImage.applyColorFilter(simColor)
                        }

                        val acceptDrawableId = when (index) {
                            0 -> R.drawable.ic_phone_one_vector
                            1 -> R.drawable.ic_phone_two_vector
                            else -> R.drawable.ic_phone_vector
                        }

                        val rippleBg = resources.getDrawable(R.drawable.ic_call_accept, theme) as RippleDrawable
                        val layerDrawable = rippleBg.findDrawableByLayerId(R.id.accept_call_background_holder) as LayerDrawable
                        layerDrawable.setDrawableByLayerId(R.id.accept_call_icon, getDrawable(acceptDrawableId))
                        binding.callAccept.setImageDrawable(rippleBg)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun updateCallState(call: Call) {
        val state = call.getStateCompat()
        when (state) {
            Call.STATE_RINGING -> callRinging()
            Call.STATE_ACTIVE -> callStarted()
            Call.STATE_DISCONNECTED -> endCall()
            Call.STATE_CONNECTING, Call.STATE_DIALING -> initOutgoingCallUI()
            Call.STATE_SELECT_PHONE_ACCOUNT -> showPhoneAccountPicker()
        }

        val statusTextId = when (state) {
            Call.STATE_RINGING -> R.string.is_calling
            Call.STATE_CONNECTING, Call.STATE_DIALING -> R.string.dialing
            else -> 0
        }

        binding.apply {
            if (statusTextId != 0) {
                callStatusLabel.text = getString(statusTextId)
            }

            callManage.beVisibleIf(!isCallEnded && call.hasCapability(Call.Details.CAPABILITY_MANAGE_CONFERENCE))
            setActionButtonEnabled(callSwap, enabled = !isCallEnded && state == Call.STATE_ACTIVE)
            setActionButtonEnabled(callMerge, enabled = !isCallEnded && state == Call.STATE_ACTIVE)
        }
    }

    private fun updateState() {
        val phoneState = CallManager.getPhoneState()
        if (phoneState is SingleCall) {
            updateCallState(phoneState.call)
            updateCallOnHoldState(null)
            val state = phoneState.call.getStateCompat()
            val isSingleCallActionsEnabled = !isCallEnded && (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED
                || state == Call.STATE_DISCONNECTING || state == Call.STATE_HOLDING)
            setActionButtonEnabled(binding.callToggleHold, isSingleCallActionsEnabled)
            setActionButtonEnabled(binding.callAdd, isSingleCallActionsEnabled)
        } else if (phoneState is TwoCalls) {
            val ringingCall = CallManager.getRingingCall()
            if (ringingCall != null) {
                val ongoingCall = CallManager.getOngoingCall()
                updateCallContactInfo(ringingCall)
                updateCallState(ringingCall)
                updateCallWaitingActiveCallState(ongoingCall)
            } else {
                updateCallState(phoneState.active)
                updateCallOnHoldState(phoneState.onHold)
            }
        }

        updateCallAudioState(CallManager.getCallAudioRoute())
    }

    private fun updateCallOnHoldState(call: Call?) {
        val hasCallOnHold = call != null
        if (hasCallOnHold) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    binding.onHoldCallerName.text = getContactNameOrNumber(contact)
                }
            }
        }
        binding.apply {
            onHoldStatusHolder.beVisibleIf(hasCallOnHold)
            controlsSingleCall.beVisibleIf(!hasCallOnHold)
            controlsTwoCalls.beVisibleIf(hasCallOnHold)
        }
    }

    private fun updateCallContactInfo(call: Call?) {
        if (call == null) return
        getCallContact(applicationContext, call) { contact ->
            if (call != CallManager.getPrimaryCall() && !CallManager.isCallWaiting()) {
                return@getCallContact
            }
            callContact = contact
            val avatar = if (!call.isConference()) contact.photoUri else null
            runOnUiThread {
                updateOtherPersonsInfo(avatar)
                checkCalledSIMCard()
                // Update UI elements for contact name, number, and profile picture
                binding.callerNameLabel.text = contact.name.ifEmpty { getString(R.string.unknown_caller) }
                binding.callerNumber.text = if (contact.number.isNotEmpty()) "Mobile ${contact.number}" else ""
                if (avatar.isNullOrEmpty()) {
                    binding.callerAvatar.setImageResource(R.drawable.ic_person_placeholder)
                } else {
                    Glide.with(this)
                        .load(avatar)
                        .apply(RequestOptions.circleCropTransform())
                        .into(binding.callerAvatar)
                }
                // Add animated gradient ring around caller avatar
                binding.callerAvatar.addAnimatedGradientRing()
            }
        }
    }

    private fun updateCallWaitingActiveCallState(call: Call?) {
        val hasOngoingCall = call != null
        if (hasOngoingCall) {
            getCallContact(applicationContext, call) { contact ->
                runOnUiThread {
                    binding.onHoldCallerName.text = getContactNameOrNumber(contact)
                }
            }
        }
        binding.apply {
            onHoldStatusHolder.beVisibleIf(hasOngoingCall)
            onHoldLabel.text = getString(R.string.ongoing_call)
            controlsSingleCall.beGone()
            controlsTwoCalls.beGone()
            ongoingCallHolder.beGone()
            callEnd.beGone()
        }
    }

    private fun declineCall() {
        if (CallManager.isCallWaiting()) {
            CallManager.rejectRingingOrPrimary()
        } else {
            endCall()
        }
    }

    private fun acceptCall() {
        CallManager.acceptRingingOrPrimary()
    }

    private fun initOutgoingCallUI() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
        
        // Add rotating borders to all call action buttons with position-based corner rounding
        with(binding) {
            callToggleMicrophone.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_LEFT))
            callToggleSpeaker.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_CENTER))
            callDialpad.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_RIGHT))
            callRecord.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_LEFT))
            callToggleHold.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_CENTER))
            callAdd.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_RIGHT))
            callManage.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_LEFT))
            callSwap.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_CENTER))
            callMerge.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_RIGHT))
            
            // Initialize button states with position-based backgrounds, preserve hold state if call is on hold
            val isOnHold = CallManager.getState() == android.telecom.Call.STATE_HOLDING
            callToggleMicrophone.updateCallButtonState(false, ButtonPosition.TOP_LEFT)
            callToggleSpeaker.updateCallButtonState(false, ButtonPosition.TOP_CENTER)
            callDialpad.updateCallButtonState(false, ButtonPosition.TOP_RIGHT)
            callRecord.updateCallButtonState(false, ButtonPosition.MIDDLE_LEFT)
            callToggleHold.updateCallButtonState(isOnHold, ButtonPosition.MIDDLE_CENTER)  // Preserve actual hold state
            callAdd.updateCallButtonState(false, ButtonPosition.MIDDLE_RIGHT)
            callManage.updateCallButtonState(false, ButtonPosition.BOTTOM_LEFT)
            callSwap.updateCallButtonState(false, ButtonPosition.BOTTOM_CENTER)
            callMerge.updateCallButtonState(false, ButtonPosition.BOTTOM_RIGHT)
            
            // Update hold status label visibility
            holdStatusLabel.beInvisibleIf(!isOnHold)
        }
    }

    private fun callRinging() {
        binding.incomingCallHolder.beVisible()
        if (CallManager.isCallWaiting()) {
            binding.ongoingCallHolder.beGone()
            binding.callEnd.beGone()
            binding.callStatusLabel.text = getString(R.string.incoming_call_while_on_call)
        }
    }

    private fun callStarted() {
        enableProximitySensor()
        binding.incomingCallHolder.beGone()
        binding.ongoingCallHolder.beVisible()
        binding.callEnd.beVisible()
        callDurationHandler.removeCallbacks(updateCallDurationTask)
        callDurationHandler.post(updateCallDurationTask)
        
        // Add rotating borders to all call action buttons with position-based corner rounding
        with(binding) {
            callToggleMicrophone.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_LEFT))
            callToggleSpeaker.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_CENTER))
            callDialpad.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.TOP_RIGHT))
            callRecord.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_LEFT))
            callToggleHold.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_CENTER))
            callAdd.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.MIDDLE_RIGHT))
            callManage.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_LEFT))
            callSwap.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_CENTER))
            callMerge.addRotatingBorder(getCornerRadiusForPosition(ButtonPosition.BOTTOM_RIGHT))
            
            // Initialize with unselected state and position-based backgrounds
            callToggleMicrophone.updateCallButtonState(false, ButtonPosition.TOP_LEFT)
            callToggleSpeaker.updateCallButtonState(false, ButtonPosition.TOP_CENTER)
            callDialpad.updateCallButtonState(false, ButtonPosition.TOP_RIGHT)
            callRecord.updateCallButtonState(false, ButtonPosition.MIDDLE_LEFT)
            callToggleHold.updateCallButtonState(false, ButtonPosition.MIDDLE_CENTER)
            callAdd.updateCallButtonState(false, ButtonPosition.MIDDLE_RIGHT)
            callManage.updateCallButtonState(false, ButtonPosition.BOTTOM_LEFT)
            callSwap.updateCallButtonState(false, ButtonPosition.BOTTOM_CENTER)
            callMerge.updateCallButtonState(false, ButtonPosition.BOTTOM_RIGHT)
        }
    }

    private fun showPhoneAccountPicker() {
        if (callContact != null) {
            getHandleToUse(intent, callContact!!.number) { handle ->
                CallManager.getPrimaryCall()?.phoneAccountSelected(handle, false)
            }
        }
    }

    private fun endCall() {
        // Stop recording if active
        if (isRecording) {
            stopRecording()
        }
        
        CallManager.reject()
        disableProximitySensor()
        audioRouteChooserDialog?.dismissAllowingStateLoss()

        if (isCallEnded) {
            safeFinishAndRemoveTask()
            return
        }

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (ignored: Exception) {
        }

        isCallEnded = true
        runOnUiThread {
            if (callDuration > 0) {
                disableAllActionButtons()
                @SuppressLint("SetTextI18n")
                binding.callStatusLabel.text = "${callDuration.getFormattedDuration()} (${getString(R.string.call_ended)})"
                Handler(mainLooper).postDelayed(3000) {
                    safeFinishAndRemoveTask()
                }
            } else {
                disableAllActionButtons()
                binding.callStatusLabel.text = getString(R.string.call_ended)
                finish()
            }
        }
    }

    private fun safeFinishAndRemoveTask() {
        try {
            if (intent != null) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private val callCallback = object : CallManagerListener {
        override fun onStateChanged() {
            updateState()
        }

        override fun onAudioStateChanged(audioState: AudioRoute) {
            updateCallAudioState(audioState)
        }

        override fun onPrimaryCallChanged(call: Call) {
            callDurationHandler.removeCallbacks(updateCallDurationTask)
            updateCallContactInfo(call)
            updateState()
        }
    }

    private val updateCallDurationTask = object : Runnable {
        override fun run() {
            callDuration = CallManager.getPrimaryCall().getCallDuration()
            if (!isCallEnded) {
                binding.callStatusLabel.text = callDuration.getFormattedDuration()
                callDurationHandler.postDelayed(this, 1000)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun addLockScreenFlags() {
        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        if (isOreoPlus()) {
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            screenOnWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "org.fossify.phone:full_wake_lock")
            screenOnWakeLock!!.acquire(5 * 1000L)
        } catch (e: Exception) {
        }
    }

    private fun enableProximitySensor() {
        if (!config.disableProximitySensor && (proximityWakeLock == null || proximityWakeLock?.isHeld == false)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "org.fossify.phone:wake_lock")
            proximityWakeLock!!.acquire(60 * MINUTE_SECONDS * 1000L)
        }
    }

    private fun disableProximitySensor() {
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock!!.release()
        }
    }

    private fun disableAllActionButtons() {
        (binding.ongoingCallHolder.children + binding.callEnd)
            .filter { it is ImageView && it.isVisible() }
            .forEach { view ->
                setActionButtonEnabled(button = view as ImageView, enabled = false)
            }
    }

    private fun setActionButtonEnabled(button: ImageView, enabled: Boolean) {
        button.apply {
            isEnabled = enabled
            alpha = if (enabled) 1.0f else LOWER_ALPHA
        }
    }

    private fun getActiveButtonColor() = getProperPrimaryColor()

    private fun getInactiveButtonColor() = getProperTextColor().adjustAlpha(0.10f)
    
    private fun getButtonPosition(view: ImageView): ButtonPosition {
        return when (view.id) {
            R.id.call_toggle_microphone -> ButtonPosition.TOP_LEFT
            R.id.call_toggle_speaker -> ButtonPosition.TOP_CENTER
            R.id.call_dialpad -> ButtonPosition.TOP_RIGHT
            R.id.call_record -> ButtonPosition.MIDDLE_LEFT
            R.id.call_toggle_hold -> ButtonPosition.MIDDLE_CENTER
            R.id.call_add -> ButtonPosition.MIDDLE_RIGHT
            R.id.call_manage -> ButtonPosition.BOTTOM_LEFT
            R.id.call_swap -> ButtonPosition.BOTTOM_CENTER
            R.id.call_merge -> ButtonPosition.BOTTOM_RIGHT
            else -> ButtonPosition.MIDDLE_CENTER
        }
    }

    private fun toggleButtonColor(view: ImageView, enabled: Boolean) {
        // Update button state with position-aware backgrounds and animated borders
        val position = getButtonPosition(view)
        view.updateCallButtonState(enabled, position)
        
        if (enabled) {
            // Selected state: red-orange gradient background with rotating border, white icon for visibility
            view.setColorFilter(0xFFFFFFFF.toInt())
        } else {
            // Unselected state: transparent with rotating border, black icon
            view.setColorFilter(0xFF000000.toInt())
        }
    }

    private fun clearChar(view: View) {
        binding.dialpadInput.dispatchKeyEvent(binding.dialpadInput.getKeyEvent(KeyEvent.KEYCODE_DEL))
    }

    private fun clearInput(): Boolean {
        binding.dialpadInput.setText("");
        return true;
    }

    private fun sendQuickReplyMessage(message: String) {
        try {
            val phoneNumber = callContact?.number
            if (phoneNumber.isNullOrEmpty()) {
                toast(R.string.unknown_caller)
                endCall()
                return
            }

            // Send SMS
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            
            // Show confirmation
            toast("Message sent: ${message.take(30)}...")
            
            // Reject the call
            endCall()
        } catch (e: Exception) {
            toast("Failed to send message: ${e.message}")
            endCall()
        }
    }
    
    // ===================== CALL RECORDING METHODS =====================
    
    /**
     * Setup recording callbacks
     */
    private fun setupRecordingCallbacks() {
        recordingManager.onRecordingStateChanged = { recording ->
            isRecording = recording
            runOnUiThread {
                updateRecordingUI()
            }
        }
        
        recordingManager.onRecordingError = { error ->
            runOnUiThread {
                toast("Recording error: $error")
            }
        }
        
        recordingManager.onSpeakerphoneRequired = { enable ->
            runOnUiThread {
                if (enable && !isSpeakerOn) {
                    // Enable speakerphone for better audio capture
                    CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    toast("Speakerphone enabled for better recording quality")
                }
            }
        }
    }
    
    /**
     * Toggle call recording (start/stop)
     */
    fun toggleCallRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Start call recording
     * MANDATORY: Check consent first (Google Play compliance)
     */
    private fun startRecording() {
        // MANDATORY: Check if user has consented to recording laws (Google Play requirement)
        if (!hasRecordingConsent()) {
            showRecordingConsentDialog()
            return
        }
        
        // Check permissions first
        if (!hasRecordingPermission()) {
            shouldStartRecordingAfterPermission = true
            showRecordingPermissionDialog()
            return
        }
        
        if (!hasStoragePermission()) {
            shouldStartRecordingAfterPermission = true
            showStoragePermissionDialog()
            return
        }
        
        val phoneNumber = callContact?.number
        val call = CallManager.getPrimaryCall()
        val isIncoming = call?.details?.callDirection == Call.Details.DIRECTION_INCOMING
        
        val success = recordingManager.startRecording(phoneNumber, isIncoming)
        if (success) {
            toast(R.string.recording_started)
        } else {
            toast(R.string.recording_failed)
        }
    }
    
    /**
     * Stop call recording
     */
    private fun stopRecording() {
        val recording = recordingManager.stopRecording()
        if (recording != null) {
            // Show review dialog
            showRecordingReviewDialog(recording)
        } else {
            toast(R.string.recording_stopped)
        }
    }
    
    /**
     * Show recording review dialog with playback controls
     */
    private fun showRecordingReviewDialog(recording: org.fossify.phone.models.CallRecording) {
        RecordingReviewDialog(
            context = this,
            recording = recording,
            onSave = {
                // Recording saved successfully
                toast(getString(R.string.recording_saved, recording.getFormattedFileSize()))
            },
            onDiscard = {
                // Recording discarded
                toast("Recording discarded")
            }
        ).show()
    }
    
    /**
     * Update UI based on recording state
     */
    private fun updateRecordingUI() {
        binding.apply {
            if (isRecording) {
                // Recording active - show indicator and change button state
                recordingIndicator.visibility = View.VISIBLE
                recordingStatusText.text = getString(R.string.recording_in_progress)
                
                // Change button to active state (red background, white icon)
                toggleButtonColor(callRecord, true)
                
                // Start recording timer
                startRecordingTimer()
                
                // Animate recording indicator with pulsing effect
                recordingIndicator.animate()
                    .alpha(0.7f)
                    .setDuration(500)
                    .withEndAction {
                        recordingIndicator.animate()
                            .alpha(1.0f)
                            .setDuration(500)
                            .start()
                    }
                    .start()
                
            } else {
                // Recording inactive - hide indicator and reset button
                recordingIndicator.visibility = View.GONE
                stopRecordingTimer()
                
                // Reset button to normal state
                toggleButtonColor(callRecord, false)
            }
        }
    }
    
    /**
     * Start timer to show recording duration
     */
    private fun startRecordingTimer() {
        stopRecordingTimer() // Stop any existing timer first
        
        recordingTimer = Timer()
        recordingTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val duration = recordingManager.getCurrentRecordingDuration()
                runOnUiThread {
                    binding.recordingDuration.text = formatRecordingDuration(duration)
                }
            }
        }, 0, 1000) // Update every second
    }
    
    /**
     * Stop recording timer
     */
    private fun stopRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
        binding.recordingDuration.text = ""
    }
    
    /**
     * Format recording duration to MM:SS
     */
    private fun formatRecordingDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }
    
    /**
     * Check if recording permission is granted
     */
    private fun hasRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if user has consented to recording laws (MANDATORY for Google Play)
     */
    private fun hasRecordingConsent(): Boolean {
        return config.callRecordingConsentGiven
    }
    
    /**
     * Show MANDATORY consent dialog (Google Play requirement)
     * User MUST consent before using recording feature
     */
    private fun showRecordingConsentDialog() {
        CallRecordingConsentDialog(
            activity = this,
            onConsent = {
                // User consented - save and proceed
                config.callRecordingConsentGiven = true
                toast(R.string.consent_granted)
                // Now start recording
                startRecording()
            },
            onDecline = {
                // User declined - cannot use recording
                toast(R.string.consent_required_to_record)
            }
        )
    }
    
    /**
     * Check if storage permission is granted
     * For Android 10+ (API 29+), we don't need WRITE_EXTERNAL_STORAGE for Downloads
     */
    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ doesn't need storage permission for public directories
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Show permission rationale dialog
     */
    private fun showRecordingPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.recording_permission_required)
            .setMessage(R.string.recording_permission_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                requestRecordingPermission()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                shouldStartRecordingAfterPermission = false
                toast(R.string.recording_permission_denied)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Show storage permission dialog
     */
    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_required)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                shouldStartRecordingAfterPermission = false
                toast(R.string.recording_permission_denied)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Request recording permission
     */
    private fun requestRecordingPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            // User previously denied - show explanation
            showRecordingPermissionDialog()
        } else {
            // First time or "Don't ask again" - request directly
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        }
    }
    
    /**
     * Request storage permission
     */
    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        } else {
            // Android 10+ doesn't need permission
            startRecording()
        }
    }
    
    /**
     * Handle permission result
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Audio permission granted, check storage permission
                    if (shouldStartRecordingAfterPermission) {
                        startRecording() // This will check storage permission
                    }
                } else {
                    // Permission denied
                    shouldStartRecordingAfterPermission = false
                    
                    // Check if permanently denied
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                        // Permission permanently denied - show settings dialog
                        showPermissionPermanentlyDeniedDialog()
                    } else {
                        toast(R.string.recording_permission_denied)
                    }
                }
            }
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storage permission granted
                    if (shouldStartRecordingAfterPermission) {
                        shouldStartRecordingAfterPermission = false
                        startRecording()
                    }
                } else {
                    // Storage permission denied
                    shouldStartRecordingAfterPermission = false
                    toast(R.string.recording_permission_denied)
                }
            }
        }
    }
    
    /**
     * Show dialog when permission is permanently denied
     */
    private fun showPermissionPermanentlyDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.recording_permission_required)
            .setMessage(R.string.recording_permission_permanently_denied)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Open app settings
     */
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }
    
    /**
     * Handle recording events from service
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Events.CallRecordingStarted) {
        isRecording = true
        updateRecordingUI()
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Events.CallRecordingStopped) {
        isRecording = false
        updateRecordingUI()
    }
    
    /**
     * Initiate a video call from an ongoing voice call
     * Shows options dialog to choose call method
     */
    private fun initVideoCallFromOngoingCall() {
        // Get current call contact info
        val currentContact = callContact
        if (currentContact == null) {
            Toast.makeText(this, "Unable to start video call", Toast.LENGTH_SHORT).show()
            return
        }
        
        val contactName = currentContact.name.ifEmpty { currentContact.number }
        val contactNumber = currentContact.number
        
        // Show video call options dialog
        VideoCallOptionsDialog(
            activity = this,
            contactName = contactName,
            contactNumber = contactNumber
        )
    }
}
