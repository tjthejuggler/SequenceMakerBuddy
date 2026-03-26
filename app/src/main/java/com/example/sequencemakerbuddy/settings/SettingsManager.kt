package com.example.sequencemakerbuddy.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent app settings using SharedPreferences.
 * Stores the .smbuddy folder location so the user only has to set it once.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smbuddy_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SMBUDDY_FOLDER_URI = "smbuddy_folder_uri"
    }

    /** Get the saved .smbuddy folder URI string, or null if not set. */
    fun getSmbuddyFolderUri(): String? {
        return prefs.getString(KEY_SMBUDDY_FOLDER_URI, null)
    }

    /** Save the .smbuddy folder URI string. */
    fun setSmbuddyFolderUri(uri: String) {
        prefs.edit().putString(KEY_SMBUDDY_FOLDER_URI, uri).apply()
    }

    /** Check if a folder has been configured. */
    fun hasFolderConfigured(): Boolean {
        return !getSmbuddyFolderUri().isNullOrEmpty()
    }
}
