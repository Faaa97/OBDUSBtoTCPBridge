package com.faaa97.obdusbtotcpbridge

import android.app.Application

/** Open so the mock build's Application can extend it (app/src/mock/). */
open class App : Application() {

    override fun onCreate() {
        super.onCreate()

        ObdLogBus.initFileLogging(this)
        ObdLogBus.log("----- App started -----")
        logCrashes()
        initializePersistedState()
        USBDeviceSelection.initialize(this)
    }

    /**
     * Writes an uncaught exception to the log file before the default handler ends the process, so a
     * crash in the car shows up in the logs (the service then comes back through START_STICKY, D-016).
     */
    private fun logCrashes() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                ObdLogBus.log("CRASH on ${thread.name}: ${android.util.Log.getStackTraceString(e)}")
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun initializePersistedState() {
        val sharedPref = applicationContext.getSharedPreferences(packageName, MODE_PRIVATE) ?: return
        PersistedState.init(sharedPref, this)
    }
}