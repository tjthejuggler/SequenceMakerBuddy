package com.example.sequencemakerbuddy.ball

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.sequencemakerbuddy.model.BallSequence
import com.example.sequencemakerbuddy.model.SequenceBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Represents a discovered LTX juggling ball on the network.
 */
data class DiscoveredBall(
    val ip: String,
    var lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Manages discovery, connection state, and communication with LTX juggling balls.
 *
 * Ball discovery works by listening for UDP broadcast packets on port 41412
 * that contain the identifier "NPLAYLTXBALL". Discovered balls are automatically
 * assigned to the 3 ball slots in discovery order.
 *
 * Ported from the Python ball_manager.py used in the main Sequence Maker app.
 */
class BallManager(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "BallManager"
        private const val BALL_CONTROL_PORT = 41412
        private const val BALL_BROADCAST_IDENTIFIER = "NPLAYLTXBALL"
        private const val DISCOVERY_TIMEOUT_MS = 5000L
        private const val LOST_CHECK_INTERVAL_MS = 1000L
        private const val MULTICAST_LOCK_TAG = "SequenceMakerBuddy.BallDiscovery"
    }

    // Application context (set via attachContext) used to acquire a Wi-Fi
    // MulticastLock. On Android the Wi-Fi chipset filters out broadcast and
    // multicast packets unless a MulticastLock is held -- without it the
    // LTX balls' UDP broadcasts are silently dropped, which is why the scan
    // button "sometimes" works after many taps.
    private var appContext: Context? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Attach an application context so the manager can acquire a Wi-Fi
     * MulticastLock during ball discovery. Must be called once (e.g. from
     * the ViewModel) before [startScanning].
     */
    fun attachContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    // 3 ball slots - each can be connected to a real ball or empty
    private val _ballSlots = MutableStateFlow<List<DiscoveredBall?>>(listOf(null, null, null))
    val ballSlots: StateFlow<List<DiscoveredBall?>> = _ballSlots

    // All discovered balls (IP -> DiscoveredBall)
    private val discoveredBalls = mutableMapOf<String, DiscoveredBall>()

    // Discovery state
    private var discoveryJob: Job? = null
    private var lostCheckJob: Job? = null
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    // Upload state
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val _uploadStatus = MutableStateFlow("")
    val uploadStatus: StateFlow<String> = _uploadStatus

    /**
     * Start scanning for balls on the network.
     * Listens for UDP broadcast packets from balls on port 41412.
     */
    fun startScanning() {
        if (_isScanning.value) return

        _isScanning.value = true
        Log.i(TAG, "Starting ball discovery scan")

        // Acquire MulticastLock so the Wi-Fi chipset stops filtering out
        // broadcast packets. Despite the name, this lock is required for
        // *broadcast* reception too on Android.
        acquireMulticastLock()

        // Discovery listener
        discoveryJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                // Mirror the Python implementation:
                //   sock = socket(AF_INET, SOCK_DGRAM)
                //   sock.setsockopt(SO_REUSEADDR, 1)
                //   sock.bind(('', 41412))
                // Order matters: SO_REUSEADDR must be set BEFORE bind().
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(BALL_CONTROL_PORT))
                    soTimeout = 1000
                }
                Log.i(TAG, "Discovery socket bound to *:$BALL_CONTROL_PORT (broadcast=${socket.broadcast})")

                val buf = ByteArray(1024)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)

                        val data = String(packet.data, packet.offset, packet.length, Charsets.ISO_8859_1)
                        if (BALL_BROADCAST_IDENTIFIER in data) {
                            val ballIp = packet.address.hostAddress ?: continue
                            onBallDiscovered(ballIp)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Normal - just loop again
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.w(TAG, "Discovery receive error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery socket error: ${e.message}")
            } finally {
                socket?.close()
            }
        }

        // Lost-ball checker
        lostCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(LOST_CHECK_INTERVAL_MS)
                checkForLostBalls()
            }
        }
    }

    /**
     * Stop scanning for balls.
     */
    fun stopScanning() {
        if (!_isScanning.value) return

        _isScanning.value = false
        discoveryJob?.cancel()
        lostCheckJob?.cancel()
        releaseMulticastLock()
        Log.i(TAG, "Stopped ball discovery scan")
    }

    /**
     * Acquire a Wi-Fi MulticastLock so broadcast/multicast packets reach the
     * socket. Safe to call multiple times.
     */
    private fun acquireMulticastLock() {
        val ctx = appContext
        if (ctx == null) {
            Log.w(TAG, "acquireMulticastLock: no Context attached -- broadcast " +
                "packets may be filtered by Wi-Fi. Call attachContext() first.")
            return
        }
        if (multicastLock?.isHeld == true) return
        try {
            val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifi == null) {
                Log.w(TAG, "acquireMulticastLock: WifiManager unavailable")
                return
            }
            val lock = wifi.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
            Log.i(TAG, "MulticastLock acquired (held=${lock.isHeld})")
        } catch (e: Exception) {
            Log.e(TAG, "acquireMulticastLock failed: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
                Log.i(TAG, "MulticastLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "releaseMulticastLock: ${e.message}")
        } finally {
            multicastLock = null
        }
    }

    /**
     * Handle a newly discovered ball.
     */
    private suspend fun onBallDiscovered(ip: String) {
        val isNew = !discoveredBalls.containsKey(ip)
        discoveredBalls[ip] = DiscoveredBall(ip)

        if (isNew) {
            Log.i(TAG, "Discovered ball: $ip")
            autoAssignSlots()
        }
    }

    /**
     * Check for balls that haven't been seen recently and remove them.
     */
    private suspend fun checkForLostBalls() {
        val now = System.currentTimeMillis()
        val lostIps = discoveredBalls.entries
            .filter { now - it.value.lastSeenMs > DISCOVERY_TIMEOUT_MS }
            .map { it.key }

        for (ip in lostIps) {
            discoveredBalls.remove(ip)
            Log.i(TAG, "Ball lost: $ip")
        }

        if (lostIps.isNotEmpty()) {
            autoAssignSlots()
        }
    }

    /**
     * Auto-assign discovered balls to the 3 slots in discovery order.
     * 1st discovered → Ball 1 (slot 0)
     * 2nd discovered → Ball 2 (slot 1)
     * 3rd discovered → Ball 3 (slot 2)
     */
    private suspend fun autoAssignSlots() {
        val ips = discoveredBalls.keys.toList()
        val newSlots = mutableListOf<DiscoveredBall?>()

        for (i in 0..2) {
            newSlots.add(if (i < ips.size) discoveredBalls[ips[i]] else null)
        }

        withContext(Dispatchers.Main) {
            _ballSlots.value = newSlots
        }
    }

    /**
     * Get the list of IPs for connected balls (non-empty slots).
     */
    fun getConnectedIps(): List<String> {
        return _ballSlots.value.mapNotNull { it?.ip }
    }

    /**
     * Check if a specific ball slot has a real ball connected.
     */
    fun isBallConnected(slotIndex: Int): Boolean {
        return slotIndex in _ballSlots.value.indices && _ballSlots.value[slotIndex] != null
    }

    /**
     * Get the IP address for a ball slot, or null if not connected.
     */
    fun getBallIp(slotIndex: Int): String? {
        return if (slotIndex in _ballSlots.value.indices) _ballSlots.value[slotIndex]?.ip else null
    }

    /**
     * Generate PRG files for each ball in the sequence and upload them
     * to the connected real balls.
     *
     * @param bundle The loaded sequence bundle containing up to 3 ball sequences.
     * @return Map of slot index to upload success/failure.
     */
    suspend fun uploadSequences(bundle: SequenceBundle): Map<Int, Boolean> {
        _isUploading.value = true
        val results = mutableMapOf<Int, Boolean>()

        try {
            for (i in bundle.balls.indices) {
                val ballIp = getBallIp(i)
                if (ballIp == null) {
                    Log.w(TAG, "uploadSequences: slot $i has no ball connected, skipping")
                    continue
                }

                val ball = bundle.balls[i]
                val filename = "Ball_${i + 1}.prg"

                _uploadStatus.value = "Generating PRG for ${ball.name}..."

                val prgBytes = withContext(Dispatchers.Default) {
                    PrgGenerator.generatePrg(ball)
                }

                _uploadStatus.value = "Uploading ${ball.name} to $ballIp..."

                val success = withContext(Dispatchers.IO) {
                    LtxBallClient.uploadPrg(ballIp, prgBytes, filename)
                }

                results[i] = success
                Log.i(TAG, "Upload slot $i ($ballIp): ${if (success) "OK" else "FAILED"}")
            }

            _uploadStatus.value = if (results.isEmpty()) {
                "No balls connected to upload to"
            } else if (results.all { it.value }) {
                "Upload complete ✓"
            } else {
                "Upload completed with errors"
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadSequences error: ${e.message}")
            _uploadStatus.value = "Upload error: ${e.message}"
        } finally {
            _isUploading.value = false
        }

        return results
    }

    /**
     * Send PLAY command to all connected balls.
     * Must be called from a coroutine (runs on IO dispatcher).
     */
    suspend fun playAllBalls(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val ips = getConnectedIps()
        if (ips.isEmpty()) {
            Log.w(TAG, "playAllBalls: no connected balls")
            return@withContext emptyMap<String, Boolean>()
        }

        val results = mutableMapOf<String, Boolean>()
        for (ip in ips) {
            results[ip] = LtxBallClient.sendPlay(ip)
        }
        results
    }

    /**
     * Send STOP command to all connected balls.
     * Must be called from a coroutine (runs on IO dispatcher).
     */
    suspend fun stopAllBalls(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val ips = getConnectedIps()
        if (ips.isEmpty()) return@withContext emptyMap<String, Boolean>()

        val results = mutableMapOf<String, Boolean>()
        for (ip in ips) {
            results[ip] = LtxBallClient.sendStop(ip)
        }
        results
    }

    /**
     * Clear all ball connections (e.g. when disconnecting).
     */
    fun clearAll() {
        stopScanning()
        discoveredBalls.clear()
        _ballSlots.value = listOf(null, null, null)
    }
}
