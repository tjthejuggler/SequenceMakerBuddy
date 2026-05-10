package com.example.sequencemakerbuddy.ball

import android.util.Log
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom

/**
 * Pure-protocol client for talking to LTX juggling balls over WiFi.
 * Ported from the Python ltx_ball_client.py used in the main Sequence Maker app.
 *
 * Implements three operations:
 * 1. uploadPrg() -- TCP/8888 upload of a .prg sequence file
 * 2. sendPlay()  -- UDP/41412 PLAY frame
 * 3. sendStop()  -- UDP/41412 STOP frame
 *
 * Protocol summary (reverse-engineered 2026-05-06):
 *
 * UPLOAD (TCP, port 8888):
 *   bytes [0..3]   = 00 00 00 00       (4 NUL bytes)
 *   bytes [4..7]   = uint32 LE         file size in bytes (PRG body length)
 *   bytes [8..11]  = 4 random bytes    nonce
 *   byte  [12]     = 16 + filename_len (header length up to & inc. NUL)
 *   bytes [13..14] = 00 00             (2 NUL bytes)
 *   byte  [15]     = 00                (NUL separator)
 *   bytes [16..]   = filename ASCII    (NO trailing NUL; PRG body follows immediately)
 *   bytes [16+L..] = raw PRG body      (exactly file_size bytes)
 *
 * PLAY/STOP (UDP, dst port 41412, src port 41413):
 *   9 bytes: 42 00 00 00 00 <SEQ> 00 00 <ACTION>
 *   where SEQ is a 1-byte monotonically incrementing counter (mod 256)
 *   and ACTION is 0x01=PLAY, 0x02=STOP.
 */
object LtxBallClient {
    private const val TAG = "LtxBallClient"

    // Network constants
    const val BALL_TCP_UPLOAD_PORT = 8888
    const val BALL_UDP_CONTROL_PORT = 41412
    const val APP_UDP_SOURCE_PORT = 41413

    // Protocol constants
    private const val PLAY_ACTION: Byte = 0x01
    private const val STOP_ACTION: Byte = 0x02
    private const val COMMAND_OPCODE: Byte = 0x42
    private const val PREFIX_BASE_LEN = 16

    // Monotonic sequence counter (shared across all balls)
    private var seqCounter = 0

    private fun nextSeq(): Int {
        seqCounter = (seqCounter + 1) and 0xFF
        if (seqCounter == 0) seqCounter = 1 // Avoid seq=0
        return seqCounter
    }

    /**
     * Build the full byte stream for a PRG upload.
     *
     * @param prgBytes Raw contents of the .prg file.
     * @param filename Filename to store on the ball (ASCII; e.g. "Ball_1.prg").
     * @return Bytes ready to write to the TCP socket.
     */
    fun buildUploadPayload(prgBytes: ByteArray, filename: String): ByteArray {
        require(prgBytes.isNotEmpty()) { "prgBytes is empty" }
        require(filename.isNotEmpty()) { "filename is empty" }

        val filenameBytes = filename.toByteArray(Charsets.US_ASCII)
        require(filenameBytes.size <= 255 - PREFIX_BASE_LEN) {
            "filename too long (${filenameBytes.size} bytes)"
        }

        val nonce = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val byte12 = (PREFIX_BASE_LEN + filenameBytes.size).toByte()

        // 16-byte fixed-shape prefix
        val prefix = ByteArray(PREFIX_BASE_LEN)
        // [0..3] = 4 NUL bytes (already 0)
        // [4..7] = file size LE
        val fileSize = prgBytes.size
        prefix[4] = (fileSize and 0xFF).toByte()
        prefix[5] = ((fileSize shr 8) and 0xFF).toByte()
        prefix[6] = ((fileSize shr 16) and 0xFF).toByte()
        prefix[7] = ((fileSize shr 24) and 0xFF).toByte()
        // [8..11] = nonce
        System.arraycopy(nonce, 0, prefix, 8, 4)
        // [12] = header length
        prefix[12] = byte12
        // [13..14] = 00 00 (already 0)
        // [15] = 00 (already 0)

        val result = ByteArray(prefix.size + filenameBytes.size + prgBytes.size)
        System.arraycopy(prefix, 0, result, 0, prefix.size)
        System.arraycopy(filenameBytes, 0, result, prefix.size, filenameBytes.size)
        System.arraycopy(prgBytes, 0, result, prefix.size + filenameBytes.size, prgBytes.size)

        return result
    }

