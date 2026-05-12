package com.example.sequencemakerbuddy.calibration

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Drives the audio↔visual calibration flow.
 *
 * Two phases (run separately so the user is unambiguous about what they
 * are reacting to):
 *
 *   PHASE_AUDIO  — plays beeps, no visuals; user taps when they HEAR a beep.
 *                  Result: median(tap − scheduled_beep) = audio perceived latency.
 *
 *   PHASE_VISUAL — emits flashes, no audio; user taps when they SEE a flash.
 *                  Result: median(tap − scheduled_flash) = visual perceived latency.
 *
 * The user's cognitive reaction time is roughly identical in both phases,
 * so it cancels out:
 *
 *   final_delay_seconds = (visual_perceived_ms − audio_perceived_ms) / 1000
 *
 * Sign convention matches SequencePlayerViewModel.delaySeconds:
 *   positive → audio starts later (used when audio is heard EARLIER than
 *              the visual is seen — rare).
 *   negative → audio starts earlier / skips ahead (used when audio is heard
 *              LATER than the visual is seen — typical for Bluetooth).
 *
 * Timing notes:
 *  - We reference all measurements to System.nanoTime() captured at the
 *    instant we triggered the stimulus (mediaPlayer.start() returns very
 *    fast; for the visual phase we capture nanoTime at the moment we
 *    flip the flash state).
 *  - We discard the FIRST sample (warm-up tap) and use the median of the
 *    remaining samples — robust to one bad tap and to outliers.
 */
class CalibrationEngine(private val scope: CoroutineScope) {

    // ---- Configurable schedule (all in ms relative to phase start) ----
    companion object {
        // 6 stimuli per phase: first one is discarded as warm-up, leaving 5 for the median.
        // 1.5 s spacing is comfortable to react to without rushing.
        // Exposed (not private) so the ball calibration PRG can be generated
        // to flash on this same schedule.
        val STIMULUS_TIMES_MS = listOf(1500, 3000, 4500, 6000, 7500, 9000)
        const val PHASE_DURATION_MS = 10_500           // a bit of trailing silence
        const val VISUAL_FLASH_DURATION_MS = 150       // matches the on-screen flash
        private const val WARMUP_DROP_COUNT = 1        // drop the first tap

        // Tap acceptance window: a tap must be within this window of *some* stimulus
        // to be considered a response to it. Outside this window the tap is ignored.
        private const val TAP_WINDOW_MS = 1200

        const val TOTAL_STIMULI = 6
        const val USABLE_STIMULI = TOTAL_STIMULI - WARMUP_DROP_COUNT
    }

    enum class Phase {
        IDLE,
        READY_AUDIO, RUNNING_AUDIO,
        READY_VISUAL, RUNNING_VISUAL,
        DONE,
        RUNNING_VERIFY
    }

    // ---- Observable state for the UI ----
    val phase = mutableStateOf(Phase.IDLE)

    /** Number of taps registered in the current phase. */
    val tapCount = mutableIntStateOf(0)

    /** Number of stimuli that have been delivered so far in the current phase. */
    val stimuliDelivered = mutableIntStateOf(0)

    /** True for the brief flash window during the visual phase — drives the UI flash square. */
    val flashOn = mutableStateOf(false)

    /** Final computed values, populated when phase == DONE. */
    val audioPerceivedMs = mutableIntStateOf(0)
    val visualPerceivedMs = mutableIntStateOf(0)
    val computedDelaySeconds = mutableStateOf(0f)

    /**
     * Optional hook invoked at T=0 of the visual schedule (both during
     * the visual measurement phase and during verification). The ViewModel
     * wires this to send a PLAY command to the connected LTX balls so the
     * uploaded calibration PRG starts at the same instant as the on-screen
     * flash schedule, allowing the user to react to the physical balls
     * instead of the phone screen if they prefer.
     *
     * Fires on the calibration coroutine; keep work non-blocking.
     */
    var onBallTrigger: (() -> Unit)? = null

