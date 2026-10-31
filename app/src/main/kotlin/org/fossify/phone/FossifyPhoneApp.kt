package org.fossify.phone

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.commons.FossifyApp
import org.fossify.phone.helpers.AppVisibility
import org.fossify.phone.helpers.ThemeManager

/**
 * Custom Application class for dynamic theming.
 * 
 * Enables automatic theme switching based on system dark mode:
 * - Light Mode (Default): All users start with professional light theme
 * - Dark Mode: Black Diamond Dark Theme auto-activates when system dark mode is on
 * 
 * Theme switching is seamless and follows OS-level dark mode setting.
 */
class FossifyPhoneApp : FossifyApp() {
    
    override fun onCreate() {
        // Initialize dynamic theme system
        // Enables MODE_NIGHT_FOLLOW_SYSTEM for automatic dark mode detection
        ThemeManager.initializeTheme(this)

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
                AppVisibility.onActivityStarted()
            }

            override fun onActivityResumed(activity: Activity) {
                AppVisibility.onActivityResumed(activity.javaClass.name)
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
                AppVisibility.onActivityStopped()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }
        })
        
        super.onCreate()
    }
}
