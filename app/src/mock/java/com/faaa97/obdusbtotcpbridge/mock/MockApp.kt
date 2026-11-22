package com.faaa97.obdusbtotcpbridge.mock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.faaa97.obdusbtotcpbridge.Adapters
import com.faaa97.obdusbtotcpbridge.App
import com.faaa97.obdusbtotcpbridge.ObdLogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The `mock` build's Application (swapped in by app/src/mock/AndroidManifest.xml): replaces the USB
 * adapter with a simulated ELM327, starts the [DemoClient] and listens for the adb commands in
 * docs/BUILDS.md. Debug and release builds don't contain this code.
 */
class MockApp : App() {

    companion object {
        private const val PREFIX = "com.faaa97.obdusbtotcpbridge.mock."
        const val ACTION_UNPLUG = PREFIX + "UNPLUG"
        const val ACTION_PLUG = PREFIX + "PLUG"
        const val ACTION_DEMO_CLIENT_ON = PREFIX + "DEMO_CLIENT_ON"
        const val ACTION_DEMO_CLIENT_OFF = PREFIX + "DEMO_CLIENT_OFF"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter = MockAdapterSource()
    private val demoClient = DemoClient(scope)

    private val controls = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    ACTION_UNPLUG -> adapter.setPlugged(false)
                    ACTION_PLUG -> adapter.setPlugged(true)
                    ACTION_DEMO_CLIENT_ON -> setDemoClient(true)
                    ACTION_DEMO_CLIENT_OFF -> setDemoClient(false)
                }
            } catch (e: Exception) {
                ObdLogBus.log("Mock control error: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        // Before App.onCreate, so nothing ever sees the USB source.
        Adapters.source = adapter
        super.onCreate()
        ObdLogBus.log("Mock build: simulated ELM327, no USB")
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_UNPLUG)
                addAction(ACTION_PLUG)
                addAction(ACTION_DEMO_CLIENT_ON)
                addAction(ACTION_DEMO_CLIENT_OFF)
            }
            // Exported so `adb shell am broadcast` can reach it; this build never ships.
            ContextCompat.registerReceiver(this, controls, filter, ContextCompat.RECEIVER_EXPORTED)
            setDemoClient(true)
        } catch (e: Exception) {
            ObdLogBus.log("Mock setup error: ${e.message}")
        }
    }

    private fun setDemoClient(enabled: Boolean) {
        demoClient.setEnabled(enabled)
        ObdLogBus.log(if (enabled) "Mock: demo client on" else "Mock: demo client off")
    }
}
