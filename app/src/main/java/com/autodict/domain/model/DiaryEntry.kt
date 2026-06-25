package com.autodict.domain.model

/**
 * Éi dagbok-oppføring. Dette er den kanoniske modellen; han speglar YAML-frontmatter i
 * `.md`-fila (sjå [com.autodict.data.markdown.FrontmatterSerializer]). Filene er databasen –
 * modellen kan alltid byggjast på nytt frå fila.
 *
 * @param id slug/basisnamn (filnamn utan suffiks), t.d. `2026-06-02T14-03-12-eit-notat`.
 * @param created ISO-8601 med offset, tidspunkt for opptak.
 * @param audio relativt filnamn til lydfila i same mappe (t.d. `<id>.wav`), eller null.
 */
data class DiaryEntry(
    val id: String,
    val created: String,
    val updated: String? = null,
    val title: String = "",
    val audio: String? = null,
    val durationSeconds: Int = 0,
    val language: String = "no",
    val transcribed: Boolean = false,
    val model: String? = null,
    val tags: List<String> = emptyList(),
    val body: String = "",
)
