package com.autodict.data.transcribe

/**
 * JNI-bru til whisper.cpp (`libautodict-whisper.so`).
 *
 * M4a koplar berre opp byggjet – desse `external`-funksjonane vert fyrst tekne i bruk av
 * [WhisperTranscriber] i M4c. Namna må matche `whisper_jni.cpp`.
 */
object WhisperJni {

    init {
        System.loadLibrary("autodict-whisper")
    }

    /** whisper.cpp sin system-info-streng (backend/SIMD) – nyttig for diagnostikk. */
    external fun nativeSystemInfo(): String

    /** Lastar ein GGML-modell og returnerer ein peikar (0 ved feil). */
    external fun nativeInit(modelPath: String): Long

    /** Transkriberer 16 kHz mono float-PCM med eksplisitt målform (`nn`/`nb`). */
    external fun nativeTranscribe(ctxPtr: Long, audio: FloatArray, language: String): String

    /** Frigjer modell-konteksten. */
    external fun nativeFree(ctxPtr: Long)
}
