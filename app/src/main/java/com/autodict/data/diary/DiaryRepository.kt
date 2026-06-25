package com.autodict.data.diary

import android.content.Context
import android.net.Uri
import com.autodict.data.markdown.FrontmatterSerializer
import com.autodict.data.storage.AppSettings
import com.autodict.data.storage.SafRepository
import com.autodict.data.storage.StoragePaths
import com.autodict.domain.model.DiaryEntry
import java.io.File

/** Enkel fabrikk inntil vi innfører eit DI-rammeverk. */
fun createDiaryRepository(context: Context): DiaryRepository {
    val app = context.applicationContext
    return DiaryRepository(SafRepository(app, AppSettings(app)))
}

/** Ei lasta oppføring med URI til lydfila (om ho finst), klar for avspeling. */
data class LoadedEntry(val entry: DiaryEntry, val audioUri: Uri?)

/**
 * Les og skriv dagbok-oppføringar mot den brukarvalde mappa. Byggjer på [SafRepository]
 * (rå SAF-I/O) + [FrontmatterSerializer] (md ↔ modell). Filene er kjelda til sanning.
 */
class DiaryRepository(private val saf: SafRepository) {

    /** True dersom ei gyldig lagringsmappe er vald. */
    suspend fun hasFolder(): Boolean = saf.hasValidFolder()

    /**
     * Lagrar ei oppføring: skriv `<id>.md` og kopierer eventuell lydfil til same datomappe.
     * Returnerer true ved suksess.
     */
    suspend fun save(entry: DiaryEntry, audioFile: File?): Boolean {
        val folders = StoragePaths.dateFoldersFromId(entry.id)
        val markdown = FrontmatterSerializer.serialize(entry)
        saf.writeTextFile(folders, "${entry.id}.md", "text/markdown", markdown) ?: return false
        if (audioFile != null && entry.audio != null) {
            saf.copyFileInto(folders, entry.audio, "audio/wav", audioFile) ?: return false
        }
        return true
    }

    /** Alle oppføringar, nyaste først. */
    suspend fun list(): List<DiaryEntry> =
        saf.listMarkdownFiles()
            .mapNotNull { runCatching { FrontmatterSerializer.parse(it.content) }.getOrNull() }
            .filter { it.id.isNotEmpty() }
            .sortedByDescending { it.created }

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
