package com.faaa97.obdusbtotcpbridge

import android.app.Application

/** Open so the mock build's Application can extend it (app/src/mock/). */
open class App : Application() {

    override fun onCreate() {
        super.onCreate()

        ObdLogBus.initFileLogging(this)
        ObdLogBus.log("----- App started -----")
        initializePersistedState()
        USBDeviceSelection.initialize(this)
    }

    private fun initializePersistedState() {
        val sharedPref = applicationContext.getSharedPreferences(packageName, MODE_PRIVATE) ?: return
        PersistedState.init(sharedPref, this)
    }
}