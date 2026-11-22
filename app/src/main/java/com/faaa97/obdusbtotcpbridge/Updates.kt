package com.faaa97.obdusbtotcpbridge

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published GitHub release with an APK attached. */
data class Release(
    val tag: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
)

/**
 * Checks GitHub for a newer release and downloads its APK (D-015).
 *
 * Only `releases/latest` is asked, which never returns drafts or pre-releases. Checks are
 * throttled so a head unit that keeps the UI open doesn't hit the API's 60 requests/hour limit.
 */
object Updates {

    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Faaa97/OBDUSBtoTCPBridge/releases/latest"

    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // After a successful check
    private const val RETRY_INTERVAL_MS = 10 * 60 * 1000L      // After a failed one (e.g. offline)
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** The newer release, or null if there is none (or it isn't known yet). */
    val available = Observable<Release?>(null)

    private var nextCheckAt = 0L

    /** Checks for a newer release than [currentVersion] unless a check ran recently. Never throws. */
    suspend fun checkIfDue(currentVersion: String) {
        val now = SystemClock.elapsedRealtime()
        if (nextCheckAt != 0L && now < nextCheckAt) return
        nextCheckAt = now + RETRY_INTERVAL_MS
        try {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            nextCheckAt = now + CHECK_INTERVAL_MS
            val newer = release?.takeIf { isNewer(it.tag, currentVersion) }
            if (newer != null && newer != available.value) {
                ObdLogBus.log("Update available: ${newer.tag} (installed $currentVersion)")
            }
            available.value = newer
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ObdLogBus.log("Update check failed: ${e.message}")
        }
    }

    private fun fetchLatest(): Release? {
        val conn = open(LATEST_RELEASE_URL).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null // No releases
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            return parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads the release's APK into [dir], replacing earlier downloads, and returns the file.
     * Reuses a complete earlier download of the same release. [onProgress] gets 0..100, from the
     * IO thread.
     */
    suspend fun download(dir: File, release: Release, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val target = File(dir, release.apkName)
            if (target.isFile && target.length() == release.apkSize) return@withContext target

            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, "${release.apkName}.part")

            val conn = open(release.apkUrl)
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${conn.responseCode}")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                var done = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            // Blocking reads ignore cancellation; check between chunks, the read
                            // timeout covers a stalled connection.
                            ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            val percent = if (total > 0) (done * 100 / total).toInt() else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent.coerceIn(0, 100))
                            }
                        }
                    }
                }
                if (release.apkSize > 0 && done != release.apkSize) {
                    throw Exception("Incomplete download ($done of ${release.apkSize} bytes)")
                }
                if (!part.renameTo(target)) throw Exception("Could not save ${target.name}")
                target
            } catch (e: Throwable) {
                part.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true // Asset downloads redirect to another https host
            setRequestProperty("User-Agent", "OBDUSBtoTCPbridge")
        }

    // ---------------- Pure helpers (unit tested) ----------------

    /**
     * Parses a GitHub release JSON. Returns null for drafts, pre-releases and releases without an
     * APK. Prefers a signed APK over a `-debug.apk` if both are attached.
     */
    fun parseRelease(json: String): Release? {
        val obj = JSONObject(json)
        if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return null
        val tag = obj.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val assets = obj.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val apk = apks.firstOrNull { !it.optString("name").endsWith("-debug.apk", true) }
            ?: apks.firstOrNull()
            ?: return null
        val url = apk.optString("browser_download_url").takeIf { it.startsWith("https://") }
            ?: return null
        return Release(tag, url, apk.optString("name"), apk.optLong("size"))
    }

    /** `v1.2.3`, `1.2` or `1.2.3-rc.1` → numeric parts; null if it isn't a version. */
    fun parseVersion(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** True if [candidate] is a higher version than [current]. Suffixes are ignored. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
