package com.autodict.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autodict.data.audio.AudioLoader
import com.autodict.data.diary.createDiaryRepository
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.TranscriberHolder
import com.autodict.data.transcribe.TranscriptMerge
import com.autodict.data.transcribe.TranscriptionResult
import com.autodict.data.actions.ActionType
import com.autodict.data.actions.ExtractedAction
import com.autodict.data.actions.RuleBasedExtractor
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class DetailUiState(
    val loading: Boolean = true,
    val entry: DiaryEntry? = null,
    val audioUri: Uri? = null,
    val transcribing: Boolean = false,
    val message: String? = null,
    val extractedActions: List<ExtractedAction> = emptyList(),
)

class EntryDetailViewModel(
    app: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)
    private val transcriber = TranscriberHolder.acquire(app)
    private val extractor = RuleBasedExtractor()
    private val entryId: String = handle.get<String>("entryId").orEmpty()

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = repo.get(entryId)
            _ui.value = DetailUiState(
                loading = false,
                entry = loaded?.entry,
                audioUri = loaded?.audioUri,
            )
        }
    }

    /**
     * Transkriberer lyden offline og skriv resultatet inn i oppføringa
     * (`body` + `transcribed: true` + `model:`).
     *
     * Køyrer i [viewModelScope] – forlèt brukaren skjermen midt i, blir jobben avbroten.
     * Robust bakgrunnskøyring (foreground service / WorkManager) er sett til M10.
     */
    fun transcribe(language: TargetLanguage? = null) {
        val state = _ui.value
        val entry = state.entry ?: return
        val audioUri = state.audioUri ?: return
        if (state.transcribing) return

        val target = language ?: TargetLanguage.fromCode(entry.language)

        viewModelScope.launch {
            _ui.value = _ui.value.copy(transcribing = true, message = "Transkriberer …")

            // Arkivet kan vere Opus (M3b) – AudioLoader dekodar det formatet fila har.
            val audio = AudioLoader.load(getApplication(), audioUri)
            if (audio == null) {
                _ui.value = _ui.value.copy(transcribing = false, message = "Klarte ikkje lese lydfila.")
                return@launch
            }

            when (val result = transcriber.transcribe(audio.samples, audio.sampleRate, target.code)) {
                is TranscriptionResult.Failure ->
                    _ui.value = _ui.value.copy(transcribing = false, message = result.message)

                is TranscriptionResult.Success -> {
                    if (result.text.isBlank()) {
                        _ui.value = _ui.value.copy(
                            transcribing = false,
                            message = "Fann ingen tale i opptaket.",
                        )
                        return@launch
                    }
                    val updatedBody = TranscriptMerge.merge(entry.body, result.text, entry.transcribed)
                    val updated = entry.copy(
                        body = updatedBody,
                        transcribed = true,
                        model = result.modelId,
                        language = target.code,
                        updated = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    )
                    val saved = repo.save(updated, null)

                    val actions = if (saved) {
                        extractor.extractActions(updated)
                    } else {
                        emptyList()
                    }

                    _ui.value = _ui.value.copy(
                        transcribing = false,
                        entry = if (saved) updated else entry,
                        extractedActions = actions,
                        message = when {
                            !saved -> "Transkriberte, men klarte ikkje lagre."
                            else -> "Transkribert (${target.displayName.lowercase()})."
                        },
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun dismissAction(action: ExtractedAction) {
        _ui.value = _ui.value.copy(
            extractedActions = _ui.value.extractedActions.filter { it != action }
        )
    }

    fun approveAction(action: ExtractedAction) {
        val state = _ui.value
        val entry = state.entry ?: return

        // Formater Markdown-teksten for handlingspunktet
        val actionText = when (action.type) {
            ActionType.CALENDAR_EVENT -> "- [ ] (Kalender) ${action.title}" + (action.time?.let { " - $it" } ?: "")
            ActionType.TASK -> "- [ ] ${action.title}"
        }

        // Legg til under ## Handlingspunkt (opprett om det ikkje finst)
        val bodyLines = entry.body.lines().toMutableList()
        val actionsHeaderIndex = bodyLines.indexOfFirst { it.trim() == "## Handlingspunkt" }

        if (actionsHeaderIndex >= 0) {
            bodyLines.add(actionsHeaderIndex + 1, actionText)
        } else {
            if (bodyLines.lastOrNull()?.isNotBlank() == true) {
                bodyLines.add("")
            }
            bodyLines.add("## Handlingspunkt")
            bodyLines.add(actionText)
        }

        val updated = entry.copy(
            body = bodyLines.joinToString("\n"),
            updated = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )

        viewModelScope.launch {
            if (repo.save(updated, null)) {
                _ui.value = _ui.value.copy(
                    entry = updated,
                    extractedActions = _ui.value.extractedActions.filter { it != action }
                )
            }
        }
    }

    override fun onCleared() {
        // Whisper-modellen tek mykje minne – slepp han når ingen skjerm brukar han lenger.
        TranscriberHolder.release()
        super.onCleared()
    }
}
