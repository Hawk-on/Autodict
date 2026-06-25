package com.autodict.data.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavWriterTest {

    private fun ascii(bytes: ByteArray, from: Int, len: Int) =
        String(bytes, from, len, Charsets.US_ASCII)

    private fun intLe(bytes: ByteArray, offset: Int) =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun shortLe(bytes: ByteArray, offset: Int) =
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    @Test
    fun header_har_rett_storleik_og_chunk_id_ar() {
        val h = WavWriter.header(sampleRate = 16000, channels = 1, bitsPerSample = 16, dataSize = 0)
        assertEquals(44, h.size)
        assertEquals("RIFF", ascii(h, 0, 4))
        assertEquals("WAVE", ascii(h, 8, 4))
        assertEquals("fmt ", ascii(h, 12, 4))
        assertEquals("data", ascii(h, 36, 4))
    }

    @Test
    fun header_har_rette_pcm_parametrar() {
        val h = WavWriter.header(sampleRate = 16000, channels = 1, bitsPerSample = 16, dataSize = 0)
        assertEquals(16, intLe(h, 16)) // fmt chunk-storleik
        assertEquals(1, shortLe(h, 20)) // PCM-format
        assertEquals(1, shortLe(h, 22)) // kanalar
        assertEquals(16000, intLe(h, 24)) // sample rate
        assertEquals(32000, intLe(h, 28)) // byte rate = 16000*1*16/8
        assertEquals(2, shortLe(h, 32)) // block align = 1*16/8
        assertEquals(16, shortLe(h, 34)) // bits per sample
    }

    @Test
    fun header_storleikar_reflekterer_datasize() {
        val h = WavWriter.header(16000, 1, 16, dataSize = 1000)
        assertEquals(36 + 1000, intLe(h, 4)) // RIFF chunk-storleik
        assertEquals(1000, intLe(h, 40)) // data chunk-storleik
    }

    @Test
    fun intLe_er_little_endian() {
        assertEquals(listOf(1, 0, 0, 0), WavWriter.intLe(1).map { it.toInt() and 0xFF })
    }
}