    /**
     * Upload a .prg file to a ball via TCP/8888.
     *
     * @param ballIp IPv4 address of the ball.
     * @param prgBytes Raw PRG file bytes.
     * @param filenameOnBall ASCII name to store on the ball (e.g. "Ball_1.prg").
     * @param timeoutMs TCP connect/send timeout in milliseconds.
     * @return true on send completion.
     */
    fun uploadPrg(
        ballIp: String,
        prgBytes: ByteArray,
        filenameOnBall: String,
        timeoutMs: Int = 15000
    ): Boolean {
        val payload = buildUploadPayload(prgBytes, filenameOnBall)
        Log.i(TAG, "uploadPrg: ip=$ballIp size=${prgBytes.size} name='$filenameOnBall' total=${payload.size}")

        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(java.net.InetSocketAddress(ballIp, BALL_TCP_UPLOAD_PORT), timeoutMs)
            socket.soTimeout = 2000
            socket.getOutputStream().apply {
                write(payload)
                flush()
            }
            // Half-close so the ball knows we're done sending
            try {
                socket.shutdownOutput()
            } catch (_: Exception) {}

            // Some firmwares send a tiny ack-shaped reply; read+discard
            try {
                val buf = ByteArray(64)
                socket.getInputStream().read(buf)
            } catch (_: Exception) {}

            return true
        } catch (e: Exception) {
            Log.e(TAG, "uploadPrg: failed to upload to $ballIp: ${e.message}")
            return false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Build the 9-byte UDP command frame.
     *
     * @param seq Sequence counter (any int; only low byte is used).
     * @param action 0x01 PLAY, 0x02 STOP.
     */
    fun buildCommandFrame(seq: Int, action: Byte): ByteArray {
        return byteArrayOf(
            COMMAND_OPCODE,  // 0x42
            0x00, 0x00, 0x00, 0x00,
            (seq and 0xFF).toByte(),
            0x00, 0x00,
            action
        )
    }

    /**
     * Send a PLAY command to a ball via UDP/41412.
     *
     * @param ballIp IPv4 address of the ball.
     * @return true if the packet was sent successfully.
     */
    fun sendPlay(ballIp: String): Boolean {
        return sendCommand(ballIp, PLAY_ACTION)
    }

    /**
     * Send a STOP command to a ball via UDP/41412.
     *
     * @param ballIp IPv4 address of the ball.
     * @return true if the packet was sent successfully.
     */
    fun sendStop(ballIp: String): Boolean {
        return sendCommand(ballIp, STOP_ACTION)
    }

    /**
     * Send a command to a ball via UDP.
     */
    private fun sendCommand(ballIp: String, action: Byte): Boolean {
        val seq = nextSeq()
        val frame = buildCommandFrame(seq, action)
        val actionName = if (action == PLAY_ACTION) "PLAY" else "STOP"

        var socket: DatagramSocket? = null
        try {
            // Try to bind to the official source port 41413
            socket = try {
                DatagramSocket(APP_UDP_SOURCE_PORT)
            } catch (_: Exception) {
                DatagramSocket() // Fall back to ephemeral port
            }

            val address = InetAddress.getByName(ballIp)
            val packet = DatagramPacket(frame, frame.size, address, BALL_UDP_CONTROL_PORT)
            socket.send(packet)

            Log.i(TAG, "$actionName ip=$ballIp seq=$seq ok=true")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "$actionName ip=$ballIp seq=$seq ok=false: ${e.message}")
            return false
        } finally {
            socket?.close()
        }
    }
}
