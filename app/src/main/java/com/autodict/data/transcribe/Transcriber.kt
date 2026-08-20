package com.autodict.data.transcribe

/** Utfallet av eit transkripsjons-forsøk. */
sealed interface TranscriptionResult {
    /** [modelId] går rett i frontmatter-feltet `model:`. */
    data class Success(val text: String, val modelId: String) : TranscriptionResult

    data class Failure(val message: String) : TranscriptionResult
}

/**
 * Tale → tekst. Implementasjonar: [WhisperTranscriber] (offline, whisper.cpp) i M4;
 * seinare kan andre motorar leggjast bak same kontrakt.
 *
 * Motoren tek ferdig dekoda lyd, ikkje ei fil – filformata (WAV, Opus) er
 * [com.autodict.data.audio.AudioLoader] sitt ansvar.
 *
 * @param samples mono float-PCM i [-1, 1].
 * @param sampleRate raten [samples] ligg i; motoren resamplar sjølv ved behov.
 * @param language målform frå oppføringa (`no`/`nb`/`nn`) – sjå [WhisperLanguage].
 */
interface Transcriber {
    suspend fun transcribe(samples: FloatArray, sampleRate: Int, language: String): TranscriptionResult
}

/**
 * Målform-koden whisper.cpp forventar. Whisper skil på norsk (`no`) og nynorsk (`nn`);
 * `nb` finst ikkje i modellen sitt språksett og blir mappa til `no`.
 */
internal object WhisperLanguage {

    fun forEntry(language: String?): String {
        val value = language?.trim()?.lowercase().orEmpty()
        return when (value) {
            "" -> "no"
            "nn", "nno", "nynorsk" -> "nn"
            "nb", "nob", "no", "nor", "norsk", "bokmål", "bokmaal" -> "no"
            else -> value
        }
    }
}

/**
 * Slår transkriptet saman med teksten som alt ligg i oppføringa.
 *
 * - Var oppføringa alt transkribert, **erstattar** vi teksten (re-transkripsjon skal ikkje
 *   duplisere maskin-teksten).
 * - Elles legg vi transkriptet under eventuell manuelt skriven tekst, så ingenting går tapt.
 */
internal object TranscriptMerge {

    fun merge(body: String, transcript: String, alreadyTranscribed: Boolean): String {
        val clean = transcript.trim()
        val existing = body.trim()
        return when {
            clean.isEmpty() -> body
            alreadyTranscribed || existing.isEmpty() -> clean
            else -> "$existing\n\n$clean"
        }
    }
}
