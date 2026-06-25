package com.autodict.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * All lagring mot den brukarvalde mappa (Storage Access Framework).
 *
 * Prinsipp (CLAUDE.md): vi held berre den persisterte tree-URI-en og reknar ut
 * [DocumentFile] på nytt kvar gong. Mappa er kjelda til sanning; ingen skjult database.
 * All I/O på [Dispatchers.IO].
 */
class SafRepository(
    private val context: Context,
    private val settings: AppSettings,
) {
    private val resolver get() = context.contentResolver

    /** Tek vare på vedvarande lese-/skrive-løyve og lagrar tree-URI-en. */
    suspend fun persistTreeUri(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(treeUri, flags)
        settings.setTreeUri(treeUri.toString())
    }

    /** Gløymer mappa (og slepp løyvet om det er teke). */
    suspend fun clearFolder() {
        currentTreeUri()?.let { uri ->
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                resolver.releasePersistableUriPermission(uri, flags)
            }
        }
        settings.setTreeUri(null)
    }

    private suspend fun currentTreeUri(): Uri? =
        settings.treeUri.first()?.let(Uri::parse)

    /** True dersom løyvet til [uri] framleis er halde (les + skriv). */
    private fun hasPersistedPermission(uri: Uri): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }

    /**
     * Rot-mappa som ein skrivbar [DocumentFile], eller null om inga mappe er vald eller
     * løyvet er tilbakekalla (t.d. etter reinstall) – då må brukaren velje på nytt.
     */
    suspend fun root(): DocumentFile? = withContext(Dispatchers.IO) {
        val uri = currentTreeUri() ?: return@withContext null
        if (!hasPersistedPermission(uri)) return@withContext null
        DocumentFile.fromTreeUri(context, uri)?.takeIf { it.isDirectory && it.canWrite() }
    }

    suspend fun hasValidFolder(): Boolean = root() != null

    /** Visningsnamn på vald mappe (siste ledd), eller null. */
    suspend fun folderDisplayName(): String? = root()?.name

    private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name)
    }

    /**
     * Skriv ei tekstfil i `<rot>/<folders...>/<fileName>` og returnerer URI-en, eller null
     * ved feil (inga mappe, manglande løyve, I/O-feil).
     *
     * Overskriv eksisterande fil med same namn. TODO (M2): atomisk skriving (temp + rename)
     * for sync-sensitive ekte oppføringar; for testfil i M1 held direkte skriving.
     */
    suspend fun writeTextFile(
        folders: List<String>,
        fileName: String,
        mimeType: String,
        content: String,
    ): Uri? = withContext(Dispatchers.IO) {
        var dir = root() ?: return@withContext null
        for (folder in folders) {
            dir = findOrCreateDir(dir, folder) ?: return@withContext null
        }
        dir.findFile(fileName)?.delete()
        val file = dir.createFile(mimeType, fileName) ?: return@withContext null
        runCatching {
            resolver.openOutputStream(file.uri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("openOutputStream gav null")
        }.fold(
            onSuccess = { file.uri },
            onFailure = { file.delete(); null },
        )
    }

    /** Les innhaldet i ei tekstfil, eller null ved feil. */
    suspend fun readTextFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }
}
