package com.faaa97.obdusbtotcpbridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ObdLogBus {

    private val _logs = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 100
    )

    val logs = _logs.asSharedFlow()

    fun emit(msg: String) {
        _logs.tryEmit(msg)
    }
}