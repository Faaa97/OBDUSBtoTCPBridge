package com.faaa97.obdusbtotcpbridge

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle

class LaunchServiceActivity : Activity()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also launched by Android when a USB serial device is plugged in or re-enumerates after the
        // head unit wakes, even if the app was force-stopped (manifest filter, D-016).
        val trigger = if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) " (USB attached)" else ""
        ObdLogBus.log("----- LaunchServiceActivity$trigger -----")
        try {
            start()
        } catch (e: Exception) {
            ObdLogBus.log("Launch error: ${e.message}")
        }
        finish()
    }

    override fun onDestroy() {
        ObdLogBus.log("----- (LaunchServiceActivity) -----")
        super.onDestroy()
    }

    private fun start() {
        USBDeviceSelection.populateDeviceOptions()

        ObdLogBus.log("Launching service with auto-reconnect...")
        val intent = Intent(this, ObdService::class.java).apply {
            putExtra(ObdService.EXTRA_AUTO_CONNECT, true)
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            ObdLogBus.log("Failed to start service: ${e.message}")
        }
    }

}