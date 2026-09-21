package com.faaa97.obdusbtotcpbridge

class Backoff(private val minMs: Long, private val maxMs: Long) {

    var delayMs: Long = minMs
        private set

    fun increase() {
        delayMs = minOf(delayMs * 2, maxMs)
    }

    fun reset() {
        delayMs = minMs
    }
}
