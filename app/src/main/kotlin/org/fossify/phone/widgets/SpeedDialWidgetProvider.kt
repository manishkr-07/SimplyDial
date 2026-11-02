package org.fossify.phone.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.fossify.phone.R
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.extensions.config

class SpeedDialWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_speed_dial)

            // Open app on header click
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_app_btn, openAppPendingIntent)

            // Fetch speed dial entries (configured slots)
            try {
                val speedDials = context.config.getSpeedDialValues().filter { it.isValid() }

                val slotIds = intArrayOf(R.id.widget_slot_1, R.id.widget_slot_2, R.id.widget_slot_3, R.id.widget_slot_4)
                val nameIds = intArrayOf(R.id.widget_name_1, R.id.widget_name_2, R.id.widget_name_3, R.id.widget_name_4)

                for (i in 0..3) {
                    if (i < speedDials.size) {
                        val sd = speedDials[i]
                        val name = sd.displayName.ifEmpty { sd.number }
                        val number = sd.number

                        views.setTextViewText(nameIds[i], name)
                        if (number.isNotEmpty()) {
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                i + 10,
                                callIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(slotIds[i], pendingIntent)
                        } else {
                            views.setOnClickPendingIntent(slotIds[i], openAppPendingIntent)
                        }
                    } else {
                        // Empty slot
                        views.setTextViewText(nameIds[i], "Slot ${i + 1}")
                        views.setOnClickPendingIntent(slotIds[i], openAppPendingIntent)
                    }
                }
            } catch (_: Exception) {
                // If query fails, default all to open app
                views.setOnClickPendingIntent(R.id.widget_slot_1, openAppPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_slot_2, openAppPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_slot_3, openAppPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_slot_4, openAppPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
