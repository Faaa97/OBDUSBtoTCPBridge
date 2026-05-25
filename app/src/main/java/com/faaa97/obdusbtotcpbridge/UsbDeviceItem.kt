package com.faaa97.obdusbtotcpbridge

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice

class UsbDeviceItem(
    val id: String,
    val label: String,
    val device: UsbDevice
) {
    companion object {
        fun Create(device: UsbDevice) : UsbDeviceItem {
            return UsbDeviceItem(
                device.deviceName,
                device.deviceName,
                device)
        }

    }

    override fun toString(): String {
        return "VID:${device.vendorId}, PID:${device.productId}, NAME:${device.deviceName} " +
                "DID:${device.deviceId}, CLS:${device.deviceClass}, SCLS:${device.deviceSubclass} " +
                "PROT:${device.deviceProtocol}, INT:${device.interfaceCount}"
    }


}
