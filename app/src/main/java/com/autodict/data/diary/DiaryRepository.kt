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

/** Stega i ei lagring, så UI-et kan seie kva appen ventar på. */
enum class SaveStage(val label: String) {
    EncodingAudio("Kodar lyd …"),
    WritingText("Skriv teksten …"),
    WritingAudio("Lastar opp lyden …"),
    Indexing("Fullfører …"),
}

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
        ensureLoaded()
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
     * og legg ho inn i indeksen.
     *
     * [onProgress] rapporterer kva steg som går – lagring mot ei sky-synka mappe (Drive)
     * er ei nettverksopplasting og kan ta fleire sekund, så UI-et må kunne vise det.
     */
    suspend fun save(
        entry: DiaryEntry,
        audioFile: File?,
        onProgress: (SaveStage) -> Unit = {},
    ): Boolean {
        val folders = StoragePaths.dateFoldersFromId(entry.id)
        val markdown = FrontmatterSerializer.serialize(entry)

        onProgress(SaveStage.WritingText)
        val mdUri = saf.writeTextFile(folders, "${entry.id}.md", "text/markdown", markdown)
            ?: return false

        if (audioFile != null && entry.audio != null) {
            onProgress(SaveStage.WritingAudio)
            saf.copyFileInto(folders, entry.audio, audioMimeType(entry.audio), audioFile)
                ?: return false
        }

        // Indekser den nye oppføringa direkte i staden for å kalle sync(). Ein full sync
        // vandrar rekursivt over heile mappa og spør providaren om namn/mtime for kvar fil;
        // mot Drive er kvart av dei ei nettverks-runde, så det voks til fleire sekund –
        // rein venting på informasjon vi alt sit på om oppføringa vi nettopp skreiv.
        // Lista gjer ein full sync sjølv når ho blir opna, så avstemminga mot mappa står.
        onProgress(SaveStage.Indexing)
        ensureLoaded()
        val indexed = IndexedEntry.from(entry.id, saf.lastModifiedOf(mdUri), entry)
        val updated = index.value.filterNot { it.id == entry.id } + indexed
        index.value = updated
        store.save(updated)
        return true
    }

    /**
     * Slettar ei oppføring: både `.md`-fila og lydfila, og tek henne ut av indeksen.
     *
     * Filene er databasen, så dette er ei ekte sletting – ikkje eit flagg. Er mappa synka
     * (Drive/Dropbox), hamnar filene i papirkorga der etter tenesta sine reglar; lokalt er
     * dei borte. Markdown-fila blir sletta **sist**: ryk noko undervegs, er det betre å sitje
     * att med ei oppføring utan lyd enn med ei foreldrelaus lydfil som ingenting peikar på.
     */
    suspend fun delete(entry: DiaryEntry): Boolean {
        val folders = StoragePaths.dateFoldersFromId(entry.id)

        entry.audio?.let { audio ->
            if (!saf.deleteFile(folders, audio)) return false
        }
        if (!saf.deleteFile(folders, "${entry.id}.md")) return false

        ensureLoaded()
        val updated = index.value.filterNot { it.id == entry.id }
        index.value = updated
        store.save(updated)
        return true
    }

    private suspend fun ensureLoaded() {
        if (loadedFromStore) return
        index.value = store.load()
        loadedFromStore = true
    }

    /** MIME-type ut frå filendinga – dagboka kan innehalde både WAV og Opus (M3b). */
    private fun audioMimeType(fileName: String): String = when {
        fileName.endsWith(".opus", ignoreCase = true) -> "audio/opus"
        fileName.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
        else -> "audio/wav"
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
