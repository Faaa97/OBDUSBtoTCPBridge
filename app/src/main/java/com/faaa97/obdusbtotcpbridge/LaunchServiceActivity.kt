package com.faaa97.obdusbtotcpbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchServiceActivity : Activity()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ObdLogBus.log("----- LaunchServiceActivity -----")
        start()
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
            putExtra("EXTRA_AUTO_CONNECT", true)
        }

        try {
            startService(intent)
        } catch (e: Exception) {
            ObdLogBus.log("Failed to start service: ${e.message}")
        }
    }

}