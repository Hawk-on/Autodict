package com.autodict.data.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Kodar eit WAV-opptak om til **Opus i OGG-container** – arkivformatet for dagboka.
 *
 * Tale i Opus på ~32 kbit/s er perseptuelt utmerkt og tek ~0,24 MB/min, mot 5,6 MB/min for
 * 48 kHz WAV. Vi transkriberer frå den tapsfrie WAV-en *før* koding, så modellen aldri ser
 * komprimert lyd.
 *
 * Krev API 29 (`MUXER_OUTPUT_OGG`). PCM-dataen blir strøyma frå fila i blokker, så lange
 * opptak ikkje sprenger minnet.
 *
 * MediaCodec-oppførsel varierer mellom einingar, så [encode] returnerer false i staden for
 * å kaste; kallaren skal då behalde WAV-en (aldri mist opptaket).
 */
object OpusEncoder {

    const val FILE_EXTENSION = "opus"
    const val MIME_TYPE = "audio/opus"

    /** Standard bitrate for tale. Opus er svært effektiv her. */
    const val DEFAULT_BITRATE = 32_000

    /** Samplingsratar Opus støttar direkte. */
    private val SUPPORTED_RATES = intArrayOf(8_000, 12_000, 16_000, 24_000, 48_000)

    private const val HEADER_PROBE_BYTES = 64 * 1024
    private const val TIMEOUT_US = 10_000L

    /**
     * @return true når [target] er ei ferdig Opus-fil. Ved false skal kallaren bruke WAV-en.
     */
    fun encode(source: File, target: File, bitrate: Int = DEFAULT_BITRATE): Boolean {
        val probe = runCatching {
            source.inputStream().use { input ->
                val buffer = ByteArray(minOf(HEADER_PROBE_BYTES, source.length().toInt().coerceAtLeast(44)))
                input.read(buffer)
                buffer
            }
        }.getOrNull() ?: return false

        val format = WavParser.readFormat(probe) ?: return false
        if (format.sampleRate !in SUPPORTED_RATES) return false

        return runCatching { encodeStream(source, target, format, bitrate) }
            .getOrElse {
                target.delete()
                false
            }
    }

    private fun encodeStream(source: File, target: File, wav: WavFormat, bitrate: Int): Boolean {
        val mediaFormat = MediaFormat.createAudioFormat(MIME_TYPE, wav.sampleRate, wav.channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        val input = RandomAccessFile(source, "r")

        var trackIndex = -1
        var muxerStarted = false
        var success = false

        try {
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            input.seek(wav.dataOffset.toLong())
            val available = (source.length() - wav.dataOffset)
                .coerceAtMost(wav.declaredDataSize.toLong().takeIf { it > 0 } ?: Long.MAX_VALUE)

            val bytesPerFrame = wav.channels * 2 // 16-bit
            val info = MediaCodec.BufferInfo()
            var bytesRead = 0L
            var presentationUs = 0L
            var inputDone = false

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        if (buffer == null) {
                            codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.clear()
                            val chunk = ByteArray(minOf(buffer.capacity().toLong(), available - bytesRead).toInt().coerceAtLeast(0))
                            val read = if (chunk.isEmpty()) -1 else input.read(chunk)
                            if (read <= 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.put(chunk, 0, read)
                                codec.queueInputBuffer(inIndex, 0, read, presentationUs, 0)
                                bytesRead += read
                                presentationUs += 1_000_000L * (read / bytesPerFrame) / wav.sampleRate
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Opus-hovuda (codec-specific data) ligg i dette formatet – muxeren
                        // treng dei før noko blir skrive.
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    outIndex >= 0 -> {
                        val encoded: ByteBuffer? = codec.getOutputBuffer(outIndex)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (encoded != null && info.size > 0 && !isConfig && muxerStarted) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            success = muxerStarted
                            break
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { input.close() }
        }

        if (!success || !target.exists() || target.length() <= 0) {
            target.delete()
            return false
        }
        return true
    }
}
