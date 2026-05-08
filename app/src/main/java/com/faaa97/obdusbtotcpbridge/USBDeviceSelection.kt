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

    private lateinit var selectedDeviceDriver: UsbSerialDriver

    lateinit var manager : UsbManager
    lateinit var context : Context

    fun initialize(context: Context) {
        this.context = context
        manager = context.getSystemService(USB_SERVICE) as UsbManager

        populateDeviceOptions()
        trySelectDevice()
    }

    private fun populateDeviceOptions() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB device found")
            return
        }

        deviceOptions = drivers.map { driver ->
            val d = driver.device
            UsbDeviceItem.Create(d)
        }
    }

    fun trySelectDevice(userSelection: Int = -1) {
        if(deviceOptions.isEmpty()){
            return
        }

        var id = PersistedState.savedUsbId
        if(userSelection != -1){
            id = deviceOptions[userSelection].id
        }

        if(id == null) {
            log("No USB device selected")
            return
        }

        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (drivers.isEmpty()) {
            log("No USB device found")
            return
        }

        val driver = drivers.find { d ->
            val device = d.device
            val matchesId = id == UsbDeviceItem.Create(device).id

            matchesId
        }

        if(driver == null) {
            return
        }

        selectedDeviceOption.value = UsbDeviceItem.Create(driver.device).id
        selectedDeviceDriver = driver

        PersistedState.setSavedUsbId(selectedDeviceOption.value.toString())
    }

    fun openSelectedDevice() : UsbSerialPort? {
        var connection = manager.openDevice(selectedDeviceDriver.device)
        if (connection == null) {
            requestPermission(selectedDeviceDriver.device)
            if(manager.hasPermission(selectedDeviceDriver.device)){
                connection = manager.openDevice(selectedDeviceDriver.device)
            }

            if(connection == null){
                log("USB permission missing")
                return null
            }
        }

        val usbPort = selectedDeviceDriver.ports[0]
        usbPort.open(connection)
        return usbPort
    }

    private fun requestPermission(usbDevice: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent("com.faaa97.obdusbtotcpbridge.USB_PERMISSION"),
            PendingIntent.FLAG_IMMUTABLE
        )

        manager.requestPermission(usbDevice, permissionIntent)
    }

    private fun log(msg: String) {
        ObdLogBus.log(msg)
    }
}