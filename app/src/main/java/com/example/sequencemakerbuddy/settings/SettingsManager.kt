package com.example.sequencemakerbuddy.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.sequencemakerbuddy.model.SequenceAudioState

/**
 * Manages persistent app settings using SharedPreferences.
 * Stores the .smbuddy folder location, audio folder location,
 * and per-sequence audio state (last audio file + delay settings).
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smbuddy_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SMBUDDY_FOLDER_URI = "smbuddy_folder_uri"
        private const val KEY_AUDIO_FOLDER_URI = "audio_folder_uri"
        private const val KEY_AUDIO_STATE_PREFIX = "audio_state_"
    }

    // --- .smbuddy folder ---

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

    // --- Audio folder ---

    /** Get the saved audio folder URI string, or null if not set. */
    fun getAudioFolderUri(): String? {
        return prefs.getString(KEY_AUDIO_FOLDER_URI, null)
    }

    /** Save the audio folder URI string. */
    fun setAudioFolderUri(uri: String) {
        prefs.edit().putString(KEY_AUDIO_FOLDER_URI, uri).apply()
    }

    /** Check if an audio folder has been configured. */
    fun hasAudioFolderConfigured(): Boolean {
        return !getAudioFolderUri().isNullOrEmpty()
    }

    // --- Per-sequence audio state ---

    /** Get the persisted audio state for a given sequence name. */
    fun getAudioState(sequenceName: String): SequenceAudioState {
        val json = prefs.getString(KEY_AUDIO_STATE_PREFIX + sequenceName, null)
        return if (json != null) SequenceAudioState.fromJson(json) else SequenceAudioState()
    }

    /** Save the audio state for a given sequence name. */
    fun setAudioState(sequenceName: String, state: SequenceAudioState) {
        prefs.edit().putString(KEY_AUDIO_STATE_PREFIX + sequenceName, state.toJson()).apply()
    }
}
