package com.faaa97.obdusbtotcpbridge

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.USB_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@SuppressLint("StaticFieldLeak")
object USBDeviceSelection {

    private const val ACTION_USB_PERMISSION = "com.faaa97.obdusbtotcpbridge.USB_PERMISSION"

    var deviceOptions : List<UsbDeviceItem> = listOf()
        private set

    var selectedDeviceOption : Observable<String?> = Observable(null)
        private set

    private var selectedDeviceDriver: UsbSerialDriver? = null

    private lateinit var manager : UsbManager
    private lateinit var context : Context

    fun initialize(context: Context) {
        if (this::context.isInitialized) return
        this.context = context.applicationContext
        manager = this.context.getSystemService(USB_SERVICE) as UsbManager
    }

    private fun isInitialized(): Boolean = this::manager.isInitialized && this::context.isInitialized

    fun populateDeviceOptions() {
        if (!isInitialized()) return
        try {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            deviceOptions = drivers.map { driver ->
                UsbDeviceItem.Create(driver.device)
            }
        } catch (e: Exception) {
            log("Error populating device options: ${e.message}")
        }
    }

    /**
     * Automated flow to find and verify the ELM device.
     * Suspends until the user grants permission and the device is verified.
     */
    suspend fun openAndVerifyDevice() : UsbSerialPort? = withContext(Dispatchers.IO) {
        if (!isInitialized()) return@withContext null

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB devices found")
            return@withContext null
        }

        val savedId = PersistedState.savedUsbId
        // Try the last used device first
        val sortedDrivers = drivers.sortedByDescending { it.device.deviceName == savedId }

        for (driver in sortedDrivers) {
            val device = driver.device
            log("Checking device: ${device.deviceName}")

            if (!manager.hasPermission(device)) {
                log("Permission required for ${device.deviceName}. Requesting...")
                
                val granted = try {
                    withTimeout(30000) {
                        requestPermissionSuspend(device)
                    }
                } catch (e: TimeoutCancellationException) {
                    false
                }
                
                if (!granted) {
                    log("Permission denied or timeout for ${device.deviceName}")
                    continue
                }
            }

            val connection = manager.openDevice(device) ?: continue
            val port = driver.ports.getOrNull(0) ?: continue

            try {
                port.open(connection)
                if (verifyElm(port)) {
                    val deviceItem = UsbDeviceItem.Create(device)
                    withContext(Dispatchers.Main) {
                        selectedDeviceOption.value = deviceItem.id
                    }
                    selectedDeviceDriver = driver
                    PersistedState.setSavedUsbId(deviceItem.id)
                    log("Verified ELM device: ${deviceItem.id} (${deviceItem.device.serialNumber})")
                    if (!isActive) { // Cancelled meanwhile, the caller will never get the port
                        try { port.close() } catch (ex: Exception) {}
                        return@withContext null
                    }
                    return@withContext port
                }
                port.close()
            } catch (e: Exception) {
                log("Verification error on ${device.deviceName}: ${e.message}")
                try { port.close() } catch (ex: Exception) {}
            }
        }

        log("No verified ELM device found among connected devices")
        null
    }

    private suspend fun verifyElm(port: UsbSerialPort): Boolean {
        return try {
            port.setParameters(38400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            ElmVerifier.verify(port.asSerialLink(), log = ::log)
        } catch (e: Exception) {
            false
        }
    }

    fun trySelectDevice(userSelection: Int = -1, deviceSelected: (UsbDeviceItem) -> Unit = {}) {
        if (!isInitialized()) return
        populateDeviceOptions()

        val id = if (userSelection != -1 && userSelection < deviceOptions.size) {
            deviceOptions[userSelection].id
        } else {
            PersistedState.savedUsbId
        }

        if (id == null) return

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        val driver = drivers.find { it.device.deviceName == id } ?: return

        val device = UsbDeviceItem.Create(driver.device)
        selectedDeviceOption.value = device.id
        selectedDeviceDriver = driver
        PersistedState.setSavedUsbId(device.id)
        deviceSelected(device)
    }

    fun openSelectedDevice() : UsbSerialPort? {
        val driver = selectedDeviceDriver ?: return null
        return try {
            val connection = manager.openDevice(driver.device) ?: return null
            val port = driver.ports.getOrNull(0) ?: return null
            port.open(connection)
            port
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requestPermissionSuspend(usbDevice: UsbDevice): Boolean = suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {}
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        val intent = Intent(ACTION_USB_PERMISSION).apply {
            `package` = context.packageName
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        continuation.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }

        manager.requestPermission(usbDevice, permissionIntent)
    }

    private fun log(msg: String) = ObdLogBus.log(msg)
}
