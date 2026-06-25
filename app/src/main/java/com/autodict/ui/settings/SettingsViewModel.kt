package com.autodict.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.SafRepository
import com.autodict.data.storage.StoragePaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class SettingsUiState(
    val loading: Boolean = true,
    val hasFolder: Boolean = false,
    val folderName: String? = null,
    val message: String? = null,
)

/**
 * ViewModel for innstillingsskjermen (M1): velje lagringsmappe og verifisere at SAF-skriving
 * fungerer. Manuell oppretting av [AppSettings]/[SafRepository] frå application-context er
 * greitt inntil vi innfører DI.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val saf = SafRepository(app, settings)

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val valid = saf.hasValidFolder()
            _ui.value = _ui.value.copy(
                loading = false,
                hasFolder = valid,
                folderName = if (valid) saf.folderDisplayName() else null,
            )
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            saf.persistTreeUri(uri)
            _ui.value = _ui.value.copy(message = "Mappe vald.")
            refresh()
        }
    }

    fun clearFolder() {
        viewModelScope.launch {
            saf.clearFolder()
            _ui.value = _ui.value.copy(message = "Mappe fjerna.")
            refresh()
        }
    }

    /** Skriv ei testfil og les ho att for å stadfeste at lagringa verkar end-to-end. */
    fun writeTestEntry() {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val slug = StoragePaths.entrySlug(now, "Testoppføring")
            val folders = StoragePaths.dateFolders(now)
            val fileName = "$slug.md"
            val content = sampleMarkdown(slug)

            val uri = saf.writeTextFile(folders, fileName, "text/markdown", content)
            if (uri == null) {
                _ui.value = _ui.value.copy(message = "Klarte ikkje skrive fila. Er mappa vald?")
                return@launch
            }
            val readBack = saf.readTextFile(uri)
            _ui.value = _ui.value.copy(
                message = if (readBack == content) {
                    "✓ Skreiv og las att: ${folders.joinToString("/")}/$fileName"
                } else {
                    "Skreiv fila, men tilbakelesing stemte ikkje."
                },
            )
        }
    }

    private fun sampleMarkdown(slug: String): String {
        val created = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return buildString {
            appendLine("---")
            appendLine("id: $slug")
            appendLine("created: $created")
            appendLine("title: Testoppføring")
            appendLine("language: no")
            appendLine("transcribed: false")
            appendLine("tags: []")
            appendLine("---")
            appendLine()
            appendLine("Dette er ei testfil skriven av Autodict (M1) for å stadfeste SAF-lagring.")
        }
    }
}
