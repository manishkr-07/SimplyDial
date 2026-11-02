package org.fossify.phone.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.models.CallContact

class MissedCallWaitingNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "simple_dialer_missed_call_waiting"
    private val notificationId = 84

    fun show(callContact: CallContact) {
        ensureChannel()

        val title = context.getString(R.string.missed_call_during_another_call)
        val callerName = if (callContact.name.isNotEmpty()) callContact.name else context.getString(R.string.unknown_caller)
        val text = context.getString(R.string.missed_call_during_another_call_from, callerName)

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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openRecentsPendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (callContact.number.isNotBlank()) {
            // Action 1: Call Back
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(callContact.number)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val callPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 1,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_call_accept, context.getString(R.string.call_back), callPendingIntent)

            // Action 2: WhatsApp
            val whatsappIntent = CommunicationHelper.getWhatsAppIntent(callContact.number).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val whatsappPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 2,
                whatsappIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_message_vector, "WhatsApp", whatsappPendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.missed_call_during_another_call),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}
