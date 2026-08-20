package com.autodict.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Les ei lydfil til mono float-PCM, uansett format.
 *
 * WAV går gjennom den reine [WavParser] (rask, unit-testa). Alt anna – i praksis `.opus`
 * frå arkivet (M3b) – blir dekoda med plattforma sin [MediaCodec], slik at ei oppføring
 * kan transkriberast om att etter at WAV-en er borte.
 */
object AudioLoader {

    private const val TAG = "autodict-audio"
    private const val TIMEOUT_US = 10_000L

    /** Lyd frå dagbok-mappa (SAF). */
    suspend fun load(context: Context, uri: Uri): ParsedWav? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        if (bytes == null) Log.w(TAG, "Klarte ikkje lese bytes frå $uri")

        // WAV først – då slepp vi plattform-dekodaren heilt.
        bytes?.let(WavParser::parse)?.let { return@withContext it }

        runCatching { decodeCompressed { it.setDataSource(context, uri, null) } }
            .onFailure { Log.e(TAG, "Dekoding feila for $uri", it) }
            .getOrNull()
            .also { if (it == null) Log.w(TAG, "Fann ingen dekodbar lyd i $uri") }
    }

    /** Lyd frå ei lokal fil (t.d. cache-WAV-en rett etter opptak). */
    suspend fun load(file: File): ParsedWav? = withContext(Dispatchers.IO) {
        runCatching { file.readBytes() }.getOrNull()?.let(WavParser::parse)?.let { return@withContext it }
        runCatching { decodeCompressed { it.setDataSource(file.absolutePath) } }
            .onFailure { Log.e(TAG, "Dekoding feila for ${file.absolutePath}", it) }
            .getOrNull()
            .also { if (it == null) Log.w(TAG, "Fann ingen dekodbar lyd i ${file.absolutePath}") }
    }

    /**
     * Dekodar eit komprimert format (Opus/AAC/…) til mono float via [MediaCodec].
     * Køyretidskode – kan berre verifiserast på eining.
     */
    private fun decodeCompressed(setSource: (MediaExtractor) -> Unit): ParsedWav? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            setSource(extractor)

            var trackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    inputFormat = format
                    break
                }
            }
            val format = inputFormat ?: return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val output = ArrayList<Float>(sampleRate * 30)
            val info = MediaCodec.BufferInfo()
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val read = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }

                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            appendMonoFloat(buffer, info.offset, info.size, channels, output)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }

            if (output.isEmpty()) {
                Log.w(TAG, "Dekodaren ga ingen sample (mime=$mime, rate=$sampleRate, kanalar=$channels)")
                return null
            }
            Log.i(TAG, "Dekoda $mime: ${output.size} sample @ ${sampleRate}Hz")
            return ParsedWav(FloatArray(output.size) { output[it] }, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Dekoding kasta unntak", e)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** 16-bits PCM frå dekodaren → mono float i [-1, 1]. */
    private fun appendMonoFloat(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        channels: Int,
        out: MutableList<Float>,
    ) {
        val shorts = buffer.duplicate()
            .order(ByteOrder.LITTLE_ENDIAN)
            .position(offset)
            .limit(offset + size)
            .let { (it as ByteBuffer).slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer() }

        val safeChannels = channels.coerceAtLeast(1)
        val frames = shorts.remaining() / safeChannels
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until safeChannels) sum += shorts.get().toInt()
            out.add((sum.toFloat() / safeChannels) / 32_768f)
        }
    }
}
