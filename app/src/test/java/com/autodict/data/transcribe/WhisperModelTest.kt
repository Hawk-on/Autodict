package com.autodict.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperModelTest {

    @Test
    fun downloadUrlUsesRepoAndQ5File() {
        assertEquals(
            "https://huggingface.co/NbAiLabBeta/nb-whisper-small/resolve/main/ggml-model-q5_0.bin",
            WhisperModel.SMALL.downloadUrl,
        )
    }

    @Test
    fun localFileNameIsUniquePerSize() {
        assertEquals("nb-whisper-base-q5_0.bin", WhisperModel.BASE.localFileName)
        assertEquals("nb-whisper-small-q5_0.bin", WhisperModel.SMALL.localFileName)
        assertEquals("nb-whisper-medium-q5_0.bin", WhisperModel.MEDIUM.localFileName)
    }

    @Test
    fun frontmatterIdReflectsModel() {
        assertEquals("nb-whisper-small-q5_0", WhisperModel.SMALL.frontmatterId)
    }

    @Test
    fun fromIdFallsBackToDefault() {
        assertEquals(WhisperModel.MEDIUM, WhisperModel.fromId("medium"))
        assertEquals(WhisperModel.DEFAULT, WhisperModel.fromId(null))
        assertEquals(WhisperModel.DEFAULT, WhisperModel.fromId("tøys"))
    }
}
