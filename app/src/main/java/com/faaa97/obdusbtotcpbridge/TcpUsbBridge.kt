package com.faaa97.obdusbtotcpbridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class UsbFailure(cause: Throwable) : Exception(cause.message, cause)

class TcpUsbBridge(
    private val link: SerialLink,
    private val server: ServerSocket,
    private val log: (String) -> Unit = {}
) {

    @Volatile private var activeClient: Socket? = null

    suspend fun run() {
        try {
            coroutineScope {
                val closer = closeOnCancel(server)
                try {
                    while (true) {
                        val client = try {
                            server.accept()
                        } catch (e: IOException) {
                            currentCoroutineContext().ensureActive() // Closed because of cancellation
                            throw e
                        }
                        activeClient = client
                        log("TCP client connected")
                        try {
                            serveClient(client)
                        } finally {
                            closeQuietly(client)
                            activeClient = null
                        }
                        log("TCP client disconnected")
                    }
                } finally {
                    closer.cancel()
                }
            }
        } finally {
            close()
        }
    }

    fun close() {
        closeQuietly(activeClient)
        closeQuietly(server)
    }

    private suspend fun serveClient(client: Socket) {
        val input = client.getInputStream()
        val output = client.getOutputStream()

        // Discard anything left from a previous client
        try {
            val trash = ByteArray(1024)
            while (link.read(trash, 50) > 0) { /* drain */ }
        } catch (e: Exception) {
            throw UsbFailure(e)
        }

        coroutineScope {
            val closer = closeOnCancel(client) // Unblocks the blocking socket read
            val tcpToUsb = launch { tcpToUsbLoop(input) }
            val usbToTcp = launch { usbToTcpLoop(output) }

            select<Unit> {
                tcpToUsb.onJoin { }
                usbToTcp.onJoin { }
            }
            // One side is done (client left): stop the other one. A UsbFailure never gets here,
            // it fails the scope and is rethrown by coroutineScope.
            closer.cancel()
            coroutineContext.cancelChildren()
        }
    }

    private suspend fun tcpToUsbLoop(input: InputStream) {
        val buffer = ByteArray(1024)
        val request = StringBuilder()

        while (currentCoroutineContext().isActive) {
            val len = try {
                input.read(buffer)
            } catch (e: IOException) {
                return // Client gone or socket closed
            }
            if (len == -1) return // Connection closed

            if (len > 0) {
                request.append(String(buffer, 0, len))
                if (request.contains("\r")) {
                    try {
                        link.write(request.toString().toByteArray(), 1000)
                    } catch (e: Exception) {
                        throw UsbFailure(e)
                    }
                    log("TCP → USB: $request")
                    request.clear()
                }
            }
        }
    }

    private suspend fun usbToTcpLoop(output: OutputStream) {
        val buffer = ByteArray(1024)
        val response = StringBuilder()

        while (currentCoroutineContext().isActive) {
            val len = try {
                link.read(buffer, 1000)
            } catch (e: Exception) {
                throw UsbFailure(e)
            }
            if (len < 0) throw UsbFailure(IOException("USB read returned $len"))

            if (len > 0) {
                response.append(String(buffer, 0, len))
                if (response.contains(">")) {
                    try {
                        output.write(response.toString().toByteArray())
                        output.flush()
                    } catch (e: IOException) {
                        return // Client gone
                    }
                    log("USB → TCP: $response")
                    response.clear()
                }
            }
        }
    }

    private fun CoroutineScope.closeOnCancel(closeable: Closeable): Job = launch {
        try {
            awaitCancellation()
        } finally {
            closeQuietly(closeable)
        }
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            // Ignore closure errors
        }
    }
}
