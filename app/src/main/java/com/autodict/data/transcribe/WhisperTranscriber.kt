package com.autodict.data.transcribe

import com.autodict.data.audio.WavParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Samplingsraten whisper krev; [com.autodict.data.audio.AudioRecorder] tek opp i denne raten. */
private const val REQUIRED_SAMPLE_RATE = 16_000

/**
 * Offline transkripsjon med whisper.cpp via [WhisperJni].
 *
 * Modellen blir lasta ein gong og halden i minnet mellom kall; byter brukaren storleik,
 * frigjer vi den gamle konteksten først. Ein [Mutex] gjer køyringa sekvensiell – whisper
 * er tung, og to samtidige køyringar ville berre slåst om CPU-en (same prinsipp som den
 * planlagde `LlmEngine` i M8).
 *
 * Sjølve inferensen kan berre verifiserast på ei arm64-eining; CI kompilerer koden.
 */
class WhisperTranscriber(
    private val downloader: ModelDownloader,
    private val selectedModel: suspend () -> WhisperModel,
) : Transcriber {

    private val mutex = Mutex()
    private var loadedModel: WhisperModel? = null
    private var contextPtr: Long = 0L

    override suspend fun transcribe(wavBytes: ByteArray, language: String): TranscriptionResult {
        val model = selectedModel()
        if (!downloader.isDownloaded(model)) {
            return TranscriptionResult.Failure(
                "Modellen «${model.displayName}» er ikkje lasta ned. Gå til Innstillingar.",
            )
        }

        val wav = WavParser.parse(wavBytes)
            ?: return TranscriptionResult.Failure("Klarte ikkje lese lydfila (ventar 16-bits PCM-WAV).")
        if (wav.sampleRate != REQUIRED_SAMPLE_RATE) {
            return TranscriptionResult.Failure(
                "Lyden er ${wav.sampleRate} Hz, men transkripsjon krev $REQUIRED_SAMPLE_RATE Hz.",
            )
        }
        if (wav.samples.isEmpty()) {
            return TranscriptionResult.Failure("Lydfila er tom.")
        }

        return withContext(Dispatchers.Default) {
            mutex.withLock {
                runCatching {
                    val ptr = ensureContext(model)
                    val text = WhisperJni.nativeTranscribe(
                        ptr,
                        wav.samples,
                        WhisperLanguage.forEntry(language),
                    )
                    TranscriptionResult.Success(text.trim(), model.frontmatterId)
                }.getOrElse { error ->
                    release()
                    TranscriptionResult.Failure(error.message ?: "Transkripsjonen feila.")
                }
            }
        }
    }

    /** Frigjer modellen frå minnet (t.d. når skjermen blir forlaten). */
    fun release() {
        if (contextPtr != 0L) {
            runCatching { WhisperJni.nativeFree(contextPtr) }
            contextPtr = 0L
        }
        loadedModel = null
    }

    private fun ensureContext(model: WhisperModel): Long {
        if (contextPtr != 0L && loadedModel == model) return contextPtr
        release()
        val ptr = WhisperJni.nativeInit(downloader.modelFile(model).absolutePath)
        check(ptr != 0L) { "Klarte ikkje laste modellen «${model.displayName}»." }
        contextPtr = ptr
        loadedModel = model
        return ptr
    }
}
