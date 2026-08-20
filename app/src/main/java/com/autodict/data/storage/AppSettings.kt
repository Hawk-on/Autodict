package com.autodict.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
     * Opt-in (M6): opprett godkjende gjeremål (`ActionType.TASK`) i Google Tasks via
     * [com.autodict.data.integration.GoogleTasksClient]. Av som standard – appen skal fungere
     * heilt offline utan denne (kjerneprinsipp 4 i CLAUDE.md).
     */
    val googleTasksEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[GOOGLE_TASKS_ENABLED] ?: false }

    suspend fun setGoogleTasksEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[GOOGLE_TASKS_ENABLED] = enabled }
    }

    /**
     * Konto-ID/e-post for kopla Google-konto (M6), berre til visning i innstillingar. Ikkje ei
     * hemmelegheit (offentleg kontoidentifikator), difor vanleg DataStore og ikkje kryptert
     * lagring.
     */
    val googleAccountEmail: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[GOOGLE_ACCOUNT_EMAIL] }

    suspend fun setGoogleAccountEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) prefs.remove(GOOGLE_ACCOUNT_EMAIL) else prefs[GOOGLE_ACCOUNT_EMAIL] = email
        }
    }

    private companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val KEEP_WAV = booleanPreferencesKey("keep_original_wav")
        val WHISPER_MODEL = stringPreferencesKey("whisper_model")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
        val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
        val AUTO_TRANSCRIBE = booleanPreferencesKey("auto_transcribe")
        val GOOGLE_TASKS_ENABLED = booleanPreferencesKey("google_tasks_enabled")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
    }
}
