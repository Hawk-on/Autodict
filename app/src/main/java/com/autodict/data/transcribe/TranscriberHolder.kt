package com.autodict.data.transcribe

import android.content.Context
import com.autodict.data.storage.AppSettings
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Delt [WhisperTranscriber] med referanseteljing.
 *
 * Modellen kan vere stor (medium ≈ 0,5 GB), så to skjermar skal ikkje laste kvar sin kopi.
 * Kvar ViewModel [acquire]-ar ved oppstart og [release]-ar i `onCleared`; når den siste
 * slepp taket, blir modellen frigjeven frå minnet.
 */
object TranscriberHolder {

    private var instance: WhisperTranscriber? = null
    private var users = 0

    @Synchronized
    fun acquire(context: Context): WhisperTranscriber {
        users++
        return instance ?: build(context.applicationContext).also { instance = it }
    }

    @Synchronized
    fun release() {
        users--
        if (users <= 0) {
            users = 0
            instance?.release()
            instance = null
        }
    }

    private fun build(app: Context): WhisperTranscriber {
        val settings = AppSettings(app)
        return WhisperTranscriber(
            downloader = ModelDownloader(File(app.filesDir, "models")),
            selectedModel = { WhisperModel.fromId(settings.whisperModelId.first()) },
        )
    }
}
