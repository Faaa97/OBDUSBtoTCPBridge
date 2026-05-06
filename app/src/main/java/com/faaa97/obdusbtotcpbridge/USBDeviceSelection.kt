package com.faaa97.obdusbtotcpbridge

import android.content.Context
import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.*

object USBDeviceSelection {

    var deviceOptions : List<String> = listOf()
        private set

    var selectedDeviceOption : Observable<String?> = Observable(null)
        private set

    private lateinit var selectedDeviceDriver: UsbSerialDriver

    lateinit var manager : UsbManager

    fun initialize(context: Context) {
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

        val list = mutableListOf<String>()
        drivers.forEach { d ->
            val serial = d.device.serialNumber
            if(serial != null){
                list.add(serial)
            }
        }
        deviceOptions = list
    }

    fun trySelectDevice(userSelection: Int = -1) {
        if(deviceOptions.isEmpty()){
            return
        }

        var serial = PersistedState.savedUSBSerial
        if(userSelection != -1){
            serial = deviceOptions[userSelection]
        }

        if(serial == null) {
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
            val matchesSerial = serial == device.serialNumber

            matchesSerial
        }

        if(driver == null) {
            return
        }

        selectedDeviceOption.value = driver.device.serialNumber
        selectedDeviceDriver = driver

        PersistedState.setSavedUSBSerial(selectedDeviceOption.value.toString())
    }

    fun openSelectedDevice() : UsbSerialPort? {
        val connection = manager.openDevice(selectedDeviceDriver.device)
        if (connection == null) {
            log("USB permission missing")
            return null
        }

        val usbPort = selectedDeviceDriver.ports[0]
        usbPort.open(connection)
        return usbPort
    }

    private fun log(msg: String) {
        ObdLogBus.log(msg)
    }
}