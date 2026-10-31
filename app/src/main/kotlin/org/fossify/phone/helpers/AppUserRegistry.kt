package org.fossify.phone.helpers

import android.content.Context
import android.content.SharedPreferences
import org.fossify.phone.extensions.config

/**
 * Registry to track which contacts have the app installed
 * This allows direct app-to-app video calling (Option 3)
 */
class AppUserRegistry(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "app_user_registry"
        private const val KEY_REGISTERED_USERS = "registered_users"
        private const val KEY_MY_NUMBER = "my_phone_number"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Register current user's phone number
     * This marks the device as having the app installed
     */
    fun registerCurrentUser(phoneNumber: String) {
        prefs.edit().putString(KEY_MY_NUMBER, phoneNumber).apply()
        addRegisteredUser(phoneNumber)
    }
    
    /**
     * Get current user's registered phone number
     */
    fun getMyPhoneNumber(): String? {
        return prefs.getString(KEY_MY_NUMBER, null)
    }
    
    /**
     * Add a phone number to the registry of known app users
     * Call this when you receive a video call from another app user
     */
    fun addRegisteredUser(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        val currentUsers = getRegisteredUsers().toMutableSet()
        currentUsers.add(cleanNumber)
        prefs.edit().putStringSet(KEY_REGISTERED_USERS, currentUsers).apply()
    }
    
    /**
     * Check if a contact has the app installed
     * Returns true if they are in the registry
     */
    fun isAppUser(phoneNumber: String): Boolean {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        return getRegisteredUsers().contains(cleanNumber)
    }
    
    /**
     * Get all registered app users
     */
    fun getRegisteredUsers(): Set<String> {
        return prefs.getStringSet(KEY_REGISTERED_USERS, emptySet()) ?: emptySet()
    }
    
    /**
     * Remove a user from the registry
     */
    fun removeRegisteredUser(phoneNumber: String) {
        val cleanNumber = cleanPhoneNumber(phoneNumber)
        val currentUsers = getRegisteredUsers().toMutableSet()
        currentUsers.remove(cleanNumber)
        prefs.edit().putStringSet(KEY_REGISTERED_USERS, currentUsers).apply()
    }
    
    /**
     * Clean phone number for consistent comparison
     * Removes all non-digit characters and normalizes format
     */
    private fun cleanPhoneNumber(phoneNumber: String): String {
        // Remove all non-digit characters
        var cleaned = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // Remove leading country code variations
        if (cleaned.startsWith("+91")) {
            cleaned = cleaned.substring(3)
        } else if (cleaned.startsWith("91") && cleaned.length > 10) {
            cleaned = cleaned.substring(2)
        } else if (cleaned.startsWith("+1")) {
            cleaned = cleaned.substring(2)
        } else if (cleaned.startsWith("1") && cleaned.length == 11) {
            cleaned = cleaned.substring(1)
        }
        
        // Take last 10 digits for comparison
        if (cleaned.length > 10) {
            cleaned = cleaned.substring(cleaned.length - 10)
        }
        
        return cleaned
    }
    
    /**
     * Mark a contact as app user (after receiving a call or confirmation)
     */
    fun markContactAsAppUser(phoneNumber: String) {
        addRegisteredUser(phoneNumber)
    }
    
    /**
     * Clear all registered users (for testing/reset)
     */
    fun clearRegistry() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Get count of registered app users
     */
    fun getAppUserCount(): Int {
        return getRegisteredUsers().size
    }
}
