package com.autodict.data.index

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IndexStoreTest {

    @Test
    fun roundTripPreservesEntries() = runBlocking {
        val tmp = File.createTempFile("entry-index", ".json").apply { deleteOnExit() }
        val store = IndexStore(tmp)
        val entries = listOf(
            IndexedEntry(
                id = "2026-06-02T14-03-12-eit-notat",
                created = "2026-06-02T14:03:12+02:00",
                title = "Eit, notat: med teikn",
                audio = "2026-06-02T14-03-12-eit-notat.wav",
                durationSeconds = 42,
                transcribed = true,
                tags = listOf("tur", "fjell"),
                lastModified = 1_700_000_000_000L,
            ),
            IndexedEntry(id = "minimal", tags = emptyList()),
        )

        store.save(entries)

        assertEquals(entries, store.load())
    }

    @Test
    fun missingFileLoadsEmptyList() = runBlocking {
        val missing = File(System.getProperty("java.io.tmpdir"), "nope-${System.nanoTime()}.json")
        assertEquals(emptyList<IndexedEntry>(), IndexStore(missing).load())
    }
}
