package com.faaa97.obdusbtotcpbridge

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Context.USB_SERVICE
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.*

@SuppressLint("StaticFieldLeak")
object USBDeviceSelection {

    var deviceOptions : List<UsbDeviceItem> = listOf()
        private set

    var selectedDeviceOption : Observable<String?> = Observable(null)
        private set

    private var selectedDeviceDriver: UsbSerialDriver? = null

    private lateinit var manager : UsbManager
    private lateinit var context : Context

    fun initialize(context: Context) {
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
            if (deviceOptions.isEmpty()) {
                log("No USB device found")
            } else {
                for (option in deviceOptions) {
                    log("Found USB device: $option")
                }
            }
        } catch (e: Exception) {
            log("Error while trying to populate device options: ${e.message}")
        }
    }

    fun trySelectDevice(userSelection: Int = -1, deviceSelected: (UsbDeviceItem) -> Unit = {}) {
        if (!isInitialized()) return
        try {
            if (deviceOptions.isEmpty()) {
                populateDeviceOptions()
            }

            val id = if (userSelection != -1 && userSelection < deviceOptions.size) {
                deviceOptions[userSelection].id
            } else {
                PersistedState.savedUsbId
            }

            if (id == null) {
                log("No USB device selected")
                selectedDeviceDriver = null
                selectedDeviceOption.value = null
                return
            }

            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            val driver = drivers.find { d ->
                UsbDeviceItem.Create(d.device).id == id
            }

            if (driver == null) {
                log("Selected device not found among connected devices")
                selectedDeviceDriver = null
                selectedDeviceOption.value = null
                return
            }

            val device = UsbDeviceItem.Create(driver.device)
            selectedDeviceOption.value = device.id
            selectedDeviceDriver = driver

            PersistedState.setSavedUsbId(selectedDeviceOption.value.toString())
            deviceSelected(device)

        } catch (e: Exception) {
            log("Error while trying to select a device: ${e.message}")
        }
    }

    fun openSelectedDevice() : UsbSerialPort? {
        if (!isInitialized()) {
            log("USB Selection not initialized")
            return null
        }

        val driver = selectedDeviceDriver ?: run {
            log("No USB device selected")
            return null
        }

        try {
            var connection = manager.openDevice(driver.device)
            if (connection == null) {
                requestPermission(driver.device)
                if (manager.hasPermission(driver.device)) {
                    connection = manager.openDevice(driver.device)
                }

                if (connection == null) {
                    log("USB permission missing or failed to open connection")
                    return null
                }
            }

            val usbPort = driver.ports.getOrNull(0) ?: run {
                log("No ports found on device")
                return null
            }

            usbPort.open(connection)
            return usbPort
        } catch (e: Exception) {
            log("Error while opening selected device: ${e.message}")
            return null
        }
    }

    private fun requestPermission(usbDevice: UsbDevice) {
        if (!isInitialized()) return
        try {
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent("com.faaa97.obdusbtotcpbridge.USB_PERMISSION"),
                PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPermission(usbDevice, permissionIntent)
        } catch (e: Exception) {
            log("Error while requesting permission: ${e.message}")
        }
    }

    private fun log(msg: String) {
        ObdLogBus.log(msg)
    }
}
