package org.fossify.phone.helpers

import android.content.Context
import org.fossify.phone.extensions.config

object SpamShieldManager {

    // Common telemarketing and high-volume robocall prefixes globally
    private val KNOWN_SPAM_PREFIXES = listOf(
        "140",      // India TRAI Telemarketing series
        "+91140",
        "160",      // India Financial Promotions series
        "+91160",
        "+1800",    // US/Canada Toll-Free Commercial
        "+1888",
        "+1877",
        "+1866",
        "+1855",
        "+1844",
        "+1833",
        "1800",
        "1888",
        "1877",
        "1866",
        "1855",
        "1844",
        "1833"
    )

    private val REPETITIVE_PATTERNS = listOf(
        Regex("^\\+?\\d?([0-9])\\1{6,}$"),   // 7+ identical digits (e.g. 0000000, 1111111)
        Regex("^\\+?123456.*$"),              // Obvious sequential spoof
        Regex("^\\+?000.*$")                  // Invalid carrier prefix
    )

    fun isSuspectedSpam(context: Context, rawNumber: String): Boolean {
        if (!context.config.spamShieldEnabled) return false

        val cleanNumber = rawNumber.trim().filter { it.isDigit() || it == '+' }
        if (cleanNumber.isEmpty()) return false

        // Check custom local spam list
        val customSpam = context.config.getCustomSpamList()
        val digitsOnly = cleanNumber.filter { it.isDigit() }
        if (customSpam.any { it.filter { ch -> ch.isDigit() } == digitsOnly }) {
            return true
        }

        // Check telemarketing prefixes
        for (prefix in KNOWN_SPAM_PREFIXES) {
            if (cleanNumber.startsWith(prefix) || digitsOnly.startsWith(prefix.filter { it.isDigit() })) {
                return true
            }
        }

        // Check repetitive spoof patterns
        for (pattern in REPETITIVE_PATTERNS) {
            if (pattern.matches(cleanNumber)) {
                return true
            }
        }

        return false
    }

    fun getSpamReason(context: Context, rawNumber: String): String {
        val cleanNumber = rawNumber.trim().filter { it.isDigit() || it == '+' }
        val digitsOnly = cleanNumber.filter { it.isDigit() }

        if (digitsOnly.startsWith("140") || digitsOnly.startsWith("160")) {
            return "Telemarketing / Promotional"
        }

        for (pattern in REPETITIVE_PATTERNS) {
            if (pattern.matches(cleanNumber)) {
                return "Spoofed / Invalid Number"
            }
        }

        return "Suspected Spam"
    }
}
