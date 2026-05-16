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

    lateinit var manager : UsbManager
    lateinit var context : Context

    fun initialize(context: Context) {
        this.context = context
        manager = context.getSystemService(USB_SERVICE) as UsbManager

        populateDeviceOptions()
        trySelectDevice()
    }

    private fun populateDeviceOptions() {
        try {
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            if (drivers.isEmpty()) {
                log("No USB device found")
                return
            }

            deviceOptions = drivers.map { driver ->
                val d = driver.device
                UsbDeviceItem.Create(d)
            }
        } catch (e: Exception) {
            log("Error while trying to populate device options: ${e.message}")
        }
    }

    fun trySelectDevice(userSelection: Int = -1) {
        try {
            if (deviceOptions.isEmpty()) {
                return
            }

            var id = PersistedState.savedUsbId
            if (userSelection != -1) {
                id = deviceOptions[userSelection].id
            }

            if (id == null) {
                log("No USB device selected")
                return
            }

            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            if (drivers.isEmpty()) {
                selectedDeviceDriver = null
                selectedDeviceOption.value = null
                log("No USB device found")
                return
            }

            val driver = drivers.find { d ->
                val device = d.device
                val matchesId = id == UsbDeviceItem.Create(device).id

                matchesId
            }

            if (driver == null) {
                log("Selected device not found among connected devices")
                selectedDeviceDriver = null
                selectedDeviceOption.value = null
                return
            }

            selectedDeviceOption.value = UsbDeviceItem.Create(driver.device).id
            selectedDeviceDriver = driver

            PersistedState.setSavedUsbId(selectedDeviceOption.value.toString())

        } catch (e: Exception) {
            log("Error while trying to select a device: ${e.message}")
        }
    }

    fun openSelectedDevice() : UsbSerialPort? {
        try {
            if(selectedDeviceDriver == null){
                log("No USB device selected")
                return null;
            }

            val deviceDriver = selectedDeviceDriver!!;

            var connection = manager.openDevice(deviceDriver.device)
            if (connection == null) {
                requestPermission(deviceDriver.device)
                if(manager.hasPermission(deviceDriver.device)){
                    connection = manager.openDevice(deviceDriver.device)
                }

                if(connection == null){
                    log("USB permission missing")
                    return null
                }
            }

            val usbPort = deviceDriver.ports[0]
            usbPort.open(connection)
            return usbPort
        } catch (e: Exception) {
            log("Error while opening selected device: ${e.message}")
            return null
        }
    }

    private fun requestPermission(usbDevice: UsbDevice) {
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