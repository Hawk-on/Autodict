package com.autodict.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autodict.data.diary.createDiaryRepository
import com.autodict.data.storage.AppSettings
import com.autodict.data.transcribe.ModelDownloader
import com.autodict.data.transcribe.TranscriptMerge
import com.autodict.data.transcribe.TranscriptionResult
import com.autodict.data.transcribe.WhisperModel
import com.autodict.data.transcribe.WhisperTranscriber
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class DetailUiState(
    val loading: Boolean = true,
    val entry: DiaryEntry? = null,
    val audioUri: Uri? = null,
    val transcribing: Boolean = false,
    val message: String? = null,
)

class EntryDetailViewModel(
    app: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)
    private val settings = AppSettings(app)
    private val transcriber = WhisperTranscriber(
        downloader = ModelDownloader(File(app.filesDir, "models")),
        selectedModel = { WhisperModel.fromId(settings.whisperModelId.first()) },
    )
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
    fun transcribe() {
        val state = _ui.value
        val entry = state.entry ?: return
        val audioUri = state.audioUri ?: return
        if (state.transcribing) return

        viewModelScope.launch {
            _ui.value = _ui.value.copy(transcribing = true, message = "Transkriberer …")

            val bytes = repo.readAudioBytes(audioUri)
            if (bytes == null) {
                _ui.value = _ui.value.copy(transcribing = false, message = "Fann ikkje lydfila.")
                return@launch
            }

            when (val result = transcriber.transcribe(bytes, entry.language)) {
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
                    val updated = entry.copy(
                        body = TranscriptMerge.merge(entry.body, result.text, entry.transcribed),
                        transcribed = true,
                        model = result.modelId,
                        updated = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    )
                    val saved = repo.save(updated, null)
                    _ui.value = _ui.value.copy(
                        transcribing = false,
                        entry = if (saved) updated else entry,
                        message = if (saved) "Transkribert." else "Transkriberte, men klarte ikkje lagre.",
                    )
                }
            }
        }
    }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    override fun onCleared() {
        // Whisper-modellen tek mykje minne – slepp han når skjermen er borte.
        transcriber.release()
        super.onCleared()
    }
}
