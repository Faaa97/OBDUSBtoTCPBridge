package com.faaa97.obdusbtotcpbridge

import android.content.*
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    companion object {
        /** Lines kept on screen. Bounded so an always-on head unit doesn't grow memory forever. */
        private const val MAX_LOG_LINES = 500
        private const val USB_REFRESH_DELAY_MS = 1_000L
    }

    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private val logLines = ArrayDeque<String>()

    private lateinit var startButton: MaterialButton
    private lateinit var deviceName: TextView
    private lateinit var deviceInfo: TextView
    private lateinit var deviceDot: View
    private lateinit var serviceStatus: TextView
    private lateinit var serviceDot: View

    private var deviceFound = false

    // Observables may notify from any thread; always hop to the UI thread.
    private val serviceObserver: (Boolean) -> Unit = { runOnUiThread { refreshUi() } }
    private val deviceObserver: (String?) -> Unit = { runOnUiThread { refreshUi() } }
    private val adapterChangeObserver: (Int) -> Unit = { runOnUiThread { refreshUi() } }

    private val adapterSource: AdapterSource get() = Adapters.source

    private val delayedRefresh = Runnable { refreshUi() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ObdLogBus.log("----- MainActivity -----")

        bindViews()
        initializeLoggingView()
        initializeLogButtons()
        initializeMainButton()
        initializeToggles()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // System broadcasts are delivered even to non-exported receivers
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        ObdService.isRunning.addObserver(serviceObserver)
        adapterSource.selectedId.addObserver(deviceObserver)
        adapterSource.changes.addObserver(adapterChangeObserver)

        refreshUi()
        autoStart()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        ObdLogBus.log("----- (MainActivity) -----")
        ObdService.isRunning.removeObserver(serviceObserver)
        adapterSource.selectedId.removeObserver(deviceObserver)
        adapterSource.changes.removeObserver(adapterChangeObserver)
        logScroll.removeCallbacks(delayedRefresh)
        try { unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action ||
                UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                refreshUi()
                // The device list may lag slightly behind the broadcast; check again shortly.
                logScroll.removeCallbacks(delayedRefresh)
                logScroll.postDelayed(delayedRefresh, USB_REFRESH_DELAY_MS)
            }
        }
    }

    private fun bindViews() {
        logText = findViewById(R.id.logs_text)
        logScroll = findViewById(R.id.logs_scrollview)
        startButton = findViewById(R.id.start_button)
        deviceName = findViewById(R.id.device_name)
        deviceInfo = findViewById(R.id.device_info)
        deviceDot = findViewById(R.id.device_status_dot)
        serviceStatus = findViewById(R.id.service_status)
        serviceDot = findViewById(R.id.service_status_dot)
    }

    // ---------------- LOG VIEW ----------------

    private fun initializeLoggingView() {
        findViewById<Button>(R.id.clear_view_button).setOnClickListener {
            logLines.clear()
            logText.text = ""
        }

        lifecycleScope.launch {
            ObdLogBus.logs.collect { msg -> appendLog(msg) }
        }
    }

    private fun appendLog(msg: String) {
        try {
            val stickToBottom = isScrolledToBottom()

            logLines.addLast(msg)
            if (logLines.size > MAX_LOG_LINES) {
                while (logLines.size > MAX_LOG_LINES) logLines.removeFirst()
                logText.text = logLines.joinToString("\n")
            } else {
                if (logText.length() > 0) logText.append("\n")
                logText.append(msg)
            }

            if (stickToBottom) {
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        } catch (e: Exception) {
            // Never let the log view take the app down
        }
    }

    /** True unless the user has scrolled up to read older lines. */
    private fun isScrolledToBottom(): Boolean {
        val child = logScroll.getChildAt(0) ?: return true
        val bottomGap = child.bottom + logScroll.paddingBottom - (logScroll.height + logScroll.scrollY)
        return bottomGap <= (48 * resources.displayMetrics.density)
    }

    // ---------------- STATUS ----------------

    private fun refreshUi() {
        if (isFinishing || isDestroyed) return
        try {
            updateDeviceStatus()
            updateServiceStatus()
        } catch (e: Exception) {
            ObdLogBus.log("UI refresh error: ${e.message}")
        }
    }

    private fun updateDeviceStatus() {
        val device = adapterSource.findAdapter()
        deviceFound = device != null

        if (device == null) {
            deviceName.setText(R.string.no_device_found)
            deviceInfo.setText(R.string.no_device_hint)
            setDotColor(deviceDot, R.color.status_idle)
            return
        }

        deviceName.text = device.label
        deviceInfo.text = device.details
        val inUse = ObdService.isRunning.value &&
                adapterSource.selectedId.value == device.id
        setDotColor(deviceDot, if (inUse) R.color.status_ok else R.color.status_warn)
    }

    private fun updateServiceStatus() {
        val running = ObdService.isRunning.value
        if (running) {
            serviceStatus.text = getString(R.string.service_running, ObdService.TCP_PORT)
            setDotColor(serviceDot, R.color.status_ok)
            startButton.setText(R.string.button_text_stop)
            startButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.stop_button_tint)
        } else {
            serviceStatus.setText(R.string.service_stopped)
            setDotColor(serviceDot, R.color.status_idle)
            startButton.setText(R.string.button_text_start)
            startButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.start_button_tint)
        }
        // Stopping must always be possible; starting needs a compatible device.
        startButton.isEnabled = running || deviceFound
    }

    private fun setDotColor(dot: View, colorRes: Int) {
        dot.backgroundTintList = ContextCompat.getColorStateList(this, colorRes)
    }

    // ---------------- CONTROLS ----------------

    private fun initializeMainButton() {
        startButton.setOnClickListener { manageService() }
    }

    private fun manageService() {
        val intent = Intent(this@MainActivity, ObdService::class.java)
        try {
            if (!ObdService.isRunning.value) {
                startService(intent)
            } else {
                stopService(intent)
            }
        } catch (e: Exception) {
            ObdLogBus.log("Failed to change service state: ${e.message}")
        }
    }

    private fun initializeLogButtons() {
        findViewById<Button>(R.id.share_logs_button).setOnClickListener {
            try {
                shareLogFiles(this)
            } catch (e: Exception) {
                ObdLogBus.log("Failed to share logs: ${e.message}")
            }
        }

        findViewById<Button>(R.id.clear_logs_button).setOnClickListener {
            clearAllLogs(this)
            Toast.makeText(this, R.string.log_files_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeToggles() {
        val logsSwitch = findViewById<SwitchMaterial>(R.id.logs_button)
        logsSwitch.isChecked = PersistedState.isLoggingEnabled
        ObdLogBus.enabled = PersistedState.isLoggingEnabled
        logsSwitch.setOnCheckedChangeListener { _, isOn ->
            ObdLogBus.enabled = isOn
            PersistedState.setLoggingEnabled(isOn)
        }

        val autoStartSwitch = findViewById<SwitchMaterial>(R.id.auto_start_button)
        autoStartSwitch.isChecked = PersistedState.isAutoStartEnabled
        autoStartSwitch.setOnCheckedChangeListener { _, isOn ->
            PersistedState.setAutoStartEnabled(isOn)
        }
    }

    private fun autoStart() {
        if (PersistedState.isAutoStartEnabled && !ObdService.isRunning.value) {
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
        try {
            ObdLogBus.getAllLogFiles(context).forEach { it.delete() }
        } catch (e: Exception) {
            ObdLogBus.log("Failed to clear log files: ${e.message}")
        }
    }

}