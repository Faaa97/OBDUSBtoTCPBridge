package com.faaa97.obdusbtotcpbridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

@SuppressLint("StaticFieldLeak")
object PersistedState {

    private lateinit var sharedPrefs : SharedPreferences
    private lateinit var context : Context

    var isLoggingEnabled: Boolean = false
        private set

    var isAutoStartEnabled : Boolean = false
        private set

    var savedUSBSerial : String? = null
        private set

    fun init(sharedPreferences: SharedPreferences, context: Context) {
        sharedPrefs = sharedPreferences
        this.context = context
        isLoggingEnabled = sharedPrefs.getBoolean(context.getString(R.string.enable_logs_persisted_value), true)
        isAutoStartEnabled = sharedPrefs.getBoolean(context.getString(R.string.autostart_persisted_value), false)
        savedUSBSerial = sharedPrefs.getString(context.getString(R.string.saved_serial_persisted_value), null)
    }

    fun setLoggingEnabled(value: Boolean) {
        isLoggingEnabled = value
        sharedPrefs.edit {
            putBoolean(context.getString(R.string.enable_logs_persisted_value), isLoggingEnabled)
        }
    }

    fun setAutoStartEnabled(value: Boolean) {
        isAutoStartEnabled = value
        sharedPrefs.edit {
            putBoolean(context.getString(R.string.autostart_persisted_value), isAutoStartEnabled)
        }
    }

    fun setSavedUSBSerial(value: String) {
        savedUSBSerial = value
        sharedPrefs.edit {
            putString(context.getString(R.string.saved_serial_persisted_value), savedUSBSerial)
        }
    }

}