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
    data class Recording(val elapsedMs: Long, val amplitude: Float) : RecorderState
    data class Paused(val elapsedMs: Long) : RecorderState

    /** Lengda så langt – 0 når det ikkje er noko opptak i gang. */
    val elapsedMsOrZero: Long
        get() = when (this) {
            Idle -> 0L
            is Recording -> elapsedMs
            is Paused -> elapsedMs
        }

    val isActive: Boolean get() = this !is Idle
}

/**
 * Tek opp lyd med [AudioRecord] som rå mono 16-bit PCM, skrive direkte som WAV.
 *
 * **Samplingsrate:** vi tek opp i telefonen si native rate (48 kHz) i staden for whisper
 * sine 16 kHz. Grunnen er arkivet: 16 kHz gir berre 8 kHz båndbredde, som gjer konsonantar
 * (s/f/sj) matte – dagboka skal vere verdt å høyre på om mange år. [AudioResampler] tek
 * lyden ned til 16 kHz når han skal transkriberast.
 *
 * **Bitdjupn:** 16-bit (96 dB dynamikk) er langt meir enn mikrofonen i ein telefon leverer
 * (~65 dB SNR), så 24/32-bit ville berre gitt større filer utan hørbar gevinst.
 *
 * MVP: opptak i prosess på ein bakgrunnstråd. TODO (M10): flytt til ein foreground service
 * så opptak overlever skjerm av / app i bakgrunn (jf. designprinsipp i CLAUDE.md).
 *
 * Innringaren må ha RECORD_AUDIO-løyve før [start] blir kalla.
 */
class AudioRecorder {

    /** Ratar vi prøver i tur og orden; første som eininga godtek blir brukt. */
    private val preferredRates = intArrayOf(48_000, 44_100, AudioResampler.WHISPER_SAMPLE_RATE)

    private var sampleRate = preferredRates.first()
    private val channels = 1
    private val bitsPerSample = 16
    private val bytesPerSecond get() = sampleRate * channels * bitsPerSample / 8

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var outFile: File? = null
    private val totalBytes = AtomicLong(0)

    @SuppressLint("MissingPermission") // innringar sikrar RECORD_AUDIO
    fun start(file: File): Boolean {
        if (running) return false

        val opened = openRecorder() ?: return false
        val recorder = opened.recorder
        val bufferSize = opened.bufferSize
        sampleRate = opened.sampleRate

        val raf = RandomAccessFile(file, "rw").apply {
            setLength(0)
            write(WavWriter.header(sampleRate, channels, bitsPerSample, dataSize = 0))
        }

        record = recorder
        outFile = file
        totalBytes.set(0)
        running = true
        paused = false
        recorder.startRecording()
        _state.value = RecorderState.Recording(0, 0f)

        thread = Thread {
            val buffer = ByteArray(bufferSize)
            try {
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Under pause held vi lesinga i gang, men kastar lyden. Å stoppe
                        // AudioRecord ville gitt eit klikk i overgangen når han startar att.
                        if (paused) {
                            _state.value = RecorderState.Paused(elapsedMs())
                            continue
                        }

                        raf.write(buffer, 0, read)
                        totalBytes.addAndGet(read.toLong())

                        // RMS-nivå til nivåmålaren i UI-et.
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            if (i + 1 < read) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                val shortSample = sample.toShort().toDouble()
                                sum += shortSample * shortSample
                            }
                        }
                        val rms = if (read > 0) Math.sqrt(sum / (read / 2)) else 0.0
                        val amplitude = (rms / Short.MAX_VALUE.toDouble()).toFloat().coerceIn(0f, 1f)

                        _state.value = RecorderState.Recording(elapsedMs(), amplitude)
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

    /**
     * Lengda utleidd frå talet på skrivne byte, ikkje frå klokka. Det gir nøyaktig den
     * lengda lydfila faktisk har, og pausar blir automatisk haldne utanfor.
     */
    private fun elapsedMs(): Long = totalBytes.get() * 1000 / bytesPerSecond

    /** Set opptaket på pause. Lyden under pausen blir forkasta. */
    fun pause() {
        if (!running || paused) return
        paused = true
        _state.value = RecorderState.Paused(elapsedMs())
    }

    fun resume() {
        if (!running || !paused) return
        paused = false
        _state.value = RecorderState.Recording(elapsedMs(), 0f)
    }

    private class OpenedRecorder(
        val recorder: AudioRecord,
        val sampleRate: Int,
        val bufferSize: Int,
    )

    /**
     * Opnar [AudioRecord] på den beste raten eininga faktisk godtek. Ikkje alle telefonar
     * støttar alle ratar, så vi fell tilbake i staden for å feile.
     */
    @SuppressLint("MissingPermission") // innringar sikrar RECORD_AUDIO
    private fun openRecorder(): OpenedRecorder? {
        for (rate in preferredRates) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue

            val bufferSize = minBuffer * 2
            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            }.getOrNull() ?: continue

            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                return OpenedRecorder(recorder, rate, bufferSize)
            }
            recorder.release()
        }
        return null
    }

    /** Stoppar opptaket og returnerer resultatet, eller null om ingenting var i gang. */
    fun stop(): RecordingResult? {
        val file = halt() ?: return null
        val durationSeconds = (totalBytes.get() / bytesPerSecond).toInt()
        return RecordingResult(file, durationSeconds)
    }

    /** Stoppar og slettar opptaket. Brukt av «Forkast» medan ein tek opp. */
    fun discard() {
        halt()?.let { runCatching { it.delete() } }
    }

    /** Felles nedstenging: stopp tråden, frigi AudioRecord, og gi tilbake fila. */
    private fun halt(): File? {
        if (!running) return null
        running = false
        paused = false
        thread?.join()
        thread = null
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
        _state.value = RecorderState.Idle

        return outFile.also { outFile = null }
    }
}
