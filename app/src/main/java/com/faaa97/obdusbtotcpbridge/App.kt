package com.faaa97.obdusbtotcpbridge

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        initializePersistedState()
        USBDeviceSelection.initialize(this)
    }

    private fun initializePersistedState() {
        val sharedPref = applicationContext.getSharedPreferences(packageName, MODE_PRIVATE) ?: return
        PersistedState.init(sharedPref, this)
    }
}