package com.example.sequencemakerbuddy.player

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sequencemakerbuddy.ball.BallManager
import com.example.sequencemakerbuddy.ball.PrgGenerator
import com.example.sequencemakerbuddy.calibration.CalibrationEngine
import com.example.sequencemakerbuddy.calibration.CalibrationPreset
import com.example.sequencemakerbuddy.model.BundleParseResult
import com.example.sequencemakerbuddy.model.DelayLabel
import com.example.sequencemakerbuddy.model.SequenceAudioState
import com.example.sequencemakerbuddy.model.SequenceBundle
import com.example.sequencemakerbuddy.settings.SettingsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Represents a .smbuddy file entry for the file browser.
 */
data class SmbuddyFileEntry(
    val name: String,
    val uri: Uri,
    val lastModified: Long
)

/**
 * Represents an audio file entry for the audio browser.
 */
data class AudioFileEntry(
    val name: String,
    val uri: Uri,
    val lastModified: Long
)

/**
 * ViewModel that manages sequence playback synced with audio.
 * Updates ball colors at 100Hz (every 10ms) based on the loaded sequence data.
 * Supports audio delay: positive = silence before audio, negative = skip start of audio.
 * Integrates with real LTX juggling balls via WiFi network.
 */
class SequencePlayerViewModel : ViewModel() {

    // Loaded data
    var bundle = mutableStateOf<SequenceBundle?>(null)
        private set

    // Ball colors (Android Color ints) - one per ball
    var ballColor1 = mutableIntStateOf(Color.DKGRAY)
        private set
    var ballColor2 = mutableIntStateOf(Color.DKGRAY)
        private set
    var ballColor3 = mutableIntStateOf(Color.DKGRAY)
        private set

    // Playback state
    var isPlaying = mutableStateOf(false)
        private set
    var currentTimeMs = mutableIntStateOf(0)
        private set
    var totalDurationMs = mutableIntStateOf(0)
        private set
    var projectName = mutableStateOf("No sequence loaded")
        private set
    var audioLoaded = mutableStateOf(false)
        private set
    var sequenceLoaded = mutableStateOf(false)
        private set

    // Audio delay state
    var delaySeconds = mutableFloatStateOf(0f)
        private set
    var delayLabels = mutableStateOf<List<DelayLabel>>(emptyList())
        private set
    var audioFileName = mutableStateOf<String?>(null)
        private set

    // File browser state
    var smbuddyFiles = mutableStateOf<List<SmbuddyFileEntry>>(emptyList())
        private set
    var showFileBrowser = mutableStateOf(false)
        private set
    var showSettings = mutableStateOf(false)
        private set
    var folderConfigured = mutableStateOf(false)
        private set

    // Audio browser state
    var audioFiles = mutableStateOf<List<AudioFileEntry>>(emptyList())
        private set
    var showAudioBrowser = mutableStateOf(false)
        private set
    var audioFolderConfigured = mutableStateOf(false)
        private set

    // Dialog state for adding delay labels
    var showAddLabelDialog = mutableStateOf(false)
        private set

    // --- Calibration ---
    val calibration = CalibrationEngine(viewModelScope)
    var showCalibrationDialog = mutableStateOf(false)
        private set
    var calibrationPresets = mutableStateOf<List<CalibrationPreset>>(emptyList())
        private set

    // Whether a calibration PRG has been uploaded to the balls in this
    // session (resets when the dialog is reopened). Drives a "✓ uploaded"
    // indicator in the calibration dialog.
    var calibrationPrgUploaded = mutableStateOf(false)
        private set

    // Human-readable status line for the calibration upload / test buttons
    // (e.g. "No balls connected", "Uploading...", "Upload complete ✓").
    var calibrationBallStatus = mutableStateOf("")
        private set

    // Ball manager for real ball connection
    val ballManager = BallManager(viewModelScope)

