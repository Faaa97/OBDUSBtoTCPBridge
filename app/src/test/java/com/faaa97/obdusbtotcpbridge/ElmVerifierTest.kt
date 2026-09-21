package com.faaa97.obdusbtotcpbridge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmVerifierTest {

    @Test
    fun `accepts a device that answers ELM`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("\r\rELM327 v1.5\r\r>") }

        assertTrue(ElmVerifier.verify(link))
        assertEquals(listOf("ATZ\r"), link.written)
    }

    @Test
    fun `is case insensitive`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("elm327 v2.1") }

        assertTrue(ElmVerifier.verify(link))
    }

    @Test
    fun `accepts an answer split in several reads`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("EL", "M327") }

        assertTrue(ElmVerifier.verify(link))
    }

    @Test
    fun `rejects a device that answers something else`() = runBlocking<Unit> {
        val link = FakeSerialLink { listOf("OK>") }

        assertFalse(ElmVerifier.verify(link, timeoutMs = 400))
    }

    @Test
    fun `rejects a silent device after the timeout`() = runBlocking<Unit> {
        val link = FakeSerialLink()

        assertFalse(ElmVerifier.verify(link, timeoutMs = 400))
    }

    @Test
    fun `rejects a device that fails on write`() = runBlocking<Unit> {
        val link = FakeSerialLink().apply { failWrites = true }

        assertFalse(ElmVerifier.verify(link))
    }

    @Test
    fun `rejects a device that fails on read`() = runBlocking<Unit> {
        val link = FakeSerialLink().apply { failReads = true }

        assertFalse(ElmVerifier.verify(link))
    }
}
