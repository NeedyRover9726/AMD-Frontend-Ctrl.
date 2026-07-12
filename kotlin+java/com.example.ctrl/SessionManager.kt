package com.example.ctrl

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("ctrl_session_prefs", Context.MODE_PRIVATE)

    var isSessionActive: Boolean
        get() = prefs.getBoolean("is_active", false)
        set(value) = prefs.edit { putBoolean("is_active", value) }

    // NEW: Tracks if the current active session is in the Break phase
    var isBreakMode: Boolean
        get() = prefs.getBoolean("is_break_mode", false)
        set(value) = prefs.edit { putBoolean("is_break_mode", value) }

    var studyMinutes: Int
        get() = prefs.getInt("study_mins", 0)
        set(value) = prefs.edit { putInt("study_mins", value) }

    var breakMinutes: Int
        get() = prefs.getInt("break_mins", 0)
        set(value) = prefs.edit { putInt("break_mins", value) }

    var elapsedMinutes: Float
        get() = prefs.getFloat("elapsed_mins", 0f)
        set(value) = prefs.edit { putFloat("elapsed_mins", value) }

    var selectedFileName: String
        get() = prefs.getString("file_name", "") ?: ""
        set(value) = prefs.edit { putString("file_name", value) }

    var selectedFileUri: Uri?
        get() = prefs.getString("file_uri", null)?.toUri()
        set(value) = prefs.edit { putString("file_uri", value?.toString()) }

    var blockedApps: List<String>
        get() = prefs.getStringSet("blocked_apps", emptySet())?.toList() ?: emptyList()
        set(value) = prefs.edit { putStringSet("blocked_apps", value.toSet()) }

    fun clearSession() {
        prefs.edit { clear() }
    }
}
