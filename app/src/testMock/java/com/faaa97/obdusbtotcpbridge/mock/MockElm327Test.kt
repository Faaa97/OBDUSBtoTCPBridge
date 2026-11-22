package com.faaa97.obdusbtotcpbridge.mock

import com.faaa97.obdusbtotcpbridge.ElmVerifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Runs with `./gradlew testMockUnitTest` (not in CI, like the rest of the mock build). */
class MockElm327Test {

    private var now = 0L
    private val elm = MockElm327 { now }

    private fun exchange(command: String): String {
        elm.write("$command\r".toByteArray(), 1000)
        val out = StringBuilder()
        val buffer = ByteArray(1024)
        while (!out.contains(">")) {
            val n = elm.read(buffer, 1000)
            if (n == 0) break
            out.append(String(buffer, 0, n))
        }
        return out.toString()
    }

    @Test
    fun passesTheRealVerifier() = runBlocking {
        assertTrue(ElmVerifier.verify(elm))
    }

    @Test
    fun echoesUntilEchoIsTurnedOff() {
        assertEquals("ATI\rELM327 v1.5\r\r>", exchange("ATI"))
        assertEquals("ATE0\rOK\r\r>", exchange("ATE0"))
        assertEquals("ELM327 v1.5\r\r>", exchange("ATI"))
    }

    @Test
    fun answersRpmAsMode01Reply() {
        exchange("ATE0")
        val reply = exchange("010C").removeSuffix("\r\r>")
        val bytes = reply.split(" ")
        assertEquals(listOf("41", "0C"), bytes.take(2))
        val rpm = (bytes[2].toInt(16) * 256 + bytes[3].toInt(16)) / 4
        assertTrue("rpm $rpm", rpm in 500..5000)
    }

    @Test
    fun ignoresTheRepliesCountSuffixAndHonoursSpacesOff() {
        exchange("ATE0")
        exchange("ATS0")
        assertTrue(exchange("010D1").startsWith("410D"))
    }

    @Test
    fun unsupportedPidsSayNoData() {
        exchange("ATE0")
        assertEquals("NO DATA\r\r>", exchange("0142"))
    }

    @Test
    fun valuesChangeOverTime() {
        exchange("ATE0")
        val first = exchange("010D")
        now += 20_000
        assertFalse(first == exchange("010D"))
    }

    @Test(expected = IOException::class)
    fun failsAfterUnplug() {
        elm.unplug()
        elm.read(ByteArray(16), 10)
    }

    @Test
    fun longRepliesSurviveSmallReadBuffers() {
        elm.write("ATI\r".toByteArray(), 1000)
        val out = StringBuilder()
        val small = ByteArray(4)
        while (!out.contains(">")) {
            val n = elm.read(small, 1000)
            if (n == 0) break
            out.append(String(small, 0, n))
        }
        assertEquals("ATI\rELM327 v1.5\r\r>", out.toString())
    }
}
