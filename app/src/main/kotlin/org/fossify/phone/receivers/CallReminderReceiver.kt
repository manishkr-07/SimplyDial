package org.fossify.phone.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.helpers.CallReminderHelper
import org.fossify.phone.helpers.CommunicationHelper

class CallReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callerName = intent.getStringExtra(CallReminderHelper.EXTRA_CALLER_NAME) ?: ""
        val phoneNumber = intent.getStringExtra(CallReminderHelper.EXTRA_PHONE_NUMBER) ?: ""

        if (phoneNumber.isEmpty()) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "simplydial_call_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.call_reminder_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to call back contacts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val displayName = callerName.ifEmpty { phoneNumber }
        val title = context.getString(R.string.call_reminder_title)
        val contentText = context.getString(R.string.call_reminder_text, displayName, phoneNumber)

        // Main Tap Action: Open App
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Call Action
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPendingIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode() + 1,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // WhatsApp Action
        val whatsappIntent = CommunicationHelper.getWhatsAppIntent(phoneNumber).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val whatsappPendingIntent = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode() + 2,
            whatsappIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_call_accept, context.getString(R.string.call_back), callPendingIntent)
            .addAction(R.drawable.ic_message_vector, "WhatsApp", whatsappPendingIntent)
            .build()

        notificationManager.notify(phoneNumber.hashCode(), notification)
    }
}

