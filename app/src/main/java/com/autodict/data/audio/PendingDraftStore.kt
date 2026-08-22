package com.autodict.data.audio

import android.content.Context
import com.autodict.data.storage.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/** Eit ferdig opptak som ventar på å bli redigert og lagra. */
data class PendingDraft(
    val audioPath: String,
    val createdMillis: Long,
    val durationSeconds: Int,
)

/**
 * Held eit stoppa opptak trygt til brukaren har sett det.
 *
 * Eit opptak kan stoppast utan at appen er framme – frå varselet, eller frå låseskjermen.
 * Låg utkastet berre i minnet, ville det forsvinne om Android rydda prosessen i mellomtida,
 * og lydfila blitt liggjande i cache utan at noko peika på henne. Difor blir det skrive til
 * disk med ein gong opptaket stoppar, og først rydda når brukaren har opna utkastet.
 */
object PendingDraftStore {

    // Eigen scope fordi skrivinga skal fullføre sjølv om tenesta stoppar rett etterpå.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun save(context: Context, result: RecordingResult, createdMillis: Long) {
        val app = context.applicationContext
        val encoded = listOf(
            result.file.absolutePath,
            createdMillis.toString(),
            result.durationSeconds.toString(),
        ).joinToString(SEPARATOR)
        scope.launch { AppSettings(app).setPendingDraft(encoded) }
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        scope.launch { AppSettings(app).setPendingDraft(null) }
    }

    /**
     * Utkastet som ventar, eller null. Ei fil som er borte gir null – då er opptaket alt
     * lagra eller rydda, og ei daud referanse er verre enn ingen.
     */
    fun observe(context: Context): Flow<PendingDraft?> =
        AppSettings(context.applicationContext).pendingDraft.map { it?.let(::decode) }

    private fun decode(value: String): PendingDraft? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 3) return null
        val path = parts[0]
        if (!File(path).exists()) return null
        return PendingDraft(
            audioPath = path,
            createdMillis = parts[1].toLongOrNull() ?: return null,
            durationSeconds = parts[2].toIntOrNull() ?: 0,
        )
    }

    private const val SEPARATOR = "|"
}
