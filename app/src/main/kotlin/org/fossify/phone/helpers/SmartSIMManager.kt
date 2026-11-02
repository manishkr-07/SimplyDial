package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog.Calls
import android.telecom.PhoneAccountHandle
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.phone.extensions.config
import org.fossify.phone.extensions.getAvailableSIMCardLabels

/**
 * Intelligent Dual-SIM Management Engine.
 * Automatically learns and associates SIM card preferences per contact from previous call history,
 * eliminating repetitive SIM-selection dialogs especially in senior/simple mode.
 */
object SmartSIMManager {

    @SuppressLint("MissingPermission")
    fun getSmartSIMHandle(context: Context, phoneNumber: String): PhoneAccountHandle? {
        val cleanNumber = phoneNumber.filter { it.isDigit() }
        if (cleanNumber.isEmpty()) return null

        val availableSIMs = context.getAvailableSIMCardLabels()
        if (availableSIMs.isEmpty()) return null
        if (availableSIMs.size == 1) return availableSIMs.first().handle

        // 1. Check if user already explicitly saved a custom SIM for this number
        context.config.getCustomSIM(phoneNumber)?.let { return it }

        // 2. Query call log to auto-detect the SIM used in previous calls with this number
        if (context.hasPermission(PERMISSION_READ_CALL_LOG)) {
            try {
                val uri = Calls.CONTENT_URI
                val projection = arrayOf(Calls.PHONE_ACCOUNT_ID)
                val selection = "${Calls.NUMBER} LIKE ?"
                val queryDigits = if (cleanNumber.length > 9) cleanNumber.takeLast(9) else cleanNumber
                val selectionArgs = arrayOf("%$queryDigits")
                val sortOrder = "${Calls.DATE} DESC LIMIT 5"

                context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val accountId = cursor.getString(0) ?: continue
                        val matchingSim = availableSIMs.firstOrNull { it.handle.id == accountId }
                        if (matchingSim != null) {
                            // Cache smart selection for instant future 0ms dispatch
                            context.config.saveCustomSIM(phoneNumber, matchingSim.handle)
                            return matchingSim.handle
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }
}

