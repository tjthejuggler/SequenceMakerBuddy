package com.example.sequencemakerbuddy.player

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sequencemakerbuddy.model.BundleParseResult
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
 * ViewModel that manages sequence playback synced with audio.
 * Updates ball colors at 100Hz (every 10ms) based on the loaded sequence data.
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

    // File browser state
    var smbuddyFiles = mutableStateOf<List<SmbuddyFileEntry>>(emptyList())
        private set
    var showFileBrowser = mutableStateOf(false)
        private set
    var showSettings = mutableStateOf(false)
        private set
    var folderConfigured = mutableStateOf(false)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private var tempAudioFile: File? = null

    /**
     * Initialize folder state from settings.
     */
    fun initSettings(context: Context) {
        val settings = SettingsManager(context)
        folderConfigured.value = settings.hasFolderConfigured()
    }

    /**
     * Load a .smbuddy ZIP bundle from a URI.
     * Extracts both the sequence JSON and the audio file from the ZIP.
     */
    fun loadBundle(context: Context, uri: Uri) {
        try {
            stop()

            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val result: BundleParseResult = SequenceBundle.fromZipInputStream(inputStream)
            inputStream.close()

            bundle.value = result.bundle
            projectName.value = result.bundle.projectName
            sequenceLoaded.value = true

            // Compute total duration from sequence data (max centisecond key -> ms)
            val maxCentiseconds = result.bundle.balls
                .flatMap { it.sortedTimes }
                .maxOrNull() ?: 0
            totalDurationMs.intValue = maxCentiseconds * 10

            // Set initial colors from time 0
            updateBallColors(0)

            // Load audio from the bundle if present
            if (result.audioBytes != null && result.audioFilename != null) {
                loadAudioFromBytes(context, result.audioBytes, result.audioFilename)
            } else {
                audioLoaded.value = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            projectName.value = "Error loading bundle"
        }
    }

    /**
     * Load audio from raw bytes extracted from the ZIP bundle.
     * Writes to a temp file since MediaPlayer needs a file/URI.
     */
    private fun loadAudioFromBytes(context: Context, audioBytes: ByteArray, filename: String) {
        try {
            mediaPlayer?.release()

            // Clean up previous temp file
            tempAudioFile?.delete()

            // Write audio bytes to a temp file
            val tempFile = File(context.cacheDir, "smbuddy_audio_$filename")
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            tempAudioFile = tempFile

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
            }
            audioLoaded.value = true

            // Use audio duration if longer than sequence duration
            val audioDurationMs = mediaPlayer?.duration ?: 0
            if (audioDurationMs > totalDurationMs.intValue) {
                totalDurationMs.intValue = audioDurationMs
            }
        } catch (e: Exception) {
            e.printStackTrace()
            audioLoaded.value = false
        }
    }

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

    /**
     * Start synchronized playback of audio + sequence.
     */
    fun play() {
        if (bundle.value == null) return
        if (isPlaying.value) return

        isPlaying.value = true
        mediaPlayer?.start()

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
     */
    fun pause() {
        isPlaying.value = false
        playbackJob?.cancel()
        mediaPlayer?.pause()
    }

    /**
     * Stop and reset to beginning.
     */
    fun stop() {
        isPlaying.value = false
        playbackJob?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.prepare()
        }
        currentTimeMs.intValue = 0
        updateBallColors(0)
    }

    /**
     * Seek to a specific time in milliseconds.
     * Updates ball colors and audio position.
     */
    fun seekTo(timeMs: Int) {
        val clampedMs = timeMs.coerceIn(0, totalDurationMs.intValue)
        currentTimeMs.intValue = clampedMs
        mediaPlayer?.seekTo(clampedMs)
        updateBallColors(clampedMs / 10)

        // If currently playing, restart the playback loop from the new position
        if (isPlaying.value) {
            playbackJob?.cancel()
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

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        tempAudioFile?.delete()
        tempAudioFile = null
    }
}
