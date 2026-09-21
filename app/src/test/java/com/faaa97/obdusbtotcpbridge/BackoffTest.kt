package com.faaa97.obdusbtotcpbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {

    @Test
    fun `starts at the minimum`() {
        assertEquals(1_000L, Backoff(1_000, 30_000).delayMs)
    }

    @Test
    fun `doubles on every increase`() {
        val backoff = Backoff(1_000, 30_000)

        backoff.increase()
        assertEquals(2_000L, backoff.delayMs)
        backoff.increase()
        assertEquals(4_000L, backoff.delayMs)
    }

    @Test
    fun `never exceeds the maximum`() {
        val backoff = Backoff(1_000, 30_000)

        repeat(20) { backoff.increase() }

        assertEquals(30_000L, backoff.delayMs)
    }

    @Test
    fun `reset goes back to the minimum`() {
        val backoff = Backoff(1_000, 30_000)
        repeat(3) { backoff.increase() }

        backoff.reset()

        assertEquals(1_000L, backoff.delayMs)
    }
}