    // ---- Internal ----
    private var phaseStartNanos: Long = 0L
    private val stimulusNanos = mutableListOf<Long>()  // actual nanoTime each stimulus fired
    private val tapNanos = mutableListOf<Long>()
    private var phaseJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempWavFile: File? = null

    /**
     * Reset to the very beginning so the user can start over.
     */
    fun reset() {
        phaseJob?.cancel()
        phaseJob = null
        releaseAudio()
        phase.value = Phase.IDLE
        tapCount.intValue = 0
        stimuliDelivered.intValue = 0
        flashOn.value = false
        audioPerceivedMs.intValue = 0
        visualPerceivedMs.intValue = 0
        computedDelaySeconds.value = 0f
        stimulusNanos.clear()
        tapNanos.clear()
    }

    /** Move from IDLE to "ready to start audio phase" (UI shows a Start button). */
    fun armAudioPhase() {
        reset()
        phase.value = Phase.READY_AUDIO
    }

    /**
     * Begin the audio phase. Builds and plays a WAV with beeps at the known
     * schedule. Captures phaseStartNanos at MediaPlayer.start().
     */
    fun startAudioPhase(context: Context) {
        if (phase.value != Phase.READY_AUDIO) return
        phase.value = Phase.RUNNING_AUDIO
        tapCount.intValue = 0
        stimuliDelivered.intValue = 0
        stimulusNanos.clear()
        tapNanos.clear()

        // Build & write the WAV
        val wavBytes = CalibrationToneGenerator.buildBeepWav(
            beepTimesMs = STIMULUS_TIMES_MS,
            totalDurationMs = PHASE_DURATION_MS
        )
        val tempFile = File(context.cacheDir, "smbuddy_calib_beeps.wav")
        FileOutputStream(tempFile).use { it.write(wavBytes) }
        tempWavFile = tempFile

        try {
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
            }
            mediaPlayer = mp

            phaseJob = scope.launch {
                mp.start()
                phaseStartNanos = System.nanoTime()

                // Drive the "stimuli delivered" counter so the UI can show progress.
                // The actual audio plays from MediaPlayer; we just bookkeep the schedule.
                for (t in STIMULUS_TIMES_MS) {
                    val targetNanos = phaseStartNanos + t * 1_000_000L
                    val waitMs = (targetNanos - System.nanoTime()) / 1_000_000L
                    if (waitMs > 0) delay(waitMs)
                    if (!isActive) return@launch
                    stimulusNanos.add(targetNanos)
                    stimuliDelivered.intValue++
                }

                // Trailing silence + buffer flush
                val endNanos = phaseStartNanos + PHASE_DURATION_MS * 1_000_000L
                val tailMs = (endNanos - System.nanoTime()) / 1_000_000L
                if (tailMs > 0) delay(tailMs)

                releaseAudio()

                // Compute audio perceived latency now.
                audioPerceivedMs.intValue = computeMedianLatencyMs()
                phase.value = Phase.READY_VISUAL
            }
        } catch (e: Exception) {
            e.printStackTrace()
            releaseAudio()
            phase.value = Phase.IDLE
        }
    }

    /**
     * Begin the visual phase. Emits flashes at the same schedule.
     * No audio is played.
     */
    fun startVisualPhase() {
        if (phase.value != Phase.READY_VISUAL) return
        phase.value = Phase.RUNNING_VISUAL
        tapCount.intValue = 0
        stimuliDelivered.intValue = 0
        stimulusNanos.clear()
        tapNanos.clear()

        phaseJob = scope.launch {
            phaseStartNanos = System.nanoTime()

            // Trigger the physical LTX balls to start their calibration PRG
            // at the same instant the on-screen visual schedule begins. The
            // ViewModel wires this to BallManager.playAllBalls(). This is
            // the EXACT same start signal used during normal sequence
            // playback, so any audio↔ball delay measured here is directly
            // applicable to the main play() path.
            onBallTrigger?.invoke()

            for (t in STIMULUS_TIMES_MS) {
                val targetNanos = phaseStartNanos + t * 1_000_000L
                val waitMs = (targetNanos - System.nanoTime()) / 1_000_000L
                if (waitMs > 0) delay(waitMs)
                if (!isActive) return@launch

                // Capture nanoTime at the moment we flip the flash on — this is
                // the closest we can get to "time the stimulus began" from the
                // app's perspective. Frame rendering adds ~1 frame of latency
                // which is the same on every flash, so it cancels into the
                // median (and is part of "visual perceived latency" anyway).
                val firedAt = System.nanoTime()
                stimulusNanos.add(firedAt)
                stimuliDelivered.intValue++
                flashOn.value = true

                // Flash duration: ~150 ms is enough to register but short
                // enough that the user can't keep tapping during it.
                delay(150)
                flashOn.value = false
            }

            // Trailing buffer
            val endNanos = phaseStartNanos + PHASE_DURATION_MS * 1_000_000L
            val tailMs = (endNanos - System.nanoTime()) / 1_000_000L
            if (tailMs > 0) delay(tailMs)

            visualPerceivedMs.intValue = computeMedianLatencyMs()
            // Final delay calculation:
            // delay = visual_perceived − audio_perceived (in seconds)
            val deltaMs = visualPerceivedMs.intValue - audioPerceivedMs.intValue
            computedDelaySeconds.value = (deltaMs / 1000f)
                .coerceIn(-10f, 10f)
            phase.value = Phase.DONE
        }
    }

    /**
     * Register a user tap. Captured at System.nanoTime() at call time.
     * Only meaningful during RUNNING_AUDIO or RUNNING_VISUAL.
     */
    fun registerTap() {
        val now = System.nanoTime()
        when (phase.value) {
            Phase.RUNNING_AUDIO, Phase.RUNNING_VISUAL -> {
                tapNanos.add(now)
                tapCount.intValue = tapNanos.size
            }
            else -> { /* ignore */ }
        }
    }

    /**
     * Cancel everything in progress and return to IDLE.
     */
    fun cancel() {
        reset()
    }

    /**
     * Verification phase: play the beep WAV alongside synchronized flashes,
     * applying [delaySecondsToTest] the same way the main playback engine
     * does. The user watches/listens and confirms whether the flash and
     * beep land at the same time.
     *
     * No taps are collected here — pure playback. Phase returns to DONE
     * when verification finishes so the user can run it again, adjust, or
     * save the preset.
     *
     * @param delaySecondsToTest the delay value to apply (positive = audio
     *   starts later than visual; negative = audio skips ahead). Mirrors
     *   SequencePlayerViewModel.play() exactly.
     */
    fun startVerification(context: Context, delaySecondsToTest: Float) {
        if (phase.value != Phase.DONE && phase.value != Phase.IDLE) return

        // Preserve the result values so the UI keeps showing them during
        // and after verification.
        val savedAudioMs = audioPerceivedMs.intValue
        val savedVisualMs = visualPerceivedMs.intValue
        val savedComputed = computedDelaySeconds.value

        phase.value = Phase.RUNNING_VERIFY
        stimuliDelivered.intValue = 0
        tapCount.intValue = 0
        flashOn.value = false
        audioPerceivedMs.intValue = savedAudioMs
        visualPerceivedMs.intValue = savedVisualMs
        computedDelaySeconds.value = savedComputed

        // Use the EXACT same schedule as the measurement phases so the
        // already-uploaded calibration PRG on the balls lines up beep-for-
        // flash with the verify audio. (Previously the verify used its own
        // 3-beep / 7 s schedule which mismatched the 6-flash PRG on the
        // balls.) No warm-up tap is needed during verify — no measurement
        // is being taken — but keeping the schedule identical means the
        // user can rely on one uploaded PRG for both phases.
        val verifyTimesMs = STIMULUS_TIMES_MS
        val totalMs = PHASE_DURATION_MS
        val wavBytes = CalibrationToneGenerator.buildBeepWav(verifyTimesMs, totalMs)
        val tempFile = File(context.cacheDir, "smbuddy_calib_verify.wav")
        FileOutputStream(tempFile).use { it.write(wavBytes) }
        tempWavFile = tempFile

        try {
            val mp = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
            }
            mediaPlayer = mp
            val delayMs = (delaySecondsToTest * 1000f).toInt()

            phaseJob = scope.launch {
                // Reference T=0 for the VISUAL schedule. Audio is offset by delayMs.
                val visualStartNanos = System.nanoTime()

                // Trigger physical balls in lockstep with the visual schedule,
                // matching what happens during normal playback.
                onBallTrigger?.invoke()

                // Mirror SequencePlayerViewModel.play() audio scheduling:
                //   delayMs > 0 → start audio `delayMs` after the visual schedule
                //   delayMs < 0 → seek audio to |delayMs| and start with the visual schedule
                //   delayMs == 0 → start together
                when {
                    delayMs > 0 -> launch {
                        delay(delayMs.toLong())
                        if (isActive) mp.start()
                    }
                    delayMs < 0 -> {
                        mp.seekTo(-delayMs)
                        mp.start()
                    }
                    else -> mp.start()
                }

                // Drive the flash schedule on the visual timeline.
                for (t in verifyTimesMs) {
                    val targetNanos = visualStartNanos + t * 1_000_000L
                    val waitMs = (targetNanos - System.nanoTime()) / 1_000_000L
                    if (waitMs > 0) delay(waitMs)
                    if (!isActive) return@launch
                    stimuliDelivered.intValue++
                    flashOn.value = true
                    delay(150)
                    flashOn.value = false
                }

                // Trailing buffer so the last beep can play out (positive
                // delay extends audio past the visual schedule end).
                val endNanos = visualStartNanos +
                    (totalMs + maxOf(0, delayMs)) * 1_000_000L
                val tailMs = (endNanos - System.nanoTime()) / 1_000_000L
                if (tailMs > 0) delay(tailMs)

                releaseAudio()
                phase.value = Phase.DONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            releaseAudio()
            phase.value = Phase.DONE
        }
    }

    /**
     * Pair each tap to its nearest stimulus, drop the first stimulus (warm-up),
     * and return the median tap-vs-stimulus delta in milliseconds.
     */
    private fun computeMedianLatencyMs(): Int {
        if (stimulusNanos.isEmpty()) return 0

        // Pair each stimulus with the closest tap that came AFTER it but
        // before the next stimulus + TAP_WINDOW_MS. Greedy from the start
        // keeps it deterministic.
        val deltasMs = mutableListOf<Long>()
        val unusedTaps = tapNanos.toMutableList()

        for (i in stimulusNanos.indices) {
            val sNanos = stimulusNanos[i]
            val nextSNanos = stimulusNanos.getOrNull(i + 1) ?: (sNanos + TAP_WINDOW_MS * 1_000_000L)
            val windowEnd = minOf(nextSNanos, sNanos + TAP_WINDOW_MS * 1_000_000L)

            // Find first tap that is >= sNanos and <= windowEnd
            val tapIdx = unusedTaps.indexOfFirst { it in sNanos..windowEnd }
            if (tapIdx >= 0) {
                val tapTime = unusedTaps.removeAt(tapIdx)
                deltasMs.add((tapTime - sNanos) / 1_000_000L)
            }
        }

        // Drop the first WARMUP_DROP_COUNT samples (warm-up taps).
        val usable = deltasMs.drop(WARMUP_DROP_COUNT)
        if (usable.isEmpty()) {
            // Fall back to whatever we have rather than report 0 —
            // returning 0 would silently produce a misleading delay.
            if (deltasMs.isEmpty()) return 0
            return median(deltasMs).toInt()
        }
        return median(usable).toInt()
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    }

    private fun releaseAudio() {
        try { mediaPlayer?.stop() } catch (_: Exception) { /* ignore */ }
        try { mediaPlayer?.release() } catch (_: Exception) { /* ignore */ }
        mediaPlayer = null
        tempWavFile?.delete()
        tempWavFile = null
    }

    /**
     * Number of usable taps so the UI can show e.g. "3/5 taps registered".
     * The first WARMUP_DROP_COUNT taps are counted but flagged as warm-up
     * by the UI separately.
     */
    fun usableTapCountSnapshot(): Int = (tapCount.intValue - WARMUP_DROP_COUNT).coerceAtLeast(0)
}
