package com.faaa97.obdusbtotcpbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableTest {

    @Test
    fun `holds the initial value`() {
        assertEquals(5, Observable(5).value)
    }

    @Test
    fun `notifies observers with the new value`() {
        val observable = Observable(false)
        val seen = mutableListOf<Boolean>()
        observable.addObserver { seen.add(it) }

        observable.value = true
        observable.value = false

        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `does not notify when the value is unchanged`() {
        val observable = Observable("a")
        val seen = mutableListOf<String>()
        observable.addObserver { seen.add(it) }

        observable.value = "a"

        assertEquals(emptyList<String>(), seen)
    }

    @Test
    fun `removed observers are not notified`() {
        val observable = Observable(0)
        val seen = mutableListOf<Int>()
        val observer: (Int) -> Unit = { seen.add(it) }
        observable.addObserver(observer)
        observable.removeObserver(observer)

        observable.value = 1

        assertEquals(emptyList<Int>(), seen)
    }
}
