package com.autodict.data.transcribe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Status under modell-nedlasting. */
sealed interface DownloadStatus {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadStatus {
        val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
    }

    data object Done : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

/**
 * Lastar ned NB-Whisper GGML-modellar til [modelsDir] (app-privat, t.d. `filesDir/models`).
 *
 * - **Resumerbar**: held ei `.part`-fil og brukar HTTP `Range` for å halde fram.
 * - **Progress**: emittar [DownloadStatus.Progress] (struping ~1 MB) via ein [Flow].
 * - **Wi-Fi-gating**: avgjerda ligg hos kallaren ([ModelDownloadSupport.downloadAllowed]).
 * - **Sjekksum**: valfri SHA256-verifisering når ein forventa hash er kjend.
 *
 * Nettverkskoden er køyretid (verifiserast på eining); den reine logikken er i
 * [ModelDownloadSupport] og er unit-testa.
 */
class ModelDownloader(private val modelsDir: File) {

    fun modelFile(model: WhisperModel): File = File(modelsDir, model.localFileName)

    fun isDownloaded(model: WhisperModel): Boolean =
        modelFile(model).let { it.exists() && it.length() > 0 }

    fun delete(model: WhisperModel): Boolean {
        val f = modelFile(model)
        File(f.parentFile, "${f.name}.part").delete()
        return f.delete()
    }

    /**
     * Lastar ned [model]. Ferdig fil ligg som [modelFile]; uferdig som `<namn>.part`.
     * @param expectedSha256 valfri hex-sjekksum for verifisering når fila er ferdig.
     */
    fun download(model: WhisperModel, expectedSha256: String? = null): Flow<DownloadStatus> = flow {
        val target = modelFile(model)
        if (target.exists() && target.length() > 0) {
            emit(DownloadStatus.Done)
            return@flow
        }
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")

        val existing = if (part.exists()) part.length() else 0L
        val conn = (URL(model.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            ModelDownloadSupport.rangeHeader(existing)?.let { setRequestProperty("Range", it) }
        }

        try {
            conn.connect()
            val code = conn.responseCode
            val resuming = code == HttpURLConnection.HTTP_PARTIAL
            if (code != HttpURLConnection.HTTP_OK && !resuming) {
                emit(DownloadStatus.Failed("HTTP $code"))
                return@flow
            }
            if (!resuming && part.exists()) part.delete()

            val remaining = conn.contentLengthLong
            val total = if (resuming) existing + remaining else remaining
            var downloaded = if (resuming) existing else 0L
            var lastEmit = -1L

            conn.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(downloaded)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        raf.write(buffer, 0, n)
                        downloaded += n
                        if (lastEmit < 0 || downloaded - lastEmit >= 1_000_000L) {
                            emit(DownloadStatus.Progress(downloaded, total))
                            lastEmit = downloaded
                        }
                    }
                }
            }

            if (expectedSha256 != null) {
                val actual = ModelDownloadSupport.sha256Hex(part)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    part.delete()
                    emit(DownloadStatus.Failed("Sjekksum stemte ikkje"))
                    return@flow
                }
            }

            if (!part.renameTo(target)) {
                target.writeBytes(part.readBytes())
                part.delete()
            }
            emit(DownloadStatus.Done)
        } catch (e: Exception) {
            emit(DownloadStatus.Failed(e.message ?: "Nedlasting feila"))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}

/** Rein, unit-testbar logikk for nedlastinga (ingen Android/nettverk). */
internal object ModelDownloadSupport {

    /** `Range`-header for å halde fram frå [existingBytes], eller null om vi startar på 0. */
    fun rangeHeader(existingBytes: Long): String? =
        if (existingBytes > 0) "bytes=$existingBytes-" else null

    /** Om nedlasting er tillaten: alltid på Wi-Fi, elles berre når «kun Wi-Fi» er av. */
    fun downloadAllowed(onWifi: Boolean, wifiOnly: Boolean): Boolean = onWifi || !wifiOnly

    fun sha256Hex(file: File): String = file.inputStream().use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            md.update(buffer, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
