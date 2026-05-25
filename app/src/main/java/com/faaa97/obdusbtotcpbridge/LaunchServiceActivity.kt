package com.faaa97.obdusbtotcpbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchServiceActivity : Activity()  {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        USBDeviceSelection.populateDeviceOptions()
        USBDeviceSelection.trySelectDevice()

        val intent = Intent(this, ObdService::class.java)
        if(!ObdService.isRunning.value) {
            startService(intent)
        }

        finish()
    }

}