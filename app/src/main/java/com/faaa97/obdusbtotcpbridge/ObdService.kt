package com.faaa97.obdusbtotcpbridge

import android.app.*
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "obd_channel"
        private const val TCP_PORT = 35000
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val STABLE_SESSION_MS = 60_000L
        var isRunning : Observable<Boolean> = Observable(false)
            private set
    }

    private class UsbFailure(cause: Throwable) : Exception(cause.message, cause)

    private var isAutoConnectEnabled = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var runJob: Job? = null

    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var sessionJob: Job? = null
    @Volatile private var usbPort: UsbSerialPort? = null
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (isAutoConnectEnabled) {
                        log("USB device attached")
                        wakeSignal.trySend(Unit)
                        ensureRunning()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached = getUsbDevice(intent)
                    val selected = USBDeviceSelection.selectedDeviceOption.value
                    if (detached == null || detached.deviceName == selected) {
                        log("USB device detached")
                        sessionJob?.cancel()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)

        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("EXTRA_AUTO_CONNECT", false) == true) {
            isAutoConnectEnabled = true
        }

        if (runJob?.isActive == true) {
            log("Service already running, ignoring request.")
            wakeSignal.trySend(Unit) // If it is waiting to retry, retry now
        } else {
            ensureRunning()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        serviceScope.cancel()
        runJob = null
        closeAll()
        log("Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- RUN LOOP ----------------

    private fun ensureRunning() {
        if (runJob?.isActive == true) return
        runJob = serviceScope.launch {
            try {
                runLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Run loop error: ${e.message}")
            }
        }
    }

    private suspend fun runLoop() {
        var backoff = MIN_BACKOFF_MS

        while (currentCoroutineContext().isActive) {
            val port = try {
                USBDeviceSelection.openAndVerifyDevice()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("USB error: ${e.message}")
                null
            }

            if (port == null) {
                if (!isAutoConnectEnabled) {
                    log("Can't continue, stopping")
                    stopSelf()
                    return
                }
            } else {
                usbPort = port
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    runSession(port)
                } finally {
                    closeQuietly(port)
                    usbPort = null
                }
                if (SystemClock.elapsedRealtime() - startedAt > STABLE_SESSION_MS) {
                    backoff = MIN_BACKOFF_MS
                }
            }

            log("Retrying in ${backoff / 1000}s")
            val woken = withTimeoutOrNull(backoff) { wakeSignal.receive(); true } == true
            backoff = if (woken) MIN_BACKOFF_MS else minOf(backoff * 2, MAX_BACKOFF_MS)
        }
    }

    private suspend fun runSession(port: UsbSerialPort) = coroutineScope {
        val job = launch {
            try {
                serve(port)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Bridge error: ${e.message}")
            }
        }
        sessionJob = job
        try {
            job.join()
        } finally {
            sessionJob = null
        }
    }

    // ---------------- TCP ----------------

    private suspend fun serve(port: UsbSerialPort) {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(TCP_PORT))
            serverSocket = server
            log("TCP listening on $TCP_PORT")

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
                            serveClient(client, port)
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
            closeQuietly(server)
            serverSocket = null
        }
    }

    private suspend fun serveClient(client: Socket, port: UsbSerialPort) {
        val input = client.getInputStream()
        val output = client.getOutputStream()

        // Discard anything left from a previous client
        try {
            val trash = ByteArray(1024)
            while (port.read(trash, 50) > 0) { /* drain */ }
        } catch (e: Exception) {
            throw UsbFailure(e)
        }

        coroutineScope {
            val closer = closeOnCancel(client) // Unblocks the blocking socket read
            val tcpToUsb = launch { tcpToUsbLoop(input, port) }
            val usbToTcp = launch { usbToTcpLoop(output, port) }

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

    private suspend fun tcpToUsbLoop(input: java.io.InputStream, port: UsbSerialPort) {
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
                        port.write(request.toString().toByteArray(), 1000)
                    } catch (e: Exception) {
                        throw UsbFailure(e)
                    }
                    log("TCP → USB: $request")
                    request.clear()
                }
            }
        }
    }

    private suspend fun usbToTcpLoop(output: java.io.OutputStream, port: UsbSerialPort) {
        val buffer = ByteArray(1024)
        val response = StringBuilder()

        while (currentCoroutineContext().isActive) {
            val len = try {
                port.read(buffer, 1000)
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

    // ---------------- CLEANUP ----------------

    private fun CoroutineScope.closeOnCancel(closeable: Closeable): Job = launch {
        try {
            awaitCancellation()
        } finally {
            closeQuietly(closeable)
        }
    }

    private fun closeAll() {
        closeQuietly(activeClient)
        closeQuietly(serverSocket)
        closeQuietly(usbPort)
        activeClient = null
        serverSocket = null
        usbPort = null
    }

    private fun closeQuietly(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            // Ignore closure errors
        }
    }

    private fun getUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    // ---------------- LOGGING ----------------

    private fun log(msg: String) {
        ObdLogBus.log(msg)
    }

    // ---------------- FOREGROUND ----------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OBD Bridge",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OBD Bridge running")
            .setSmallIcon(android.R.drawable.stat_notify_sdcard_usb)
            .build()
    }
}
