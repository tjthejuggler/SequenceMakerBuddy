package com.example.sequencemakerbuddy.ball

import android.graphics.Color
import com.example.sequencemakerbuddy.model.BallSequence
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Generates PRG binary files for LTX juggling balls from BallSequence data.
 * Ported from the Python prg_generator.py (v7) used in the main Sequence Maker app.
 *
 * The PRG format is a binary format with:
 * - 8-byte file signature
 * - 32-byte header
 * - N duration blocks (19 bytes each)
 * - RGB data (100 triples per solid segment)
 * - 6-byte footer
 *
 * Always outputs at 100Hz refresh rate. The buddy format only stores solid
 * colors (no fades), so all segments are treated as solid.
 */
object PrgGenerator {

    // File format constants
    private val FILE_SIGNATURE = byteArrayOf(0x50, 0x52, 0x03, 0x49, 0x4E, 0x05, 0x00, 0x00)
    private val HEADER_CONST_0A = byteArrayOf(0x00, 0x08)
    private val HEADER_CONST_PI = byteArrayOf(0x50, 0x49) // "PI"
    private val HEADER_CONST_1C = byteArrayOf(0x00, 0x00)
    private val BLOCK_CONST_02 = byteArrayOf(0x01, 0x00, 0x00)
    private val BLOCK_CONST_07 = byteArrayOf(0x00, 0x00)
    private val LAST_BLOCK_CONST_09 = byteArrayOf(0x43, 0x44) // "CD"
    private val FOOTER = byteArrayOf(0x42, 0x54, 0x00, 0x00, 0x00, 0x00) // "BT"

    private const val RGB_TRIPLE_COUNT = 100
    private const val DURATION_BLOCK_SIZE = 19
    private const val HEADER_SIZE = 32
    private const val TARGET_PRG_REFRESH_RATE = 100
    private const val NOMINAL_BASE = 100
    private const val MAX_SEGMENT_DURATION = 65535

    /** A single solid color segment in the sequence. */
    private data class Segment(
        val duration: Int,                    // Duration in PRG time units (centiseconds)
        val color: Triple<Int, Int, Int>,     // RGB color (0-255 each)
        val pixels: Int                       // Number of pixels (1-4)
    )

    /**
     * Generate a PRG file from a BallSequence.
     * Returns the raw PRG bytes ready to upload to a ball.
     */
    fun generatePrg(ball: BallSequence): ByteArray {
        val segments = ballSequenceToSegments(ball)
        val splitSegments = splitLongSegments(segments)
        return generatePrgFromSegments(splitSegments, ball.defaultPixels)
    }

    /**
     * Convert a BallSequence to a list of solid color segments.
     * Each consecutive pair of time keys forms one segment.
     * The last segment gets a default duration of 100 (1 second).
     */
    private fun ballSequenceToSegments(ball: BallSequence): List<Segment> {
        if (ball.sortedTimes.isEmpty()) {
            return listOf(Segment(100, Triple(0, 0, 0), ball.defaultPixels))
        }

        val segments = mutableListOf<Segment>()
        val times = ball.sortedTimes

        for (i in times.indices) {
            val time = times[i]
            val colorInt = ball.colorAtTime[time] ?: Color.BLACK
            val r = Color.red(colorInt)
            val g = Color.green(colorInt)
            val b = Color.blue(colorInt)

            val duration = if (i + 1 < times.size) {
                times[i + 1] - time
            } else {
                100 // Default 1 second for last segment
            }

            if (duration > 0) {
                segments.add(Segment(duration, Triple(r, g, b), ball.defaultPixels))
            }
        }

        return if (segments.isEmpty()) {
            listOf(Segment(100, Triple(0, 0, 0), ball.defaultPixels))
        } else {
            segments
        }
    }

    /**
     * Split segments with durations exceeding 65535 PRG units.
     * Required by the PRG format (16-bit duration field).
     */
    private fun splitLongSegments(segments: List<Segment>): List<Segment> {
        val result = mutableListOf<Segment>()
        for (seg in segments) {
            if (seg.duration <= MAX_SEGMENT_DURATION) {
                result.add(seg)
                continue
            }
            var remaining = seg.duration
            while (remaining > 0) {
                val chunkDur = min(remaining, MAX_SEGMENT_DURATION)
                result.add(seg.copy(duration = chunkDur))
                remaining -= chunkDur
            }
        }
        return result
    }

