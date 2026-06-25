package com.autodict.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val TREE_URI = stringPreferencesKey("tree_uri")
    }
}
