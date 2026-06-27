package com.autodict.data.index

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persisterer indeks-cachen som ei JSON-fil i app-privat lagring. Rein cache – kan når
 * som helst slettast og byggjast på nytt frå mappa. Skriv atomisk (temp + rename).
 */
class IndexStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(IndexedEntry.serializer())

    suspend fun load(): List<IndexedEntry> = withContext(Dispatchers.IO) {
        runCatching {
            if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun save(entries: List<IndexedEntry>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(serializer, entries))
                if (!tmp.renameTo(file)) {
                    // Rename kan feile på tvers av filsystem – fall tilbake til kopi.
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }
}