    /**
     * Generate the PRG binary from a list of segments.
     * All segments are treated as solid (no fades) since the buddy format
     * doesn't preserve fade information.
     */
    private fun generatePrgFromSegments(segments: List<Segment>, defaultPixels: Int): ByteArray {
        val n = segments.size
        require(n > 0) { "Cannot generate PRG with no segments" }

        val output = ByteArrayOutputStream()

        // --- Header (32 bytes) ---
        output.write(FILE_SIGNATURE)
        writeU16BE(output, defaultPixels)
        output.write(HEADER_CONST_0A)
        writeU16LE(output, TARGET_PRG_REFRESH_RATE)
        output.write(HEADER_CONST_PI)

        val pointer1 = 21 + 19 * (n - 1)
        writeU32LE(output, pointer1)
        writeU16LE(output, n)

        // Header fields depend on first segment
        val firstDur = segments[0].duration
        val headerField16 = floor(firstDur.toDouble() / NOMINAL_BASE).toInt() and 0xFFFF
        val headerField18 = NOMINAL_BASE and 0xFFFF
        val headerField1E = (firstDur % NOMINAL_BASE) and 0xFFFF

        writeU16LE(output, headerField16)
        writeU16LE(output, headerField18)

        val rgbStartPointer = HEADER_SIZE + n * DURATION_BLOCK_SIZE
        writeU16LE(output, rgbStartPointer)
        output.write(HEADER_CONST_1C)
        writeU16LE(output, headerField1E)

        check(output.size() == HEADER_SIZE) { "Header size mismatch: ${output.size()}" }

        // --- Duration Blocks ---
        // All segments are solid, so each contributes RGB_TRIPLE_COUNT (100) triples
        val triplesPerSegment = segments.map { RGB_TRIPLE_COUNT }
        val totalRgbTriples = triplesPerSegment.sum()

        // Cumulative triples before each block
        val cumTriples = mutableListOf<Int>()
        var running = 0
        for (t in triplesPerSegment) {
            cumTriples.add(running)
            running += t
        }

        for (i in segments.indices) {
            val seg = segments[i]
            val isLast = (i == n - 1)

            writeU16LE(output, seg.pixels)
            output.write(BLOCK_CONST_02)
            writeU16LE(output, seg.duration)
            output.write(BLOCK_CONST_07)

            if (!isLast) {
                // Non-last block: look ahead at next segment
                val nextDur = segments[i + 1].duration

                val field09Part1 = floor(nextDur.toDouble() / NOMINAL_BASE).toInt() and 0xFFFF
                val field09Part2 = NOMINAL_BASE and 0xFFFF
                val field11 = (nextDur % NOMINAL_BASE) and 0xFFFF

                // idx1: offset where this block's RGB data ends
                val triplesThroughSelf = cumTriples[i] + triplesPerSegment[i]
                val index1Full = rgbStartPointer + 3 * triplesThroughSelf

                writeU16LE(output, field09Part1)
                writeU16LE(output, field09Part2)
                writeU16LE(output, index1Full and 0xFFFF)
                writeU16LE(output, (index1Full shr 16) and 0xFFFF)
                writeU16LE(output, field11)
            } else {
                // Last block: "CD" marker + idx2
                val index2Part1 = 4 + 3 * totalRgbTriples
                val index2Part2 = totalRgbTriples

                output.write(LAST_BLOCK_CONST_09)
                writeU16LE(output, index2Part1 and 0xFFFF)
                writeU16LE(output, (index2Part1 shr 16) and 0xFFFF)
                writeU16LE(output, index2Part2 and 0xFFFF)
                writeU16LE(output, (index2Part2 shr 16) and 0xFFFF)
            }
        }

        // --- RGB Data ---
        for (seg in segments) {
            val (r, g, b) = seg.color
            val rgbByte = byteArrayOf(
                max(0, min(255, r)).toByte(),
                max(0, min(255, g)).toByte(),
                max(0, min(255, b)).toByte()
            )
            repeat(RGB_TRIPLE_COUNT) {
                output.write(rgbByte)
            }
        }

        // --- Footer ---
        output.write(FOOTER)

        return output.toByteArray()
    }

    // --- Byte writing helpers ---

    private fun writeU16LE(out: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort((value and 0xFFFF).toShort())
        out.write(buf.array())
    }

    private fun writeU16BE(out: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        buf.putShort((value and 0xFFFF).toShort())
        out.write(buf.array())
    }

    private fun writeU32LE(out: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(value)
        out.write(buf.array())
    }
}
