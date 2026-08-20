package com.autodict.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autodict.data.audio.AudioLoader
import com.autodict.data.audio.OpusEncoder
import com.autodict.data.diary.SaveStage
import com.autodict.data.diary.createDiaryRepository
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.StoragePaths
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.TranscriberHolder
import com.autodict.data.transcribe.TranscriptMerge
import com.autodict.data.transcribe.TranscriptionResult
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class EditUiState(
    val title: String = "",
    val body: String = "",
    val saving: Boolean = false,
    /** Kva lagringa held på med no – vist medan ein ventar (kan ta tid mot Drive). */
    val saveStage: SaveStage? = null,
    val error: String? = null,
    val audioPath: String? = null,
    val language: TargetLanguage = TargetLanguage.DEFAULT,
    val transcribing: Boolean = false,
    val message: String? = null,
    /** Modell-id når teksten kjem frå transkripsjon – går i frontmatter ved lagring. */
    val transcribedModel: String? = null,
)

/**
 * Redigerer eit nytt utkast (frå opptak) og lagrar det som ei oppføring.
 * Argumenta (audio-sti, opptakstid, lengd) kjem frå navigasjonen via [SavedStateHandle].
 *
 * Frå M4d kan utkastet transkriberast **før** lagring, slik at du ser og kan rette teksten
 * med ein gong – i staden for å måtte lagre, opne oppføringa og transkribere der.
 */
class EntryEditViewModel(
    app: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)
    private val settings = AppSettings(app)
    private val transcriber = TranscriberHolder.acquire(app)

    private val audioPath: String? = handle.get<String>("audio")?.takeIf { it.isNotBlank() }
    private val createdMillis: Long = handle.get<String>("created")?.toLongOrNull() ?: System.currentTimeMillis()
    private val durationSeconds: Int = handle.get<String>("duration")?.toIntOrNull() ?: 0

    private val _ui = MutableStateFlow(EditUiState(audioPath = audioPath))
    val ui: StateFlow<EditUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val language = TargetLanguage.fromCode(settings.transcriptionLanguage.first())
            _ui.update { it.copy(language = language) }
            if (audioPath != null && settings.autoTranscribe.first()) {
                transcribe()
            }
        }
    }

    fun onTitleChange(value: String) = _ui.update { it.copy(title = value) }
    fun onBodyChange(value: String) = _ui.update { it.copy(body = value) }

    fun onLanguageChange(language: TargetLanguage) = _ui.update { it.copy(language = language) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    /** Transkriberer cache-WAV-en og legg teksten i tekstfeltet, klar til retting. */
    fun transcribe() {
        val path = audioPath ?: return
        if (_ui.value.transcribing) return

        viewModelScope.launch {
            _ui.update { it.copy(transcribing = true, message = "Transkriberer …") }

            // Transkriberer frå den tapsfrie cache-WAV-en, før Opus-koding ved lagring.
            val audio = AudioLoader.load(File(path))
            if (audio == null) {
                _ui.update { it.copy(transcribing = false, message = "Fann ikkje opptaket.") }
                return@launch
            }

            when (val result = transcriber.transcribe(audio.samples, audio.sampleRate, _ui.value.language.code)) {
                is TranscriptionResult.Failure ->
                    _ui.update { it.copy(transcribing = false, message = result.message) }

                is TranscriptionResult.Success -> _ui.update { state ->
                    if (result.text.isBlank()) {
                        state.copy(transcribing = false, message = "Fann ingen tale i opptaket.")
                    } else {
                        state.copy(
                            transcribing = false,
                            // Ny transkripsjon erstattar førre, men lèt manuell tekst stå.
                            body = TranscriptMerge.merge(
                                body = state.body,
                                transcript = result.text,
                                alreadyTranscribed = state.transcribedModel != null,
                            ),
                            transcribedModel = result.modelId,
                            message = "Transkribert (${state.language.displayName.lowercase()}).",
                        )
                    }
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true, saveStage = SaveStage.EncodingAudio, error = null) }
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(createdMillis)
            val localDateTime = instant.atZone(zone).toLocalDateTime()
            val state = _ui.value
            val title = state.title.trim()

            val id = StoragePaths.entrySlug(localDateTime, title.ifBlank { null })
            val createdIso = OffsetDateTime.ofInstant(instant, zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            // Sjølve skrivinga er NonCancellable: blir denne ViewModel-en rydda medan vi
            // held på (appen swipa vekk, aktiviteten avslutta), skal vi ikkje etterlate ei
            // .md-fil utan lyd. Arbeidet er avgrensa, så det er trygt å la det fullføre.
            val ok = withContext(NonCancellable) {
                // Arkiver som Opus (~1/23 av plassen). Feilar kodinga, lagrar vi WAV-en –
                // eit opptak skal aldri gå tapt fordi ein kodek oppførte seg uventa.
                val archive = audioPath?.let { prepareArchive(File(it)) }

                val entry = DiaryEntry(
                    id = id,
                    created = createdIso,
                    title = title.ifBlank { "Utan tittel" },
                    audio = archive?.let { "$id.${it.extension}" },
                    durationSeconds = durationSeconds,
                    language = state.language.code,
                    transcribed = state.transcribedModel != null,
                    model = state.transcribedModel,
                    tags = emptyList(),
                    body = state.body.trim(),
                )

                val saved = repo.save(entry, archive?.file) { stage ->
                    _ui.update { it.copy(saveStage = stage) }
                }
                if (saved) {
                    // Rydd cache: både originalopptaket og ei eventuell Opus-fil.
                    audioPath?.let { runCatching { File(it).delete() } }
                    archive?.file?.let { runCatching { if (it.path != audioPath) it.delete() } }
                }
                saved
            }

            // Vart scopet kansellert undervegs, kastar denne linja – lagringa over er
            // fullført, men vi skal ikkje navigere i ein nav-graf som ikkje finst lenger.
            ensureActive()

            if (ok) {
                onSaved()
            } else {
                _ui.update {
                    it.copy(
                        saving = false,
                        saveStage = null,
                        error = "Klarte ikkje lagre. Har du vald ei lagringsmappe (Innstillingar)?",
                    )
                }
            }
        }
    }

    private class Archive(val file: File, val extension: String)

    /**
     * Vel arkivfila: Opus når kodinga går bra, elles den tapsfrie WAV-en. Brukaren kan
     * òg velje WAV eksplisitt i innstillingane.
     */
    private suspend fun prepareArchive(wav: File): Archive {
        if (settings.keepOriginalWav.first()) return Archive(wav, "wav")

        val opus = File(wav.parentFile, "${wav.nameWithoutExtension}.${OpusEncoder.FILE_EXTENSION}")
        val encoded = withContext(Dispatchers.Default) { OpusEncoder.encode(wav, opus) }
        return if (encoded) Archive(opus, OpusEncoder.FILE_EXTENSION) else Archive(wav, "wav")
    }

    override fun onCleared() {
        TranscriberHolder.release()
        super.onCleared()
    }
}
