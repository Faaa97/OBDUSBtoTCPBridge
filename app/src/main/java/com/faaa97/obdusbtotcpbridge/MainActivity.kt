package com.faaa97.obdusbtotcpbridge

import android.app.Activity
import android.content.*
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.io.path.exists


class MainActivity : AppCompatActivity() {

    private lateinit var logView: LinearLayout
    private var button: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ObdLogBus.log("----- MainActivity -----")

        registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED))
        registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

        initializeLoggingView()
        initializeDeviceDropdown()
        initializeLogButtons()
        initializeMainButton()
        initializeToggles()
        autoStart()
    }

    override fun onDestroy() {
        ObdLogBus.log("----- (MainActivity) -----")
        super.onDestroy()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action ||
                UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                initializeDeviceDropdown()
            }
        }
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
            updateMainButton()
        }

        USBDeviceSelection.selectedDeviceOption.addObserver { _ ->
            updateMainButton()
        }

        button = this.findViewById<Button>(R.id.start_button)
        button?.setOnClickListener {
            manageService()
        }

        updateMainButton()
    }

    private fun manageService() {
        val intent = Intent(this@MainActivity, ObdService::class.java)
        if(!ObdService.isRunning.value) {
            startService(intent)
        } else {
            stopService(intent)
        }
    }

    private fun updateMainButton() {
        if(!ObdService.isRunning.value) {
            button?.setText(R.string.button_text_start)
        } else {
            button?.setText(R.string.button_text_stop)
        }

        if(USBDeviceSelection.selectedDeviceOption.value == null){
            button?.isEnabled = false
        } else {
            button?.isEnabled = true
        }
    }

    private fun initializeDeviceDropdown() {
        USBDeviceSelection.populateDeviceOptions()

        var options: List<String>
        if(USBDeviceSelection.deviceOptions.isNotEmpty()){
            options = USBDeviceSelection.deviceOptions.map { option ->
                option.label
            }
        } else {
            options = mutableListOf("No devices available")
        }

        val dropdown = this.findViewById<Spinner>(R.id.device_dropdown)
        val deviceInfoTextView = this.findViewById<TextView>(R.id.device_info)
        dropdown.onItemSelectedListener = object : Activity(), AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                USBDeviceSelection.trySelectDevice(position, deviceSelected = { device ->
                    deviceInfoTextView.text = device.toString()
                })
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Optional: handle no selection
                ObdLogBus.log("No selection")
            }
        }

        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                options)
        dropdown.adapter = adapter
    }

    private fun initializeLogButtons() {
        val shareLogsButton = this.findViewById<Button>(R.id.share_logs_button)
        shareLogsButton.setOnClickListener {
            shareLogFiles(this)
        }

        val clearLogsButton = this.findViewById<Button>(R.id.clear_logs_button)
        clearLogsButton.setOnClickListener {
            clearAllLogs(this)
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
        if(PersistedState.isAutoStartEnabled && !ObdService.isRunning.value){
            manageService()
        }
    }

    fun shareLogFiles(context: Context) {
        val logFiles = ObdLogBus.getAllLogFiles(context)
        if (logFiles.isEmpty()) {
            ObdLogBus.log("No log files found to share")
            Toast.makeText(context, "No log files found to share", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = logFiles.map { file ->
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Ensure the chooser can see the URIs by adding them to ClipData
            clipData = ClipData.newRawUri("Logs", uris[0]).apply {
                for (i in 1 until uris.size) {
                    addItem(ClipData.Item(uris[i]))
                }
            }
        }

        val chooser = Intent.createChooser(intent, "Share Logs")
        // Check if there is an activity that can handle the intent before starting
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            ObdLogBus.log("No app found to share logs")
            Toast.makeText(context, "No app found to share logs", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllLogs(context: Context) {
        val logFiles = ObdLogBus.getAllLogFiles(context)
        logFiles.forEach { it.delete() }
    }

}