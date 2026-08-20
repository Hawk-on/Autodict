package com.autodict.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavParserTest {

    /** Byggjer ei WAV-fil frå 16-bits samples (kanal-fletta), slik AudioRecorder gjer. */
    private fun wav(
        samples: ShortArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            samples.forEach { putShort(it) }
        }.array()
        return WavWriter.header(sampleRate, channels, bitsPerSample, data.size) + data
    }

    @Test
    fun parsesMonoRoundTrip() {
        val parsed = WavParser.parse(wav(shortArrayOf(0, 16_384, -16_384)))

        assertNotNull(parsed)
        assertEquals(16_000, parsed!!.sampleRate)
        assertEquals(3, parsed.samples.size)
        assertEquals(0f, parsed.samples[0], 1e-6f)
        assertEquals(0.5f, parsed.samples[1], 1e-6f)
        assertEquals(-0.5f, parsed.samples[2], 1e-6f)
    }

    @Test
    fun downmixesStereoToMono() {
        // To frames: (1000, 3000) og (-2000, 0) → snitt 2000 og -1000.
        val parsed = WavParser.parse(wav(shortArrayOf(1000, 3000, -2000, 0), channels = 2))

        assertNotNull(parsed)
        assertEquals(2, parsed!!.samples.size)
        assertEquals(2000f / 32768f, parsed.samples[0], 1e-6f)
        assertEquals(-1000f / 32768f, parsed.samples[1], 1e-6f)
    }

    @Test
    fun skipsUnknownChunkBeforeData() {
        val base = wav(shortArrayOf(16_384))
        val list = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("LIST".toByteArray(Charsets.US_ASCII))
            putInt(4)
            put("INFO".toByteArray(Charsets.US_ASCII))
        }.array()
        // Splis LIST-chunken inn rett før "data"-chunken (offset 36 i standardheaderen).
        val spliced = base.copyOfRange(0, 36) + list + base.copyOfRange(36, base.size)

        val parsed = WavParser.parse(spliced)

        assertNotNull(parsed)
        assertEquals(1, parsed!!.samples.size)
        assertEquals(0.5f, parsed.samples[0], 1e-6f)
    }

    @Test
    fun toleratesDataSizeLargerThanFile() {
        // Avbrote opptak: headeren lovar meir data enn fila faktisk har.
        val bytes = wav(shortArrayOf(16_384, -16_384))
        val patched = bytes.copyOf()
        ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 9_999)

        val parsed = WavParser.parse(patched)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.samples.size)
    }

    @Test
    fun rejectsNonWavData() {
        assertNull(WavParser.parse(ByteArray(100)))
        assertNull(WavParser.parse("ikkje ei wav-fil".toByteArray()))
    }

    @Test
    fun rejectsUnsupportedBitDepth() {
        assertNull(WavParser.parse(wav(shortArrayOf(1, 2, 3), bitsPerSample = 8)))
    }
}
