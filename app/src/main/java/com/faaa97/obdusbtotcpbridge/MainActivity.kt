package com.faaa97.obdusbtotcpbridge

import android.content.*
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File


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

    private lateinit var updateBanner: View
    private lateinit var updateMessage: TextView
    private lateinit var updateDetail: TextView
    private lateinit var updateButton: MaterialButton
    private var updateJob: Job? = null
    private val installedVersion: String by lazy { readInstalledVersion() }

    // Observables may notify from any thread; always hop to the UI thread.
    private val serviceObserver: (Boolean) -> Unit = { runOnUiThread { refreshUi() } }
    private val deviceObserver: (String?) -> Unit = { runOnUiThread { refreshUi() } }
    private val adapterChangeObserver: (Int) -> Unit = { runOnUiThread { refreshUi() } }
    private val updateObserver: (Release?) -> Unit = { runOnUiThread { refreshUpdateBanner() } }

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
        initializeUpdateBanner()

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        // System broadcasts are delivered even to non-exported receivers
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        ObdService.isRunning.addObserver(serviceObserver)
        adapterSource.selectedId.addObserver(deviceObserver)
        adapterSource.changes.addObserver(adapterChangeObserver)
        Updates.available.addObserver(updateObserver)

        refreshUi()
        autoStart()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        // Throttled inside Updates, so returning to the app doesn't call GitHub every time
        lifecycleScope.launch { Updates.checkIfDue(installedVersion) }
    }

    override fun onDestroy() {
        ObdLogBus.log("----- (MainActivity) -----")
        ObdService.isRunning.removeObserver(serviceObserver)
        adapterSource.selectedId.removeObserver(deviceObserver)
        adapterSource.changes.removeObserver(adapterChangeObserver)
        Updates.available.removeObserver(updateObserver)
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
        updateBanner = findViewById(R.id.update_banner)
        updateMessage = findViewById(R.id.update_message)
        updateDetail = findViewById(R.id.update_detail)
        updateButton = findViewById(R.id.update_button)
    }

    // ---------------- UPDATES (D-015) ----------------

    private fun initializeUpdateBanner() {
        updateButton.setOnClickListener { startUpdate() }
        refreshUpdateBanner()
    }

    private fun refreshUpdateBanner() {
        if (isFinishing || isDestroyed) return
        val release = Updates.available.value
        if (release == null) {
            updateBanner.visibility = View.GONE
            return
        }
        updateMessage.text = getString(R.string.update_available, release.tag)
        updateDetail.text = getString(R.string.update_installed_version, installedVersion)
        if (updateJob?.isActive != true) {
            updateButton.isEnabled = true
            updateButton.setText(R.string.update_button)
        }
        updateBanner.visibility = View.VISIBLE
    }

    private fun startUpdate() {
        val release = Updates.available.value ?: return
        if (updateJob?.isActive == true) return
        // Without permission to install, send the user to the setting first; they tap Update again.
        if (!canInstallPackages()) {
            requestInstallPermission()
            return
        }

        updateButton.isEnabled = false
        updateButton.text = getString(R.string.update_downloading, 0)
        updateJob = lifecycleScope.launch {
            try {
                ObdLogBus.log("Downloading update ${release.tag}...")
                val apk = Updates.download(File(cacheDir, "updates"), release) { percent ->
                    runOnUiThread {
                        if (!isDestroyed) updateButton.text = getString(R.string.update_downloading, percent)
                    }
                }
                ObdLogBus.log("Update downloaded, opening the installer")
                installApk(apk)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ObdLogBus.log("Update download failed: ${e.message}")
                Toast.makeText(this@MainActivity, getString(R.string.update_failed, e.message), Toast.LENGTH_LONG).show()
            } finally {
                if (!isDestroyed) {
                    updateButton.isEnabled = true
                    updateButton.setText(R.string.update_button)
                }
            }
        }
    }

    private fun canInstallPackages(): Boolean = packageManager.canRequestPackageInstalls()

    private fun requestInstallPermission() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
            )
            Toast.makeText(this, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            ObdLogBus.log("Can't open the install permission setting: ${e.message}")
            Toast.makeText(this, getString(R.string.update_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(apk: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            ObdLogBus.log("Can't open the package installer: ${e.message}")
            Toast.makeText(this, getString(R.string.update_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun readInstalledVersion(): String = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName ?: ""
    } catch (e: Exception) {
        ""
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

    /** [autoConnect]: keep retrying until an adapter shows up, instead of stopping on the first failure. */
    private fun manageService(autoConnect: Boolean = false) {
        val intent = Intent(this@MainActivity, ObdService::class.java).apply {
            if (autoConnect) putExtra(ObdService.EXTRA_AUTO_CONNECT, true)
        }
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
            manageService(autoConnect = true)
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