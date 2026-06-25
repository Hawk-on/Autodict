package com.autodict.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.audio.AudioRecorder
import com.autodict.data.audio.RecorderState
import com.autodict.data.diary.createDiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Eit ferdig opptak klart til å redigerast/lagrast. */
data class RecordedDraft(
    val audioPath: String,
    val createdMillis: Long,
    val durationSeconds: Int,
)

class RecordViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)
    private val recorder = AudioRecorder()

    val recorderState: StateFlow<RecorderState> = recorder.state

    private val _hasFolder = MutableStateFlow(true)
    val hasFolder: StateFlow<Boolean> = _hasFolder.asStateFlow()

    private val _draft = MutableStateFlow<RecordedDraft?>(null)
    val draft: StateFlow<RecordedDraft?> = _draft.asStateFlow()

    private var startedAtMillis = 0L

    fun refreshFolder() {
        viewModelScope.launch { _hasFolder.value = repo.hasFolder() }
    }

    fun start() {
        if (recorderState.value is RecorderState.Recording) return
        val cacheFile = File(getApplication<Application>().cacheDir, "recording_${System.currentTimeMillis()}.wav")
        startedAtMillis = System.currentTimeMillis()
        recorder.start(cacheFile)
    }

    fun stop() {
        val result = recorder.stop() ?: return
        _draft.value = RecordedDraft(
            audioPath = result.file.absolutePath,
            createdMillis = startedAtMillis,
            durationSeconds = result.durationSeconds,
        )
    }

    fun consumeDraft() {
        _draft.value = null
    }
}
