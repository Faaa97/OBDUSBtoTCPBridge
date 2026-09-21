package com.faaa97.obdusbtotcpbridge

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * In-memory serial device. Every write is recorded and answered by [responder]; reads block
 * for the timeout like a real port and return 0 when nothing arrives.
 */
class FakeSerialLink(
    private val responder: (String) -> List<String> = { emptyList() }
) : SerialLink {

    private val incoming = LinkedBlockingQueue<ByteArray>()

    val written = CopyOnWriteArrayList<String>()

    @Volatile var failReads = false
    @Volatile var failWrites = false

    /** Makes [data] available to the next read, as if the device had sent it. */
    fun feed(data: String) {
        incoming.add(data.toByteArray())
    }

    override fun read(dest: ByteArray, timeoutMs: Int): Int {
        if (failReads) throw IOException("read failed")
        val chunk = incoming.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        chunk.copyInto(dest)
        return chunk.size
    }

    override fun write(src: ByteArray, timeoutMs: Int) {
        if (failWrites) throw IOException("write failed")
        val text = String(src)
        written.add(text)
        responder(text).forEach(::feed)
    }
}
