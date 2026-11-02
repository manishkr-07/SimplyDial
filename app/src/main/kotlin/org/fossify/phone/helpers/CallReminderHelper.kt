package org.fossify.phone.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.fossify.phone.receivers.CallReminderReceiver
import java.util.Calendar

object CallReminderHelper {

    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_PHONE_NUMBER = "extra_phone_number"
    const val ACTION_CALL_REMINDER = "org.fossify.phone.ACTION_CALL_REMINDER"

    fun scheduleReminder(context: Context, callerName: String, phoneNumber: String, delayMinutes: Int): String {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return ""

        val triggerTime = Calendar.getInstance().apply {
            if (delayMinutes == -1) {
                // Tomorrow at 9:00 AM
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            } else {
                add(Calendar.MINUTE, delayMinutes)
            }
        }.timeInMillis

        val intent = Intent(context, CallReminderReceiver::class.java).apply {
            action = ACTION_CALL_REMINDER
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            phoneNumber.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (_: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        val timeFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return if (delayMinutes == -1) "Tomorrow at 9:00 AM" else timeFormat.format(java.util.Date(triggerTime))
    }
}

