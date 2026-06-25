package com.autodict.data.storage

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Reine hjelpefunksjonar for mappe-/filnamn i den brukarvalde mappa. Held desse fri for
 * Android-API slik at dei kan unit-testast på JVM.
 *
 * Struktur: `<rot>/2026/2026-06/2026-06-02T14-03-12-<slug>.md` (+ tilhøyrande lydfil).
 */
object StoragePaths {

    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")

    /** Datomappene ei oppføring skal liggje i, t.d. ["2026", "2026-06"]. */
    fun dateFolders(dateTime: LocalDateTime): List<String> {
        val year = "%04d".format(dateTime.year)
        val yearMonth = "%04d-%02d".format(dateTime.year, dateTime.monthValue)
        return listOf(year, yearMonth)
    }

    /**
     * Utleier datomappene frå ein id/slug som startar med `yyyy-MM-dd` (slik [entrySlug] lagar).
     * Fell tilbake til tom liste (= rotmappa) om formatet ikkje stemmer.
     */
    fun dateFoldersFromId(id: String): List<String> {
        val valid = id.length >= 7 && id[4] == '-' &&
            id.take(4).all(Char::isDigit) && id.substring(5, 7).all(Char::isDigit)
        if (!valid) return emptyList()
        return listOf(id.take(4), id.take(7))
    }

    /**
     * Slug/basisnamn for ei oppføring: tidsstempel + valfri tittel-slug.
     * T.d. `2026-06-02T14-03-12-eit-kort-notat`, eller berre tidsstempelet om tittelen er tom.
     */
    fun entrySlug(dateTime: LocalDateTime, title: String? = null): String {
        val ts = dateTime.format(TIMESTAMP)
        val titleSlug = title?.let(::slugifyTitle)?.takeIf { it.isNotEmpty() }
        return if (titleSlug == null) ts else "$ts-$titleSlug"
    }

    /**
     * Gjer ein tittel om til ein trygg, filnamn-vennleg slug. Behaldar norske bokstavar
     * (æ/ø/å), gjer om til små bokstavar, byter ut anna med bindestrek og kortar ned.
     */
    fun slugifyTitle(title: String, maxLength: Int = 40): String {
        val builder = StringBuilder()
        var pendingDash = false
        for (ch in title.lowercase()) {
            val keep = ch in 'a'..'z' || ch in '0'..'9' || ch == 'æ' || ch == 'ø' || ch == 'å'
            if (keep) {
                builder.append(ch)
                pendingDash = false
            } else if (builder.isNotEmpty() && !pendingDash) {
                builder.append('-')
                pendingDash = true
            }
        }
        var slug = builder.toString().trim('-')
        if (slug.length > maxLength) slug = slug.substring(0, maxLength).trim('-')
        return slug
    }
}
