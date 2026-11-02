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

    var disableProximitySensor: Boolean
        get() = prefs.getBoolean(DISABLE_PROXIMITY_SENSOR, false)
        set(disableProximitySensor) = prefs.edit().putBoolean(DISABLE_PROXIMITY_SENSOR, disableProximitySensor).apply()

    var wasOverlaySnackbarConfirmed: Boolean
        get() = prefs.getBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, false)
        set(wasOverlaySnackbarConfirmed) = prefs.edit().putBoolean(WAS_OVERLAY_SNACKBAR_CONFIRMED, wasOverlaySnackbarConfirmed).apply()

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

    var interfaceMode: Int
        get() = prefs.getInt(APP_INTERFACE_MODE, INTERFACE_MODE_MODERN)
        set(interfaceMode) = prefs.edit().putInt(APP_INTERFACE_MODE, interfaceMode).apply()

    val isSimpleMode: Boolean
        get() = interfaceMode == INTERFACE_MODE_SIMPLE

    // --- Mode-Scoped Interface Features Architecture ---

    data class ModeFeatures(
        var directOneTapCalling: Boolean,
        var showCallConfirmation: Boolean,
        var showQuickActionsSheet: Boolean,
        var enableProfilePictureClick: Boolean,
        var disableSwipeToAnswer: Boolean,
        var alwaysShowFullscreen: Boolean,
        var dialpadVibration: Boolean,
        var dialpadBeeps: Boolean,
        var openDialPadAtLaunch: Boolean,
        var hideDialpadNumbers: Boolean,
        var enableMessagesJoystick: Boolean,
        var groupSubsequentCalls: Boolean,
        var announceCallerName: Boolean,
        var smartDualSIM: Boolean,
        var showEmergencySOSTile: Boolean,
        var showPostCallSummary: Boolean
    )

    fun getModePrefKey(baseKey: String, mode: Int = interfaceMode): String {
        val modeSuffix = if (mode == INTERFACE_MODE_SIMPLE) "_simple" else "_modern"
        return "${baseKey}${modeSuffix}"
    }

    fun getModeBoolean(baseKey: String, mode: Int, defaultForMode: Boolean): Boolean {
        val modeKey = getModePrefKey(baseKey, mode)
        return if (prefs.contains(modeKey)) {
            prefs.getBoolean(modeKey, defaultForMode)
        } else if (prefs.contains(baseKey)) {
            prefs.getBoolean(baseKey, defaultForMode)
        } else {
            defaultForMode
        }
    }

    fun setModeBoolean(baseKey: String, mode: Int, value: Boolean) {
        val modeKey = getModePrefKey(baseKey, mode)
        prefs.edit().putBoolean(modeKey, value).apply()
        if (mode == interfaceMode) {
            prefs.edit().putBoolean(baseKey, value).apply()
        }
    }

    fun getModeFeatures(mode: Int): ModeFeatures {
        val isSimple = mode == INTERFACE_MODE_SIMPLE
        return ModeFeatures(
            directOneTapCalling = getModeBoolean(DIRECT_ONE_TAP_CALLING, mode, isSimple),
            showCallConfirmation = getModeBoolean(SHOW_CALL_CONFIRMATION, mode, isSimple),
            showQuickActionsSheet = getModeBoolean(SHOW_QUICK_ACTIONS_SHEET, mode, !isSimple),
            enableProfilePictureClick = getModeBoolean(ENABLE_PROFILE_PICTURE_CLICK, mode, !isSimple),
            disableSwipeToAnswer = getModeBoolean(DISABLE_SWIPE_TO_ANSWER, mode, isSimple),
            alwaysShowFullscreen = getModeBoolean(ALWAYS_SHOW_FULLSCREEN, mode, isSimple),
            dialpadVibration = getModeBoolean(DIALPAD_VIBRATION, mode, isSimple),
            dialpadBeeps = getModeBoolean(DIALPAD_BEEPS, mode, isSimple),
            openDialPadAtLaunch = getModeBoolean(OPEN_DIAL_PAD_AT_LAUNCH, mode, false),
            hideDialpadNumbers = getModeBoolean(HIDE_DIALPAD_NUMBERS, mode, false),
            enableMessagesJoystick = getModeBoolean(ENABLE_MESSAGES_JOYSTICK, mode, !isSimple),
            groupSubsequentCalls = getModeBoolean(GROUP_SUBSEQUENT_CALLS, mode, true),
            announceCallerName = getModeBoolean(ANNOUNCE_CALLER_NAME, mode, isSimple),
            smartDualSIM = getModeBoolean(SMART_DUAL_SIM, mode, true),
            showEmergencySOSTile = getModeBoolean(SHOW_EMERGENCY_SOS_TILE, mode, isSimple),
            showPostCallSummary = getModeBoolean(SHOW_POST_CALL_SUMMARY, mode, true)
        )
    }

    fun saveModeFeatures(mode: Int, features: ModeFeatures) {
        setModeBoolean(DIRECT_ONE_TAP_CALLING, mode, features.directOneTapCalling)
        setModeBoolean(SHOW_CALL_CONFIRMATION, mode, features.showCallConfirmation)
        setModeBoolean(SHOW_QUICK_ACTIONS_SHEET, mode, features.showQuickActionsSheet)
        setModeBoolean(ENABLE_PROFILE_PICTURE_CLICK, mode, features.enableProfilePictureClick)
        setModeBoolean(DISABLE_SWIPE_TO_ANSWER, mode, features.disableSwipeToAnswer)
        setModeBoolean(ALWAYS_SHOW_FULLSCREEN, mode, features.alwaysShowFullscreen)
        setModeBoolean(DIALPAD_VIBRATION, mode, features.dialpadVibration)
        setModeBoolean(DIALPAD_BEEPS, mode, features.dialpadBeeps)
        setModeBoolean(OPEN_DIAL_PAD_AT_LAUNCH, mode, features.openDialPadAtLaunch)
        setModeBoolean(HIDE_DIALPAD_NUMBERS, mode, features.hideDialpadNumbers)
        setModeBoolean(ENABLE_MESSAGES_JOYSTICK, mode, features.enableMessagesJoystick)
        setModeBoolean(GROUP_SUBSEQUENT_CALLS, mode, features.groupSubsequentCalls)
        setModeBoolean(ANNOUNCE_CALLER_NAME, mode, features.announceCallerName)
        setModeBoolean(SMART_DUAL_SIM, mode, features.smartDualSIM)
        setModeBoolean(SHOW_EMERGENCY_SOS_TILE, mode, features.showEmergencySOSTile)
        setModeBoolean(SHOW_POST_CALL_SUMMARY, mode, features.showPostCallSummary)
        if (mode == interfaceMode) {
            syncModePreferences(mode)
        }
    }

    fun getDefaultModeFeatures(mode: Int): ModeFeatures {
        val isSimple = mode == INTERFACE_MODE_SIMPLE
        return ModeFeatures(
            directOneTapCalling = isSimple,
            showCallConfirmation = isSimple,
            showQuickActionsSheet = !isSimple,
            enableProfilePictureClick = !isSimple,
            disableSwipeToAnswer = isSimple,
            alwaysShowFullscreen = isSimple,
            dialpadVibration = isSimple,
            dialpadBeeps = isSimple,
            openDialPadAtLaunch = false,
            hideDialpadNumbers = false,
            enableMessagesJoystick = !isSimple,
            groupSubsequentCalls = true,
            announceCallerName = isSimple,
            smartDualSIM = true,
            showEmergencySOSTile = isSimple,
            showPostCallSummary = true
        )
    }

    fun syncModePreferences(mode: Int = interfaceMode) {
        val features = getModeFeatures(mode)
        showCallConfirmation = features.showCallConfirmation
        prefs.edit()
            .putBoolean(DIRECT_ONE_TAP_CALLING, features.directOneTapCalling)
            .putBoolean(SHOW_CALL_CONFIRMATION, features.showCallConfirmation)
            .putBoolean(SHOW_QUICK_ACTIONS_SHEET, features.showQuickActionsSheet)
            .putBoolean(ENABLE_PROFILE_PICTURE_CLICK, features.enableProfilePictureClick)
            .putBoolean(DISABLE_SWIPE_TO_ANSWER, features.disableSwipeToAnswer)
            .putBoolean(ALWAYS_SHOW_FULLSCREEN, features.alwaysShowFullscreen)
            .putBoolean(DIALPAD_VIBRATION, features.dialpadVibration)
            .putBoolean(DIALPAD_BEEPS, features.dialpadBeeps)
            .putBoolean(OPEN_DIAL_PAD_AT_LAUNCH, features.openDialPadAtLaunch)
            .putBoolean(HIDE_DIALPAD_NUMBERS, features.hideDialpadNumbers)
            .putBoolean(ENABLE_MESSAGES_JOYSTICK, features.enableMessagesJoystick)
            .putBoolean(GROUP_SUBSEQUENT_CALLS, features.groupSubsequentCalls)
            .putBoolean(ANNOUNCE_CALLER_NAME, features.announceCallerName)
            .putBoolean(SMART_DUAL_SIM, features.smartDualSIM)
            .putBoolean(SHOW_EMERGENCY_SOS_TILE, features.showEmergencySOSTile)
            .putBoolean(SHOW_POST_CALL_SUMMARY, features.showPostCallSummary)
            .apply()
    }

    // Property accessors automatically scoped to the active interfaceMode:

    var isCallConfirmationEnabled: Boolean
        get() = getModeBoolean(SHOW_CALL_CONFIRMATION, interfaceMode, isSimpleMode)
        set(value) {
            showCallConfirmation = value
            setModeBoolean(SHOW_CALL_CONFIRMATION, interfaceMode, value)
        }

    var directOneTapCalling: Boolean
        get() = getModeBoolean(DIRECT_ONE_TAP_CALLING, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(DIRECT_ONE_TAP_CALLING, interfaceMode, value)

    var showQuickActionsSheet: Boolean
        get() = getModeBoolean(SHOW_QUICK_ACTIONS_SHEET, interfaceMode, !isSimpleMode)
        set(value) = setModeBoolean(SHOW_QUICK_ACTIONS_SHEET, interfaceMode, value)

    var enableProfilePictureClick: Boolean
        get() = getModeBoolean(ENABLE_PROFILE_PICTURE_CLICK, interfaceMode, !isSimpleMode)
        set(value) = setModeBoolean(ENABLE_PROFILE_PICTURE_CLICK, interfaceMode, value)

    var disableSwipeToAnswer: Boolean
        get() = getModeBoolean(DISABLE_SWIPE_TO_ANSWER, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(DISABLE_SWIPE_TO_ANSWER, interfaceMode, value)

    var alwaysShowFullscreen: Boolean
        get() = getModeBoolean(ALWAYS_SHOW_FULLSCREEN, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(ALWAYS_SHOW_FULLSCREEN, interfaceMode, value)

    var dialpadVibration: Boolean
        get() = getModeBoolean(DIALPAD_VIBRATION, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(DIALPAD_VIBRATION, interfaceMode, value)

    var dialpadBeeps: Boolean
        get() = getModeBoolean(DIALPAD_BEEPS, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(DIALPAD_BEEPS, interfaceMode, value)

    var openDialPadAtLaunch: Boolean
        get() = getModeBoolean(OPEN_DIAL_PAD_AT_LAUNCH, interfaceMode, false)
        set(value) = setModeBoolean(OPEN_DIAL_PAD_AT_LAUNCH, interfaceMode, value)

    var hideDialpadNumbers: Boolean
        get() = getModeBoolean(HIDE_DIALPAD_NUMBERS, interfaceMode, false)
        set(value) = setModeBoolean(HIDE_DIALPAD_NUMBERS, interfaceMode, value)

    var enableMessagesJoystick: Boolean
        get() = getModeBoolean(ENABLE_MESSAGES_JOYSTICK, interfaceMode, !isSimpleMode)
        set(value) = setModeBoolean(ENABLE_MESSAGES_JOYSTICK, interfaceMode, value)

    var groupSubsequentCalls: Boolean
        get() = getModeBoolean(GROUP_SUBSEQUENT_CALLS, interfaceMode, true)
        set(value) = setModeBoolean(GROUP_SUBSEQUENT_CALLS, interfaceMode, value)

    var announceCallerName: Boolean
        get() = getModeBoolean(ANNOUNCE_CALLER_NAME, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(ANNOUNCE_CALLER_NAME, interfaceMode, value)

    var smartDualSIM: Boolean
        get() = getModeBoolean(SMART_DUAL_SIM, interfaceMode, true)
        set(value) = setModeBoolean(SMART_DUAL_SIM, interfaceMode, value)

    var showEmergencySOSTile: Boolean
        get() = getModeBoolean(SHOW_EMERGENCY_SOS_TILE, interfaceMode, isSimpleMode)
        set(value) = setModeBoolean(SHOW_EMERGENCY_SOS_TILE, interfaceMode, value)

    var showPostCallSummary: Boolean
        get() = getModeBoolean(SHOW_POST_CALL_SUMMARY, interfaceMode, true)
        set(value) = setModeBoolean(SHOW_POST_CALL_SUMMARY, interfaceMode, value)

    var emergencySOSNumber: String
        get() = prefs.getString(EMERGENCY_SOS_NUMBER, "112") ?: "112"
        set(value) = prefs.edit().putString(EMERGENCY_SOS_NUMBER, value.trim()).apply()

    var emergencySOSContact: String
        get() = prefs.getString(EMERGENCY_SOS_CONTACT, "") ?: ""
        set(value) = prefs.edit().putString(EMERGENCY_SOS_CONTACT, value.trim()).apply()

    fun getContactNote(number: String): String {
        val cleanNumber = number.filter { it.isDigit() }
        if (cleanNumber.isEmpty()) return ""
        return prefs.getString("contact_note_$cleanNumber", "") ?: ""
    }

    fun setContactNote(number: String, note: String) {
        val cleanNumber = number.filter { it.isDigit() }
        if (cleanNumber.isEmpty()) return
        val key = "contact_note_$cleanNumber"
        if (note.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, note.trim()).apply()
        }
    }
}
