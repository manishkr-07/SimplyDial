package org.fossify.phone.helpers

import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES

// shared prefs
const val SPEED_DIAL = "speed_dial"
const val REMEMBER_SIM_PREFIX = "remember_sim_"
const val GROUP_SUBSEQUENT_CALLS = "group_subsequent_calls"
const val OPEN_DIAL_PAD_AT_LAUNCH = "open_dial_pad_at_launch"
const val DISABLE_PROXIMITY_SENSOR = "disable_proximity_sensor"
const val DISABLE_SWIPE_TO_ANSWER = "disable_swipe_to_answer"
const val SHOW_TABS = "show_tabs"
const val FAVORITES_CONTACTS_ORDER = "favorites_contacts_order"
const val FAVORITES_CUSTOM_ORDER_SELECTED = "favorites_custom_order_selected"
const val WAS_OVERLAY_SNACKBAR_CONFIRMED = "was_overlay_snackbar_confirmed"
const val DIALPAD_VIBRATION = "dialpad_vibration"
const val DIALPAD_BEEPS = "dialpad_beeps"
const val HIDE_DIALPAD_NUMBERS = "hide_dialpad_numbers"
const val ALWAYS_SHOW_FULLSCREEN = "always_show_fullscreen"
const val APP_LANGUAGE = "app_language"
const val LANGUAGE_SELECTED = "language_selected"
const val VOICE_DIAL_LANGUAGE = "voice_dial_language"
const val VOICE_DIAL_LANGUAGE_SELECTED = "voice_dial_language_selected"
const val AUTO_RECORD_CALLS = "auto_record_calls"
const val CALL_RECORDING_CONSENT_GIVEN = "call_recording_consent_given" // MANDATORY for Google Play
const val ENABLE_PROFILE_PICTURE_CLICK = "enable_profile_picture_click"
const val ENABLE_MESSAGES_JOYSTICK = "enable_messages_joystick"

// Language codes
const val LANGUAGE_ENGLISH = "en"
const val LANGUAGE_HINDI = "hi"

const val ALL_TABS_MASK = TAB_CONTACTS or TAB_FAVORITES or TAB_CALL_HISTORY

val tabsList = arrayListOf(TAB_CONTACTS, TAB_FAVORITES, TAB_CALL_HISTORY)

private const val PATH = "org.fossify.phone.action."
const val ACCEPT_CALL = PATH + "ACCEPT_CALL"
const val DECLINE_CALL = PATH + "DECLINE_CALL"

const val DIALPAD_TONE_LENGTH_MS = 150L // The length of DTMF tones in milliseconds

// App Interface Mode (Simple Senior-Friendly vs Modern Advanced)
const val APP_INTERFACE_MODE = "app_interface_mode"
const val INTERFACE_MODE_SIMPLE = 0
const val INTERFACE_MODE_MODERN = 1

// Theme Settings (Independent of System Dark Mode)
const val IS_DARK_THEME_ENABLED = "is_dark_theme_enabled"
const val THEME_MODE = "theme_mode"
const val THEME_MODE_LIGHT = "light"
const val THEME_MODE_DARK = "dark"
