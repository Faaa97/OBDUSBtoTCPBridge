package com.faaa97.obdusbtotcpbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPolicyTest {

    private fun auto(enabled: Boolean = false, requested: Boolean = false, sticky: Boolean = false) =
        StartPolicy.autoConnect(enabled, requested, sticky)

    @Test
    fun plainStart_doesNotAutoConnect() {
        assertFalse(auto())
    }

    @Test
    fun requestedStart_autoConnects() {
        assertTrue(auto(requested = true))
    }

    @Test
    fun stickyRestart_autoConnects() {
        // The killed service comes back with a null intent: it must keep retrying, not stop.
        assertTrue(auto(sticky = true))
    }

    @Test
    fun plainStartAfterAutoConnect_keepsAutoConnect() {
        // A later start without the extra (e.g. the UI) must not turn retries off for a running bridge.
        assertTrue(auto(enabled = true))
    }
}
