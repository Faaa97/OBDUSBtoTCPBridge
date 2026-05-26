package com.faaa97.obdusbtotcpbridge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle

class LaunchServiceActivity : Activity()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

        finish()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action ||
                UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                start()
            }
        }
    }

    private fun start() {
        USBDeviceSelection.populateDeviceOptions()
        USBDeviceSelection.trySelectDevice()

        val intent = Intent(this, ObdService::class.java)
        if(!ObdService.isRunning.value) {
            startService(intent)
        }
    }

}