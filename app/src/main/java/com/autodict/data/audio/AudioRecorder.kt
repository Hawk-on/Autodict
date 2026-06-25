package com.autodict.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/** Resultatet av eit fullført opptak. */
data class RecordingResult(val file: File, val durationSeconds: Int)

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val elapsedMs: Long) : RecorderState
}

/**
 * Tek opp lyd med [AudioRecord] som rå 16 kHz mono 16-bit PCM, skrive direkte som WAV –
 * akkurat formatet whisper.cpp vil ha (M4), så ingen resampling seinare.
 *
 * MVP: opptak i prosess på ein bakgrunnstråd. TODO (M10): flytt til ein foreground service
 * så opptak overlever skjerm av / app i bakgrunn (jf. designprinsipp i CLAUDE.md).
 *
 * Innringaren må ha RECORD_AUDIO-løyve før [start] blir kalla.
 */
class AudioRecorder {

    private val sampleRate = 16_000
    private val channels = 1
    private val bitsPerSample = 16
    private val bytesPerSecond = sampleRate * channels * bitsPerSample / 8

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var outFile: File? = null
    private val totalBytes = AtomicLong(0)

    @SuppressLint("MissingPermission") // innringar sikrar RECORD_AUDIO
    fun start(file: File): Boolean {
        if (running) return false

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else bytesPerSecond

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        val raf = RandomAccessFile(file, "rw").apply {
            setLength(0)
            write(WavWriter.header(sampleRate, channels, bitsPerSample, dataSize = 0))
        }

        record = recorder
        outFile = file
        totalBytes.set(0)
        running = true
        recorder.startRecording()
        _state.value = RecorderState.Recording(0)

        val startedAt = System.currentTimeMillis()
        thread = Thread {
            val buffer = ByteArray(bufferSize)
            try {
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                        totalBytes.addAndGet(read.toLong())
                        _state.value = RecorderState.Recording(System.currentTimeMillis() - startedAt)
                    }
                }
            } finally {
                val data = totalBytes.get().toInt()
                raf.seek(4)
                raf.write(WavWriter.intLe(36 + data))
                raf.seek(40)
                raf.write(WavWriter.intLe(data))
                raf.close()
            }
        }.also { it.start() }
        return true
    }

    /** Stoppar opptaket og returnerer resultatet, eller null om ingenting var i gang. */
    fun stop(): RecordingResult? {
        if (!running) return null
        running = false
        thread?.join()
        thread = null
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        _state.value = RecorderState.Idle

        val file = outFile ?: return null
        outFile = null
        val durationSeconds = (totalBytes.get() / bytesPerSecond).toInt()
        return RecordingResult(file, durationSeconds)
    }
}
