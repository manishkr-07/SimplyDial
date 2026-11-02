package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity
import org.fossify.phone.extensions.powerManager
import org.fossify.phone.extensions.getStateCompat
import org.fossify.phone.receivers.CallActionReceiver

class CallNotificationManager(private val context: Context) {
    private val CALL_NOTIFICATION_ID = 42
    private val CALL_WAITING_NOTIFICATION_ID = 43
    private val ACCEPT_CALL_CODE = 0
    private val DECLINE_CALL_CODE = 1
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val callContactAvatarHelper = CallContactAvatarHelper(context)

    private val CALL_WAITING_CHANNEL = "simple_dialer_call_waiting_active_v3"
    private val CALL_WAITING_CHANNEL_HIGH_PRIORITY = "simple_dialer_call_waiting_active_high_priority_v3"

    @SuppressLint("NewApi")
    fun setupNotification(forceLowPriority: Boolean = false) {
        val primaryCall = CallManager.getPrimaryCall()
        notificationManager.cancel(CALL_WAITING_NOTIFICATION_ID)
        setupNotificationForCall(primaryCall, notificationId = CALL_NOTIFICATION_ID, forceLowPriority = forceLowPriority, isCallWaitingNotification = false)
    }

    @SuppressLint("NewApi")
    fun setupCallWaitingNotifications(ongoingCall: Call?, ringingCall: Call?, forceLowPriority: Boolean = false) {
        if (ongoingCall == null || ringingCall == null) {
            notificationManager.cancel(CALL_WAITING_NOTIFICATION_ID)
            setupNotification()
            return
        }

        setupNotificationForCall(ongoingCall, notificationId = CALL_NOTIFICATION_ID, forceLowPriority = true, isCallWaitingNotification = false)
        setupNotificationForCall(ringingCall, notificationId = CALL_WAITING_NOTIFICATION_ID, forceLowPriority = forceLowPriority, isCallWaitingNotification = true)
    }

    private fun setupNotificationForCall(call: Call?, notificationId: Int, forceLowPriority: Boolean, isCallWaitingNotification: Boolean) {
        val initialCallState = call?.getStateCompat() ?: Call.STATE_DISCONNECTED
        if (call == null || initialCallState == Call.STATE_DISCONNECTED || initialCallState == Call.STATE_DISCONNECTING || CallManager.getPhoneState() == NoCall) {
            cancelNotification()
            return
        }

        getCallContact(context.applicationContext, call) { callContact ->
            val currentState = call.getStateCompat()
            if (currentState == Call.STATE_DISCONNECTED || currentState == Call.STATE_DISCONNECTING || CallManager.getPhoneState() == NoCall) {
                cancelNotification()
                return@getCallContact
            }
            val callContactAvatar = callContactAvatarHelper.getCallContactAvatar(callContact)
            val callState = currentState
            val isHighPriority = context.powerManager.isInteractive && callState == Call.STATE_RINGING && !forceLowPriority
            val channelId = when {
                isCallWaitingNotification && isHighPriority -> CALL_WAITING_CHANNEL_HIGH_PRIORITY
                isCallWaitingNotification -> CALL_WAITING_CHANNEL
                isHighPriority -> "simple_dialer_call_high_priority"
                else -> "simple_dialer_call"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (isCallWaitingNotification || isHighPriority) {
                    NotificationManager.IMPORTANCE_HIGH
                } else {
                    NotificationManager.IMPORTANCE_DEFAULT
                }
                val name = if (isCallWaitingNotification) {
                    context.getString(R.string.call_waiting)
                } else if (isHighPriority) {
                    context.getString(R.string.call_notification_channel_high_priority)
                } else {
                    context.getString(R.string.call_notification_channel)
                }

                NotificationChannel(channelId, name, importance).apply {
                    setSound(null, null)
                    if (isCallWaitingNotification) {
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 200, 200, 200)
                    } else {
                        enableVibration(false)
                        setVibrationPattern(null)
                    }
                    notificationManager.createNotificationChannel(this)
                }
            }

            val openAppIntent = CallActivity.getStartIntent(context)
            val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_MUTABLE)

