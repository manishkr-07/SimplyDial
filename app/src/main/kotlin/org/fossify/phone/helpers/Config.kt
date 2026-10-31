package org.fossify.phone.helpers

import android.content.Context
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.fossify.commons.helpers.BaseConfig
import org.fossify.phone.extensions.getPhoneAccountHandleModel
import org.fossify.phone.extensions.putPhoneAccountHandle
import org.fossify.phone.models.SpeedDial
import androidx.core.content.edit
import java.util.Locale

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    private val regionHint: String by lazy {
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
        listOf(
            telephonyManager?.simCountryIso,
            telephonyManager?.networkCountryIso,
            Locale.getDefault().country
        )
            .firstOrNull { !it.isNullOrBlank() }
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    fun getSpeedDialValues(): ArrayList<SpeedDial> {
        val speedDialType = object : TypeToken<List<SpeedDial>>() {}.type
        val speedDialValues = Gson().fromJson<ArrayList<SpeedDial>>(speedDial, speedDialType) ?: ArrayList(1)

        for (i in 1..9) {
            val speedDial = SpeedDial(i, "", "")
            if (speedDialValues.firstOrNull { it.id == i } == null) {
                speedDialValues.add(speedDial)
            }
        }

        return speedDialValues
    }

    fun saveCustomSIM(number: String, handle: PhoneAccountHandle) {
        prefs.edit().putPhoneAccountHandle(
            key = getKeyForCustomSIM(number),
            parcelable = handle
        ).apply()
    }

    fun getCustomSIM(number: String): PhoneAccountHandle? {
        val key = getKeyForCustomSIM(number)
        prefs.getPhoneAccountHandleModel(key, null)?.let {
            return it.toPhoneAccountHandle()
        }

        // fallback for old unstable keys. should be removed in future versions
        val migratedHandle = prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(REMEMBER_SIM_PREFIX) }
            .firstOrNull {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.compare(
                    it.removePrefix(REMEMBER_SIM_PREFIX),
                    normalizeCustomSIMNumber(number)
                )
            }?.let { legacyKey ->
                prefs.getPhoneAccountHandleModel(legacyKey, null)?.let {
                    val handle = it.toPhoneAccountHandle()
                    prefs.edit {
                        remove(legacyKey)
                        putPhoneAccountHandle(key, handle)
                    }
                    handle
                }
            }

        return migratedHandle
    }

    fun removeCustomSIM(number: String) {
        prefs.edit().remove(getKeyForCustomSIM(number)).apply()
    }

    private fun getKeyForCustomSIM(number: String): String {
        return REMEMBER_SIM_PREFIX + normalizeCustomSIMNumber(number)
    }

    private fun normalizeCustomSIMNumber(number: String): String {
        val decoded = Uri.decode(number).removePrefix("tel:")
        val formatted = PhoneNumberUtils.formatNumberToE164(decoded, regionHint)
        return formatted ?: PhoneNumberUtils.normalizeNumber(decoded)
    }

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, ALL_TABS_MASK)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var groupSubsequentCalls: Boolean
        get() = prefs.getBoolean(GROUP_SUBSEQUENT_CALLS, true)
        set(groupSubsequentCalls) = prefs.edit().putBoolean(GROUP_SUBSEQUENT_CALLS, groupSubsequentCalls).apply()

    var openDialPadAtLaunch: Boolean
        get() = prefs.getBoolean(OPEN_DIAL_PAD_AT_LAUNCH, false)
        set(openDialPad) = prefs.edit().putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, openDialPad).apply()

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var disableSwipeToAnswer: Boolean
        get() = prefs.getBoolean(DISABLE_SWIPE_TO_ANSWER, false)
        set(disableSwipeToAnswer) = prefs.edit().putBoolean(DISABLE_SWIPE_TO_ANSWER, disableSwipeToAnswer).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

    var dialpadVibration: Boolean
        get() = prefs.getBoolean(DIALPAD_VIBRATION, true)
        set(dialpadVibration) = prefs.edit().putBoolean(DIALPAD_VIBRATION, dialpadVibration).apply()

    var hideDialpadNumbers: Boolean
        get() = prefs.getBoolean(HIDE_DIALPAD_NUMBERS, false)
        set(hideDialpadNumbers) = prefs.edit().putBoolean(HIDE_DIALPAD_NUMBERS, hideDialpadNumbers).apply()

    var dialpadBeeps: Boolean
        get() = prefs.getBoolean(DIALPAD_BEEPS, true)
        set(dialpadBeeps) = prefs.edit().putBoolean(DIALPAD_BEEPS, dialpadBeeps).apply()

    var alwaysShowFullscreen: Boolean
        get() = prefs.getBoolean(ALWAYS_SHOW_FULLSCREEN, false)
        set(alwaysShowFullscreen) = prefs.edit().putBoolean(ALWAYS_SHOW_FULLSCREEN, alwaysShowFullscreen).apply()

    var appLanguage: String
        get() = prefs.getString(APP_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
        set(appLanguage) = prefs.edit().putString(APP_LANGUAGE, appLanguage).apply()

    var isLanguageSelected: Boolean
        get() = prefs.getBoolean(LANGUAGE_SELECTED, false)
        set(isLanguageSelected) = prefs.edit().putBoolean(LANGUAGE_SELECTED, isLanguageSelected).apply()

    var voiceDialLanguage: String
        get() = prefs.getString(VOICE_DIAL_LANGUAGE, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH
        set(voiceDialLanguage) = prefs.edit().putString(VOICE_DIAL_LANGUAGE, voiceDialLanguage).apply()

    var isVoiceDialLanguageSelected: Boolean
        get() = prefs.getBoolean(VOICE_DIAL_LANGUAGE_SELECTED, false)
        set(isVoiceDialLanguageSelected) = prefs.edit().putBoolean(VOICE_DIAL_LANGUAGE_SELECTED, isVoiceDialLanguageSelected).apply()

    var autoRecordCalls: Boolean
        get() = prefs.getBoolean(AUTO_RECORD_CALLS, false)
        set(autoRecordCalls) = prefs.edit().putBoolean(AUTO_RECORD_CALLS, autoRecordCalls).apply()
    
    // MANDATORY: Call recording consent for Google Play compliance
    // User must explicitly consent to recording laws and beep notification
    var callRecordingConsentGiven: Boolean
        get() = prefs.getBoolean(CALL_RECORDING_CONSENT_GIVEN, false)
        set(callRecordingConsentGiven) = prefs.edit().putBoolean(CALL_RECORDING_CONSENT_GIVEN, callRecordingConsentGiven).apply()
    
    // App Theme Settings (Independent of System Dark Mode)
    var isDarkThemeEnabled: Boolean
        get() = prefs.getBoolean(IS_DARK_THEME_ENABLED, false)
        set(isDarkThemeEnabled) = prefs.edit().putBoolean(IS_DARK_THEME_ENABLED, isDarkThemeEnabled).apply()
    
    var themeMode: String
        get() = prefs.getString(THEME_MODE, THEME_MODE_LIGHT) ?: THEME_MODE_LIGHT
        set(themeMode) = prefs.edit().putString(THEME_MODE, themeMode).apply()
    
    var enableProfilePictureClick: Boolean
        get() = prefs.getBoolean(ENABLE_PROFILE_PICTURE_CLICK, false)
        set(enableProfilePictureClick) = prefs.edit().putBoolean(ENABLE_PROFILE_PICTURE_CLICK, enableProfilePictureClick).apply()

    var enableMessagesJoystick: Boolean
        get() = prefs.getBoolean(ENABLE_MESSAGES_JOYSTICK, true)
        set(enableMessagesJoystick) = prefs.edit().putBoolean(ENABLE_MESSAGES_JOYSTICK, enableMessagesJoystick).apply()
}
