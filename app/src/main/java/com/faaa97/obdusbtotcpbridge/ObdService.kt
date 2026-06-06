package com.faaa97.obdusbtotcpbridge

import android.app.*
import android.content.*
import android.hardware.usb.UsbManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "obd_channel"
        var isRunning : Observable<Boolean> = Observable(false)
            private set
    }

    private var isAutoConnectEnabled = false
    private var isConnecting = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
                // Trigger the USB logic when plugged in
                serviceScope.launch {
                    if(usbPort == null){
                        start()
                    }
                }
            }
        }
    }

    private var serverSocket: ServerSocket? = null
    private var usbPort: UsbSerialPort? = null
    private var tcpSocket: Socket? = null
    private var tcpIn: InputStream? = null
    private var tcpOut: OutputStream? = null

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var bridgeJob: Job? = null


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val autoConnect = intent?.getBooleanExtra("EXTRA_AUTO_CONNECT", false) ?: false
        if (autoConnect && !isAutoConnectEnabled) {
            isAutoConnectEnabled = true
            val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            registerReceiver(usbReceiver, filter)
        }

        serviceScope.launch {
            if (isConnecting || isRunning.value) {
                log("Service already connecting or running, ignoring request.")
                return@launch
            }

            isConnecting = true
            try {
                if(!start()) {
                    log("Can't continue, stopping")
                    if(!isAutoConnectEnabled) stopSelf()
                } else {
                    isRunning.value = true
                    log("Service started")
                }
            } finally {
                isConnecting = false
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        end()
        log("Service stopped")
        if (isAutoConnectEnabled) {
            unregisterReceiver(usbReceiver)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- SEQUENCE -----------

    private suspend fun start() : Boolean {
        startUsb()
        if(usbPort == null) {
            return false
        }
        startTcp()
        return true
    }

    private fun end() {
        isRunning.value = false
        bridgeJob?.cancel() // Stop the loops
        bridgeJob = null
        endUsb()
        endTcp()
    }
    // ---------------- USB ----------------

    private suspend fun startUsb() {
        log("Searching for ELM device...")
        usbPort = USBDeviceSelection.openAndVerifyDevice()
        if(usbPort == null) {
            log("ELM device not found or permission pending")
            return
        }
        log("USB connected and verified")
    }

    private fun endUsb() {
        if(usbPort == null) {
            return
        }

        usbPort?.close()
    }

    // ---------------- TCP ----------------

    private suspend fun startTcp() = withContext(Dispatchers.IO) {
        try {
            endTcp()

            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(java.net.InetSocketAddress(35000))
            serverSocket = socket
            log("TCP listening on 35000")

            val client = socket.accept()
            tcpSocket = client
            tcpIn = client.getInputStream()
            tcpOut = client.getOutputStream()

            log("TCP client connected")
            startBridge()

        } catch (e: Exception) {
            log("TCP error: ${e.message}")
            end()
        }
    }

    private fun endTcp() {
        try {
            tcpIn?.close()
            tcpOut?.close()
            tcpSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore closure errors
        } finally {
            tcpIn = null
            tcpOut = null
            tcpSocket = null
            serverSocket = null
        }
        log("TCP connections closed")
    }

    // ---------------- BRIDGE ----------------

    private fun startBridge() {
        bridgeJob?.cancel()

        bridgeJob = serviceScope.launch(Dispatchers.IO) {
            // TCP → USB Loop
            launch {
                val buffer = ByteArray(1024)
                val request = StringBuilder()
                try {
                    while (isActive && isRunning.value) {
                        val len = tcpIn?.read(buffer) ?: -1
                        if (len == -1) break // Connection closed

                        if (len > 0) {
                            val data = String(buffer, 0, len)
                            request.append(data)
                            if (request.contains("\r")) {
                                usbPort?.write(request.toString().toByteArray(), 1000)
                                log("TCP → USB: $request")
                                request.clear()
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("TCP→USB error: ${e.message}")
                    end()
                }
            }

            // USB → TCP Loop
            launch {
                val buffer = ByteArray(1024)
                val response = StringBuilder()
                try {
                    while (isActive && isRunning.value) {
                        val len = usbPort?.read(buffer, 1000) ?: 0
                        if (len > 0) {
                            val data = String(buffer, 0, len)
                            response.append(data)
                            if (response.contains(">")) {
                                tcpOut?.write(response.toString().toByteArray())
                                log("USB → TCP: $response")
                                response.clear()
                            }
                        }
                    }
                } catch (e: Exception) {
                    log("USB→TCP error: ${e.message}")
                    end()
                }
            }
        }
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