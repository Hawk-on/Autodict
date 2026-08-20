package com.autodict.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ferdig dekoda lyd: mono float-samples i [-1, 1] + samplingsrate. */
class ParsedWav(val samples: FloatArray, val sampleRate: Int)

/**
 * Formatet til ei WAV-fil og kvar PCM-dataen ligg. Lèt [OpusEncoder] strøyme store filer
 * utan å laste heile opptaket i minnet.
 */
data class WavFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val declaredDataSize: Int,
)

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

    /**
     * Les format og posisjonen til PCM-dataen. Fungerer på ein *prefiks* av fila (chunk-
     * hovuda ligg alltid før innhaldet), så store opptak kan strøymast i staden for å
     * lastast heilt inn.
     */
    fun readFormat(bytes: ByteArray): WavFormat? {
        if (bytes.size < 44) return null
        if (tagAt(bytes, 0) != "RIFF" || tagAt(bytes, 8) != "WAVE") return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var declaredDataSize = 0

        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = tagAt(bytes, pos)
            val size = buffer.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body > bytes.size) break

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
                    declaredDataSize = size
                }
            }
            if (dataOffset >= 0) break // alt vi treng er funne
            // RIFF-chunkar er padda til partal lengd.
            pos = body + size + (size and 1)
        }

        if (format != WavWriter.PCM_FORMAT) return null
        if (bitsPerSample != BITS_PER_SAMPLE) return null
        if (channels < 1 || sampleRate <= 0) return null
        if (dataOffset < 0) return null
        return WavFormat(sampleRate, channels, bitsPerSample, dataOffset, declaredDataSize)
    }

    /** Returnerer null om dette ikkje er ei lesbar 16-bits PCM-WAV-fil. */
    fun parse(bytes: ByteArray): ParsedWav? {
        val info = readFormat(bytes) ?: return null
        val channels = info.channels
        val sampleRate = info.sampleRate
        val dataOffset = info.dataOffset
        // Toler at storleiken i headeren er større enn fila (avbrote opptak).
        val dataSize = minOf(info.declaredDataSize, bytes.size - dataOffset)
        if (dataSize <= 0) return null

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
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
