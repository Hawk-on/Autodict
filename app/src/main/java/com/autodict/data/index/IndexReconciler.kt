package com.autodict.data.index

/**
 * Rein diff mellom den lagra cachen og det som faktisk ligg i mappa, basert på fil-mtime.
 * Ingen Android-API → unit-testbar. Dette er kjernen i den billige stale-sjekken: vi les
 * berre innhaldet (og parser) for filer som er nye eller endra.
 */
object IndexReconciler {

    data class Diff(val reparseIds: List<String>, val removeIds: List<String>) {
        val isEmpty: Boolean get() = reparseIds.isEmpty() && removeIds.isEmpty()
    }

    /**
     * @param cached  id → lastModified frå cachen
     * @param current id → lastModified frå mappa no
     * @return id-ar som må (re)parsast (nye eller endra mtime) og id-ar som skal fjernast
     *         (finst i cachen, men ikkje lenger i mappa).
     */
    fun computeDiff(cached: Map<String, Long>, current: Map<String, Long>): Diff {
        val reparse = current.filterNot { (id, mtime) -> cached[id] == mtime }.keys.toList()
        val remove = cached.keys.filterNot { it in current }
        return Diff(reparse, remove)
    }
}
