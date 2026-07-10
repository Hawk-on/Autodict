package com.autodict.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
        val WHISPER_MODEL = stringPreferencesKey("whisper_model")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
    }
}
