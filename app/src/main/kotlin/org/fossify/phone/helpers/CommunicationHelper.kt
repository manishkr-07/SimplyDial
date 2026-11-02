package org.fossify.phone.helpers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.fossify.commons.extensions.toast
import org.fossify.phone.R

object CommunicationHelper {

    fun cleanPhoneNumber(rawNumber: String): String {
        return rawNumber.filter { it.isDigit() || it == '+' }
    }

    fun openWhatsApp(activity: Activity, rawNumber: String) {
        val cleanNumber = cleanPhoneNumber(rawNumber)
        if (cleanNumber.isEmpty()) {
            activity.toast(R.string.unknown_caller)
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
                `package` = "com.whatsapp"
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber"))
                activity.startActivity(browserIntent)
            } catch (_: Exception) {
                activity.toast("Could not open WhatsApp")
            }
        }
    }

    fun openTelegram(activity: Activity, rawNumber: String) {
        val cleanNumber = cleanPhoneNumber(rawNumber)
        if (cleanNumber.isEmpty()) {
            activity.toast(R.string.unknown_caller)
            return
        }

        try {
            // Telegram expects international format e.g. https://t.me/+919876543210
            val telegramUri = if (cleanNumber.startsWith("+")) {
                "https://t.me/$cleanNumber"
            } else {
                "https://t.me/+$cleanNumber"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUri)).apply {
                `package` = "org.telegram.messenger"
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            try {
                val telegramUri = if (cleanNumber.startsWith("+")) "https://t.me/$cleanNumber" else "https://t.me/+$cleanNumber"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUri))
                activity.startActivity(browserIntent)
            } catch (_: Exception) {
                activity.toast("Could not open Telegram")
            }
        }
    }

    fun getWhatsAppIntent(rawNumber: String): Intent {
        val cleanNumber = cleanPhoneNumber(rawNumber)
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber")
            `package` = "com.whatsapp"
        }
    }

}
