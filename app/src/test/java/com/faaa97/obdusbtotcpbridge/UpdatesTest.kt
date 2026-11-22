package com.faaa97.obdusbtotcpbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun parsesVersions() {
        assertEquals(listOf(1, 2, 3), Updates.parseVersion("v1.2.3"))
        assertEquals(listOf(0, 2), Updates.parseVersion("0.2"))
        assertEquals(listOf(1, 0, 0), Updates.parseVersion("1.0.0-rc.1"))
        assertEquals(listOf(0, 2, 0), Updates.parseVersion("0.2.0-mock"))
        assertNull(Updates.parseVersion("latest"))
        assertNull(Updates.parseVersion(""))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Updates.isNewer("v0.2.1", "0.2.0"))
        assertTrue(Updates.isNewer("v0.10.0", "0.9.9"))
        assertTrue(Updates.isNewer("v1.0", "0.9.9"))
        assertTrue(Updates.isNewer("v0.2.1", "0.2"))
        assertFalse(Updates.isNewer("v0.2.0", "0.2.0"))
        assertFalse(Updates.isNewer("v0.2.0", "0.2.0-mock"))
        assertFalse(Updates.isNewer("v0.2", "0.2.0"))
        assertFalse(Updates.isNewer("v0.1.9", "0.2.0"))
        assertFalse(Updates.isNewer("nightly", "0.2.0"))
        assertFalse(Updates.isNewer("v0.3.0", "unknown"))
    }

    private fun releaseJson(
        tag: String = "v0.3.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """{"name": "OBDUSBtoTCPbridge-v0.3.0.apk", "size": 1234,
            "browser_download_url": "https://github.com/x/y/releases/download/v0.3.0/a.apk"}""",
    ) = """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
        "html_url": "https://github.com/x/y/releases/tag/$tag", "assets": [$assets]}"""

    @Test
    fun parsesRelease() {
        val release = Updates.parseRelease(releaseJson())!!
        assertEquals("v0.3.0", release.tag)
        assertEquals("OBDUSBtoTCPbridge-v0.3.0.apk", release.apkName)
        assertEquals(1234L, release.apkSize)
        assertEquals("https://github.com/x/y/releases/download/v0.3.0/a.apk", release.apkUrl)
    }

    @Test
    fun ignoresDraftsPrereleasesAndReleasesWithoutApk() {
        assertNull(Updates.parseRelease(releaseJson(draft = true)))
        assertNull(Updates.parseRelease(releaseJson(prerelease = true)))
        assertNull(Updates.parseRelease(releaseJson(assets = "")))
        assertNull(Updates.parseRelease(releaseJson(assets =
            """{"name": "notes.txt", "size": 1, "browser_download_url": "https://x/notes.txt"}""")))
        assertNull(Updates.parseRelease(releaseJson(assets =
            """{"name": "a.apk", "size": 1, "browser_download_url": "http://insecure/a.apk"}""")))
    }

    @Test
    fun prefersSignedApkOverDebugApk() {
        val assets = """
            {"name": "app-v0.3.0-debug.apk", "size": 1, "browser_download_url": "https://x/debug.apk"},
            {"name": "app-v0.3.0.apk", "size": 2, "browser_download_url": "https://x/release.apk"}
        """
        assertEquals("https://x/release.apk", Updates.parseRelease(releaseJson(assets = assets))!!.apkUrl)

        val debugOnly = """{"name": "app-debug.apk", "size": 1, "browser_download_url": "https://x/d.apk"}"""
        assertEquals("https://x/d.apk", Updates.parseRelease(releaseJson(assets = debugOnly))!!.apkUrl)
    }
}
