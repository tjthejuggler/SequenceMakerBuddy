package com.example.sequencemakerbuddy.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.sequencemakerbuddy.calibration.CalibrationPreset
import com.example.sequencemakerbuddy.model.SequenceAudioState

/**
 * Manages persistent app settings using SharedPreferences.
 * Stores the .smbuddy folder location, audio folder location,
 * per-sequence audio state (last audio file + delay settings),
 * and device-wide calibration presets (audio↔visual delay for a
 * particular hardware situation, e.g. "AirPods Pro" or
 * "Phone speaker — real balls").
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smbuddy_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SMBUDDY_FOLDER_URI = "smbuddy_folder_uri"
        private const val KEY_AUDIO_FOLDER_URI = "audio_folder_uri"
        private const val KEY_AUDIO_STATE_PREFIX = "audio_state_"
        private const val KEY_LAST_BUNDLE_URI = "last_bundle_uri"
        private const val KEY_CALIBRATION_PRESETS = "calibration_presets"
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

    // --- Last loaded bundle ---

    /** Get the URI of the last successfully loaded .smbuddy bundle, or null. */
    fun getLastBundleUri(): String? {
        return prefs.getString(KEY_LAST_BUNDLE_URI, null)
    }

    /** Save the URI of the last successfully loaded .smbuddy bundle. */
    fun setLastBundleUri(uri: String) {
        prefs.edit().putString(KEY_LAST_BUNDLE_URI, uri).apply()
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

    // --- Device-wide calibration presets ---

    /** Get all saved calibration presets (device-wide, not per-sequence). */
    fun getCalibrationPresets(): List<CalibrationPreset> {
        val json = prefs.getString(KEY_CALIBRATION_PRESETS, null)
        return CalibrationPreset.listFromJson(json)
    }

    /**
     * Add or replace a calibration preset by name.
     * Replacing keeps the dropdown clean and lets the user re-calibrate
     * a known situation without producing duplicates.
     */
    fun saveCalibrationPreset(preset: CalibrationPreset) {
        val current = getCalibrationPresets().filter { it.name != preset.name }.toMutableList()
        current.add(preset)
        // Sort by most-recently-created first so the freshest is at the top.
        current.sortByDescending { it.createdAtMs }
        prefs.edit()
            .putString(KEY_CALIBRATION_PRESETS, CalibrationPreset.listToJson(current))
            .apply()
    }

    /** Remove a calibration preset by name. No-op if not found. */
    fun removeCalibrationPreset(name: String) {
        val current = getCalibrationPresets().filter { it.name != name }
        prefs.edit()
            .putString(KEY_CALIBRATION_PRESETS, CalibrationPreset.listToJson(current))
            .apply()
    }
}