    init {
        // Wire the calibration engine's ball-trigger hook so the visual phase
        // (and verification) of calibration sends the SAME PLAY UDP frame to
        // all connected LTX balls that normal sequence playback uses. This
        // lets the user calibrate against the physical ball flashes instead
        // of (or in addition to) the on-screen flash square.
        calibration.onBallTrigger = {
            viewModelScope.launch { ballManager.playAllBalls() }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var tempAudioFile: File? = null
    private var currentAudioUri: Uri? = null
    private var audioStartJob: Job? = null

    // Track whether audio was loaded from external file (overrides bundle audio)
    private var externalAudioLoaded = false

    /**
     * Initialize folder state from settings and restore last loaded bundle.
     * Also attaches the application context to the ball manager so it can
     * acquire a Wi-Fi MulticastLock during ball discovery (without it the
     * Wi-Fi chipset filters out the LTX broadcast packets and the scan
     * appears unreliable).
     */
    fun initSettings(context: Context) {
        // Attach context for MulticastLock acquisition during scanning.
        ballManager.attachContext(context)

        val settings = SettingsManager(context)
        folderConfigured.value = settings.hasFolderConfigured()
        audioFolderConfigured.value = settings.hasAudioFolderConfigured()

        // Load device-wide calibration presets
        calibrationPresets.value = settings.getCalibrationPresets()

        // Auto-restore the last loaded bundle
        val lastUri = settings.getLastBundleUri()
        if (lastUri != null) {
            loadBundle(context, Uri.parse(lastUri))
        }
    }

    /**
     * Load a .smbuddy ZIP bundle from a URI.
     * Extracts both the sequence JSON and the audio file from the ZIP.
     * Restores the last-used audio and delay for this sequence.
     */
    fun loadBundle(context: Context, uri: Uri) {
        try {
            safeStop()

            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val result: BundleParseResult = SequenceBundle.fromZipInputStream(inputStream)
            inputStream.close()

            bundle.value = result.bundle
            projectName.value = result.bundle.projectName
            sequenceLoaded.value = true
            externalAudioLoaded = false

            // Persist the URI so we can auto-load next time
            SettingsManager(context).setLastBundleUri(uri.toString())

            // Compute total duration from sequence data (max centisecond key -> ms)
            val maxCentiseconds = result.bundle.balls
                .flatMap { it.sortedTimes }
                .maxOrNull() ?: 0
            totalDurationMs.intValue = maxCentiseconds * 10

            // Set initial colors from time 0
            updateBallColors(0)

            // Restore per-sequence audio state
            val settings = SettingsManager(context)
            val audioState = settings.getAudioState(result.bundle.projectName)
            delaySeconds.floatValue = audioState.delaySeconds
            delayLabels.value = audioState.delayLabels

            if (audioState.audioUri != null) {
                // Restore last audio file for this sequence
                loadAudioFromUri(context, Uri.parse(audioState.audioUri))
            } else if (result.audioBytes != null && result.audioFilename != null) {
                // Fall back to audio from the bundle
                loadAudioFromBytes(context, result.audioBytes, result.audioFilename)
            } else {
                audioLoaded.value = false
                audioFileName.value = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            projectName.value = "Error loading bundle"
        }
    }

    /**
     * Load audio from an external URI (from the audio folder browser).
     */
    fun loadAudioFromUri(context: Context, uri: Uri) {
        try {
            safeStop()
            mediaPlayer?.release()
            mediaPlayer = null
            tempAudioFile?.delete()
            tempAudioFile = null

            // Get the file extension from the original filename
            val docFile = DocumentFile.fromSingleUri(context, uri)
            val originalName = docFile?.name ?: "audio.mp3"
            val extension = originalName.substringAfterLast('.', "mp3")

            // Copy URI content to a temp file with proper extension for MediaPlayer
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val tempFile = File(context.cacheDir, "smbuddy_audio_ext.$extension")
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(8192)
                var len: Int
                while (inputStream.read(buffer).also { len = it } != -1) {
                    out.write(buffer, 0, len)
                }
            }
            inputStream.close()
            tempAudioFile = tempFile

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
            }
            currentAudioUri = uri
            audioLoaded.value = true
            externalAudioLoaded = true

            // Get display name from URI (reuse docFile from above)
            audioFileName.value = docFile?.name ?: "Audio"

            // Use audio duration if longer than sequence duration
            val audioDurationMs = mediaPlayer?.duration ?: 0
            if (audioDurationMs > totalDurationMs.intValue) {
                totalDurationMs.intValue = audioDurationMs
            }

            // Save audio state
            saveAudioState(context)
        } catch (e: Exception) {
            e.printStackTrace()
            audioLoaded.value = false
            audioFileName.value = null
        }
    }

    /**
     * Load audio from raw bytes extracted from the ZIP bundle.
     * Writes to a temp file since MediaPlayer needs a file/URI.
     */
    private fun loadAudioFromBytes(context: Context, audioBytes: ByteArray, filename: String) {
        try {
            mediaPlayer?.release()
            tempAudioFile?.delete()

            val tempFile = File(context.cacheDir, "smbuddy_audio_$filename")
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            tempAudioFile = tempFile

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
            }
            currentAudioUri = null
            audioLoaded.value = true
            externalAudioLoaded = false
            audioFileName.value = filename

            // Use audio duration if longer than sequence duration
            val audioDurationMs = mediaPlayer?.duration ?: 0
            if (audioDurationMs > totalDurationMs.intValue) {
                totalDurationMs.intValue = audioDurationMs
            }
        } catch (e: Exception) {
            e.printStackTrace()
            audioLoaded.value = false
            audioFileName.value = null
        }
    }

