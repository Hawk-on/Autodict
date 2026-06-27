package com.autodict.data.index

import com.autodict.domain.model.DiaryEntry
import kotlinx.serialization.Serializable

/**
 * Lett samandrag av ei oppføring, lagra i den lokale indeks-cachen ([IndexStore]).
 * Inneheld berre det lista treng – **ikkje** brødteksten. `lastModified` er fil-mtime
 * brukt til billig stale-sjekk i [IndexReconciler].
 *
 * Cachen er rein avleidd cache; mappa er framleis kjelda til sanning (CLAUDE.md-prinsipp 1),
 * og full oppføring (med brødtekst) lesast frå `.md`-fila ved behov.
 */
@Serializable
data class IndexedEntry(
    val id: String,
    val created: String = "",
    val title: String = "",
    val audio: String? = null,
    val durationSeconds: Int = 0,
    val transcribed: Boolean = false,
    val tags: List<String> = emptyList(),
    val lastModified: Long = 0L,
) {
    /** Til UI-modellen for lista (brødtekst tom – detalj les fulle data frå fila). */
    fun toDiaryEntry(): DiaryEntry = DiaryEntry(
        id = id,
        created = created,
        title = title,
        audio = audio,
        durationSeconds = durationSeconds,
        transcribed = transcribed,
        tags = tags,
    )

    companion object {
        fun from(id: String, lastModified: Long, entry: DiaryEntry): IndexedEntry = IndexedEntry(
            id = id,
            created = entry.created,
            title = entry.title,
            audio = entry.audio,
            durationSeconds = entry.durationSeconds,
            transcribed = entry.transcribed,
            tags = entry.tags,
            lastModified = lastModified,
        )
    }
}
