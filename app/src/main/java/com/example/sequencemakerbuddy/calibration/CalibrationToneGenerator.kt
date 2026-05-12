package com.example.sequencemakerbuddy.calibration

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a calibration WAV file in memory: silence with short, sharp
 * sine-wave beeps at known scheduled timestamps.
 *
 * The beeps are deliberately short (~80ms) with a fast attack so the
 * onset is unambiguous to the listener — this is what makes the median
 * tap-vs-scheduled latency a clean estimate of audio output latency
 * (plus the user's reaction time, which cancels out against the visual
 * phase reaction time).
 *
 * Output is a 16-bit PCM mono WAV at 44.1 kHz, suitable for any
 * Android MediaPlayer.
 */
object CalibrationToneGenerator {

    private const val SAMPLE_RATE = 44100
    private const val FREQ_HZ = 1000.0          // 1 kHz — sharp, easy to localise
    private const val BEEP_DURATION_MS = 80
    private const val ATTACK_MS = 3              // very fast attack for clean onset
    private const val RELEASE_MS = 30
    private const val AMPLITUDE = 0.6            // headroom; some users have loud volumes

    /**
     * Build a WAV byte array containing beeps at the given timestamps (ms).
     * @param beepTimesMs sorted list of beep onset times in milliseconds.
     * @param totalDurationMs total length of the resulting WAV in ms.
     */
    fun buildBeepWav(beepTimesMs: List<Int>, totalDurationMs: Int): ByteArray {
        val totalSamples = (totalDurationMs.toLong() * SAMPLE_RATE / 1000).toInt()
        val pcm = ShortArray(totalSamples)

        val beepSamples = SAMPLE_RATE * BEEP_DURATION_MS / 1000
        val attackSamples = SAMPLE_RATE * ATTACK_MS / 1000
        val releaseSamples = SAMPLE_RATE * RELEASE_MS / 1000

        for (onsetMs in beepTimesMs) {
            val onsetSample = (onsetMs.toLong() * SAMPLE_RATE / 1000).toInt()
            for (i in 0 until beepSamples) {
                val sampleIdx = onsetSample + i
                if (sampleIdx >= totalSamples) break

                // Envelope: linear attack, sustain, linear release.
                val envelope = when {
                    i < attackSamples -> i.toDouble() / attackSamples
                    i > beepSamples - releaseSamples ->
                        (beepSamples - i).toDouble() / releaseSamples
                    else -> 1.0
                }

                val t = sampleIdx.toDouble() / SAMPLE_RATE
                val sample = sin(2.0 * PI * FREQ_HZ * t) * envelope * AMPLITUDE
                val pcmValue = (sample * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                pcm[sampleIdx] = pcmValue.toShort()
            }
        }

        return wrapAsWav(pcm)
    }

    /**
     * Wrap a 16-bit mono PCM buffer in a minimal WAV container.
     */
    private fun wrapAsWav(pcm: ShortArray): ByteArray {
        val byteRate = SAMPLE_RATE * 2 // 16-bit mono = 2 bytes per sample
        val dataSize = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)

        // RIFF header
        out.write("RIFF".toByteArray())
        out.writeIntLE(36 + dataSize)
        out.write("WAVE".toByteArray())

        // fmt chunk
        out.write("fmt ".toByteArray())
        out.writeIntLE(16)              // subchunk size
        out.writeShortLE(1)             // PCM
        out.writeShortLE(1)             // mono
        out.writeIntLE(SAMPLE_RATE)
        out.writeIntLE(byteRate)
        out.writeShortLE(2)             // block align
        out.writeShortLE(16)            // bits per sample

        // data chunk
        out.write("data".toByteArray())
        out.writeIntLE(dataSize)
        for (s in pcm) {
            out.write(s.toInt() and 0xFF)
            out.write((s.toInt() shr 8) and 0xFF)
        }

        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }
}
