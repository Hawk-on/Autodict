package com.autodict.ui.settings

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.SafRepository
import com.autodict.data.storage.StoragePaths
import com.autodict.data.transcribe.DownloadStatus
import com.autodict.data.transcribe.ModelDownloadSupport
import com.autodict.data.transcribe.ModelDownloader
import com.autodict.data.transcribe.WhisperModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class SettingsUiState(
    val loading: Boolean = true,
    val hasFolder: Boolean = false,
    val folderName: String? = null,
    val message: String? = null,
    // Transkripsjonsmodell (M4)
    val selectedModelId: String = WhisperModel.DEFAULT.id,
    val wifiOnly: Boolean = true,
    val modelDownloaded: Boolean = false,
    val downloadFraction: Float? = null,
)

/**
 * ViewModel for innstillingsskjermen: vel lagringsmappe (M1) og handter transkripsjons-
 * modellen (M4) – val av storleik, Wi-Fi-gata nedlasting med progress, og sletting.
 * Manuell oppretting av avhengnader frå application-context inntil vi innfører DI.
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val saf = SafRepository(app, settings)
    private val downloader = ModelDownloader(File(app.filesDir, "models"))

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        refresh()
        loadModelState()
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

    private fun loadModelState() {
        viewModelScope.launch {
            val id = settings.whisperModelId.first()
            _ui.value = _ui.value.copy(
                selectedModelId = id,
                wifiOnly = settings.wifiOnlyDownload.first(),
                modelDownloaded = downloader.isDownloaded(WhisperModel.fromId(id)),
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

    // --- Transkripsjonsmodell (M4) ---

    fun selectModel(id: String) {
        viewModelScope.launch {
            settings.setWhisperModelId(id)
            _ui.value = _ui.value.copy(
                selectedModelId = id,
                modelDownloaded = downloader.isDownloaded(WhisperModel.fromId(id)),
            )
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settings.setWifiOnlyDownload(enabled)
            _ui.value = _ui.value.copy(wifiOnly = enabled)
        }
    }

    fun startDownload() {
        viewModelScope.launch {
            val model = WhisperModel.fromId(_ui.value.selectedModelId)
            if (!ModelDownloadSupport.downloadAllowed(onWifi(), _ui.value.wifiOnly)) {
                _ui.value = _ui.value.copy(
                    message = "Ventar på Wi-Fi. Slå av «berre Wi-Fi» for å bruke mobildata.",
                )
                return@launch
            }
            downloader.download(model).collect { status ->
                _ui.value = when (status) {
                    is DownloadStatus.Progress -> _ui.value.copy(downloadFraction = status.fraction)
                    DownloadStatus.Done -> _ui.value.copy(
                        downloadFraction = null,
                        modelDownloaded = true,
                        message = "Modell lasta ned.",
                    )
                    is DownloadStatus.Failed -> _ui.value.copy(
                        downloadFraction = null,
                        message = "Nedlasting feila: ${status.message}",
                    )
                }
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            downloader.delete(WhisperModel.fromId(_ui.value.selectedModelId))
            _ui.value = _ui.value.copy(modelDownloaded = false, message = "Modell sletta.")
        }
    }

    private fun onWifi(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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
