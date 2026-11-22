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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.ServerSocket

class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "obd_channel"
        const val TCP_PORT = 35000
        const val EXTRA_AUTO_CONNECT = "EXTRA_AUTO_CONNECT"
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val STABLE_SESSION_MS = 60_000L
        var isRunning : Observable<Boolean> = Observable(false)
            private set
    }

    private var isAutoConnectEnabled = false

    // Last resort: anything that escapes a coroutine is logged instead of crashing the process.
    private val crashGuard = CoroutineExceptionHandler { _, e -> log("Unexpected error: $e") }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

    private var runJob: Job? = null

    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var sessionJob: Job? = null
    @Volatile private var adapter: OpenAdapter? = null
    @Volatile private var bridge: TcpUsbBridge? = null

    private val adapterSource: AdapterSource get() = Adapters.source

    // Attach/detach of an adapter that sends no USB broadcast (the mock build's simulated one).
    // Same reaction as the USB broadcasts below.
    private val adapterChangeObserver: (Int) -> Unit = {
        try {
            val open = adapter
            if (open != null && adapterSource.findAdapter()?.id != open.id) {
                log("Adapter detached")
                sessionJob?.cancel()
            } else if (open == null && isAutoConnectEnabled && adapterSource.findAdapter() != null) {
                log("Adapter attached")
                wakeSignal.trySend(Unit)
                ensureRunning()
            }
        } catch (e: Exception) {
            log("Adapter change error: ${e.message}")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
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
                        val selected = adapterSource.selectedId.value
                        if (detached == null || detached.deviceName == selected) {
                            log("USB device detached")
                            sessionJob?.cancel()
                        }
                    }
                }
            } catch (e: Exception) {
                log("USB broadcast error: ${e.message}")
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
        try {
            registerReceiver(usbReceiver, filter)
            adapterSource.changes.addObserver(adapterChangeObserver)
        } catch (e: Exception) {
            log("Couldn't watch USB changes: ${e.message}")
        }

        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val isStickyRestart = intent == null
            if (isStickyRestart) log("Service restarted by the system")
            isAutoConnectEnabled = StartPolicy.autoConnect(
                alreadyEnabled = isAutoConnectEnabled,
                requested = intent?.getBooleanExtra(EXTRA_AUTO_CONNECT, false) == true,
                isStickyRestart = isStickyRestart
            )

            if (runJob?.isActive == true) {
                log("Service already running, ignoring request.")
                wakeSignal.trySend(Unit) // If it is waiting to retry, retry now
            } else {
                ensureRunning()
            }
        } catch (e: Exception) {
            log("Start error: ${e.message}")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.value = false
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        adapterSource.changes.removeObserver(adapterChangeObserver)
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
        val backoff = Backoff(MIN_BACKOFF_MS, MAX_BACKOFF_MS)

        while (currentCoroutineContext().isActive) {
            val opened = try {
                adapterSource.openAndVerify()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("USB error: ${e.message}")
                null
            }

            if (opened == null) {
                if (!isAutoConnectEnabled) {
                    log("Can't continue, stopping")
                    stopSelf()
                    return
                }
            } else {
                adapter = opened
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    runSession(opened)
                } finally {
                    closeQuietly(opened)
                    adapter = null
                }
                if (SystemClock.elapsedRealtime() - startedAt > STABLE_SESSION_MS) {
                    backoff.reset()
                }
            }

            log("Retrying in ${backoff.delayMs / 1000}s")
            val woken = withTimeoutOrNull(backoff.delayMs) { wakeSignal.receive(); true } == true
            if (woken) backoff.reset() else backoff.increase()
        }
    }

    private suspend fun runSession(opened: OpenAdapter) = coroutineScope {
        val job = launch {
            try {
                serve(opened)
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

    private suspend fun serve(opened: OpenAdapter) {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(TCP_PORT))
        } catch (e: Exception) {
            closeQuietly(server)
            throw e
        }

        val newBridge = TcpUsbBridge(opened.link, server, ::log)
        bridge = newBridge
        log("TCP listening on $TCP_PORT")
        try {
            newBridge.run()
        } finally {
            newBridge.close()
            bridge = null
        }
    }

    // ---------------- CLEANUP ----------------

    private fun closeAll() {
        bridge?.close()
        closeQuietly(adapter)
        bridge = null
        adapter = null
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
