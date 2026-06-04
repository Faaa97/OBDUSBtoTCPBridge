package com.faaa97.obdusbtotcpbridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ObdLogBus {

    private val _logs = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 100
    )

    val logs = _logs.asSharedFlow()

    var enabled : Boolean = true
    private var logFile: File? = null

    fun initFileLogging(context: Context) {
        val fileName = "obd_logs_${
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
                Date()
            )}.txt"
        // FilesDir is internal to the app, so it doesn't need storage permissions
        logFile = File(context.filesDir, fileName)
    }


    fun log(msg: String) {
        if(!enabled) {
            return
        }

        val timestamp = SimpleDateFormat(
            "HH:mm:ss.SSS",
            Locale.getDefault()
        ).format(
            Date())
        val formattedMsg = "[$timestamp] $msg"

        Log.d("OBD", formattedMsg)
        _logs.tryEmit(formattedMsg)

        writeToFile(formattedMsg)
    }

    private fun writeToFile(msg: String) {
        logFile?.let { file ->
            try {
                FileOutputStream(file, true).use { output ->
                    output.write("$msg\n".toByteArray())
                }
            } catch (e: Exception) {
                Log.e("OBD", "Failed to write to log file", e)
            }
        }
    }

    fun getAllLogFiles(context: Context): List<File> {
        return context.filesDir.listFiles { file ->
            file.name.startsWith("obd_logs_") && file.name.endsWith(".txt")
        }?.toList() ?: emptyList()
    }
}