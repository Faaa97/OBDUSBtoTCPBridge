package com.faaa97.obdusbtotcpbridge

class Observable<T>(initialValue: T) {

    private val observers = mutableSetOf<(T) -> Unit>()

    var value: T = initialValue
        set(newValue) {
            if (field != newValue) {
                field = newValue
                notifyObservers()
            }
        }

    private fun notifyObservers() {
        for (observer in observers) {
            observer(value)
        }
    }

    fun addObserver(observer: (T) -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: (T) -> Unit) {
        observers.remove(observer)
    }
}