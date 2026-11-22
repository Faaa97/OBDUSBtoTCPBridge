package com.faaa97.obdusbtotcpbridge.mock

import com.faaa97.obdusbtotcpbridge.ObdService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Plays the part of an OBD2 app: connects to the bridge on 127.0.0.1, sets the adapter up, polls
 * RPM, speed, coolant and throttle for a while, disconnects and starts over. Keeps the log and the
 * status moving without installing a real OBD2 app. Only in the `mock` build.
 *
 * The bridge serves one client at a time, so turn this off (see docs/BUILDS.md) to try a real app.
 */
class DemoClient(private val scope: CoroutineScope) {

    companion object {
        private const val RETRY_MS = 5_000L        // Bridge not listening yet
        private const val PAUSE_MS = 10_000L       // Between sessions, leaves room for a real app
        private const val POLL_MS = 1_500L
        private const val ROUNDS_PER_SESSION = 20
        private val SETUP = listOf("ATZ", "ATE0", "ATL0", "ATS1", "ATSP0", "0100")
        private val POLLED = listOf("010C", "010D", "0105", "0111")
    }

    @Volatile private var job: Job? = null
    @Volatile private var socket: Socket? = null

    val isEnabled: Boolean get() = job?.isActive == true

    fun setEnabled(enabled: Boolean) {
        if (enabled == isEnabled) return
        if (enabled) {
            job = scope.launch { loop() }
        } else {
            job?.cancel()
            job = null
            try { socket?.close() } catch (e: Exception) {} // Unblocks a blocking read
        }
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val waitMs = try {
                Socket().use { s ->
                    socket = s
                    s.connect(InetSocketAddress("127.0.0.1", ObdService.TCP_PORT), 2_000)
                    s.soTimeout = 5_000
                    session(s)
                }
                PAUSE_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RETRY_MS // Not listening, or the bridge dropped us: try again later
            } finally {
                socket = null
            }
            delay(waitMs)
        }
    }

    private suspend fun session(s: Socket) {
        val input = s.getInputStream()
        val output = s.getOutputStream()
        suspend fun ask(command: String) {
            output.write("$command\r".toByteArray())
            output.flush()
            readUntilPrompt(input)
        }
        SETUP.forEach { ask(it) }
        repeat(ROUNDS_PER_SESSION) {
            POLLED.forEach { ask(it) }
            delay(POLL_MS)
        }
    }

    private fun readUntilPrompt(input: InputStream) {
        val buffer = ByteArray(256)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) throw java.io.EOFException("Bridge closed the connection")
            if (String(buffer, 0, n).contains('>')) return
        }
    }
}