            val acceptCallIntent = Intent(context, CallActionReceiver::class.java)
            acceptCallIntent.action = ACCEPT_CALL
            val acceptPendingIntent =
                PendingIntent.getBroadcast(context, ACCEPT_CALL_CODE, acceptCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            val declineCallIntent = Intent(context, CallActionReceiver::class.java)
            declineCallIntent.action = DECLINE_CALL
            val declinePendingIntent =
                PendingIntent.getBroadcast(context, DECLINE_CALL_CODE, declineCallIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)

            var callerName = if (callContact.name.isNotEmpty()) callContact.name else callContact.number.ifEmpty { context.getString(R.string.unknown_caller) }
            if (callContact.numberLabel.isNotEmpty()) {
                callerName += " - ${callContact.numberLabel}"
            }

            val contentTextId = when (callState) {
                Call.STATE_RINGING -> R.string.is_calling
                Call.STATE_DIALING -> R.string.dialing
                Call.STATE_DISCONNECTED -> R.string.call_ended
                Call.STATE_DISCONNECTING -> R.string.call_ending
                else -> R.string.ongoing_call
            }

            val statusText = if (isCallWaitingNotification) {
                if (callContact.number.isNotEmpty() && callContact.name.isNotEmpty()) {
                    "${callContact.number} • ${context.getString(R.string.call_waiting)}"
                } else {
                    context.getString(R.string.call_waiting)
                }
            } else {
                context.getString(contentTextId)
            }

            val collapsedView = RemoteViews(context.packageName, R.layout.call_notification).apply {
                setTextViewText(R.id.notification_caller_name, callerName)
                setTextViewText(R.id.notification_call_status, statusText)
                setViewVisibility(R.id.notification_accept_call, if (callState == Call.STATE_RINGING) View.VISIBLE else View.GONE)
                setViewVisibility(R.id.notification_decline_call, View.VISIBLE)

                setOnClickPendingIntent(R.id.notification_decline_call, declinePendingIntent)
                setOnClickPendingIntent(R.id.notification_accept_call, acceptPendingIntent)

                if (callContactAvatar != null) {
                    setImageViewBitmap(R.id.notification_thumbnail, callContactAvatarHelper.getCircularBitmap(callContactAvatar))
                }
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_phone_vector)
                .setContentIntent(openAppPendingIntent)
                .setContentTitle(callerName)
                .setContentText(statusText)
                .setPriority(if (isHighPriority || isCallWaitingNotification) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_CALL)
                .setCustomContentView(collapsedView)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .setSound(null)
                .setUsesChronometer(!isCallWaitingNotification && callState == Call.STATE_ACTIVE)
                .setChannelId(channelId)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

            if (isCallWaitingNotification) {
                builder.setVibrate(longArrayOf(0, 200, 200, 200))
            } else {
                builder.setDefaults(0)
                builder.setVibrate(null)
            }

            if (isHighPriority || isCallWaitingNotification) {
                builder.setFullScreenIntent(openAppPendingIntent, true)
            }

            if (callState == Call.STATE_RINGING) {
                builder.addAction(R.drawable.ic_call_end, context.getString(R.string.decline), declinePendingIntent)
                builder.addAction(R.drawable.ic_call_accept, context.getString(R.string.accept), acceptPendingIntent)
            }

            val notification = builder.build()
            val stateAtNotify = call.getStateCompat()
            if (stateAtNotify != Call.STATE_DISCONNECTED && stateAtNotify != Call.STATE_DISCONNECTING && CallManager.getPhoneState() != NoCall) {
                notificationManager.notify(notificationId, notification)
            } else {
                cancelNotification()
            }
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(CALL_NOTIFICATION_ID)
        notificationManager.cancel(CALL_WAITING_NOTIFICATION_ID)
    }
}
