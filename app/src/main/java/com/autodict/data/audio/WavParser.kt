package com.autodict.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ferdig dekoda WAV: mono float-samples i [-1, 1] + samplingsrate. */
class ParsedWav(val samples: FloatArray, val sampleRate: Int)

/**
 * Les ei PCM-WAV-fil til mono float-samples – formatet whisper.cpp krev.
 *
 * Rein funksjon utan Android-API → unit-testbar. Vi går gjennom RIFF-chunkane i staden for
 * å anta ein fast 44-byte header, slik at filer med ekstra chunkar (LIST/fact) òg går bra.
 * Fleirkanals lyd blir mikst ned til mono. Ingen resampling: [ParsedWav.sampleRate] blir
 * returnert som han er, og kallaren avgjer om raten er brukbar.
 */
object WavParser {

    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val FULL_SCALE = 32_768f

    /** Returnerer null om dette ikkje er ei lesbar 16-bits PCM-WAV-fil. */
    fun parse(bytes: ByteArray): ParsedWav? {
        if (bytes.size < 44) return null
        if (tagAt(bytes, 0) != "RIFF" || tagAt(bytes, 8) != "WAVE") return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = tagAt(bytes, pos)
            val size = buffer.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body > bytes.size) return null

            when (id) {
                "fmt " -> {
                    if (body + 16 > bytes.size) return null
                    format = buffer.getShort(body).toInt()
                    channels = buffer.getShort(body + 2).toInt()
                    sampleRate = buffer.getInt(body + 4)
                    bitsPerSample = buffer.getShort(body + 14).toInt()
                }

                "data" -> {
                    dataOffset = body
                    // Toler at storleiken i headeren er større enn fila (avbrote opptak).
                    dataSize = minOf(size, bytes.size - body)
                }
            }
            // RIFF-chunkar er padda til partal lengd.
            pos = body + size + (size and 1)
        }

        if (format != WavWriter.PCM_FORMAT) return null
        if (bitsPerSample != BITS_PER_SAMPLE) return null
        if (channels < 1 || sampleRate <= 0) return null
        if (dataOffset < 0 || dataSize <= 0) return null

        val frameBytes = BYTES_PER_SAMPLE * channels
        val frames = dataSize / frameBytes
        val samples = FloatArray(frames)
        var read = dataOffset
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                sum += buffer.getShort(read).toInt()
                read += BYTES_PER_SAMPLE
            }
            samples[i] = (sum.toFloat() / channels) / FULL_SCALE
        }
        return ParsedWav(samples, sampleRate)
    }

    private fun tagAt(bytes: ByteArray, offset: Int): String =
        if (offset + 4 > bytes.size) "" else String(bytes, offset, 4, Charsets.US_ASCII)
}
