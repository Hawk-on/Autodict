package com.autodict.data.diary

import android.content.Context
import android.net.Uri
import com.autodict.data.index.IndexReconciler
import com.autodict.data.index.IndexStore
import com.autodict.data.index.IndexedEntry
import com.autodict.data.markdown.FrontmatterSerializer
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.SafRepository
import com.autodict.data.storage.StoragePaths
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

@Volatile
private var diaryRepositoryInstance: DiaryRepository? = null

/**
 * Fabrikk som returnerer ein delt **singleton**, slik at den reaktive indeksen er felles
 * for alle ViewModel-ar (lagring frå éin → lista i ein annan oppdaterer seg straks).
 */
fun createDiaryRepository(context: Context): DiaryRepository {
    val app = context.applicationContext
    return diaryRepositoryInstance ?: synchronized(DiaryRepository::class.java) {
        diaryRepositoryInstance ?: DiaryRepository(
            SafRepository(app, AppSettings(app)),
            IndexStore(File(app.filesDir, "entry-index.json")),
        ).also { diaryRepositoryInstance = it }
    }
}

/** Ei lasta oppføring med URI til lydfila (om ho finst), klar for avspeling. */
data class LoadedEntry(val entry: DiaryEntry, val audioUri: Uri?)

/**
 * Les og skriv dagbok-oppføringar mot den brukarvalde mappa. Byggjer på [SafRepository]
 * (rå SAF-I/O) + [FrontmatterSerializer] (md ↔ modell). Filene er kjelda til sanning.
 *
 * Lista går via ein lokal indeks-cache ([IndexStore]): [observeEntries] er reaktiv og
 * visast straks frå cachen, medan [sync] gjer ein billig mtime-stale-sjekk og re-parser
 * berre filer som faktisk er nye/endra. Cachen kan alltid byggjast på nytt frå mappa.
 */
class DiaryRepository(
    private val saf: SafRepository,
    private val store: IndexStore,
) {
    private val index = MutableStateFlow<List<IndexedEntry>>(emptyList())
    private var loadedFromStore = false

    /** Reaktiv straum av oppføringar (nyaste først) frå den lokale cachen. */
    fun observeEntries(): Flow<List<DiaryEntry>> =
        index.map { list -> list.sortedByDescending { it.created }.map { it.toDiaryEntry() } }

    /** True dersom ei gyldig lagringsmappe er vald. */
    suspend fun hasFolder(): Boolean = saf.hasValidFolder()

    /**
     * Billig avstemming mot mappa: les den persisterte cachen (éin gong), samanlikn
     * fil-mtime og re-parse berre nye/endra filer; fjern slettar. Oppdaterer den reaktive
     * straumen og persisterer cachen.
     */
    suspend fun sync() {
        if (!loadedFromStore) {
            index.value = store.load()
            loadedFromStore = true
        }
        if (!saf.hasValidFolder()) return

        val refs = saf.listMarkdownFileRefs()
        val currentMtime = refs.associate { it.id to it.lastModified }
        val cachedMtime = index.value.associate { it.id to it.lastModified }

        val diff = IndexReconciler.computeDiff(cachedMtime, currentMtime)
        if (diff.isEmpty) return

        val byId = index.value.associateBy { it.id }.toMutableMap()
        diff.removeIds.forEach { byId.remove(it) }

        val refsById = refs.associateBy { it.id }
        for (id in diff.reparseIds) {
            val ref = refsById[id] ?: continue
            val content = saf.readTextFile(ref.uri) ?: continue
            val entry = runCatching { FrontmatterSerializer.parse(content) }.getOrNull() ?: continue
            if (entry.id.isEmpty()) continue
            byId[id] = IndexedEntry.from(id, ref.lastModified, entry)
        }

        val updated = byId.values.toList()
        index.value = updated
        store.save(updated)
    }

    /**
     * Lagrar ei oppføring: skriv `<id>.md` og kopierer eventuell lydfil til same datomappe,
     * og oppdaterer indeksen.
     */
    suspend fun save(entry: DiaryEntry, audioFile: File?): Boolean {
        val folders = StoragePaths.dateFoldersFromId(entry.id)
        val markdown = FrontmatterSerializer.serialize(entry)
        saf.writeTextFile(folders, "${entry.id}.md", "text/markdown", markdown) ?: return false
        if (audioFile != null && entry.audio != null) {
            saf.copyFileInto(folders, entry.audio, "audio/wav", audioFile) ?: return false
        }
        sync()
        return true
    }

    /** Hentar ei enkelt oppføring med id, inkludert URI til lydfila. */
    suspend fun get(id: String): LoadedEntry? {
        val folders = StoragePaths.dateFoldersFromId(id)
        val mdUri = saf.findFileUri(folders, "$id.md") ?: return null
        val content = saf.readTextFile(mdUri) ?: return null
        val entry = FrontmatterSerializer.parse(content)
        val audioUri = entry.audio?.let { saf.findFileUri(folders, it) }
        return LoadedEntry(entry, audioUri)
    }
}
