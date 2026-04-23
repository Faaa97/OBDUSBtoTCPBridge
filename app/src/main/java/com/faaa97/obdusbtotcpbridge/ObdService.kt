package com.faaa97.obdusbtotcpbridge

import android.app.*
import android.content.*
import android.hardware.usb.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "obd_channel"
    }

    private var serverSocket: ServerSocket? = null
    private var usbPort: UsbSerialPort? = null
    private var tcpSocket: Socket? = null
    private var tcpIn: InputStream? = null
    private var tcpOut: OutputStream? = null

    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        startUsb()
        startTcp()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        log("Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        endTcp()
        stopSelf(1)
        running = false
        log("Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- USB ----------------

    private fun startUsb() {
        val manager = getSystemService(USB_SERVICE) as UsbManager

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB device found")
            return
        }

        val driver = drivers[0]
        val connection = manager.openDevice(driver.device)

        if (connection == null) {
            log("USB permission missing")
            return
        }

        usbPort = driver.ports[0]

        try {
            usbPort?.open(connection)
            usbPort?.setParameters(
                38400,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            log("USB connected")

        } catch (e: Exception) {
            log("USB error: ${e.message}")
        }
    }

    // ---------------- TCP ----------------

    private fun startTcp() {
        Thread {
            try {
                serverSocket = ServerSocket(35000)
                log("TCP listening on 35000")

                tcpSocket = serverSocket?.accept()
                tcpIn = tcpSocket!!.getInputStream()
                tcpOut = tcpSocket!!.getOutputStream()

                log("TCP client connected")

                startBridge()

            } catch (e: Exception) {
                log("TCP error: ${e.message}")
            }
        }.start()
    }

    private fun endTcp() {
        serverSocket?.close()
        tcpSocket?.close()
        log("TCP client closed")
    }

    // ---------------- BRIDGE ----------------

    private fun startBridge() {

        // TCP → USB
        Thread {
            val buffer = ByteArray(1024)
            val request = StringBuilder()

            while (running) {
                try {
                    val len = tcpIn?.read(buffer) ?: break
                    if (len > 0) {
                        val data = String(buffer, 0, len)
                        request.append(data)

                        if (request.contains("\r")) {
                            usbPort?.write(request.toString().toByteArray(), 1000)
                            log("TCP → USB: $request")
                            request.clear()
                        }
                    }
                } catch (e: Exception) {
                    log("TCP→USB error: ${e.message}")
                    break
                }
            }
        }.start()

        // USB → TCP
        Thread {
            val buffer = ByteArray(1024)
            val response = StringBuilder()

            while (running) {
                try {
                    val len = usbPort?.read(buffer, 1000) ?: break
                    if (len > 0) {
                        val data = String(buffer, 0, len)
                        response.append(data)

                        if (response.contains(">")) {
                            tcpOut?.write(response.toString().toByteArray())
                            log("USB → TCP: $response")
                            response.clear()
                        }
                    }
                } catch (e: Exception) {
                    log("USB→TCP error: ${e.message}")
                    break
                }
            }
        }.start()
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