package com.autodict.data.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Delt eigar av opptaket, slik at foreground-tenesta og UI-et ser same tilstand.
 *
 * Grunnen til at dette er ein singleton og ikkje eit felt i ein ViewModel: frå og med
 * API 29 kan ein app i bakgrunnen ikkje lese frå mikrofonen utan ei foreground service av
 * typen `microphone`. Opptaket må difor eigast av tenesta, medan skjermen berre observerer.
 * Låser du telefonen midt i eit opptak, held tenesta fram; ViewModel-en kan bli rydda utan
 * at det gjer noko.
 *
 * Ingen synkronisering her: [AudioRecorder] er alt trygg på tvers av trådar (flagga er
 * `@Volatile`, tellaren er atomisk), og start/stopp kjem alltid frå hovudtråden.
 */
object RecordingController {

    private val recorder = AudioRecorder()

    val state: StateFlow<RecorderState> = recorder.state

    private val _result = MutableStateFlow<RecordingResult?>(null)

    /** Sett når eit opptak er stoppa. UI-et konsumerer det og går til utkastet. */
    val result: StateFlow<RecordingResult?> = _result.asStateFlow()

    /** Når opptaket starta – brukt som tidspunkt for oppføringa. */
    @Volatile
    var startedAtMillis: Long = 0L
        private set

    fun start(file: File): Boolean {
        startedAtMillis = System.currentTimeMillis()
        return recorder.start(file)
    }

    fun stop() {
        _result.value = recorder.stop()
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    fun discard() = recorder.discard()

    fun consumeResult() {
        _result.value = null
    }
}
