package org.fossify.phone.helpers

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.commons.extensions.baseConfig
import org.fossify.phone.R

/**
 * Dynamic Theme Manager - Controls Light Mode and Black Diamond Dark Mode.
 * Ensures complete compatibility and synchronized baseConfig across the application.
 */
object ThemeManager {

    fun isDarkThemeEnabled(context: Context): Boolean {
        return Config(context).isDarkThemeEnabled
    }

    fun initializeTheme(context: Context) {
        val isDark = isDarkThemeEnabled(context)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun applyTheme(activity: Activity) {
        val isDark = isDarkThemeEnabled(activity)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        activity.setTheme(R.style.AppTheme)

        // Synchronize fossify.commons baseConfig colors
        if (isDark) {
            activity.baseConfig.backgroundColor = Color.BLACK
            activity.baseConfig.textColor = Color.WHITE
            activity.baseConfig.primaryColor = Color.parseColor("#00E5FF")
        } else {
            activity.baseConfig.backgroundColor = Color.parseColor("#F8FAFC")
            activity.baseConfig.textColor = Color.parseColor("#0F172A")
            activity.baseConfig.primaryColor = Color.parseColor("#2563EB")
        }
    }

    fun isSystemInDarkMode(context: Context): Boolean {
        return isDarkThemeEnabled(context)
    }

    fun getThemeResourceId(context: Context): Int {
        return R.style.AppTheme
    }

    fun getThemedColor(context: Context, lightColor: Int, darkColor: Int): Int {
        return if (isDarkThemeEnabled(context)) {
            context.getColor(darkColor)
        } else {
            context.getColor(lightColor)
        }
    }
}
