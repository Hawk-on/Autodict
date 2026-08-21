package com.autodict.ui.onboarding

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.SafRepository
import com.autodict.data.transcribe.DownloadStatus
import com.autodict.data.transcribe.ModelDownloadSupport
import com.autodict.data.transcribe.ModelDownloader
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.WhisperModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Stega i rettleiinga, i rekkjefølgje. */
enum class OnboardingStep { FOLDER, LANGUAGE, MODEL, PERMISSION }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.FOLDER,
    val folderName: String? = null,
    val language: TargetLanguage = TargetLanguage.DEFAULT,
    val selectedModelId: String = WhisperModel.DEFAULT.id,
    val wifiOnly: Boolean = true,
    val modelDownloaded: Boolean = false,
    val downloadFraction: Float? = null,
    val message: String? = null,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val totalSteps: Int get() = OnboardingStep.entries.size
}

/**
 * Oppstartsrettleiing: mappe → målform → modell → mikrofon.
 *
 * Bakgrunnen er at appen tidlegare lét deg ta opp før mappa var vald og før modellen var
 * lasta ned. Då blir opptaket liggjande i cache og «Transkriber» gjer tilsynelatande
 * ingenting – same symptom som ein feil. Dette gjer dei to vala til noko ein tek stilling
 * til i staden for noko ein oppdagar.
 */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = AppSettings(app)
    private val saf = SafRepository(app, settings)
    private val downloader = ModelDownloader(File(app.filesDir, "models"))

    private val _ui = MutableStateFlow(OnboardingUiState())
    val ui: StateFlow<OnboardingUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val modelId = settings.whisperModelId.first()
            _ui.update {
                it.copy(
                    folderName = if (saf.hasValidFolder()) saf.folderDisplayName() else null,
                    language = TargetLanguage.fromCode(settings.transcriptionLanguage.first()),
                    selectedModelId = modelId,
                    wifiOnly = settings.wifiOnlyDownload.first(),
                    modelDownloaded = downloader.isDownloaded(WhisperModel.fromId(modelId)),
                )
            }
        }
    }

    fun next() {
        val current = _ui.value.step
        val next = OnboardingStep.entries.getOrNull(current.ordinal + 1) ?: return
        _ui.update { it.copy(step = next, message = null) }
    }

    fun back() {
        val current = _ui.value.step
        val previous = OnboardingStep.entries.getOrNull(current.ordinal - 1) ?: return
        _ui.update { it.copy(step = previous, message = null) }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            saf.persistTreeUri(uri)
            _ui.update { it.copy(folderName = saf.folderDisplayName()) }
            next()
        }
    }

    fun selectLanguage(language: TargetLanguage) {
        viewModelScope.launch {
            settings.setTranscriptionLanguage(language.code)
            _ui.update { it.copy(language = language) }
        }
    }

    fun selectModel(id: String) {
        viewModelScope.launch {
            settings.setWhisperModelId(id)
            _ui.update {
                it.copy(
                    selectedModelId = id,
                    modelDownloaded = downloader.isDownloaded(WhisperModel.fromId(id)),
                )
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settings.setWifiOnlyDownload(enabled)
            _ui.update { it.copy(wifiOnly = enabled) }
        }
    }

    /**
     * Startar nedlastinga, men blokkerer ikkje: rettleiinga kan fullførast medan modellen
     * lastar, og transkripsjonen tek att når han er klar. Ei halv gigabyte er for lenge å
     * halde nokon fast på ein oppsettsskjerm.
     */
    fun startDownload() {
        viewModelScope.launch {
            val model = WhisperModel.fromId(_ui.value.selectedModelId)
            if (!ModelDownloadSupport.downloadAllowed(onWifi(), _ui.value.wifiOnly)) {
                _ui.update {
                    it.copy(message = "Ventar på Wi-Fi. Slå av «berre Wi-Fi» for å bruke mobildata.")
                }
                return@launch
            }
            downloader.download(model).collect { status ->
                _ui.update {
                    when (status) {
                        is DownloadStatus.Progress -> it.copy(downloadFraction = status.fraction)
                        DownloadStatus.Done -> it.copy(downloadFraction = null, modelDownloaded = true)
                        is DownloadStatus.Failed -> it.copy(
                            downloadFraction = null,
                            message = "Nedlasting feila: ${status.message}",
                        )
                    }
                }
            }
        }
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    private fun onWifi(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
