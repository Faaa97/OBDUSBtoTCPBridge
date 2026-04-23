package com.faaa97.obdusbtotcpbridge

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ObdLogBus {

    private val _logs = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 100
    )

    val logs = _logs.asSharedFlow()

    var enabled : Boolean = true

    fun log(msg: String) {
        if(!enabled) {
            return
        }

        Log.d("OBD", msg)
        _logs.tryEmit(msg)
    }
}