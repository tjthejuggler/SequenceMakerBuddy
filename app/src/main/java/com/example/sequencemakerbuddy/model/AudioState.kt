package com.example.sequencemakerbuddy.model

import com.google.gson.Gson

/**
 * A named delay preset that can be quickly applied via dropdown.
 */
data class DelayLabel(
    val name: String,
    val delaySeconds: Float
)

/**
 * Persisted audio state for a specific sequence.
 * Stored in SharedPreferences keyed by sequence name.
 */
data class SequenceAudioState(
    val audioUri: String? = null,
    val delaySeconds: Float = 0f,
    val delayLabels: List<DelayLabel> = emptyList()
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): SequenceAudioState {
            return gson.fromJson(json, SequenceAudioState::class.java) ?: SequenceAudioState()
        }
    }

    fun toJson(): String = gson.toJson(this)
}
