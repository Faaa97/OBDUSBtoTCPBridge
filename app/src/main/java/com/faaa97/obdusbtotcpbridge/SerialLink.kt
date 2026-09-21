package com.faaa97.obdusbtotcpbridge

import com.hoho.android.usbserial.driver.UsbSerialPort

interface SerialLink {
    fun read(dest: ByteArray, timeoutMs: Int): Int

    fun write(src: ByteArray, timeoutMs: Int)
}

fun UsbSerialPort.asSerialLink(): SerialLink = object : SerialLink {
    override fun read(dest: ByteArray, timeoutMs: Int): Int = this@asSerialLink.read(dest, timeoutMs)

    override fun write(src: ByteArray, timeoutMs: Int) = this@asSerialLink.write(src, timeoutMs)
}
