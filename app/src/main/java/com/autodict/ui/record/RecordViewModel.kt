package com.autodict.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.audio.PendingDraftStore
import com.autodict.data.audio.RecorderState
import com.autodict.data.audio.RecordingController
import com.autodict.data.audio.RecordingService
import com.autodict.data.diary.createDiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.io.File

/** Eit ferdig opptak klart til å redigerast/lagrast. */
data class RecordedDraft(
    val audioPath: String,
    val createdMillis: Long,
    val durationSeconds: Int,
)

/**
 * Styrer opptaket, men **eig det ikkje**: sjølve lyden ligg i [RecordingController], og
 * [RecordingService] held han i live medan skjermen er av. Denne klassa sender berre
 * kommandoar til tenesta og speglar tilstanden ut i UI-et.
 */
class RecordViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)

    val recorderState: StateFlow<RecorderState> = RecordingController.state

    private val _hasFolder = MutableStateFlow(true)
    val hasFolder: StateFlow<Boolean> = _hasFolder.asStateFlow()

    /**
     * Utkastet som ventar. Kjelda er den persisterte [PendingDraftStore], ikkje minnet:
     * eit opptak kan vere stoppa frå varselet eller frå låseskjermen, kanskje for lenge
     * sidan, og då er prosessen ofte rydda før appen blir opna att.
     */
    val draft: StateFlow<RecordedDraft?> = PendingDraftStore.observe(app)
        .map { pending ->
            pending?.let {
                RecordedDraft(
                    audioPath = it.audioPath,
                    createdMillis = it.createdMillis,
                    durationSeconds = it.durationSeconds,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshFolder() {
        viewModelScope.launch { _hasFolder.value = repo.hasFolder() }
    }

    fun start() {
        if (recorderState.value.isActive) return
        val context = getApplication<Application>()
        val cacheFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
        RecordingService.start(context, cacheFile)
    }

    fun stop() = send(RecordingService.ACTION_STOP)

    fun pause() = send(RecordingService.ACTION_PAUSE)

    fun resume() = send(RecordingService.ACTION_RESUME)

    /** Avbryt og slett opptaket. Ingen draft blir laga, så vi går ikkje vidare til utkast. */
    fun discard() = send(RecordingService.ACTION_DISCARD)

    fun consumeDraft() {
        RecordingController.consumeResult()
        PendingDraftStore.clear(getApplication())
    }

    private fun send(action: String) =
        RecordingService.send(getApplication<Application>(), action)
}
