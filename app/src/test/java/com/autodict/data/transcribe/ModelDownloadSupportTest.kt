package com.autodict.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelDownloadSupportTest {

    @Test
    fun rangeHeaderForFreshDownloadIsNull() {
        assertNull(ModelDownloadSupport.rangeHeader(0))
    }

    @Test
    fun rangeHeaderResumesFromOffset() {
        assertEquals("bytes=1048576-", ModelDownloadSupport.rangeHeader(1_048_576))
    }

    @Test
    fun downloadAllowedRespectsWifiOnly() {
        // Wi-Fi: alltid lov.
        assertTrue(ModelDownloadSupport.downloadAllowed(onWifi = true, wifiOnly = true))
        assertTrue(ModelDownloadSupport.downloadAllowed(onWifi = true, wifiOnly = false))
        // Mobildata: berre lov når «kun Wi-Fi» er av.
        assertFalse(ModelDownloadSupport.downloadAllowed(onWifi = false, wifiOnly = true))
        assertTrue(ModelDownloadSupport.downloadAllowed(onWifi = false, wifiOnly = false))
    }

    @Test
    fun sha256MatchesKnownVector() {
        val tmp = File.createTempFile("hash", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelDownloadSupport.sha256Hex(tmp),
        )
    }
}
