package com.autodict.data.transcribe

import android.util.Log

/**
 * JNI-bru til whisper.cpp (`libautodict-whisper.so`).
 *
 * Lastinga er **feilsikra med vilje**: gjekk `System.loadLibrary` gale i eit `init`-blokk
 * som kastar, ville objektet vore permanent ubrukeleg og kvart seinare kall gitt
 * `NoClassDefFoundError` med tom melding – nesten umogleg å feilsøkje frå UI-et. No lastar
 * objektet alltid, og [loadError] fortel kva som eventuelt gjekk gale.
 */
object WhisperJni {

    private const val TAG = "autodict-whisper"

    /** Null når det native biblioteket er lasta; elles ei lesbar årsak. */
    val loadError: String?

    val isAvailable: Boolean get() = loadError == null

    init {
        loadError = try {
            System.loadLibrary("autodict-whisper")
            Log.i(TAG, "libautodict-whisper.so lasta")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Klarte ikkje laste libautodict-whisper.so", t)
            describe(t)
        }
    }

    /** whisper.cpp sin system-info-streng (backend/SIMD) – nyttig for diagnostikk. */
    external fun nativeSystemInfo(): String

    /** Lastar ein GGML-modell og returnerer ein peikar (0 ved feil). */
    external fun nativeInit(modelPath: String): Long

    /** Transkriberer 16 kHz mono float-PCM med eksplisitt målform (`nn`/`no`). */
    external fun nativeTranscribe(ctxPtr: Long, audio: FloatArray, language: String): String

    /** Frigjer modell-konteksten. */
    external fun nativeFree(ctxPtr: Long)

    /** Kort systeminfo for diagnostikk, eller årsaka til at han ikkje kan hentast. */
    fun systemInfoOrError(): String =
        loadError?.let { "Native bibliotek ikkje lasta: $it" }
            ?: runCatching { nativeSystemInfo() }.getOrElse { describe(it) }
}

/**
 * `Throwable.message` er ofte null (særleg for `UnsatisfiedLinkError`), så vi tek alltid med
 * klassenamnet. Utan dette blir feilmeldinga i UI-et berre «Transkripsjonen feila».
 */
internal fun describe(t: Throwable): String {
    val name = t::class.java.simpleName
    val message = t.message?.takeIf { it.isNotBlank() }
    return if (message != null) "$name: $message" else name
}
