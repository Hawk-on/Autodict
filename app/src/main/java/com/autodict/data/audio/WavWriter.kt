package com.autodict.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Byggjer ein standard 44-byte WAV/PCM-header. Rein funksjon → unit-testbar.
 *
 * [AudioRecorder] skriv ein placeholder-header først, strøymer PCM, og patchar deretter
 * storleikane (offset 4 og 40) når opptaket er ferdig.
 */
object WavWriter {

    const val PCM_FORMAT = 1

    fun header(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataSize: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // fmt chunk-storleik for PCM
            putShort(PCM_FORMAT.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
    }

    /** 4-byte little-endian – brukt til å patche RIFF-/data-storleikane etter opptak. */
    fun intLe(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