    // --- Audio folder management ---

    /**
     * Scan the configured audio folder for audio files.
     */
    fun refreshAudioFileList(context: Context) {
        val settings = SettingsManager(context)
        val folderUriStr = settings.getAudioFolderUri() ?: return
        val folderUri = Uri.parse(folderUriStr)

        try {
            val docTree = DocumentFile.fromTreeUri(context, folderUri) ?: return
            val files = mutableListOf<AudioFileEntry>()
            val audioExtensions = setOf("mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus")

            docTree.listFiles().forEach { doc ->
                val name = doc.name ?: return@forEach
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in audioExtensions && doc.isFile) {
                    files.add(
                        AudioFileEntry(
                            name = name,
                            uri = doc.uri,
                            lastModified = doc.lastModified()
                        )
                    )
                }
            }

            audioFiles.value = files
        } catch (e: Exception) {
            e.printStackTrace()
            audioFiles.value = emptyList()
        }
    }

    /**
     * Save the audio folder URI and take persistent permission.
     */
    fun setAudioFolder(context: Context, uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)

        val settings = SettingsManager(context)
        settings.setAudioFolderUri(uri.toString())
        audioFolderConfigured.value = true

        refreshAudioFileList(context)
    }

    // --- Delay management ---

    /**
     * Set the audio delay in seconds.
     * Positive = silence before audio starts, Negative = skip start of audio.
     */
    fun setDelay(context: Context, seconds: Float) {
        delaySeconds.floatValue = seconds.coerceIn(-10f, 10f)
        saveAudioState(context)
    }

    /**
     * Increment the delay by a step (0.05s).
     */
    fun incrementDelay(context: Context) {
        setDelay(context, delaySeconds.floatValue + 0.05f)
    }

    /**
     * Decrement the delay by a step (0.05s).
     */
    fun decrementDelay(context: Context) {
        setDelay(context, delaySeconds.floatValue - 0.05f)
    }

    /**
     * Add a named label for the current delay value.
     */
    fun addDelayLabel(context: Context, name: String) {
        if (name.isBlank()) return
        val current = delayLabels.value.toMutableList()
        // Remove existing label with same name
        current.removeAll { it.name == name }
        current.add(DelayLabel(name, delaySeconds.floatValue))
        // Sort by delay value
        current.sortBy { it.delaySeconds }
        delayLabels.value = current
        saveAudioState(context)
    }

    /**
     * Remove a delay label by name.
     */
    fun removeDelayLabel(context: Context, name: String) {
        val current = delayLabels.value.toMutableList()
        current.removeAll { it.name == name }
        delayLabels.value = current
        saveAudioState(context)
    }

    /**
     * Apply a saved delay label by name.
     */
    fun applyDelayLabel(context: Context, name: String) {
        val label = delayLabels.value.find { it.name == name } ?: return
        delaySeconds.floatValue = label.delaySeconds
        saveAudioState(context)
    }

    /**
     * Show the add-label dialog.
     */
    fun showAddLabelDialog() {
        showAddLabelDialog.value = true
    }

    /**
     * Dismiss the add-label dialog.
     */
    fun dismissAddLabelDialog() {
        showAddLabelDialog.value = false
    }

    // --- Calibration ---

    /**
     * Open the calibration wizard. Stops normal playback first so audio
     * resources don't fight over the speaker.
     */
    fun openCalibrationDialog() {
        safeStop()
        calibration.armAudioPhase()
        calibrationPrgUploaded.value = false
        calibrationBallStatus.value = ""
        showCalibrationDialog.value = true
    }

    /**
     * Generate and upload the calibration PRG (a sequence that flashes the
     * balls white at the same schedule as the on-screen visual phase) to
     * every connected ball. The user runs this once before starting the
     * visual phase if they want to react to the physical balls.
     */
    fun uploadCalibrationToBalls() {
        val ips = ballManager.getConnectedIps()
        if (ips.isEmpty()) {
            calibrationBallStatus.value = "No balls connected"
            return
        }
        calibrationBallStatus.value = "Uploading calibration PRG to ${ips.size} ball(s)..."
        viewModelScope.launch {
            val prgBytes = PrgGenerator.generateCalibrationPrg(
                flashTimesMs = CalibrationEngine.STIMULUS_TIMES_MS,
                flashDurationMs = CalibrationEngine.VISUAL_FLASH_DURATION_MS,
                totalDurationMs = CalibrationEngine.PHASE_DURATION_MS
            )
            val results = ballManager.uploadCalibrationPrg(prgBytes)
            val ok = results.values.count { it }
            val total = results.size
            calibrationPrgUploaded.value = ok > 0 && ok == total
            calibrationBallStatus.value = when {
                total == 0 -> "No balls connected"
                ok == total -> "Calibration PRG uploaded to all $total ball(s) ✓"
                ok == 0 -> "Upload failed for all $total ball(s)"
                else -> "Uploaded to $ok/$total ball(s) — some failed"
            }
        }
    }

    /**
     * Send a PLAY command to all connected balls right now, so the user can
     * verify that the start signal reaches the balls and that the uploaded
     * calibration PRG plays as expected. Identical to the PLAY frame sent
     * during normal sequence playback.
     */
    fun testCalibrationStartSignal() {
        val ips = ballManager.getConnectedIps()
        if (ips.isEmpty()) {
            calibrationBallStatus.value = "No balls connected"
            return
        }
        calibrationBallStatus.value = "Sending start signal to ${ips.size} ball(s)..."
        viewModelScope.launch {
            val results = ballManager.playAllBalls()
            val ok = results.values.count { it }
            calibrationBallStatus.value = if (ok == results.size) {
                "Start signal sent ✓ — watch the ball(s) flash"
            } else {
                "Start signal: $ok/${results.size} ball(s) ok"
            }
        }
    }

    /**
     * Close the calibration wizard and tear down any in-progress run.
     */
    fun dismissCalibrationDialog() {
        calibration.cancel()
        showCalibrationDialog.value = false
    }

    /**
     * Save the just-completed calibration as a named device-wide preset.
     * Refreshes the in-memory preset list so the dropdown updates.
     */
    fun saveCalibrationPreset(context: Context, name: String) {
        if (name.isBlank()) return
        val preset = CalibrationPreset(
            name = name.trim(),
            delaySeconds = calibration.computedDelaySeconds.value,
            audioPerceivedMs = calibration.audioPerceivedMs.intValue,
            visualPerceivedMs = calibration.visualPerceivedMs.intValue,
            createdAtMs = System.currentTimeMillis()
        )
        val settings = SettingsManager(context)
        settings.saveCalibrationPreset(preset)
        calibrationPresets.value = settings.getCalibrationPresets()
        // Apply it to the current sequence right away so the user can verify.
        setDelay(context, preset.delaySeconds)
    }

    /**
     * Apply a calibration preset by name: copies its delaySeconds onto the
     * current sequence's delay.
     */
    fun applyCalibrationPreset(context: Context, name: String) {
        val preset = calibrationPresets.value.find { it.name == name } ?: return
        setDelay(context, preset.delaySeconds)
    }

    /**
     * Remove a calibration preset by name and refresh the list.
     */
    fun removeCalibrationPreset(context: Context, name: String) {
        val settings = SettingsManager(context)
        settings.removeCalibrationPreset(name)
        calibrationPresets.value = settings.getCalibrationPresets()
    }

    /**
     * Start an inline verification run from the main screen, using the
     * currently applied delaySeconds. Stops any normal playback first so
     * the verify audio can play without conflict.
     */
    fun startInlineVerification(context: Context) {
        safeStop()
        calibration.startVerification(context, delaySeconds.floatValue)
    }

    /**
     * Cancel an in-progress inline verification (e.g. user tapped Stop).
     */
    fun cancelInlineVerification() {
        calibration.cancel()
    }

    // --- Sequence folder management ---

    /**
     * Scan the configured .smbuddy folder for files.
     */
    fun refreshFileList(context: Context) {
        val settings = SettingsManager(context)
        val folderUriStr = settings.getSmbuddyFolderUri() ?: return
        val folderUri = Uri.parse(folderUriStr)

        try {
            val docTree = DocumentFile.fromTreeUri(context, folderUri) ?: return
            val files = mutableListOf<SmbuddyFileEntry>()

            docTree.listFiles().forEach { doc ->
                val name = doc.name ?: return@forEach
                if (name.endsWith(".smbuddy", ignoreCase = true) && doc.isFile) {
                    files.add(
                        SmbuddyFileEntry(
                            name = name,
                            uri = doc.uri,
                            lastModified = doc.lastModified()
                        )
                    )
                }
            }

            smbuddyFiles.value = files
        } catch (e: Exception) {
            e.printStackTrace()
            smbuddyFiles.value = emptyList()
        }
    }

    /**
     * Save the folder URI and take persistent permission.
     */
    fun setFolder(context: Context, uri: Uri) {
        // Take persistent read permission
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)

        val settings = SettingsManager(context)
        settings.setSmbuddyFolderUri(uri.toString())
        folderConfigured.value = true

        refreshFileList(context)
    }

    // --- Ball management ---

    /**
     * Toggle ball scanning on/off.
     */
    fun toggleBallScanning() {
        if (ballManager.isScanning.value) {
            ballManager.stopScanning()
        } else {
            ballManager.startScanning()
        }
    }

    /**
     * Upload the current sequence to all connected balls.
     * Generates 3 PRG files (one per ball timeline) and uploads each.
     */
    fun uploadToBalls() {
        val b = bundle.value ?: return
        viewModelScope.launch {
            ballManager.uploadSequences(b)
        }
    }

    // --- Playback ---

    /**
     * Start synchronized playback of audio + sequence.
     * Handles delay: positive = audio starts later, negative = audio skips ahead.
     * Also sends PLAY command to all connected real balls.
     */
    fun play() {
        if (bundle.value == null) return
        if (isPlaying.value) return

        isPlaying.value = true

        // Send PLAY to all connected real balls (on IO dispatcher)
        viewModelScope.launch { ballManager.playAllBalls() }

        val delayMs = (delaySeconds.floatValue * 1000).toInt()

        if (audioLoaded.value && mediaPlayer != null) {
            if (delayMs > 0) {
                // Positive delay: start sequence now, start audio after delay
                audioStartJob?.cancel()
                audioStartJob = viewModelScope.launch {
                    delay(delayMs.toLong())
                    if (isPlaying.value) {
                        mediaPlayer?.start()
                    }
                }
            } else if (delayMs < 0) {
                // Negative delay: skip into audio by |delay| seconds, start both together
                mediaPlayer?.seekTo(-delayMs)
                mediaPlayer?.start()
            } else {
                // No delay: start both together
                mediaPlayer?.start()
            }
        }

        playbackJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis() - currentTimeMs.intValue
            while (isActive && isPlaying.value) {
                val elapsed = (System.currentTimeMillis() - startTime).toInt()
                currentTimeMs.intValue = elapsed

                // Convert ms to centiseconds (100Hz ticks)
                val centiseconds = elapsed / 10
                updateBallColors(centiseconds)

                // Sleep ~10ms for 100Hz update rate
                delay(10)
            }
        }
    }

    /**
     * Pause playback.
     * Also sends STOP command to all connected real balls.
     */
    fun pause() {
        isPlaying.value = false
        playbackJob?.cancel()
        audioStartJob?.cancel()
        mediaPlayer?.pause()

        // Send STOP to all connected real balls (on IO dispatcher)
        viewModelScope.launch { ballManager.stopAllBalls() }
    }

    /**
     * Stop that never throws — safe to call from inside try/catch blocks
     * where an exception would cause the caller to silently fail.
     */
    private fun safeStop() {
        try { stop() } catch (_: Exception) { /* ignore */ }
    }

    /**
     * Stop and reset to beginning.
     * Also sends STOP command to all connected real balls.
     */
    fun stop() {
        isPlaying.value = false
        playbackJob?.cancel()
        audioStartJob?.cancel()
        mediaPlayer?.let {
            try {
                it.stop()
                it.prepare()
            } catch (_: IllegalStateException) {
                // MediaPlayer was in a state where stop/prepare is invalid (e.g. Idle)
            }
        }
        currentTimeMs.intValue = 0
        updateBallColors(0)

        // Send STOP to all connected real balls (on IO dispatcher)
        viewModelScope.launch { ballManager.stopAllBalls() }
    }

    /**
     * Seek to a specific time in milliseconds.
     * Updates ball colors and audio position.
     */
    fun seekTo(timeMs: Int) {
        val clampedMs = timeMs.coerceIn(0, totalDurationMs.intValue)
        currentTimeMs.intValue = clampedMs

        // Adjust audio position based on delay
        val delayMs = (delaySeconds.floatValue * 1000).toInt()
        if (audioLoaded.value && mediaPlayer != null) {
            val audioMs = clampedMs - delayMs
            if (audioMs >= 0) {
                mediaPlayer?.seekTo(audioMs.coerceAtMost((mediaPlayer?.duration ?: 0) - 1).coerceAtLeast(0))
            } else {
                // Audio hasn't started yet at this position, seek to 0
                mediaPlayer?.seekTo(0)
            }
        }

        updateBallColors(clampedMs / 10)

        // If currently playing, restart the playback loop from the new position
        if (isPlaying.value) {
            playbackJob?.cancel()
            audioStartJob?.cancel()
            playbackJob = viewModelScope.launch {
                val startTime = System.currentTimeMillis() - clampedMs
                while (isActive && isPlaying.value) {
                    val elapsed = (System.currentTimeMillis() - startTime).toInt()
                    currentTimeMs.intValue = elapsed
                    updateBallColors(elapsed / 10)
                    delay(10)
                }
            }
        }
    }

    /**
     * Update ball colors based on the current centisecond time.
     */
    private fun updateBallColors(centiseconds: Int) {
        val b = bundle.value ?: return
        val balls = b.balls

        if (balls.isNotEmpty()) {
            ballColor1.intValue = balls[0].getColorAt(centiseconds)
        }
        if (balls.size > 1) {
            ballColor2.intValue = balls[1].getColorAt(centiseconds)
        }
        if (balls.size > 2) {
            ballColor3.intValue = balls[2].getColorAt(centiseconds)
        }
    }

    /**
     * Save the current audio state (audio URI, delay, labels) for the loaded sequence.
     */
    private fun saveAudioState(context: Context) {
        val name = bundle.value?.projectName ?: return
        val settings = SettingsManager(context)
        settings.setAudioState(
            name, SequenceAudioState(
                audioUri = currentAudioUri?.toString(),
                delaySeconds = delaySeconds.floatValue,
                delayLabels = delayLabels.value
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        audioStartJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        tempAudioFile?.delete()
        tempAudioFile = null
        ballManager.clearAll()
    }
}
