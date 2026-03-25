package com.example.sequencemakerbuddy.player

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sequencemakerbuddy.model.SequenceBundle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    var projectName = mutableStateOf("No sequence loaded")
        private set
    var audioLoaded = mutableStateOf(false)
        private set
    var sequenceLoaded = mutableStateOf(false)
        private set

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    /**
     * Load a .smbuddy sequence bundle from a URI.
     */
    fun loadSequence(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val parsed = SequenceBundle.fromInputStream(inputStream)
            inputStream.close()

            bundle.value = parsed
            projectName.value = parsed.projectName
            sequenceLoaded.value = true

            // Set initial colors from time 0
            updateBallColors(0)
        } catch (e: Exception) {
            e.printStackTrace()
            projectName.value = "Error loading sequence"
        }
    }

    /**
     * Load an audio file from a URI.
     */
    fun loadAudio(context: Context, uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
            }
            audioLoaded.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            audioLoaded.value = false
        }
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
    }
}
