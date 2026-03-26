package com.example.sequencemakerbuddy.model

import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Represents a single ball's color sequence.
 * The sequence maps time (in centiseconds at 100Hz) to RGB colors.
 */
data class BallSequence(
    val name: String,
    val defaultPixels: Int,
    /** Time (centiseconds) -> Color (as Android Color int) */
    val colorAtTime: Map<Int, Int>,
    /** Sorted list of time keys for efficient lookup */
    val sortedTimes: List<Int>
) {
    /**
     * Get the color at a given time in centiseconds.
     * Returns the color of the most recent keyframe at or before the given time.
     */
    fun getColorAt(centiseconds: Int): Int {
        if (sortedTimes.isEmpty()) return Color.BLACK

        // Binary search for the largest time <= centiseconds
        var low = 0
        var high = sortedTimes.size - 1
        var result = 0

        while (low <= high) {
            val mid = (low + high) / 2
            if (sortedTimes[mid] <= centiseconds) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return colorAtTime[sortedTimes[result]] ?: Color.BLACK
    }
}

/**
 * Result of parsing a .smbuddy ZIP bundle.
 * Contains the sequence data and optionally the raw audio bytes.
 */
data class BundleParseResult(
    val bundle: SequenceBundle,
    val audioBytes: ByteArray?,
    val audioFilename: String?
)

/**
 * Represents the full sequence bundle with all balls.
 */
data class SequenceBundle(
    val projectName: String,
    val audioFilename: String?,
    val refreshRate: Int,
    val balls: List<BallSequence>
) {
    companion object {
        /**
         * Parse a .smbuddy ZIP file from an InputStream.
         * The ZIP contains sequence.json and optionally an audio file.
         *
         * Returns a [BundleParseResult] with the parsed bundle and audio bytes.
         */
        fun fromZipInputStream(inputStream: InputStream): BundleParseResult {
            val zipInput = ZipInputStream(inputStream)
            var jsonString: String? = null
            var audioBytes: ByteArray? = null
            var audioFilename: String? = null

            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "sequence.json") {
                    jsonString = readEntryAsString(zipInput)
                } else if (name.startsWith("audio.")) {
                    audioFilename = name
                    audioBytes = readEntryAsBytes(zipInput)
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
            zipInput.close()

            if (jsonString == null) {
                throw IllegalArgumentException("No sequence.json found in .smbuddy bundle")
            }

            val bundle = fromJson(jsonString)
            return BundleParseResult(bundle, audioBytes, audioFilename)
        }

        /**
         * Parse a .smbuddy JSON string (for backward compatibility with v1 format).
         */
        fun fromJson(jsonString: String): SequenceBundle {
            val gson = Gson()
            val root = gson.fromJson(jsonString, JsonObject::class.java)

            val projectName = root.get("project_name")?.asString ?: "Unknown"
            val audioFilename = root.get("audio_filename")?.asString
            val refreshRate = root.get("refresh_rate")?.asInt ?: 100

            val ballsArray = root.getAsJsonArray("balls")
            val balls = mutableListOf<BallSequence>()

            for (ballElement in ballsArray) {
                val ballObj = ballElement.asJsonObject
                val name = ballObj.get("name")?.asString ?: "Ball"
                val defaultPixels = ballObj.get("default_pixels")?.asInt ?: 4

                val sequenceObj = ballObj.getAsJsonObject("sequence")
                val colorAtTime = mutableMapOf<Int, Int>()

                for ((timeKey, colorValue) in sequenceObj.entrySet()) {
                    val time = timeKey.toInt()
                    val colorArray = colorValue.asJsonArray
                    val r = colorArray[0].asInt
                    val g = colorArray[1].asInt
                    val b = colorArray[2].asInt
                    colorAtTime[time] = Color.rgb(r, g, b)
                }

                val sortedTimes = colorAtTime.keys.sorted()

                balls.add(
                    BallSequence(
                        name = name,
                        defaultPixels = defaultPixels,
                        colorAtTime = colorAtTime,
                        sortedTimes = sortedTimes
                    )
                )
            }

            return SequenceBundle(
                projectName = projectName,
                audioFilename = audioFilename,
                refreshRate = refreshRate,
                balls = balls
            )
        }

        private fun readEntryAsString(zipInput: ZipInputStream): String {
            return readEntryAsBytes(zipInput).toString(Charsets.UTF_8)
        }

        private fun readEntryAsBytes(zipInput: ZipInputStream): ByteArray {
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var len: Int
            while (zipInput.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
            }
            return output.toByteArray()
        }
    }
}
