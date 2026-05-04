package com.faaa97.obdusbtotcpbridge

import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var logView: LinearLayout
    private var button: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializePersistedState()
        initializeLoggingView()
        initializeMainButton()
        initializeToggles()
        autoStart()
    }

    private fun initializePersistedState() {
        val sharedPref = getPreferences(MODE_PRIVATE) ?: return
        PersistedState.init(sharedPref, this)
    }

    private fun initializeLoggingView() {
        logView = this.findViewById(R.id.logs_container)
        lifecycleScope.launch {
            ObdLogBus.logs.collect { msg ->
                logView.addView(TextView(logView.context).apply { text = msg })
            }
        }
    }

    private fun initializeMainButton(){
        ObdService.isRunning.addObserver { _ ->
            ObdLogBus.log("Reacting to observer")
            updateButtonText()
        }

        button = this.findViewById<Button>(R.id.start_button)
        button?.setOnClickListener {
            manageService()
        }
    }

    private fun manageService() {
        val intent =Intent(this@MainActivity, ObdService::class.java)
        if(!ObdService.isRunning.value) {
            startService(intent)
        } else {
            stopService(intent)
        }
    }

    private fun updateButtonText() {
        if(!ObdService.isRunning.value) {
            button?.setText(R.string.button_text_start)
        } else {
            button?.setText(R.string.button_text_stop)
        }
    }

    private fun initializeToggles() {
        val logsButton = this.findViewById<ToggleButton>(R.id.logs_button)
        logsButton.setOnCheckedChangeListener { _, isOn ->
            ObdLogBus.enabled = isOn
            PersistedState.setLoggingEnabled(isOn)
        }

        logsButton.isChecked = PersistedState.isLoggingEnabled
        ObdLogBus.enabled = PersistedState.isLoggingEnabled

        val autoStartButton = this.findViewById<ToggleButton>(R.id.auto_start_button)
        autoStartButton.setOnCheckedChangeListener { _, isOn ->
            PersistedState.setAutoStartEnabled(isOn)
        }

        autoStartButton.isChecked = PersistedState.isAutoStartEnabled
    }

    private fun autoStart() {
        if(PersistedState.isAutoStartEnabled){
            manageService()
        }
    }

}