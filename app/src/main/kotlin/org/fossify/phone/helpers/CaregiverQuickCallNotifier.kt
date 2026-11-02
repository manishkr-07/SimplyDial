package org.fossify.phone.helpers

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
import org.fossify.phone.extensions.config

object CaregiverQuickCallNotifier {

    private const val CHANNEL_ID = "simplydial_caregiver_channel"
    private const val NOTIFICATION_ID = 9911

    fun updateNotification(context: Context) {
        val config = context.config
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!config.caregiverQuickCallEnabled) {
            notificationManager.cancel(NOTIFICATION_ID)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.family_quick_call),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent quick calling buttons for family and caregivers"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_vector)
            .setContentTitle(context.getString(R.string.family_quick_call))
            .setContentText(context.getString(R.string.caregiver_quick_call_desc))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Action 1: Trusted Emergency Contact
        val trustedContact = config.emergencySOSContact
        if (trustedContact.isNotBlank()) {
            val callTrustedIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(trustedContact)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val callTrustedPending = PendingIntent.getActivity(
                context,
                101,
                callTrustedIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_call_accept, "${context.getString(R.string.call_trusted)} ($trustedContact)", callTrustedPending)
        }

        // Action 2: Speed Dial Slot 1
        val speedDials = config.getSpeedDialValues().filter { it.isValid() }
        val firstSpeedDial = speedDials.firstOrNull()
        if (firstSpeedDial != null) {
            val callSdIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(firstSpeedDial.number)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val callSdPending = PendingIntent.getActivity(
                context,
                102,
                callSdIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val name = firstSpeedDial.displayName.ifEmpty { firstSpeedDial.number }
            builder.addAction(R.drawable.ic_call_accept, "📞 $name", callSdPending)
        }

        // Action 3: SOS 112
        val sosIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${config.emergencySOSNumber}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val sosPending = PendingIntent.getActivity(
            context,
            103,
            sosIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(R.drawable.ic_sos_vector, "SOS ${config.emergencySOSNumber}", sosPending)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

