package com.faaa97.obdusbtotcpbridge.mock

import com.faaa97.obdusbtotcpbridge.SerialLink
import java.io.IOException
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A simulated ELM327 behind a serial link: answers AT commands and a few mode 01 PIDs with values
 * that drift over time like a car driving around. Reads block for the timeout like a real port.
 * Only in the `mock` build type.
 */
class MockElm327(
    private val nowMs: () -> Long = System::currentTimeMillis
) : SerialLink {

    companion object {
        const val VERSION = "ELM327 v1.5"
    }

    private val incoming = LinkedBlockingQueue<ByteArray>()
    private val pending = StringBuilder()
    private val startMs = nowMs()

    @Volatile private var plugged = true
    private var echo = true
    private var spaces = true

    /** Simulates pulling the adapter out: every later read and write fails. */
    fun unplug() {
        plugged = false
        incoming.clear()
    }

    override fun read(dest: ByteArray, timeoutMs: Int): Int {
        if (!plugged) throw IOException("Simulated adapter unplugged")
        val chunk = incoming.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        if (!plugged) throw IOException("Simulated adapter unplugged")
        val n = minOf(chunk.size, dest.size)
        chunk.copyInto(dest, 0, 0, n)
        if (n < chunk.size) {
            // Keep the rest for the next read, ahead of anything newer.
            val rest = chunk.copyOfRange(n, chunk.size)
            val newer = ArrayList<ByteArray>().also { incoming.drainTo(it) }
            incoming.add(rest)
            incoming.addAll(newer)
        }
        return n
    }

    override fun write(src: ByteArray, timeoutMs: Int) {
        if (!plugged) throw IOException("Simulated adapter unplugged")
        synchronized(pending) {
            pending.append(String(src))
            while (true) {
                val end = pending.indexOf("\r")
                if (end < 0) break
                val command = pending.substring(0, end)
                pending.delete(0, end + 1)
                if (command.isNotBlank()) incoming.add(respond(command).toByteArray())
            }
        }
    }

    /** The full reply to one command, as the adapter would send it: echo, lines, then the prompt. */
    internal fun respond(raw: String): String {
        // Echo is decided before the command runs: ATE0 is itself still echoed.
        val echoed = if (echo) "$raw\r" else ""
        val body = answer(raw.uppercase(Locale.US).replace(" ", "").trim())
        return "$echoed${body.joinToString("\r")}\r\r>"
    }

    private fun answer(cmd: String): List<String> {
        if (cmd.startsWith("AT")) return answerAt(cmd.substring(2))
        // A trailing digit is the "expected replies" count some apps add: 010C1
        val request = if (cmd.length == 5 && cmd.startsWith("01")) cmd.substring(0, 4) else cmd
        return when {
            request == "03" -> listOf("43 00".spaced())
            request == "04" -> listOf("44")
            request.length == 4 && request.startsWith("01") -> listOf(answerMode01(request.substring(2)))
            request.all { it.isLetterOrDigit() } && request.isNotEmpty() -> listOf("NO DATA")
            else -> listOf("?")
        }
    }

    private fun answerAt(at: String): List<String> = when (at) {
        "Z", "WS" -> { echo = true; spaces = true; listOf("", VERSION) }
        "D" -> { echo = true; spaces = true; listOf("OK") }
        "I" -> listOf(VERSION)
        "@1" -> listOf("OBDII to RS232 Interpreter")
        "RV" -> listOf(String.format(Locale.US, "%.1fV", 13.8 + 0.2 * sin(seconds() / 7)))
        "DP" -> listOf("AUTO, ISO 15765-4 (CAN 11/500)")
        "DPN" -> listOf("A6")
        "E0" -> { echo = false; listOf("OK") }
        "E1" -> { echo = true; listOf("OK") }
        "S0" -> { spaces = false; listOf("OK") }
        "S1" -> { spaces = true; listOf("OK") }
        else -> listOf("OK") // Headers, linefeeds, protocol, timeouts...: accept everything
    }

    private fun answerMode01(pid: String): String {
        val t = seconds()
        val speed = max(0.0, 45 + 40 * sin(t / 25)).roundToInt()          // km/h
        val rpm = (800 + speed * 32 + 250 * sin(t / 3)).roundToInt()
        val throttle = (12 + speed / 4 + 8 * sin(t / 4)).coerceIn(0.0, 100.0)
        val load = (25 + speed / 3 + 10 * sin(t / 5)).coerceIn(0.0, 100.0)
        val coolant = (88 + 2 * sin(t / 60)).roundToInt()                   // °C
        val bytes: List<Int> = when (pid) {
            "00" -> listOf(0xBE, 0x3E, 0xB8, 0x11)
            "20" -> listOf(0x00, 0x00, 0x00, 0x00)
            "04" -> listOf(percent(load))
            "05" -> listOf(coolant + 40)
            "0B" -> listOf((30 + speed / 2).coerceIn(0, 255))               // kPa
            "0C" -> (rpm * 4).let { listOf(it shr 8 and 0xFF, it and 0xFF) }
            "0D" -> listOf(speed.coerceIn(0, 255))
            "0F" -> listOf(28 + 40)                                         // °C
            "10" -> (rpm / 2).let { listOf(it shr 8 and 0xFF, it and 0xFF) } // MAF, g/s * 100
            "11" -> listOf(percent(throttle))
            else -> return "NO DATA"
        }
        return (listOf(0x41, pid.toInt(16)) + bytes).joinToString(" ") { "%02X".format(it) }.spaced()
    }

    private fun percent(value: Double) = (value * 255 / 100).roundToInt().coerceIn(0, 255)

    private fun seconds() = (nowMs() - startMs) / 1000.0

    private fun String.spaced() = if (spaces) this else replace(" ", "")
}
