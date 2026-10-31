package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.phone.R

/**
 * Dynamic Theme Manager - Automatically detects and applies system dark mode
 * 
 * Theme Behavior:
 * - Light Mode (Default): Professional light theme for all users
 * - Dark Mode: Black Diamond Dark Theme activated when system dark mode is enabled
 * - Automatic switching: Follows OS-level dark mode setting
 * - Seamless transitions: No UI glitches or broken elements
 * 
 * Black Diamond Dark Theme Features:
 * - AMOLED-optimized true black backgrounds (#000000)
 * - Electric cyan/aqua neon accents (#00E5FF, #4DD0E1)
 * - High contrast text (#FFFFFF on black)
 * - Neon glow effects and smooth animations
 * - Battery-efficient pure black for OLED displays
 */
object ThemeManager {
    
    /**
     * Initialize theme system to follow system dark mode
     * Called once in Application.onCreate()
     */
    fun initializeTheme(context: Context) {
        // Enable automatic dark mode detection
        // App will follow system dark mode setting
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
    
    /**
     * Apply the appropriate theme based on system dark mode
     * Should be called before setContentView() in every Activity
     * 
     * Theme Selection:
     * - System in Light Mode → AppTheme (Light theme)
     * - System in Dark Mode → AppTheme.Dark (Black Diamond theme)
     */
    fun applyTheme(activity: Activity) {
        val isDarkMode = isSystemInDarkMode(activity)
        
        if (isDarkMode) {
            // Apply Black Diamond Dark Theme
            activity.setTheme(R.style.AppTheme_Dark)
        } else {
            // Apply default Light Theme
            activity.setTheme(R.style.AppTheme)
        }
    }
    
    /**
     * Check if system is currently in dark mode
     * 
     * @return true if system dark mode is enabled, false otherwise
     */
    fun isSystemInDarkMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * Get theme resource ID based on system dark mode
     * 
     * @return R.style.AppTheme_Dark if dark mode, R.style.AppTheme otherwise
     */
    fun getThemeResourceId(context: Context): Int {
        return if (isSystemInDarkMode(context)) {
            R.style.AppTheme_Dark
        } else {
            R.style.AppTheme
        }
    }
    
    /**
     * Get color value based on current theme
     * Automatically selects light or dark color based on system dark mode
     * 
     * @param lightColor Color resource ID for light theme
     * @param darkColor Color resource ID for dark theme
     * @return Resolved color value
     */
    fun getThemedColor(context: Context, lightColor: Int, darkColor: Int): Int {
        return if (isSystemInDarkMode(context)) {
            context.getColor(darkColor)
        } else {
            context.getColor(lightColor)
        }
    }
    
    /**
     * Check if dark theme is currently active
     * Alias for isSystemInDarkMode() for backward compatibility
     */
    fun isDarkThemeEnabled(context: Context): Boolean {
        return isSystemInDarkMode(context)
    }
}
