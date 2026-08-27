package com.autodict.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autodict.data.assistant.AssistantConfig
import com.autodict.data.assistant.AssistantCredential
import com.autodict.data.assistant.AssistantPreset
import com.autodict.data.assistant.SecretStore
import com.autodict.data.transcribe.TargetLanguage
import com.autodict.data.transcribe.WhisperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "autodict_settings")

/**
 * Innstillingar lagra i Preferences DataStore.
 *
 * Per M1 held vi berre den persisterte SAF tree-URI-en. Vi lagrar URI-en som streng – aldri
 * ein filsti (jf. kjerneprinsipp i CLAUDE.md). `DocumentFile` reknast ut på nytt frå URI-en
 * kvar økt i [SafRepository].
 */
class AppSettings(private val context: Context) {

    val treeUri: Flow<String?> = context.dataStore.data.map { prefs -> prefs[TREE_URI] }

    suspend fun setTreeUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(TREE_URI) else prefs[TREE_URI] = uri
        }
    }

    /** Vald Whisper-modellstorleik for transkripsjon (M4). Standard = small. */
    val whisperModelId: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[WHISPER_MODEL] ?: WhisperModel.DEFAULT.id }

    suspend fun setWhisperModelId(id: String) {
        context.dataStore.edit { prefs -> prefs[WHISPER_MODEL] = id }
    }

    /** Berre last ned modellar på Wi-Fi (standard på). */
    val wifiOnlyDownload: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[WIFI_ONLY] ?: true }

    suspend fun setWifiOnlyDownload(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[WIFI_ONLY] = enabled }
    }

    /**
     * Målform transkripsjonen siktar mot for nye oppføringar. Modellen normaliserer talen
     * (òg dialekt) mot denne målforma. Standard = bokmål, som har lågast feilrate.
     */
    val transcriptionLanguage: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[TRANSCRIPTION_LANGUAGE] ?: TargetLanguage.DEFAULT.code }

    suspend fun setTranscriptionLanguage(code: String) {
        context.dataStore.edit { prefs -> prefs[TRANSCRIPTION_LANGUAGE] = code }
    }

    /** Start transkripsjonen automatisk når eit opptak er ferdig (standard av). */
    val autoTranscribe: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[AUTO_TRANSCRIBE] ?: false }

    suspend fun setAutoTranscribe(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_TRANSCRIBE] = enabled }
    }

    /**
     * Arkiver tapsfri WAV i staden for Opus. Standard av – Opus på ~32 kbit/s er
     * perseptuelt utmerkt for tale og tek ~1/23 av plassen (0,24 mot 5,6 MB/min).
     */
    val keepOriginalWav: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[KEEP_WAV] ?: false }

    suspend fun setKeepOriginalWav(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEEP_WAV] = enabled }
    }

    /**
     * Lyst/mørkt tema, eller følg systemet (standard). Lagra som rå id; tolkinga skjer i
     * UI-laget (`ThemeMode.fromId`), så datalaget slepp å kjenne til ein Compose-type.
     */
    val themeMode: Flow<String> =
        context.dataStore.data.map { prefs -> prefs[THEME_MODE] ?: "system" }

    suspend fun setThemeMode(id: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = id }
    }

    /**
     * Om oppstartsrettleiinga er gjennomgått. Styrer berre om ho blir vist – vala ho gjer
     * er vanlege innstillingar, så det er trygt å køyre ho på nytt.
     */
    val onboardingCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = done }
    }

    /**
     * Eit ferdig opptak som enno ikkje er lagra som oppføring, lagra som
     * `sti|starttid|lengd`.
     *
     * Trengst fordi eit opptak kan stoppast utan at appen er framme – frå varselet eller frå
     * låseskjermen. Held vi utkastet berre i minnet, forsvinn det om prosessen blir rydda før
     * du opnar appen att, og lydfila blir liggjande i cache utan at noko peikar på henne.
     */
    val pendingDraft: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[PENDING_DRAFT] }

    suspend fun setPendingDraft(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(PENDING_DRAFT) else prefs[PENDING_DRAFT] = value
        }
    }

    /**
     * Oppsettet for assistenten (M7). Nøkkelen ligg kryptert – sjå
     * [com.autodict.data.assistant.SecretStore] – og blir aldri lagra i klartekst.
     */
    val assistantConfig: Flow<AssistantConfig> = context.dataStore.data.map { prefs ->
        val preset = AssistantPreset.fromId(prefs[ASSISTANT_PRESET])
        val secret = prefs[ASSISTANT_SECRET]?.let(SecretStore::decrypt)
        AssistantConfig(
            enabled = prefs[ASSISTANT_ENABLED] ?: false,
            preset = preset,
            baseUrl = prefs[ASSISTANT_BASE_URL] ?: preset.baseUrl,
            model = prefs[ASSISTANT_MODEL] ?: preset.defaultModel,
            credential = if (secret.isNullOrBlank()) {
                AssistantCredential.None
            } else {
                AssistantCredential.ApiKey(secret)
            },
        )
    }

    suspend fun setAssistantConfig(config: AssistantConfig) {
        context.dataStore.edit { prefs ->
            prefs[ASSISTANT_ENABLED] = config.enabled
            prefs[ASSISTANT_PRESET] = config.preset.name
            prefs[ASSISTANT_BASE_URL] = config.baseUrl
            prefs[ASSISTANT_MODEL] = config.model

            when (val credential = config.credential) {
                is AssistantCredential.ApiKey -> {
                    val encrypted = SecretStore.encrypt(credential.key)
                    // Kan vi ikkje kryptere, lagrar vi ingenting. Ein nøkkel i klartekst
                    // er verre enn ein nøkkel som må skrivast inn på nytt.
                    if (encrypted != null) prefs[ASSISTANT_SECRET] = encrypted
                    else prefs.remove(ASSISTANT_SECRET)
                }
                else -> prefs.remove(ASSISTANT_SECRET)
            }
        }
    }

    private companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val PENDING_DRAFT = stringPreferencesKey("pending_draft")
        val ASSISTANT_ENABLED = booleanPreferencesKey("assistant_enabled")
        val ASSISTANT_PRESET = stringPreferencesKey("assistant_preset")
        val ASSISTANT_BASE_URL = stringPreferencesKey("assistant_base_url")
        val ASSISTANT_MODEL = stringPreferencesKey("assistant_model")
        val ASSISTANT_SECRET = stringPreferencesKey("assistant_secret")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val KEEP_WAV = booleanPreferencesKey("keep_original_wav")
        val WHISPER_MODEL = stringPreferencesKey("whisper_model")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
        val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
        val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
    }
}
