package com.autodict.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autodict.data.diary.createDiaryRepository
import com.autodict.data.storage.StoragePaths
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class EditUiState(
    val title: String = "",
    val body: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Redigerer eit nytt utkast (frå opptak) og lagrar det som ei oppføring.
 * Argumenta (audio-sti, opptakstid, lengd) kjem frå navigasjonen via [SavedStateHandle].
 */
class EntryEditViewModel(
    app: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)

    private val audioPath: String? = handle.get<String>("audio")?.takeIf { it.isNotBlank() }
    private val createdMillis: Long = handle.get<String>("created")?.toLongOrNull() ?: System.currentTimeMillis()
    private val durationSeconds: Int = handle.get<String>("duration")?.toIntOrNull() ?: 0

    private val _ui = MutableStateFlow(EditUiState())
    val ui: StateFlow<EditUiState> = _ui.asStateFlow()

    fun onTitleChange(value: String) = _ui.update { it.copy(title = value) }
    fun onBodyChange(value: String) = _ui.update { it.copy(body = value) }

    fun save(onSaved: () -> Unit) {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val instant = Instant.ofEpochMilli(createdMillis)
            val localDateTime = instant.atZone(zone).toLocalDateTime()
            val title = _ui.value.title.trim()

            val id = StoragePaths.entrySlug(localDateTime, title.ifBlank { null })
            val createdIso = OffsetDateTime.ofInstant(instant, zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val entry = DiaryEntry(
                id = id,
                created = createdIso,
                title = title.ifBlank { "Utan tittel" },
                audio = if (audioPath != null) "$id.wav" else null,
                durationSeconds = durationSeconds,
                language = "no",
                transcribed = false,
                tags = emptyList(),
                body = _ui.value.body.trim(),
            )

            val ok = repo.save(entry, audioPath?.let(::File))
            if (ok) {
                audioPath?.let { runCatching { File(it).delete() } } // rydd opp cache-fila
                onSaved()
            } else {
                _ui.update {
                    it.copy(
                        saving = false,
                        error = "Klarte ikkje lagre. Har du vald ei lagringsmappe (Innstillingar)?",
                    )
                }
            }
        }
    }
}
