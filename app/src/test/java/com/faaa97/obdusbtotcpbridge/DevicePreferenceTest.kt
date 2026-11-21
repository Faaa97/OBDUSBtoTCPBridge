package com.faaa97.obdusbtotcpbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePreferenceTest {

    private fun pick(ids: List<String>, selected: String?, saved: String?) =
        USBDeviceSelection.pickPreferredId(ids, selected, saved)

    @Test
    fun noDevices_returnsNull() {
        assertNull(pick(emptyList(), "a", "b"))
    }

    @Test
    fun deviceInUse_winsOverSaved() {
        assertEquals("b", pick(listOf("a", "b", "c"), selected = "b", saved = "c"))
    }

    @Test
    fun savedDevice_usedWhenNoneInUse() {
        assertEquals("c", pick(listOf("a", "b", "c"), selected = null, saved = "c"))
    }

    @Test
    fun disconnectedPreferences_fallBackToFirst() {
        assertEquals("a", pick(listOf("a", "b"), selected = "x", saved = "y"))
    }

    @Test
    fun noPreferences_returnsFirst() {
        assertEquals("a", pick(listOf("a"), selected = null, saved = null))
    }
}
