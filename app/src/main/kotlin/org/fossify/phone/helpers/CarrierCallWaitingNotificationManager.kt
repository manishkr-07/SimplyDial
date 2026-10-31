package org.fossify.phone.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.activities.CallActivity

class CarrierCallWaitingNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "simple_dialer_call_waiting_carrier_v3"
    private val notificationId = 86

    fun show(callerNumber: String? = null) {
        ensureChannel()

        val displayCaller = callerNumber?.takeIf { it.isNotBlank() }
        val title = if (displayCaller != null) {
            "${context.getString(R.string.call_waiting)}: $displayCaller"
        } else {
            context.getString(R.string.call_waiting)
        }
        val text = if (displayCaller != null) {
            "$displayCaller • ${context.getString(R.string.incoming_call_while_on_call_desc)}"
        } else {
            context.getString(R.string.incoming_call_while_on_call_desc)
        }

        val openCallIntent = CallActivity.getStartIntent(context)
        val openCallPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openCallIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openCallPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 200, 200, 200))
            .setFullScreenIntent(openCallPendingIntent, true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun cancel() {
        notificationManager.cancel(notificationId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.call_waiting),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 200, 200, 200)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
