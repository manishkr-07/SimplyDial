package org.fossify.phone.helpers

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.fossify.phone.extensions.config
import org.fossify.phone.models.SpeedDial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    data class BackupData(
        val version: Int = 1,
        val appVersion: String = "2.0.0",
        val timestamp: Long = System.currentTimeMillis(),
        val interfaceMode: Int = 0,
        val contactNotes: Map<String, String> = emptyMap(),
        val speedDials: List<SpeedDial> = emptyList(),
        val preferences: Map<String, Boolean> = emptyMap(),
        val emergencySOSNumber: String = "112",
        val emergencySOSContact: String = "",
        val customSpamList: List<String> = emptyList()
    )

    data class RestoreResult(
        val success: Boolean,
        val notesCount: Int = 0,
        val speedDialsCount: Int = 0,
        val preferencesCount: Int = 0,
        val errorMessage: String = ""
    )

    private val gson: Gson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }

    fun createBackupData(context: Context): BackupData {
        val prefs = context.getSharedPreferences("Prefs", Context.MODE_PRIVATE)
        val allPrefs = prefs.all

        // Collect all contact notes
        val notes = mutableMapOf<String, String>()
        for ((key, value) in allPrefs) {
            if (key.startsWith("contact_note_") && value is String && value.isNotEmpty()) {
                notes[key.removePrefix("contact_note_")] = value
            }
        }

        // Collect all mode-scoped boolean preferences
        val booleanPrefs = mutableMapOf<String, Boolean>()
        val baseKeys = listOf(
            DIRECT_ONE_TAP_CALLING,
            SHOW_CALL_CONFIRMATION,
            SHOW_QUICK_ACTIONS_SHEET,
            ENABLE_PROFILE_PICTURE_CLICK,
            DISABLE_SWIPE_TO_ANSWER,
            ALWAYS_SHOW_FULLSCREEN,
            DIALPAD_VIBRATION,
            DIALPAD_BEEPS,
            OPEN_DIAL_PAD_AT_LAUNCH,
            HIDE_DIALPAD_NUMBERS,
            ENABLE_MESSAGES_JOYSTICK,
            GROUP_SUBSEQUENT_CALLS,
            ANNOUNCE_CALLER_NAME,
            SMART_DUAL_SIM,
            SHOW_EMERGENCY_SOS_TILE,
            SHOW_POST_CALL_SUMMARY,
            SPAM_SHIELD_ENABLED
        )

        for (baseKey in baseKeys) {
            val simpleKey = "${baseKey}_simple"
            val modernKey = "${baseKey}_modern"
            if (prefs.contains(simpleKey)) booleanPrefs[simpleKey] = prefs.getBoolean(simpleKey, false)
            if (prefs.contains(modernKey)) booleanPrefs[modernKey] = prefs.getBoolean(modernKey, false)
            if (prefs.contains(baseKey)) booleanPrefs[baseKey] = prefs.getBoolean(baseKey, false)
        }

        // Collect speed dials
        val speedDials = context.config.getSpeedDialValues()

        return BackupData(
            interfaceMode = context.config.interfaceMode,
            contactNotes = notes,
            speedDials = speedDials,
            preferences = booleanPrefs,
            emergencySOSNumber = context.config.emergencySOSNumber,
            emergencySOSContact = context.config.emergencySOSContact,
            customSpamList = context.config.getCustomSpamList().toList()
        )
    }

    fun getBackupJsonString(context: Context): String {
        val backup = createBackupData(context)
        return gson.toJson(backup)
    }

    fun exportBackupToFile(context: Context): File? {
        return try {
            val backup = createBackupData(context)
            val json = gson.toJson(backup)

            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Backups").apply {
                if (!exists()) mkdirs()
            }

            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "SimplyDial_Backup_$timestampStr.json")
            file.writeText(json, Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun restoreBackupFromJson(context: Context, jsonString: String): RestoreResult {
        return try {
            val backup = gson.fromJson(jsonString, BackupData::class.java) ?: return RestoreResult(false, errorMessage = "Empty backup")

            val prefs = context.getSharedPreferences("Prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Restore Notes
            var notesRestored = 0
            for ((number, note) in backup.contactNotes) {
                if (number.isNotEmpty() && note.isNotEmpty()) {
                    editor.putString("contact_note_$number", note)
                    notesRestored++
                }
            }

            // Restore Mode Preferences
            var prefsRestored = 0
            for ((key, value) in backup.preferences) {
                editor.putBoolean(key, value)
                prefsRestored++
            }

            // Restore Emergency SOS
            if (backup.emergencySOSNumber.isNotEmpty()) {
                editor.putString(EMERGENCY_SOS_NUMBER, backup.emergencySOSNumber)
            }
            if (backup.emergencySOSContact.isNotEmpty()) {
                editor.putString(EMERGENCY_SOS_CONTACT, backup.emergencySOSContact)
            }

            // Restore Custom Spam List
            if (backup.customSpamList.isNotEmpty()) {
                editor.putStringSet(CUSTOM_SPAM_LIST, backup.customSpamList.toSet())
            }

            // Restore Interface Mode
            editor.putInt(APP_INTERFACE_MODE, backup.interfaceMode)

            editor.apply()

            // Restore Speed Dials
            val speedDialsCount = backup.speedDials.size
            if (backup.speedDials.isNotEmpty()) {
                val speedDialJson = gson.toJson(backup.speedDials)
                context.config.speedDial = speedDialJson
            }

            // Sync active mode
            context.config.syncModePreferences(backup.interfaceMode)

            RestoreResult(
                success = true,
                notesCount = notesRestored,
                speedDialsCount = speedDialsCount,
                preferencesCount = prefsRestored
            )
        } catch (e: Exception) {
            RestoreResult(false, errorMessage = e.localizedMessage ?: "Invalid backup file")
        }
    }
}
