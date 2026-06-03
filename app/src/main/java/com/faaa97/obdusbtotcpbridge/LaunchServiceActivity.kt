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
        ObdLogBus.log("LaunchServiceActivity...")
        start()
        finish()
    }

    private fun start() {
        USBDeviceSelection.populateDeviceOptions()

        if(!ObdService.isRunning.value) {
            ObdLogBus.log("Launching service with auto-reconnect...")
            val intent = Intent(this, ObdService::class.java).apply {
                putExtra("EXTRA_AUTO_CONNECT", true)
            }

            startService(intent)
        }
    }

}