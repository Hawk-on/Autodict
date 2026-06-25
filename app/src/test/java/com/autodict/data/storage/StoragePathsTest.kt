package com.autodict.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class StoragePathsTest {

    private val dt = LocalDateTime.of(2026, 6, 2, 14, 3, 12)

    @Test
    fun dateFolders_byggjer_aar_og_aar_maanad() {
        assertEquals(listOf("2026", "2026-06"), StoragePaths.dateFolders(dt))
    }

    @Test
    fun dateFolders_padder_eitt_siffer_maanad() {
        val jan = LocalDateTime.of(2026, 1, 9, 8, 5, 1)
        assertEquals(listOf("2026", "2026-01"), StoragePaths.dateFolders(jan))
    }

    @Test
    fun entrySlug_med_tittel() {
        assertEquals(
            "2026-06-02T14-03-12-eit-kort-notat",
            StoragePaths.entrySlug(dt, "Eit kort notat"),
        )
    }

    @Test
    fun entrySlug_utan_tittel_er_berre_tidsstempel() {
        assertEquals("2026-06-02T14-03-12", StoragePaths.entrySlug(dt, null))
        assertEquals("2026-06-02T14-03-12", StoragePaths.entrySlug(dt, "   "))
    }

    @Test
    fun slugify_behaldar_norske_bokstavar_og_kollapsar_skilje() {
        assertEquals("blåbærtur-på-fjellet-i-år", StoragePaths.slugifyTitle("Blåbærtur på fjellet i år!"))
    }

    @Test
    fun slugify_kortar_ned_til_maxlengde() {
        val long = "a".repeat(100)
        assertEquals(40, StoragePaths.slugifyTitle(long).length)
    }
}
