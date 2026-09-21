package com.faaa97.obdusbtotcpbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class TcpUsbBridgeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logs = CopyOnWriteArrayList<String>()
    private val clients = mutableListOf<Socket>()
    private lateinit var server: ServerSocket

    @Before
    fun setUp() {
        server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
    }

    @After
    fun tearDown() {
        scope.cancel()
        clients.forEach { try { it.close() } catch (e: IOException) {} }
        server.close()
    }

    private fun startBridge(link: SerialLink): Deferred<Unit> =
        scope.async { TcpUsbBridge(link, server, logs::add).run() }

    private fun connect(): Socket =
        Socket(InetAddress.getLoopbackAddress(), server.localPort).also {
            it.soTimeout = 5000
            clients.add(it)
        }

    private fun Socket.send(text: String) {
        getOutputStream().write(text.toByteArray())
        getOutputStream().flush()
    }

    /** Reads up to and including the ELM prompt, or until the connection closes. */
    private fun Socket.readUntilPrompt(): String {
        val text = StringBuilder()
        val input = getInputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) break
            text.append(byte.toChar())
            if (byte == '>'.code) break
        }
        return text.toString()
    }

    private suspend fun waitForLog(text: String) = withTimeout(5000) {
        while (!logs.contains(text)) delay(10)
    }

    private suspend fun Deferred<Unit>.awaitUsbFailure(): UsbFailure = withTimeout(5000) {
        try {
            await()
            fail("The bridge should have failed")
            throw AssertionError()
        } catch (e: UsbFailure) {
            e
        }
    }

    // ---------------- data flow ----------------

    @Test
    fun `sends a command to the device and the answer back`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("ELM327 v1.5\r\r>") }
        startBridge(link)

        connect().use { client ->
            client.send("ATZ\r")
            assertEquals("ELM327 v1.5\r\r>", client.readUntilPrompt())
        }

        assertEquals(listOf("ATZ\r"), link.written)
    }

    @Test
    fun `waits for the end of line before writing to the device`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("OK\r\r>") }
        startBridge(link)

        connect().use { client ->
            client.send("AT")
            delay(200)
            assertEquals(emptyList<String>(), link.written)

            client.send("Z\r")
            client.readUntilPrompt()
        }

        assertEquals(listOf("ATZ\r"), link.written)
    }

    @Test
    fun `waits for the prompt before answering the client`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("41 0C ", "1A F8\r\r", ">") }
        startBridge(link)

        connect().use { client ->
            client.send("010C\r")
            assertEquals("41 0C 1A F8\r\r>", client.readUntilPrompt())
        }
    }

    // ---------------- clients coming and going ----------------

    @Test
    fun `serves a new client after the previous one disconnects`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("OK\r\r>") }
        val bridge = startBridge(link)

        repeat(3) {
            connect().use { client ->
                client.send("ATE0\r")
                assertEquals("OK\r\r>", client.readUntilPrompt())
            }
            waitForLog("TCP client disconnected")
            logs.clear()
        }

        assertTrue("A client leaving must not stop the bridge", bridge.isActive)
    }

    @Test
    fun `discards device data left from a previous client`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("FRESH\r\r>") }
        startBridge(link)

        connect().use { client ->
            client.send("ATZ\r")
            client.readUntilPrompt()
        }
        waitForLog("TCP client disconnected")
        link.feed("STALE\r\r>") // Late answer nobody asked for

        connect().use { client ->
            client.send("ATZ\r")
            assertEquals("FRESH\r\r>", client.readUntilPrompt())
        }
    }

    // ---------------- device failures ----------------

    @Test
    fun `fails with UsbFailure when writing to the device fails`() = runBlocking<Unit> {
        val link = FakeSerialLink().apply { failWrites = true }
        val bridge = startBridge(link)

        connect().use { client ->
            client.send("ATZ\r")
            bridge.awaitUsbFailure()
        }
    }

    @Test
    fun `fails with UsbFailure when reading from the device fails`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("OK\r\r>") }
        val bridge = startBridge(link)

        connect().use { client ->
            client.send("ATZ\r")
            client.readUntilPrompt()

            link.failReads = true // e.g. the device was unplugged
            bridge.awaitUsbFailure()
        }
    }

    @Test
    fun `releases the server socket after a device failure`() = runBlocking<Unit> {
        val link = FakeSerialLink().apply { failReads = true }
        val bridge = startBridge(link)

        connect().use { bridge.awaitUsbFailure() }

        assertTrue(server.isClosed)
    }

    // ---------------- cancellation ----------------

    @Test
    fun `stops when cancelled while waiting for a client`() = runBlocking<Unit> {
        val bridge = startBridge(FakeSerialLink())
        delay(200) // Let it block on accept

        bridge.cancel()
        withTimeout(5000) { bridge.join() }

        assertTrue(bridge.isCancelled)
        assertTrue("The port must be free for the next run", server.isClosed)
    }

    @Test
    fun `stops and disconnects the client when cancelled during a session`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("OK\r\r>") }
        val bridge = startBridge(link)
        val client = connect()
        client.send("ATZ\r")
        client.readUntilPrompt()

        bridge.cancel()
        withTimeout(5000) { bridge.join() }

        assertTrue(server.isClosed)
        val closedByBridge = try {
            client.getInputStream().read() == -1
        } catch (e: IOException) {
            true // Connection reset
        }
        assertTrue(closedByBridge)
        assertFalse(bridge.isActive)
    }
}
