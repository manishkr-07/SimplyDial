package org.fossify.phone.services

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.extensions.isOutgoing
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.helpers.AppVisibility
import org.fossify.phone.helpers.CallManager
import org.fossify.phone.helpers.CallNotificationManager
import org.fossify.phone.helpers.CallWaitingAlertHelper
import org.fossify.phone.helpers.CallWaitingAttemptTracker
import org.fossify.phone.helpers.CallWaitingMissedTracker
import org.fossify.phone.helpers.MissedCallWaitingNotificationManager
import org.fossify.phone.helpers.AttemptedCallDuringCallNotificationManager
import org.fossify.phone.helpers.CarrierCallWaitingNotificationManager
import org.fossify.phone.helpers.NoCall
import org.fossify.phone.helpers.getCallContact
import org.fossify.phone.models.Events
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ConcurrentHashMap

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private val missedCallWaitingNotificationManager by lazy { MissedCallWaitingNotificationManager(this) }
    private val attemptedCallDuringCallNotificationManager by lazy { AttemptedCallDuringCallNotificationManager(this) }
    private val carrierCallWaitingNotificationManager by lazy { CarrierCallWaitingNotificationManager(this) }
    private val callWaitingAlertHelper by lazy { CallWaitingAlertHelper(this) }
    private var recordingStarted = false

    private val callWaitingCallContacts = ConcurrentHashMap<Int, org.fossify.phone.models.CallContact>()

    private val logTag = "CallServiceCallWaiting"

    private val telephonyManager by lazy { getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }
    private var legacyPhoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)

            Log.d(logTag, "onStateChanged call=${System.identityHashCode(call)} state=$state ringing=${CallManager.getRingingCall()?.getStateCompat()} ongoing=${CallManager.getOngoingCall()?.getStateCompat()}")
            
            // Handle call recording based on state
            handleCallRecording(call, state)

            if (state == Call.STATE_RINGING) {
                val activeCall = CallManager.getOngoingCall()
                val ringingCall = CallManager.getRingingCall()
                val isCallWaiting = activeCall != null && ringingCall != null && activeCall != ringingCall
                if (isCallWaiting && ringingCall == call) {
                    callWaitingAlertHelper.startAlert()
                    val callKey = System.identityHashCode(call)
                    if (!callWaitingCallContacts.containsKey(callKey)) {
                        getCallContact(applicationContext, call) { callContact ->
                            callWaitingCallContacts[callKey] = callContact
                        }
                    }
                }
            } else if (state == Call.STATE_ACTIVE) {
                callWaitingCallContacts.remove(System.identityHashCode(call))
                if (!CallManager.isCallWaiting()) {
                    callWaitingAlertHelper.stopAlert()
                }
            } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                val callKey = System.identityHashCode(call)
                val callContact = callWaitingCallContacts.remove(callKey)
                if (callContact != null) {
                    // The call-waiting ringing call ended while another call was active -> treat as missed during active call
                    val activeCall = CallManager.getOngoingCall()
                    if (activeCall != null && activeCall != call && activeCall.getStateCompat() != Call.STATE_DISCONNECTED && activeCall.getStateCompat() != Call.STATE_DISCONNECTING) {
                        CallWaitingMissedTracker.setPending(callContact)
                    }
                }
                if (!CallManager.isCallWaiting()) {
                    callWaitingAlertHelper.stopAlert()
                }
            }

            updateNotifications()
        }
    }

    private fun updateNotifications() {
        val ringingCall = CallManager.getRingingCall()
        val ongoingCall = CallManager.getOngoingCall()
        val isCallWaiting = ringingCall != null && ongoingCall != null && ringingCall != ongoingCall &&
            ringingCall.getStateCompat() == Call.STATE_RINGING &&
            (ongoingCall.getStateCompat() == Call.STATE_ACTIVE || ongoingCall.getStateCompat() == Call.STATE_HOLDING)

        Log.d(logTag, "updateNotifications isCallWaiting=$isCallWaiting ringing=${ringingCall?.getStateCompat()} ongoing=${ongoingCall?.getStateCompat()}")

        if (isCallWaiting) {
            callWaitingAlertHelper.startAlert()
            callNotificationManager.setupCallWaitingNotifications(ongoingCall, ringingCall)
        } else {
            callWaitingAlertHelper.stopAlert()
            callNotificationManager.setupNotification()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)

        Log.d(logTag, "onCallAdded call=${System.identityHashCode(call)} state=${call.getStateCompat()} phoneState=${CallManager.getPhoneState()}")

        val ringingCall = CallManager.getRingingCall()
        val activeCall = CallManager.getOngoingCall()
        val isCallWaiting = ringingCall != null && activeCall != null && ringingCall != activeCall &&
            ringingCall.getStateCompat() == Call.STATE_RINGING &&
            (activeCall.getStateCompat() == Call.STATE_ACTIVE || activeCall.getStateCompat() == Call.STATE_HOLDING)

        if (isCallWaiting) {
            CallWaitingAttemptTracker.clear()
            callWaitingAlertHelper.startAlert()
            callNotificationManager.setupCallWaitingNotifications(activeCall, ringingCall)
            try {
                startActivity(CallActivity.getStartIntent(this))
            } catch (e: Exception) {
                Log.w(logTag, "Could not start CallActivity for call waiting", e)
            }
        } else {
            val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
            if (!powerManager.isInteractive || call.isOutgoing() || isScreenLocked || config.alwaysShowFullscreen) {
                try {
                    callNotificationManager.setupNotification(true)
                    startActivity(CallActivity.getStartIntent(this))
                } catch (e: Exception) {
                    callNotificationManager.setupNotification()
                }
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)

        Log.d(logTag, "onCallRemoved call=${System.identityHashCode(call)} remainingState=${CallManager.getPhoneState()}")
        if (!CallManager.isCallWaiting()) {
            callWaitingAlertHelper.stopAlert()
        }

        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()

            val missedDuringCall = CallWaitingMissedTracker.consumePending()
            if (missedDuringCall != null) {
                missedCallWaitingNotificationManager.show(missedDuringCall)
            } else if (CallWaitingAttemptTracker.consumeIfPending()) {
                attemptedCallDuringCallNotificationManager.show()
            }
        } else {
            updateNotifications()
            if (wasPrimaryCall && AppVisibility.isAppInForeground()) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callWaitingAlertHelper.stopAlert()
        callNotificationManager.cancelNotification()
        unregisterCarrierAttemptListener()
        stopRecordingIfActive()
    }

    override fun onCreate() {
        super.onCreate()
        registerCarrierAttemptListener()
    }

    private fun registerCarrierAttemptListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleTelephonyCallState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleTelephonyCallState(state)
                    }
                }
                legacyPhoneStateListener = listener
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to register carrier attempt listener", e)
        }
    }

    private fun unregisterCarrierAttemptListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                legacyPhoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
                legacyPhoneStateListener = null
            }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to unregister carrier attempt listener", e)
        }
    }

    private fun handleTelephonyCallState(state: Int) {
        val hasOngoingCall = CallManager.getOngoingCall() != null
        val hasInCallServiceRingingCall = CallManager.getRingingCall() != null

        Log.d(logTag, "telephonyCallState=$state hasOngoing=$hasOngoingCall hasInCallServiceRinging=$hasInCallServiceRingingCall")

        if (state == TelephonyManager.CALL_STATE_RINGING && hasOngoingCall && !hasInCallServiceRingingCall) {
            Log.d(logTag, "Carrier/OEM call attempt detected during ongoing call (no InCallService ringing call)")
            CallWaitingAttemptTracker.markAttempted()
            carrierCallWaitingNotificationManager.show()
            callWaitingAlertHelper.startAlert()
        } else {
            carrierCallWaitingNotificationManager.cancel()
            if (!CallManager.isCallWaiting()) {
                callWaitingAlertHelper.stopAlert()
            }
        }
    }
    
    /**
     * Handle call recording based on call state
     * MANUAL MODE: Only stop recording if call ends
     */
    private fun handleCallRecording(call: Call, state: Int) {
        when (state) {
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                // Call ended (IDLE), stop recording if active
                if (recordingStarted) {
                    stopRecording()
                }
            }
        }
    }
    
    /**
     * Start call recording
     */
    private fun startRecording(call: Call) {
        try {
            val phoneNumber = call.details?.handle?.schemeSpecificPart
            val isIncoming = !call.isOutgoing()
            
            CallRecordingService.startRecording(this, phoneNumber, isIncoming)
            recordingStarted = true
            
            // Notify UI that recording started
            EventBus.getDefault().post(Events.CallRecordingStarted)
        } catch (e: Exception) {
            android.util.Log.e("CallService", "Error starting recording", e)
        }
    }
    
    /**
     * Stop call recording
     */
    private fun stopRecording() {
        try {
            CallRecordingService.stopRecording(this)
            recordingStarted = false
            
            // Notify UI that recording stopped
            EventBus.getDefault().post(Events.CallRecordingStopped)
        } catch (e: Exception) {
            android.util.Log.e("CallService", "Error stopping recording", e)
        }
    }
    
    /**
     * Stop recording if currently active
     */
    private fun stopRecordingIfActive() {
        if (recordingStarted) {
            stopRecording()
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    private fun hasRecordingPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
