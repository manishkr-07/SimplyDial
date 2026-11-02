package org.fossify.phone.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.fossify.phone.R
import org.fossify.phone.activities.DialpadActivity

class DialpadWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_dialpad)

            // Open dialpad activity when clicking call or header
            val dialpadIntent = Intent(context, DialpadActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val dialpadPendingIntent = PendingIntent.getActivity(
                context,
                100,
                dialpadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_dialpad_header, dialpadPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_dialpad_call_btn, dialpadPendingIntent)

            val keyIds = intArrayOf(
                R.id.btn_key_1, R.id.btn_key_2, R.id.btn_key_3,
                R.id.btn_key_4, R.id.btn_key_5, R.id.btn_key_6,
                R.id.btn_key_7, R.id.btn_key_8, R.id.btn_key_9,
                R.id.btn_key_star, R.id.btn_key_0, R.id.btn_key_hash
            )

            val digits = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

            for (i in keyIds.indices) {
                val digitIntent = Intent(context, DialpadActivity::class.java).apply {
                    action = Intent.ACTION_DIAL
                    data = Uri.parse("tel:${digits[i]}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val pending = PendingIntent.getActivity(
                    context,
                    200 + i,
                    digitIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(keyIds[i], pending)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

