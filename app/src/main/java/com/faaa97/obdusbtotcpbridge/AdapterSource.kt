package com.faaa97.obdusbtotcpbridge

import java.io.Closeable

/**
 * Where the bridge gets its OBD2 adapter from. The debug and release builds use [UsbAdapterSource];
 * the `mock` build type swaps in a simulated adapter (see `app/src/mock/`) so the app can be tried
 * on an emulator or a PC without a head unit (D-014).
 */
interface AdapterSource {

    /** The id of the adapter the bridge last verified, or null. Used to show it as "in use". */
    val selectedId: Observable<String?>

    /**
     * Bumped when an adapter is attached or detached by something the system USB broadcasts don't
     * cover (only the simulated adapter). The service and the UI re-check when it changes.
     */
    val changes: Observable<Int>

    /** The adapter to show in the UI, without opening it. Never throws; null when none is connected. */
    fun findAdapter(): AdapterInfo?

    /** Finds, opens and verifies an adapter. Null when none is usable. */
    suspend fun openAndVerify(): OpenAdapter?
}

/** What the UI shows about a connected adapter. */
data class AdapterInfo(val id: String, val label: String, val details: String)

/** An opened and verified adapter. Closing it releases the underlying port. */
class OpenAdapter(
    val id: String,
    val link: SerialLink,
    private val closeAction: () -> Unit
) : Closeable {
    override fun close() = closeAction()
}

/** The adapter source the app uses. Replaced only by the mock build's Application, before anything runs. */
object Adapters {
    @Volatile var source: AdapterSource = UsbAdapterSource
}

/** The real adapter: a USB serial ELM327, found and verified by [USBDeviceSelection]. */
object UsbAdapterSource : AdapterSource {

    override val selectedId: Observable<String?> get() = USBDeviceSelection.selectedDeviceOption

    // USB attach/detach arrive as system broadcasts, which the service and the UI already handle.
    override val changes: Observable<Int> = Observable(0)

    override fun findAdapter(): AdapterInfo? {
        val item = USBDeviceSelection.findCompatibleDevice() ?: return null
        val d = item.device
        return AdapterInfo(
            id = item.id,
            label = item.label,
            details = String.format(
                java.util.Locale.US, "VID %04X · PID %04X · %s", d.vendorId, d.productId, d.deviceName
            )
        )
    }

    override suspend fun openAndVerify(): OpenAdapter? {
        val port = USBDeviceSelection.openAndVerifyDevice() ?: return null
        return OpenAdapter(
            id = USBDeviceSelection.selectedDeviceOption.value ?: "",
            link = port.asSerialLink(),
            closeAction = { port.close() }
        )
    }
}
