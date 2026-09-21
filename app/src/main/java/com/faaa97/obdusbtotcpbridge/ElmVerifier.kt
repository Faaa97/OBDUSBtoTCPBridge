package com.faaa97.obdusbtotcpbridge

import kotlinx.coroutines.delay

object ElmVerifier {

    suspend fun verify(
        link: SerialLink,
        timeoutMs: Long = 2000,
        log: (String) -> Unit = {}
    ): Boolean {
        return try {
            log("Sending ATZ...")
            link.write("ATZ\r".toByteArray(), 1000)

            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()
            val responseBuilder = StringBuilder()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val len = link.read(buffer, 500)
                if (len > 0) {
                    responseBuilder.append(String(buffer, 0, len))
                    if (responseBuilder.contains("ELM", ignoreCase = true)) {
                        log("Response: ${responseBuilder.toString().trim()}")
                        return true
                    }
                }
                delay(100)
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
