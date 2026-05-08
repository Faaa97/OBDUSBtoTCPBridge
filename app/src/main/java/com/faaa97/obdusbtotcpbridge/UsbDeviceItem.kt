package com.faaa97.obdusbtotcpbridge

import android.hardware.usb.UsbDevice

class UsbDeviceItem(
    val id: String,
    val label: String,
    val device: UsbDevice
) {
    companion object {
        fun Create(device: UsbDevice) : UsbDeviceItem {
            return UsbDeviceItem(
                "${device.vendorId}:${device.productId}:${device.deviceName}",
                "VID:${device.vendorId} PID:${device.productId}",
                device)
        }
    }


}
