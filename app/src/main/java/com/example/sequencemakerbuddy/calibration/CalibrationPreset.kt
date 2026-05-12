package com.example.sequencemakerbuddy.calibration

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A device-wide calibration preset describing the audio↔visual delay
 * for a particular hardware situation (e.g. "AirPods Pro", "Phone speaker",
 * "JBL on table — real balls").
 *
 * These are stored globally (NOT per-sequence) because they describe a
 * physical situation that's independent of any particular sequence.
 *
 * @param name Display name chosen by the user.
 * @param delaySeconds Final delay value to apply to the player. Same sign
 *   convention as SequenceAudioState.delaySeconds: positive = audio starts
 *   later, negative = audio skips ahead.
 * @param audioPerceivedMs Median measured (tap_ms − scheduled_beep_ms),
 *   for diagnostic display.
 * @param visualPerceivedMs Median measured (tap_ms − scheduled_flash_ms),
 *   for diagnostic display.
 * @param createdAtMs Wall-clock time of creation.
 */
data class CalibrationPreset(
    val name: String,
    val delaySeconds: Float,
    val audioPerceivedMs: Int,
    val visualPerceivedMs: Int,
    val createdAtMs: Long
) {
    companion object {
        private val gson = Gson()
        private val listType = object : TypeToken<List<CalibrationPreset>>() {}.type

        fun listFromJson(json: String?): List<CalibrationPreset> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                gson.fromJson(json, listType) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun listToJson(presets: List<CalibrationPreset>): String = gson.toJson(presets)
    }
}
