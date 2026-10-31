package org.fossify.phone.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.phone.R
import org.fossify.phone.helpers.ThemeManager

open class SimpleActivity : BaseSimpleActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme based on system dark mode setting
        // ThemeManager automatically detects and applies:
        // - Light Theme (Default) when system is in light mode
        // - Black Diamond Dark Theme when system dark mode is enabled
        ThemeManager.applyTheme(this)
        
        super.onCreate(savedInstanceState)
    }
    
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Phone"
}
