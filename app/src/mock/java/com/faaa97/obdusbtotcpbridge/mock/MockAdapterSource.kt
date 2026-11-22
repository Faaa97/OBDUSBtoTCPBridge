package com.faaa97.obdusbtotcpbridge.mock

import com.faaa97.obdusbtotcpbridge.AdapterInfo
import com.faaa97.obdusbtotcpbridge.AdapterSource
import com.faaa97.obdusbtotcpbridge.ElmVerifier
import com.faaa97.obdusbtotcpbridge.ObdLogBus
import com.faaa97.obdusbtotcpbridge.Observable
import com.faaa97.obdusbtotcpbridge.OpenAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A simulated adapter that is "plugged in" until [setPlugged] says otherwise. Each session gets a
 * fresh [MockElm327], verified with the same [ElmVerifier] as a real one. Only in the `mock` build.
 */
class MockAdapterSource : AdapterSource {

    companion object {
        const val ID = "mock-elm327"
        private val INFO = AdapterInfo(
            id = ID,
            label = "Simulated ELM327",
            details = "Mock build · no USB device · ${MockElm327.VERSION}"
        )
    }

    override val selectedId: Observable<String?> = Observable(null)
    override val changes: Observable<Int> = Observable(0)

    @Volatile var isPlugged = true
        private set

    @Volatile private var current: MockElm327? = null

    /** Plugs the simulated adapter in or out. Call on the main thread (observers run inline). */
    fun setPlugged(plugged: Boolean) {
        if (plugged == isPlugged) return
        isPlugged = plugged
        if (!plugged) current?.unplug()
        ObdLogBus.log(if (plugged) "Mock: adapter plugged in" else "Mock: adapter unplugged")
        changes.value = changes.value + 1
    }

    override fun findAdapter(): AdapterInfo? = if (isPlugged) INFO else null

    override suspend fun openAndVerify(): OpenAdapter? {
        if (!isPlugged) {
            ObdLogBus.log("No USB devices found")
            return null
        }
        val elm = MockElm327()
        if (!ElmVerifier.verify(elm, log = ObdLogBus::log)) return null
        withContext(Dispatchers.Main) { selectedId.value = ID }
        current = elm
        ObdLogBus.log("Verified ELM device: $ID (simulated)")
        return OpenAdapter(ID, elm) {
            elm.unplug()
            if (current === elm) current = null
        }
    }
}
