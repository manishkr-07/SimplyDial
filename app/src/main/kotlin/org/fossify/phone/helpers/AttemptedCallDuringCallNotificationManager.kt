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
import org.fossify.phone.activities.MainActivity

class AttemptedCallDuringCallNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "simple_dialer_attempted_call_during_call"
    private val notificationId = 85

    fun show() {
        ensureChannel()

        val title = context.getString(R.string.call_attempted_during_another_call)
        val text = context.getString(R.string.call_attempted_during_another_call_desc)

        val openRecentsIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            type = "vnd.android.cursor.dir/calls"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openRecentsPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openRecentsIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openRecentsPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.call_attempted_during_another_call),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
