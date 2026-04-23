package com.faaa97.obdusbtotcpbridge

import android.content.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var logView: LinearLayout
    private var runningService: ComponentName? = null

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
        val button = this.findViewById<Button>(R.id.start_button)
        button.setOnClickListener {
            manageService()
            updateButtonText(button)
        }
    }

    private fun manageService() {
        if(runningService == null) {
            runningService = startService(Intent(this@MainActivity, ObdService::class.java))
        } else {
            stopService(Intent(this@MainActivity, ObdService::class.java))
            runningService = null
        }
    }

    private fun updateButtonText(button : Button) {
        if(runningService == null) {
            button.setText(R.string.button_text_start)
        } else {
            button.setText(R.string.button_text_stop)
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